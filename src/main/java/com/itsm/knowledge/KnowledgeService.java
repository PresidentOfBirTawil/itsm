package com.itsm.knowledge;

import java.util.List;

import com.itsm.domain.Category;
import com.itsm.domain.ITService;
import com.itsm.domain.Incident;
import com.itsm.domain.KnowledgeArticle;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.intelligence.CategoryClassifier;
import com.itsm.intelligence.text.SimilarityIndex;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The knowledge base: articles that describe a known problem and its
 * solution. Searching uses the same similarity ranking as the suggestions,
 * so "vpn keeps dropping" finds "VPN disconnects every few minutes".
 */
@Service
@Transactional
public class KnowledgeService {

    private static final double SEARCH_MINIMUM = 0.05;
    private static final int SEARCH_LIMIT = 50;

    private final KnowledgeArticleRepository articles;
    private final CategoryRepository categories;
    private final ITServiceRepository services;
    private final CategoryClassifier classifier;

    public KnowledgeService(KnowledgeArticleRepository articles, CategoryRepository categories,
                            ITServiceRepository services, CategoryClassifier classifier) {
        this.articles = articles;
        this.categories = categories;
        this.services = services;
        this.classifier = classifier;
    }

    /** All articles, newest first, or those that match a search, best first. */
    @Transactional(readOnly = true)
    public List<KnowledgeArticle> search(String query) {
        List<KnowledgeArticle> all = articles.findAllByOrderByUpdatedAtDescIdDesc();
        if (query == null || query.isBlank()) {
            return all;
        }
        String lowerCase = query.trim().toLowerCase();
        List<KnowledgeArticle> ranked = SimilarityIndex.build(all, article -> article.searchText() + " " + article.getSolution())
                .search(query, SEARCH_LIMIT, SEARCH_MINIMUM).stream()
                .map(SimilarityIndex.Match::item)
                .toList();
        if (!ranked.isEmpty()) {
            return ranked;
        }
        // A reference such as "KB-0003", or a word the ranking ignores: plain text search.
        return all.stream()
                .filter(article -> article.getReference().toLowerCase().equals(lowerCase)
                        || article.getTitle().toLowerCase().contains(lowerCase))
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle get(Long id) {
        return articles.findById(id)
                .orElseThrow(() -> new NotFoundException("There is no knowledge article with the number " + id + "."));
    }

    /** Opens an article, counting the view. */
    public KnowledgeArticle view(Long id) {
        KnowledgeArticle article = get(id);
        article.recordView();
        return article;
    }

    public KnowledgeArticle create(String title, String problem, String solution, Long categoryId, Long serviceId,
                                   User author, Incident sourceIncident) {
        KnowledgeArticle article = new KnowledgeArticle(title, problem, solution, category(categoryId),
                service(serviceId), author);
        if (sourceIncident != null) {
            articles.findFirstBySourceIncidentId(sourceIncident.getId()).ifPresent(existing -> {
                throw new InvalidInputException("%s has already been written up as %s."
                        .formatted(sourceIncident.getCode(), existing.getReference()));
            });
            article.writtenFrom(sourceIncident);
        }
        articles.save(article);
        classifier.markStale();
        return article;
    }

    public KnowledgeArticle update(Long id, String title, String problem, String solution, Long categoryId,
                                   Long serviceId) {
        KnowledgeArticle article = get(id);
        article.update(title, problem, solution, category(categoryId), service(serviceId));
        classifier.markStale();
        return article;
    }

    public void delete(Long id) {
        articles.delete(get(id));
        classifier.markStale();
    }

    public KnowledgeArticle vote(Long id, boolean helpful) {
        KnowledgeArticle article = get(id);
        article.vote(helpful);
        return article;
    }

    /** Articles like this one, for "see also". */
    @Transactional(readOnly = true)
    public List<KnowledgeArticle> related(KnowledgeArticle article, int limit) {
        return SimilarityIndex.build(articles.findAll(), KnowledgeArticle::searchText)
                .search(article.searchText(), limit + 1, 0.1).stream()
                .map(SimilarityIndex.Match::item)
                .filter(other -> !other.getId().equals(article.getId()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<KnowledgeArticle> writtenFrom(Incident incident) {
        return articles.findFirstBySourceIncidentId(incident.getId());
    }

    private Category category(Long id) {
        return id == null ? null : categories.findById(id)
                .orElseThrow(() -> new NotFoundException("The chosen category does not exist."));
    }

    private ITService service(Long id) {
        return id == null ? null : services.findById(id)
                .orElseThrow(() -> new NotFoundException("The chosen service does not exist."));
    }
}
