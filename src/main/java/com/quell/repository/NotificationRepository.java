package com.quell.repository;

import java.util.List;
import java.util.Optional;

import com.quell.domain.Notification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** A user's most recent notifications, newest first. */
    @EntityGraph(attributePaths = "incident")
    List<Notification> findTop100ByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);

    List<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId);

    /** The number shown on the bell. */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** The newest notification of a user. */
    Optional<Notification> findFirstByRecipientIdOrderByIdDesc(Long recipientId);
}
