package com.itsm.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an administrator fills in to add or change an IT service. */
public class ServiceForm {

    @NotBlank(message = "Please enter a name")
    @Size(max = 100, message = "The name can be at most 100 characters")
    private String name;

    @NotNull(message = "Please choose a criticality")
    @Min(value = 1, message = "Criticality must be 1, 2 or 3")
    @Max(value = 3, message = "Criticality must be 1, 2 or 3")
    private Integer criticality;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCriticality() {
        return criticality;
    }

    public void setCriticality(Integer criticality) {
        this.criticality = criticality;
    }

    /** Words people use when this service is in trouble, separated by commas. */
    @Size(max = 1000, message = "The keywords can be at most 1000 characters in total")
    private String keywords;

    /** The services this one needs. */
    private java.util.List<Long> dependencyIds = new java.util.ArrayList<>();

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    /** The keywords as a list: split at commas, trimmed, empty ones dropped. */
    public java.util.List<String> keywordList() {
        if (keywords == null) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(keywords.split(",")).map(String::trim).filter(word -> !word.isEmpty()).toList();
    }

    public java.util.List<Long> getDependencyIds() {
        return dependencyIds;
    }

    public void setDependencyIds(java.util.List<Long> dependencyIds) {
        this.dependencyIds = dependencyIds == null ? new java.util.ArrayList<>() : dependencyIds;
    }
}
