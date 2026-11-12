package com.itsm.intelligence;

import java.util.List;

import com.itsm.domain.Category;

/**
 * A category the system proposes for an incident, with the reason.
 *
 * @param category        the proposed category
 * @param matchedKeywords the category's keywords that were found in the report
 */
public record CategorySuggestion(Category category, List<String> matchedKeywords) {

    /** E.g. "Category Network was chosen automatically because the report mentions: vpn, wifi." */
    public String explain() {
        return "Category %s was chosen automatically because the report mentions: %s."
                .formatted(category.getName(), String.join(", ", matchedKeywords));
    }
}
