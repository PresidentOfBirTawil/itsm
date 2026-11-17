package com.itsm.api.model;

import java.time.LocalDateTime;

import com.itsm.domain.KnowledgeArticle;

/** A knowledge base article. */
public record ArticleView(Long id, String reference, String title, String problem, String solution, String category,
                          Integer helpfulPercent, LocalDateTime updatedAt) {

    public static ArticleView of(KnowledgeArticle article) {
        return new ArticleView(article.getId(), article.getReference(), article.getTitle(), article.getProblem(),
                article.getSolution(), article.getCategory() == null ? null : article.getCategory().getName(),
                article.getHelpfulPercent(), article.getUpdatedAt());
    }
}
