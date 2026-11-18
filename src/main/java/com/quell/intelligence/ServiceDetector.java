package com.quell.intelligence;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.quell.domain.ITService;
import org.springframework.stereotype.Component;

/**
 * Recognises which service a report is about from its words: each service
 * has keywords ("vpn", "wifi" for the network; "checkout", "card" for
 * payments), editable by an administrator. The service with the most
 * keywords found wins; no keyword, no guess.
 */
@Component
public class ServiceDetector {

    /** The detected service and the words that gave it away. */
    public record Detection(ITService service, List<String> matchedKeywords) {

        public String explain() {
            return "the report mentions: " + String.join(", ", matchedKeywords);
        }
    }

    public Optional<Detection> detect(String text, Collection<ITService> services) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lowerCase = text.toLowerCase();
        return services.stream()
                .map(service -> new Detection(service, service.getKeywords().stream()
                        .filter(keyword -> containsWholeWord(lowerCase, keyword)).sorted().toList()))
                .filter(detection -> !detection.matchedKeywords().isEmpty())
                .max(Comparator.<Detection>comparingInt(detection -> detection.matchedKeywords().size())
                        .thenComparingInt(detection -> String.join("", detection.matchedKeywords()).length())
                        .thenComparing(detection -> detection.service().getName(), Comparator.reverseOrder()));
    }

    private static boolean containsWholeWord(String text, String keyword) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])").matcher(text).find();
    }
}
