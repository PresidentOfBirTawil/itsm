package com.itsm.repository;

import java.util.List;

import com.itsm.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByEnabledTrue();

    List<Webhook> findAllByOrderByNameAsc();
}
