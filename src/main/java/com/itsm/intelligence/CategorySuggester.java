package com.itsm.intelligence;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.itsm.domain.Category;
import org.springframework.stereotype.Component;

/**
 * Proposes a category for an incident from what the reporter wrote.
 *
 * <p>How it decides: every category has keywords (an administrator can edit
 * them). The suggester looks for each keyword in the title and description as
 * a whole word, so "app" does not match "apple". The category with the most
 * keywords found wins; if two are level, the one whose matched keywords are
 * longer wins, because a longer keyword is a more specific hint. If no
 * keyword is found at all, it makes no suggestion rather than guess.</p>
 */
@Component
public class CategorySuggester {

    public Optional<CategorySuggestion> suggest(String text, Collection<Category> categories) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lowerCase = text.toLowerCase();
        return categories.stream()
                .map(category -> new CategorySuggestion(category, matchedKeywords(lowerCase, category)))
                .filter(suggestion -> !suggestion.matchedKeywords().isEmpty())
                .max(Comparator.<CategorySuggestion>comparingInt(suggestion -> suggestion.matchedKeywords().size())
                        .thenComparingInt(suggestion -> String.join("", suggestion.matchedKeywords()).length())
                        // still level: take the first by name, so the answer is always the same
                        .thenComparing(suggestion -> suggestion.category().getName(), Comparator.reverseOrder()));
    }

    private List<String> matchedKeywords(String lowerCaseText, Category category) {
        return category.getKeywords().stream()
                .filter(keyword -> containsWholeWord(lowerCaseText, keyword))
                .sorted()
                .toList();
    }

    private boolean containsWholeWord(String lowerCaseText, String keyword) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword.toLowerCase()) + "(?![a-z0-9])")
                .matcher(lowerCaseText).find();
    }
}
