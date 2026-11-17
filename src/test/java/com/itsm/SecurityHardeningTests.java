package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.itsm.security.LoginAttempts;
import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Security headers on every page, and the pause after too many wrong passwords. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTests {

    private static final String KARAN = "emp4@itsm.local";
    private static final String NOBODY = "nobody-here@itsm.local";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoginAttempts loginAttempts;

    @AfterEach
    void forgetTheAttempts() {
        loginAttempts.succeeded(KARAN);
        loginAttempts.succeeded(NOBODY);
    }

    // ---- headers ----

    @Test
    void everyPageTellsTheBrowserToRunOnlyTheSitesOwnScripts() throws Exception {
        for (String page : List.of("/", "/login")) {
            mvc.perform(get(page))
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    @Test
    void noTemplateHasInlineScriptsOrEventHandlers() throws Exception {
        String login = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        String landing = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        for (String html : List.of(login, landing)) {
            assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)[^>]*>");
            assertThat(html).doesNotContainPattern("\\son(click|submit|load|change|input)=");
        }
    }

    // ---- too many wrong passwords ----

    @Test
    void fiveWrongPasswordsPauseSignInEvenWithTheRightPassword() throws Exception {
        for (int i = 1; i < LoginAttempts.MAX_FAILURES; i++) {
            mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password("guess-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password("guess-5"))
                .andExpect(redirectedUrl("/login?locked"));
        mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));

        // Other accounts are not affected.
        mvc.perform(formLogin("/login").userParameter("email").user("emp3@itsm.local").password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void anAddressWithoutAnAccountIsPausedTheSameWaySoNothingGivesAccountsAway() throws Exception {
        for (int i = 1; i < LoginAttempts.MAX_FAILURES; i++) {
            mvc.perform(formLogin("/login").userParameter("email").user(NOBODY).password("guess-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin("/login").userParameter("email").user(NOBODY).password("guess-5"))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void aSuccessfulSignInStartsTheCountAgain() throws Exception {
        for (int i = 1; i < LoginAttempts.MAX_FAILURES; i++) {
            mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password("guess-" + i));
        }
        mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));
        mvc.perform(formLogin("/login").userParameter("email").user(KARAN).password("one-more-guess"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void thePauseEndsByItselfAndOldFailuresAreForgotten() {
        MutableClock clock = new MutableClock();
        LoginAttempts attempts = new LoginAttempts(clock);

        // Failures spread over more than the window never add up to a pause.
        for (int i = 0; i < 10; i++) {
            assertThat(attempts.failed("a@b.c")).isFalse();
            clock.advance(LoginAttempts.WINDOW.dividedBy(4).plusSeconds(1));
        }
        assertThat(attempts.isLocked("a@b.c")).isFalse();

        for (int i = 1; i < LoginAttempts.MAX_FAILURES; i++) {
            attempts.failed("x@y.z");
        }
        assertThat(attempts.failed("X@Y.Z ")).isTrue();     // the same address, however it is typed
        assertThat(attempts.isLocked("x@y.z")).isTrue();
        clock.advance(LoginAttempts.LOCK.minusSeconds(1));
        assertThat(attempts.isLocked("x@y.z")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(attempts.isLocked("x@y.z")).isFalse();
    }

    /** A clock that only moves when told to. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
