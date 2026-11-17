package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.itsm.attachment.AttachmentService;
import com.itsm.domain.Attachment;
import com.itsm.domain.Employee;
import com.itsm.domain.FileKind;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.repository.AttachmentRepository;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Screenshots and other files on incidents: what may be attached, by whom, and who may see it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentTests {

    /** The smallest valid PNG: one transparent pixel. */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0, 0, 0, 0x0A, 'I', 'D', 'A',
            'T', 0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 'I', 'E',
            'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private AttachmentRepository attachments;
    @Autowired
    private AuditEntryRepository auditEntries;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private UserRepository users;

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Incident reportAs(String email) {
        return incidentService.report((Employee) user(email), "Printer shows an error", "The printer shows E-13.",
                services.findByName("Workplace Devices").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Hardware").orElseThrow().getId(), Impact.LOW, Urgency.MEDIUM);
    }

    // ---- what may be attached ----

    @Test
    void theKindOfFileComesFromItsContentNotItsName() {
        assertThat(FileKind.detect(PNG, "anything.bin")).contains(FileKind.PNG);
        assertThat(FileKind.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}, "photo.jpg")).contains(FileKind.JPEG);
        assertThat(FileKind.detect("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII), "report.pdf")).contains(FileKind.PDF);
        assertThat(FileKind.detect("ERROR 42 at line 7\n".getBytes(StandardCharsets.UTF_8), "app.log")).contains(FileKind.TEXT);

        assertThat(FileKind.detect(SVG, "picture.svg")).isEmpty();
        assertThat(FileKind.detect("<html><script>x</script></html>".getBytes(StandardCharsets.UTF_8), "fake.png")).isEmpty();
        assertThat(FileKind.detect(new byte[]{'M', 'Z', (byte) 0x90, 0}, "setup.txt")).isEmpty();   // not text
        assertThat(FileKind.detect("plain words".getBytes(StandardCharsets.UTF_8), "notes.html")).isEmpty();
    }

    @Test
    void theReporterAttachesAScreenshotAndItIsInTheTimeline() {
        Incident incident = reportAs("emp1@itsm.local");
        Attachment attachment = attachmentService.attach(incident.getCode(), user("emp1@itsm.local"), "C:\\Users\\priya\\error.png", PNG);

        assertThat(attachment.getFileName()).isEqualTo("error.png");
        assertThat(attachment.getKind()).isEqualTo(FileKind.PNG);
        assertThat(attachment.getSize()).isEqualTo(PNG.length);
        assertThat(attachmentService.of(incident)).containsExactly(attachment);
        assertThat(auditEntries.findByIncidentIdOrderByOccurredAtAscIdAsc(incident.getId()))
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo(IncidentAction.ATTACHMENT_ADDED);
                    assertThat(entry.getNote()).isEqualTo("Attached error.png (" + PNG.length + " bytes).");
                });
    }

    @Test
    void namesAreMadeSafeAndEndInTheRealExtension() {
        Incident incident = reportAs("emp1@itsm.local");
        User priya = user("emp1@itsm.local");
        assertThat(attachmentService.attach(incident.getCode(), priya, "../../etc/passwd<script>.png", PNG).getFileName())
                .isEqualTo("passwd_script_.png");
        assertThat(attachmentService.attach(incident.getCode(), priya, "screen shot.exe", PNG).getFileName())
                .isEqualTo("screen shot.exe.png");
        assertThat(attachmentService.attach(incident.getCode(), priya, "", PNG).getFileName()).isEqualTo("screenshot.png");
    }

    @Test
    void scriptsTooBigFilesAndEmptyFilesAreRefused() {
        Incident incident = reportAs("emp1@itsm.local");
        User priya = user("emp1@itsm.local");
        byte[] huge = Arrays.copyOf(PNG, Attachment.MAX_BYTES + 1);

        assertThatThrownBy(() -> attachmentService.attach(incident.getCode(), priya, "picture.svg", SVG))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("cannot be attached");
        assertThatThrownBy(() -> attachmentService.attach(incident.getCode(), priya, "big.png", huge))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("at most 5.0 MB");
        assertThatThrownBy(() -> attachmentService.attach(incident.getCode(), priya, "empty.png", new byte[0]))
                .isInstanceOf(InvalidInputException.class).hasMessage("The file is empty.");
        assertThat(attachments.countByIncidentId(incident.getId())).isZero();
    }

    @Test
    void anIncidentHasAtMostTenFiles() {
        Incident incident = reportAs("emp1@itsm.local");
        User priya = user("emp1@itsm.local");
        for (int i = 0; i < Attachment.MAX_PER_INCIDENT; i++) {
            attachmentService.attach(incident.getCode(), priya, "shot" + i + ".png", PNG);
        }
        assertThatThrownBy(() -> attachmentService.attach(incident.getCode(), priya, "one-more.png", PNG))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("already has 10 files");
    }

    // ---- who may attach, see and remove ----

    @Test
    void otherEmployeesCanNeitherAttachNorSeeTheFiles() {
        Incident incident = reportAs("emp1@itsm.local");
        Attachment attachment = attachmentService.attach(incident.getCode(), user("emp1@itsm.local"), "error.png", PNG);

        assertThatThrownBy(() -> attachmentService.attach(incident.getCode(), user("emp2@itsm.local"), "mine.png", PNG))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThatThrownBy(() -> attachmentService.open(attachment.getId(), user("emp2@itsm.local")))
                .isInstanceOf(NotFoundException.class);
        assertThat(attachmentService.open(attachment.getId(), user("admin@itsm.local")).data()).isEqualTo(PNG);
    }

    @Test
    void onlyTheUploaderOrAnAdministratorCanRemoveAFile() {
        Incident incident = reportAs("emp1@itsm.local");
        Attachment attachment = attachmentService.attach(incident.getCode(), user("emp1@itsm.local"), "error.png", PNG);
        User assignee = incidents.findByCode(incident.getCode()).orElseThrow().getAssignee();

        assertThatThrownBy(() -> attachmentService.remove(attachment.getId(), assignee))
                .isInstanceOf(UnauthorizedOperationException.class);
        attachmentService.remove(attachment.getId(), user("admin@itsm.local"));
        assertThat(attachments.findById(attachment.getId())).isEmpty();
    }

    // ---- the web side ----

    @Test
    void uploadingFromTheIncidentPageAndViewingTheImage() throws Exception {
        Incident incident = reportAs("emp1@itsm.local");
        MockHttpSession priya = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(multipart("/incidents/" + incident.getCode() + "/attachments")
                        .file(new MockMultipartFile("file", "error.png", "image/png", PNG)).session(priya).with(csrf()))
                .andExpect(redirectedUrl("/employee/incidents/" + incident.getCode() + "#attachments"))
                .andExpect(flash().attribute("success", "Attached error.png."));
        Attachment attachment = attachments.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()).getFirst();

        mvc.perform(get("/employee/incidents/" + incident.getCode()).session(priya))
                .andExpect(content().string(containsString("/attachments/" + attachment.getId())))
                .andExpect(content().string(containsString("Drop a screenshot here")));
        mvc.perform(get("/attachments/" + attachment.getId()).session(priya))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/attachments/" + attachment.getId()).session(signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFileThatIsNotAllowedIsExplainedOnTheIncidentPage() throws Exception {
        Incident incident = reportAs("emp1@itsm.local");
        mvc.perform(multipart("/incidents/" + incident.getCode() + "/attachments")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", SVG))
                        .session(signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)).with(csrf()))
                .andExpect(redirectedUrl("/employee/incidents/" + incident.getCode() + "#attachments"))
                .andExpect(flash().attribute("error", containsString("cannot be attached")));
    }

    @Test
    void textFilesAndPdfsAreDownloadedNotShown() throws Exception {
        Incident incident = reportAs("emp1@itsm.local");
        Attachment log = attachmentService.attach(incident.getCode(), user("emp1@itsm.local"), "app.log",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/attachments/" + log.getId()).session(signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void aScreenshotCanBeAddedWhenReporting() throws Exception {
        long before = incidents.count();
        mvc.perform(multipart("/employee/incidents")
                        .file(new MockMultipartFile("screenshot", "error.png", "image/png", PNG))
                        .param("title", "Monitor flickers").param("description", "The second monitor flickers.")
                        .param("serviceId", String.valueOf(services.findByName("Workplace Devices").orElseThrow().getId()))
                        .param("impact", "LOW").param("urgency", "MEDIUM")
                        .session(signIn("emp3@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Incident incident = incidents.findAll().stream().filter(i -> i.getTitle().equals("Monitor flickers")).findFirst().orElseThrow();
        assertThat(incidents.count()).isEqualTo(before + 1);
        assertThat(attachments.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()))
                .extracting(Attachment::getFileName).containsExactly("error.png");
    }

    @Test
    void aWrongScreenshotStopsTheReportBeforeAnythingIsSaved() throws Exception {
        long before = incidents.count();
        mvc.perform(multipart("/employee/incidents")
                        .file(new MockMultipartFile("screenshot", "logo.svg", "image/svg+xml", SVG))
                        .param("title", "Monitor flickers").param("description", "The second monitor flickers.")
                        .param("serviceId", String.valueOf(services.findByName("Workplace Devices").orElseThrow().getId()))
                        .param("impact", "LOW").param("urgency", "MEDIUM")
                        .session(signIn("emp3@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be attached")))
                .andExpect(content().string(containsString("Please choose it again")));
        assertThat(incidents.count()).isEqualTo(before);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
