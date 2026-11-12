package com.itsm.web;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What an administrator fills in to add or change an incident category. */
public class CategoryForm {

    @NotBlank(message = "Please enter a name")
    @Size(max = 60, message = "The name can be at most 60 characters")
    private String name;

    /** Keywords separated by commas, e.g. "vpn, wifi, router". */
    @Size(max = 1000, message = "The keywords can be at most 1000 characters in total")
    private String keywords;

    /** The keywords as a list: split at commas, trimmed, empty ones dropped, at most 60 characters each. */
    public List<String> keywordList() {
        if (keywords == null) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .map(keyword -> keyword.length() > 60 ? keyword.substring(0, 60) : keyword)
                .toList();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }
}
