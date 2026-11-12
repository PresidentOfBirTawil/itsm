package com.itsm.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A kind of incident, such as Network or Database.
 *
 * <p>Categories are rows in the database, not an enum, so an administrator can
 * add a new one without any code changing.</p>
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    /**
     * Words that hint an incident belongs to this category ("vpn", "wifi", …).
     * The keywords are part of the category: they are stored for it alone and
     * are deleted with it.
     */
    @ElementCollection
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", nullable = false, length = 60)
    @OrderBy
    private Set<String> keywords = new LinkedHashSet<>();

    /** Required by JPA. */
    protected Category() {
    }

    public Category(String name) {
        this.name = name == null ? null : name.trim();
    }

    public Category(String name, Collection<String> keywords) {
        this(name);
        keywords.forEach(this::addKeyword);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    /** A read-only view: keywords are changed through {@link #addKeyword} and {@link #removeKeyword}. */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    public void addKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("A keyword cannot be blank");
        }
        keywords.add(keyword.trim().toLowerCase());
    }

    /** Replaces all keywords with the given ones. Blank entries are ignored. */
    public void replaceKeywords(Collection<String> newKeywords) {
        keywords.clear();
        newKeywords.stream().filter(keyword -> keyword != null && !keyword.isBlank()).forEach(this::addKeyword);
    }

    public void removeKeyword(String keyword) {
        if (keyword != null) {
            keywords.remove(keyword.trim().toLowerCase());
        }
    }

    /** Two category objects are the same category when they have the same database id. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category that)) {
            return false;
        }
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Category.class.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
