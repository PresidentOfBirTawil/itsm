package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import com.itsm.desktop.DesktopTestAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The product's name on every page, the one-click demo sign-in, and the desktop launcher's helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesktopAndBrandTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void everyPageCarriesTheProductName() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("<title>Sign in · Quell</title>")))
                .andExpect(content().string(containsString("<meta name=\"application-name\" content=\"Quell\">")))
                .andExpect(content().string(containsString("class=\"brand-mark\"")))
                .andExpect(content().string(not(containsString("I-ITSM"))));
    }

    @Test
    void inDemoModeOneClickSignsInAsADemoPerson() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("Just looking around?")))
                .andExpect(content().string(containsString("value=\"tech4@itsm.local\"")));

        // What the "Employee" button sends:
        mvc.perform(post("/login").with(csrf()).param("email", "emp1@itsm.local").param("password", "emp12345"))
                .andExpect(authenticated().withRoles("EMPLOYEE"));
    }

    @Test
    void theDataFolderIsTheOneAskedForOrTheOperatingSystemsUsualPlace() {
        String before = System.getProperty("app.data-dir");
        try {
            System.setProperty("app.data-dir", "my-data");
            assertThat(DesktopTestAccess.dataDirectory()).isEqualTo(Path.of("my-data").toAbsolutePath());

            System.clearProperty("app.data-dir");
            Path usual = DesktopTestAccess.dataDirectory();
            assertThat(usual.getFileName().toString()).isIn("Quell", "quell");
            assertThat(usual.isAbsolute()).isTrue();
        } finally {
            if (before == null) {
                System.clearProperty("app.data-dir");
            } else {
                System.setProperty("app.data-dir", before);
            }
        }
    }

    @Test
    void aTakenPortIsReplacedByAFreeOne() throws IOException {
        try (ServerSocket taken = new ServerSocket(0)) {
            int port = DesktopTestAccess.freePort(taken.getLocalPort());

            assertThat(port).isNotEqualTo(taken.getLocalPort()).isPositive();
        }
    }

    @Test
    void aLeftoverPortFileFromACrashIsIgnoredAndRemoved(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("running.port"), "1");   // nothing answers on port 1

        assertThat(DesktopTestAccess.runningPort(folder)).isEmpty();
        assertThat(folder.resolve("running.port")).doesNotExist();
    }

    @Test
    void theDesktopModeIsOnlyUsedWhenAskedFor() {
        assertThat(com.itsm.desktop.DesktopLauncher.isRequested()).isFalse();
    }

    @Test
    void theStatusOfTheSignInPageIsFine() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
