package com.itsm.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.itsm.attachment.AttachmentService;
import com.itsm.attachment.AttachmentService.Download;
import com.itsm.domain.Attachment;
import com.itsm.domain.Incident;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.ItsmException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.repository.UserRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Uploading, viewing and removing the files of an incident. The same URLs
 * serve all three roles; after a change the user goes back to the incident
 * page of their own role.
 */
@Controller
public class AttachmentController {

    /**
     * Sent with every file: never run anything in it, even if a browser were
     * tricked into treating it as a page.
     */
    static final String FILE_POLICY = "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox";

    private final AttachmentService attachmentService;
    private final UserRepository users;

    public AttachmentController(AttachmentService attachmentService, UserRepository users) {
        this.attachmentService = attachmentService;
        this.users = users;
    }

    @PostMapping("/incidents/{code}/attachments")
    public String upload(@AuthenticationPrincipal ItsmUserDetails principal, @PathVariable String code,
                         @RequestParam(name = "file", required = false) MultipartFile file,
                         RedirectAttributes redirect) throws IOException {
        User user = user(principal);
        String page = RolePaths.incidents(user.getRole()) + "/" + code;
        try {
            if (file == null || file.isEmpty()) {
                throw new InvalidInputException("Choose a file to attach first.");
            }
            Attachment attachment = attachmentService.attach(code, user, file.getOriginalFilename(), file.getBytes());
            redirect.addFlashAttribute("success", "Attached %s.".formatted(attachment.getFileName()));
        } catch (NotFoundException | UnauthorizedOperationException e) {
            throw e; // the 404 or 403 page
        } catch (ItsmException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + page + "#attachments";
    }

    /** The file itself: images are shown in the browser, other files are downloaded. */
    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal ItsmUserDetails principal, @PathVariable Long id) {
        Download file = attachmentService.open(id, user(principal));
        ContentDisposition disposition = (file.kind().isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.kind().getMediaType()))
                .contentLength(file.data().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("Content-Security-Policy", FILE_POLICY)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(file.data());
    }

    @PostMapping("/attachments/{id}/delete")
    public String remove(@AuthenticationPrincipal ItsmUserDetails principal, @PathVariable Long id,
                         RedirectAttributes redirect) {
        User user = user(principal);
        Incident incident = attachmentService.remove(id, user);
        redirect.addFlashAttribute("success", "The file has been removed.");
        return "redirect:" + RolePaths.incidents(user.getRole()) + "/" + incident.getCode() + "#attachments";
    }

    private User user(ItsmUserDetails principal) {
        return users.findById(principal.getId()).orElseThrow();
    }
}
