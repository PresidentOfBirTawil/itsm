package com.itsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The bytes of an attached file, kept apart from its {@link Attachment} so
 * that listing the files of an incident does not load the files themselves.
 */
@Entity
@Table(name = "attachment_contents")
public class AttachmentContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = Attachment.MAX_BYTES)
    private byte[] data;

    protected AttachmentContent() {
        // for JPA
    }

    AttachmentContent(byte[] data) {
        this.data = data.clone();
    }

    public byte[] getData() {
        return data.clone();
    }
}
