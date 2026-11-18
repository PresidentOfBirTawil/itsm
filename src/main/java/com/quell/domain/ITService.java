package com.quell.domain;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An IT service the organisation depends on, such as the payment application. */
@Entity
@Table(name = "it_services")
public class ITService {

    public static final int MIN_CRITICALITY = 1;
    public static final int MAX_CRITICALITY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** How much the business depends on this service: 1 = low, 2 = medium, 3 = high. */
    @Min(MIN_CRITICALITY)
    @Max(MAX_CRITICALITY)
    @Column(nullable = false)
    private int criticality;

    /** Required by JPA. */
    protected ITService() {
    }

    public ITService(String name, int criticality) {
        this.name = name == null ? null : name.trim();
        setCriticality(criticality);
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

    public int getCriticality() {
        return criticality;
    }

    /**
     * The services this one needs in order to work: if one of them fails, this
     * one is probably affected too. Used for the status page, the dependency
     * map and root-cause detection.
     */
    @ManyToMany
    @JoinTable(name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
    @OrderBy("name")
    private Set<ITService> dependsOn = new LinkedHashSet<>();

    /** Words people use when this service is in trouble ("vpn", "wifi"), to recognise it in a report. */
    @ElementCollection
    @CollectionTable(name = "service_keywords", joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "keyword", nullable = false, length = 60)
    @OrderBy
    private Set<String> keywords = new LinkedHashSet<>();

    /** A read-only view: changed with {@link #replaceDependencies}, after the cycle check in ServiceGraph. */
    public Set<ITService> getDependsOn() {
        return Collections.unmodifiableSet(dependsOn);
    }

    public void replaceDependencies(Collection<ITService> services) {
        if (services.contains(this)) {
            throw new IllegalArgumentException("A service cannot depend on itself");
        }
        dependsOn.clear();
        dependsOn.addAll(services);
    }

    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    /** Replaces the keywords: lower case, trimmed, without blanks or repeats. */
    public void replaceKeywords(Collection<String> words) {
        keywords.clear();
        words.stream()
                .filter(word -> word != null && !word.isBlank())
                .map(word -> word.trim().toLowerCase())
                .map(word -> word.length() <= 60 ? word : word.substring(0, 60))
                .forEach(keywords::add);
    }

    public void setCriticality(int criticality) {
        if (criticality < MIN_CRITICALITY || criticality > MAX_CRITICALITY) {
            throw new IllegalArgumentException(
                    "Criticality must be between " + MIN_CRITICALITY + " and " + MAX_CRITICALITY);
        }
        this.criticality = criticality;
    }

    @Override
    public String toString() {
        return name;
    }
}
