package com.quell.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.quell.domain.WebhookDelivery;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /** The messages whose (next) attempt is due, oldest first. */
    @EntityGraph(attributePaths = "webhook")
    List<WebhookDelivery> findByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(WebhookDelivery.State state,
                                                                             LocalDateTime now, Limit limit);

    @EntityGraph(attributePaths = "webhook")
    List<WebhookDelivery> findAllByOrderByIdDesc(Limit limit);

    @EntityGraph(attributePaths = "webhook")
    List<WebhookDelivery> findByWebhookIdOrderByIdDesc(Long webhookId, Limit limit);

    @Modifying
    @Query("delete from WebhookDelivery delivery where delivery.webhook.id = :webhookId")
    int deleteByWebhook(Long webhookId);
}
