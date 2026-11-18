package com.quell.repository;

import java.util.List;

import com.quell.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByEnabledTrue();

    List<Webhook> findAllByOrderByNameAsc();
}
