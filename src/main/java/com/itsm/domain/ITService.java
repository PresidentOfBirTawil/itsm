package com.itsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
