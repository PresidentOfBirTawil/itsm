package com.quell.repository;

import java.util.List;

import com.quell.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);

    long countByIncidentId(Long incidentId);

    /** Removes every attachment and its file, for the demo reset. */
    @Modifying
    @Query(value = "delete from attachments", nativeQuery = true)
    int deleteAllAttachments();

    @Modifying
    @Query(value = "delete from attachment_contents", nativeQuery = true)
    int deleteAllContents();
}
