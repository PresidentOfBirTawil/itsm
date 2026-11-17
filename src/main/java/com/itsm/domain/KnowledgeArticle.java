package com.itsm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A knowledge-base article: a known problem and how to solve it. Technicians
 * write them, often straight from an incident they have just resolved, and
 * they are suggested to technicians working on similar incidents and to
 * employees while they type a report (so some problems are solved without an
 * incident at all).
 */
@Entity
@Table(name = "knowledge_articles")
public class KnowledgeArticle {

    public static final int MAX_TITLE = 150;
    public static final int MAX_TEXT = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    /** What the user sees: the symptoms, in the words people use when they report it. */
    @Column(nullable = false, length = MAX_TEXT)
    private String problem;

    /** How to fix it. */
    @Column(nullable = false, length = MAX_TEXT)
    private String solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ITService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    /** The incident the article was written from, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_incident_id")
    private Incident sourceIncident;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Long views;

    @Column(name = "helpful_votes")
    private Long helpfulVotes;

    @Column(name = "not_helpful_votes")
    private Long notHelpfulVotes;

    /** Required by JPA. */
    protected KnowledgeArticle() {
    }

    public KnowledgeArticle(String title, String problem, String solution, Category category, ITService service,
                            User author) {
        update(title, problem, solution, category, service);
        this.author = author;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    /** Changes the content. Title, problem and solution are required. */
    public void update(String newTitle, String newProblem, String newSolution, Category newCategory,
                       ITService newService) {
        this.title = required(newTitle, "title", MAX_TITLE);
        this.problem = required(newProblem, "problem", MAX_TEXT);
        this.solution = required(newSolution, "solution", MAX_TEXT);
        this.category = newCategory;
        this.service = newService;
        this.updatedAt = LocalDateTime.now();
    }

    public void writtenFrom(Incident incident) {
        this.sourceIncident = incident;
    }

    /** Forgets the source incident, e.g. when the incidents are removed by a demo-data reset. */
    public void forgetSourceIncident() {
        this.sourceIncident = null;
    }

    public void recordView() {
        views = getViews() + 1;
    }

    /** Someone said whether the article helped them. */
    public void vote(boolean helpful) {
        if (helpful) {
            helpfulVotes = getHelpfulVotes() + 1;
        } else {
            notHelpfulVotes = getNotHelpfulVotes() + 1;
        }
    }

    /** The share of votes that said "helpful", or null before anyone voted. */
    public Integer getHelpfulPercent() {
        long total = getHelpfulVotes() + getNotHelpfulVotes();
        return total == 0 ? null : (int) Math.round(getHelpfulVotes() * 100.0 / total);
    }

    /** The reference shown to people, e.g. KB-0012. */
    public String getReference() {
        return id == null ? "KB-new" : "KB-%04d".formatted(id);
    }

    /** The text that is compared with incident reports: what the problem looks like. */
    public String searchText() {
        return title + " " + problem;
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A knowledge article needs a " + name);
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getProblem() {
        return problem;
    }

    public String getSolution() {
        return solution;
    }

    public Category getCategory() {
        return category;
    }

    public ITService getService() {
        return service;
    }

    public User getAuthor() {
        return author;
    }

    public Incident getSourceIncident() {
        return sourceIncident;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getViews() {
        return views == null ? 0 : views;
    }

    public long getHelpfulVotes() {
        return helpfulVotes == null ? 0 : helpfulVotes;
    }

    public long getNotHelpfulVotes() {
        return notHelpfulVotes == null ? 0 : notHelpfulVotes;
    }
}
