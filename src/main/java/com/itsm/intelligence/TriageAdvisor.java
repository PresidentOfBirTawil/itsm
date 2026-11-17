package com.itsm.intelligence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itsm.domain.Impact;
import com.itsm.domain.Urgency;
import org.springframework.stereotype.Component;

/**
 * Smart triage: reads a report the way an experienced service-desk agent
 * would and suggests the impact and urgency. "Nobody in finance can log in"
 * means many people; "no rush" means it can wait.
 *
 * <p>Reporters often choose Medium for everything. The suggestion is shown
 * next to the form with the phrase that triggered it, and the reporter
 * decides. Rules are checked in order and the first match wins, so a more
 * specific phrase ("not urgent") is checked before a more general one
 * ("urgent").</p>
 */
@Component
public class TriageAdvisor {

    private static final List<Map.Entry<Pattern, Impact>> IMPACT_RULES = List.of(
            rule("only me|just me|only my|just my|my (?:own )?(?:laptop|computer|pc|desktop|monitor|keyboard|mouse|phone|headset|account|mailbox|printer)", Impact.LOW),
            rule("everyone|everybody|all (?:users|staff|employees|customers|of us|branches|offices)|whole (?:company|office|organisation|organization)|entire (?:company|office|organisation|organization|floor|building)|company-?wide|nobody can|no one can|customers", Impact.HIGH),
            rule("our team|the team|whole team|entire team|department|several (?:people|users|colleagues)|multiple (?:people|users)|many (?:people|users|of us)|colleagues|the floor|finance|sales team|support staff", Impact.MEDIUM));

    private static final List<Map.Entry<Pattern, Urgency>> URGENCY_RULES = List.of(
            rule("not urgent|no rush|whenever|when you (?:can|have time)|low priority|minor|cosmetic|nice to have|can wait", Urgency.LOW),
            rule("cannot work|can't work|cant work|unable to work|work (?:has )?stopped|stopped working|completely|outage|is down|are down|went down|blocked|urgent|asap|critical|emergency|unable to (?:complete|process|log ?in|sign in|access|pay)|cannot (?:log ?in|sign in|access|pay)|not working at all|losing (?:money|sales|customers)", Urgency.HIGH),
            rule("slow|sometimes|intermittent|every (?:few )?(?:minutes|hours)|keeps (?:dropping|disconnecting|crashing|freezing)|workaround|delayed|from time to time", Urgency.MEDIUM));

    public TriageAdvice advise(String text) {
        if (text == null || text.isBlank()) {
            return new TriageAdvice(null, null, null, null);
        }
        String lowerCase = text.toLowerCase();
        Optional<Map.Entry<String, Impact>> impact = firstMatch(lowerCase, IMPACT_RULES);
        Optional<Map.Entry<String, Urgency>> urgency = firstMatch(lowerCase, URGENCY_RULES);
        return new TriageAdvice(
                impact.map(Map.Entry::getValue).orElse(null), impact.map(Map.Entry::getKey).orElse(null),
                urgency.map(Map.Entry::getValue).orElse(null), urgency.map(Map.Entry::getKey).orElse(null));
    }

    private static <V> Optional<Map.Entry<String, V>> firstMatch(String text, List<Map.Entry<Pattern, V>> rules) {
        for (Map.Entry<Pattern, V> rule : rules) {
            Matcher matcher = rule.getKey().matcher(text);
            if (matcher.find()) {
                return Optional.of(Map.entry(matcher.group(), rule.getValue()));
            }
        }
        return Optional.empty();
    }

    private static <V> Map.Entry<Pattern, V> rule(String phrases, V value) {
        return Map.entry(Pattern.compile("(?<![a-z])(?:" + phrases + ")(?![a-z])"), value);
    }
}
