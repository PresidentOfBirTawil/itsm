package com.itsm.web;

import com.itsm.domain.KnowledgeArticle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The form for writing or changing a knowledge article. */
public class KnowledgeForm {

    @NotBlank(message = "Please give the article a title")
    @Size(max = KnowledgeArticle.MAX_TITLE, message = "The title can be at most {max} characters")
    private String title;

    @NotBlank(message = "Please describe the problem as users see it")
    @Size(max = KnowledgeArticle.MAX_TEXT, message = "This can be at most {max} characters")
    private String problem;

    @NotBlank(message = "Please describe the solution")
    @Size(max = KnowledgeArticle.MAX_TEXT, message = "This can be at most {max} characters")
    private String solution;

    private Long categoryId;
    private Long serviceId;

    /** The incident the article is written from, if any. */
    private String sourceIncidentCode;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getSourceIncidentCode() {
        return sourceIncidentCode;
    }

    public void setSourceIncidentCode(String sourceIncidentCode) {
        this.sourceIncidentCode = sourceIncidentCode;
    }
}
