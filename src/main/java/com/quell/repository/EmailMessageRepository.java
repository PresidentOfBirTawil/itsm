package com.quell.repository;

import java.util.List;

import com.quell.domain.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findTop200ByOrderByCreatedAtDescIdDesc();
}
