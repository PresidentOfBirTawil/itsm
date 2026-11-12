package com.itsm.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.Min;

/**
 * A support engineer who works on incidents.
 *
 * <p>On top of what every user has, a technician has skills (the incident
 * categories they can handle), a limit on how many incidents they can work on
 * at once, and a flag saying whether they are currently available.</p>
 */
@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    public static final int DEFAULT_MAX_WORKLOAD = 5;

    /**
     * The categories this technician can handle. A technician has many skills
     * and a category can be a skill of many technicians, so the links are kept
     * in their own table. Removing a technician never removes a category.
     */
    @ManyToMany
    @JoinTable(name = "technician_skills",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @OrderBy("name")
    private Set<Category> skills = new LinkedHashSet<>();

    /** The most open incidents this technician should hold at the same time. */
    @Min(1)
    @Column(name = "max_workload")
    private int maxWorkload = DEFAULT_MAX_WORKLOAD;

    /** False when the technician is away and must not receive new incidents. */
    private boolean available = true;

    /** Required by JPA. */
    protected Technician() {
    }

    public Technician(String name, String email, String passwordHash) {
        super(name, email, passwordHash);
    }

    public Technician(String name, String email, String passwordHash, int maxWorkload, Set<Category> skills) {
        super(name, email, passwordHash);
        setMaxWorkload(maxWorkload);
        skills.forEach(this::addSkill);
    }

    @Override
    public Role getRole() {
        return Role.TECHNICIAN;
    }

    /** A technician sees the incidents assigned to them. */
    @Override
    public boolean canAccess(Incident incident) {
        return equals(incident.getAssignee());
    }

    /** A read-only view: skills are changed through {@link #addSkill} and {@link #removeSkill}. */
    public Set<Category> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public void addSkill(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("A skill must be a category");
        }
        skills.add(category);
    }

    public void removeSkill(Category category) {
        skills.remove(category);
    }

    public boolean hasSkill(Category category) {
        return category != null && skills.contains(category);
    }

    public int getMaxWorkload() {
        return maxWorkload;
    }

    public void setMaxWorkload(int maxWorkload) {
        if (maxWorkload < 1) {
            throw new IllegalArgumentException("Max workload must be at least 1");
        }
        this.maxWorkload = maxWorkload;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
