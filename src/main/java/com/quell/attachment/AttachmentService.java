package com.quell.attachment;

import java.util.List;

import com.quell.domain.Attachment;
import com.quell.domain.FileKind;
import com.quell.domain.Incident;
import com.quell.domain.IncidentAction;
import com.quell.domain.Role;
import com.quell.domain.Status;
import com.quell.domain.User;
import com.quell.error.InvalidInputException;
import com.quell.error.NotFoundException;
import com.quell.error.UnauthorizedOperationException;
import com.quell.event.IncidentEvent;
import com.quell.event.IncidentEventPublisher;
import com.quell.repository.AttachmentRepository;
import com.quell.service.IncidentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files attached to incidents: screenshots of the error, PDFs, log files.
 *
 * <p>Who may attach: the reporter and the people affected, the technician
 * working on it, and administrators, until the incident is closed. Who may
 * look: everyone who may see the incident; to anyone else a file does not
 * exist. Every file is checked by its content (see {@link FileKind}).</p>
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final IncidentService incidentService;
    private final IncidentEventPublisher events;

    public AttachmentService(AttachmentRepository attachments, IncidentService incidentService,
                             IncidentEventPublisher events) {
        this.attachments = attachments;
        this.incidentService = incidentService;
        this.events = events;
    }

    /**
     * Attaches a file to an incident.
     *
     * @throws InvalidInputException if the file is empty, too big or of a kind that is not allowed,
     *                               the incident is closed, or it already has the most files allowed
     */
    @Transactional
    public Attachment attach(String code, User uploader, String fileName, byte[] data) {
        Incident incident = incidentService.getFor(code, uploader);
        if (!mayAttach(uploader, incident)) {
            throw new UnauthorizedOperationException("Only the people working on %s can attach files to it.".formatted(code));
        }
        if (incident.getStatus() == Status.CLOSED) {
            throw new InvalidInputException("%s is closed, so no more files can be attached.".formatted(code));
        }
        if (attachments.countByIncidentId(incident.getId()) >= Attachment.MAX_PER_INCIDENT) {
            throw new InvalidInputException("%s already has %d files, the most an incident can have."
                    .formatted(code, Attachment.MAX_PER_INCIDENT));
        }
        FileKind kind = check(fileName, data);
        Attachment attachment = attachments.save(new Attachment(incident, uploader, cleanName(fileName, kind), kind, data));
        events.publish(new IncidentEvent(incident, uploader, IncidentAction.ATTACHMENT_ADDED, incident.getStatus(),
                incident.getStatus(), "Attached %s (%s).".formatted(attachment.getFileName(), attachment.getSizeLabel())));
        return attachment;
    }

    /**
     * Checks a file before it is attached, for example on the report form
     * before the incident exists.
     *
     * @return the kind of file
     * @throws InvalidInputException if it is empty, too big, or not a screenshot, PDF or text file
     */
    public FileKind check(String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidInputException("The file is empty.");
        }
        if (data.length > Attachment.MAX_BYTES) {
            throw new InvalidInputException("%s is %s; files can be at most %s.".formatted(
                    displayName(fileName), Attachment.sizeLabel(data.length), Attachment.sizeLabel(Attachment.MAX_BYTES)));
        }
        return FileKind.detect(data, fileName).orElseThrow(() -> new InvalidInputException(
                "%s cannot be attached. Attach screenshots (PNG, JPEG, GIF, WebP), PDFs or text files (.txt, .log, .csv)."
                        .formatted(displayName(fileName))));
    }

    /** The files of an incident, oldest first. */
    @Transactional(readOnly = true)
    public List<Attachment> of(Incident incident) {
        return attachments.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
    }

    /**
     * A file, for someone who may see its incident.
     *
     * @throws NotFoundException if there is no such file, or the viewer may not see its incident
     */
    @Transactional(readOnly = true)
    public Download open(Long id, User viewer) {
        Attachment attachment = attachments.findById(id)
                .filter(found -> viewer.canView(found.getIncident()))
                .orElseThrow(() -> new NotFoundException("There is no file %d.".formatted(id)));
        return new Download(attachment.getFileName(), attachment.getKind(), attachment.getData());
    }

    /**
     * Removes a file. The person who attached it can, and administrators,
     * while the incident is not closed.
     *
     * @return the incident it belonged to
     */
    @Transactional
    public Incident remove(Long id, User actor) {
        Attachment attachment = attachments.findById(id)
                .filter(found -> actor.canView(found.getIncident()))
                .orElseThrow(() -> new NotFoundException("There is no file %d.".formatted(id)));
        Incident incident = attachment.getIncident();
        if (!actor.equals(attachment.getUploadedBy()) && actor.getRole() != Role.ADMIN) {
            throw new UnauthorizedOperationException("Only the person who attached %s, or an administrator, can remove it."
                    .formatted(attachment.getFileName()));
        }
        if (incident.getStatus() == Status.CLOSED) {
            throw new InvalidInputException("%s is closed, so its files can no longer be removed.".formatted(incident.getCode()));
        }
        attachments.delete(attachment);
        events.publish(new IncidentEvent(incident, actor, IncidentAction.ATTACHMENT_REMOVED, incident.getStatus(),
                incident.getStatus(), "Removed %s.".formatted(attachment.getFileName())));
        return incident;
    }

    /** Whether the user may attach files to the incident (closed or not). */
    public boolean mayAttach(User user, Incident incident) {
        return user.getRole() == Role.EMPLOYEE ? user.canView(incident) : user.canWorkOn(incident);
    }

    /**
     * A safe name to store and show: no folders, only ordinary characters,
     * at most 100 characters, ending in the extension of its real kind.
     */
    static String cleanName(String fileName, FileKind kind) {
        String name = fileName == null ? "" : fileName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_").replaceAll("\\s+", " ").strip();
        name = name.replaceAll("^[.\\s]+", "");
        String extension = FileKind.extensionOf(name);
        boolean fits = kind == FileKind.TEXT ? List.of("txt", "log", "csv").contains(extension)
                : extension.equals(kind.getExtension()) || (kind == FileKind.JPEG && extension.equals("jpeg"));
        String base = fits ? name.substring(0, name.length() - extension.length() - 1) : name;
        if (base.isBlank()) {
            base = kind.isImage() ? "screenshot" : "file";
        }
        if (base.length() > 90) {
            base = base.substring(0, 90).strip();
        }
        return base + "." + (fits ? extension : kind.getExtension());
    }

    private static String displayName(String fileName) {
        return fileName == null || fileName.isBlank() ? "The file" : "\"" + cleanName(fileName, FileKind.TEXT)
                .replaceAll("\\.txt$", "") + "\"";
    }

    /** What is sent to the browser: the name, the kind and the bytes. */
    public record Download(String fileName, FileKind kind, byte[] data) {
    }
}
