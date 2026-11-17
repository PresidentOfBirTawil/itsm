package com.itsm.repository;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    List<KnowledgeArticle> findAllByOrderByUpdatedAtDescIdDesc();

    Optional<KnowledgeArticle> findFirstBySourceIncidentId(Long incidentId);

    /** Before incidents are deleted (demo-data reset): articles keep their content but lose the link. */
    @Modifying
    @Query("update KnowledgeArticle article set article.sourceIncident = null")
    int forgetSourceIncidents();
}
