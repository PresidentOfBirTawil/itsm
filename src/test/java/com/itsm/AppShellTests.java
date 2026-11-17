package com.itsm;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Quell as an installable app (PWA): manifest, service worker, offline page, and desktop alerts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppShellTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void theManifestDescribesAnInstallableApp() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
                .andExpect(jsonPath("$.name").value("Quell"))
                .andExpect(jsonPath("$.display").value("standalone"))
                .andExpect(jsonPath("$.start_url").value("/dashboard"))
                .andExpect(jsonPath("$.icons[?(@.purpose == 'maskable')].src").value("/img/logo-maskable-512.png"));
        mvc.perform(get("/img/logo-maskable-512.png")).andExpect(status().isOk());
    }

    @Test
    void theServiceWorkerAndOfflinePageNeedNoSignIn() throws Exception {
        mvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OFFLINE_PAGE")));
        mvc.perform(get("/offline.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You're offline")));
    }

    @Test
    void everyPageLinksTheManifest() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("<link rel=\"manifest\" href=\"/manifest.webmanifest\">")));
        mvc.perform(get("/employee/dashboard").session(signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(content().string(containsString("data-desktop-alerts")))
                .andExpect(content().string(containsString("data-install-app")));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
