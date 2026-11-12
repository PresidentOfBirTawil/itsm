package com.itsm.repository;

import java.util.List;

import com.itsm.domain.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findTop200ByOrderByCreatedAtDescIdDesc();
}
