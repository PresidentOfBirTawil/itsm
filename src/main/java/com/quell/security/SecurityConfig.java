package com.quell.security;

import java.util.Map;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Who may open which URL. The rules are checked on the server for every
 * request, so hiding a link in the navbar is never the only protection.
 *
 * <ul>
 *   <li>/ (the product site), /login, /register and static files: everyone</li>
 *   <li>/employee/**: role EMPLOYEE</li>
 *   <li>/technician/**: role TECHNICIAN</li>
 *   <li>/admin/** (and the H2 console in dev): role ADMIN</li>
 *   <li>anything else: any signed-in user</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String H2_CONSOLE = "/h2-console/**";

    /**
     * Only this site's own scripts run: no inline scripts, no scripts from
     * elsewhere, so injected markup cannot run code. Styles may be inline
     * (progress bars set their width that way). Nothing may frame the app.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self'",
            "connect-src 'self'",
            "worker-src 'self'",
            "manifest-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** Browser features the app does not use are switched off; the microphone is for voice input. */
    static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), payment=(), usb=(), microphone=(self)";

    /** True only when the H2 console is switched on (the dev profile). */
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry)
            throws Exception {
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/", "/login", "/register", "/error").permitAll();
            auth.requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.svg").permitAll();
            // The installable app: its description, its service worker and its offline page.
            auth.requestMatchers("/manifest.webmanifest", "/sw.js", "/offline.html").permitAll();
            // The API documentation pages (the API itself has its own rules, see ApiSecurityConfig).
            auth.requestMatchers("/swagger-ui/**").permitAll();
            // The live-updates connection (/live) is checked when it is opened, like any other
            // address. When the server later finishes it, that is not a new request to check.
            auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
            auth.requestMatchers("/employee/**").hasRole("EMPLOYEE");
            auth.requestMatchers("/technician/**").hasRole("TECHNICIAN");
            auth.requestMatchers("/admin/**").hasRole("ADMIN");
            // Writing knowledge articles: technicians and administrators. Reading /kb: anyone signed in.
            auth.requestMatchers("/kb/manage/**").hasAnyRole("TECHNICIAN", "ADMIN");
            if (h2ConsoleEnabled) {
                auth.requestMatchers(H2_CONSOLE).hasRole("ADMIN");
            }
            auth.anyRequest().authenticated();
        });

        // A paused account (too many wrong passwords) gets its own message; everything else is "wrong email or password".
        ExceptionMappingAuthenticationFailureHandler failures = new ExceptionMappingAuthenticationFailureHandler();
        failures.setExceptionMappings(Map.of(LockedException.class.getName(), "/login?locked"));
        failures.setDefaultFailureUrl("/login?error");
        http.formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/dashboard", true)
                .failureHandler(failures));

        http.logout(logout -> logout
                .logoutSuccessUrl("/login?logout"));

        // Keep a list of who is signed in, so that disabling an account can
        // end that person's sessions at once (see SignedInSessions). A person
        // may still be signed in on any number of devices.
        http.sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(event -> {
                    // Normally: send the browser to the sign-in page. But when the request that
                    // notices it is a live-updates stream being closed, the answer has long been
                    // sent and there is nothing left to redirect.
                    if (!event.getResponse().isCommitted()) {
                        event.getResponse().sendRedirect(event.getRequest().getContextPath() + "/login?expired");
                    }
                }));

        // Security headers on every page: what the browser may load and run, and what it may tell other sites.
        RequestMatcher appPages = h2ConsoleEnabled
                ? new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(H2_CONSOLE))
                : AnyRequestMatcher.INSTANCE;
        http.headers(headers -> headers
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(appPages,
                        new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY)))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY)));

        if (h2ConsoleEnabled) {
            // The H2 console is a small third-party web app: it has no CSRF
            // tokens and shows its pages in frames, so relax both for it only.
            http.csrf(csrf -> csrf.ignoringRequestMatchers(H2_CONSOLE));
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }

    /**
     * Email and password, checked with BCrypt, behind the protection against
     * password guessing (see {@link LoginAttempts}).
     */
    @Bean
    public AuthenticationProvider authenticationProvider(QuellUserDetailsService users, PasswordEncoder passwordEncoder,
                                                         LoginAttempts loginAttempts) {
        DaoAuthenticationProvider passwords = new DaoAuthenticationProvider(users);
        passwords.setPasswordEncoder(passwordEncoder);
        return new LockoutAuthenticationProvider(passwords, loginAttempts);
    }

    /** The sessions of everyone who is signed in. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the session registry when a session ends, so that it does not grow for ever. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** Passwords are stored as BCrypt hashes, never as plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
