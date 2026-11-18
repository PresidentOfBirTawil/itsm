package com.quell.domain;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** A file attached to an incident: a screenshot of the error, a PDF, a log file. */
@Entity
@Table(name = "attachments", indexes = @Index(name = "idx_attachments_incident", columnList = "incident_id"))
public class Attachment {

    /** The largest file that can be attached: 5 MB. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** How many files one incident can have. */
    public static final int MAX_PER_INCIDENT = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(nullable = false, length = 150)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileKind kind;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "content_id", nullable = false, unique = true)
    private AttachmentContent content;

    protected Attachment() {
        // for JPA
    }

    public Attachment(Incident incident, User uploadedBy, String fileName, FileKind kind, byte[] data) {
        this.incident = Objects.requireNonNull(incident);
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
        this.fileName = Objects.requireNonNull(fileName);
        this.kind = Objects.requireNonNull(kind);
        this.size = data.length;
        this.content = new AttachmentContent(data);
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public FileKind getKind() {
        return kind;
    }

    public long getSize() {
        return size;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public byte[] getData() {
        return content.getData();
    }

    public boolean isImage() {
        return kind.isImage();
    }

    /** The size for people: "820 bytes", "240 KB", "1.4 MB". */
    public String getSizeLabel() {
        return sizeLabel(size);
    }

    public static String sizeLabel(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** A Bootstrap icon for the kind of file. */
    public String getIcon() {
        return switch (kind) {
            case PDF -> "bi-file-earmark-pdf";
            case TEXT -> "bi-file-earmark-text";
            default -> "bi-file-earmark-image";
        };
    }
}
