package com.itsm.assistant;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.itsm.assistant.AssistantTurn.Link;
import com.itsm.assistant.AssistantTurn.Reply;
import com.itsm.domain.Category;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.KnowledgeArticle;
import com.itsm.domain.Urgency;
import com.itsm.intelligence.CategoryPrediction;
import com.itsm.intelligence.CategorySuggestion;
import com.itsm.intelligence.ServiceDetector;
import com.itsm.intelligence.TriageAdvice;
import com.itsm.intelligence.text.SimilarityIndex;

/**
 * The steps of a conversation with the reporting assistant: the State pattern
 * again, as for incidents. Each step knows what to say when the conversation
 * arrives at it ({@link #enter}) and what to do with the person's answer
 * ({@link #handle}), which may move the conversation on. Every question has
 * buttons, but typed (or spoken) answers work too: "it's the vpn", "yes",
 * "only me", "I can't work".
 *
 * <pre>
 * DESCRIBE ─> SERVICE ─> KNOWN_ISSUE? ─> ARTICLES? ─> WHO ─> URGENCY ─> CATEGORY? ─> CONFIRM ─> DONE
 *                          └─ "that's my problem": joined, DONE      └─ "that fixed it": DONE
 * </pre>
 */
public enum Step {

    /** "What is not working?" Free text, typed or spoken. */
    DESCRIBE {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            String name = context.employee().getName().split("\\s+")[0];
            return AssistantTurn.askText(List.of(
                    "Hi " + name + "! I'll help you report an IT problem. It takes about a minute.",
                    "What is not working? Describe it in your own words, the way you would tell a colleague."),
                    "e.g. The VPN keeps disconnecting when I work from home");
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            if (text.length() < 12 || text.split("\\s+").length < 3) {
                said.add("Could you tell me a little more? What were you trying to do, and what happened?");
                return this;
            }
            conversation.description = text;
            conversation.title = shortTitle(text);
            TriageAdvice triage = context.triage().advise(text);
            conversation.suggestedImpact = triage.impact();
            conversation.suggestedImpactReason = triage.impactReason();
            conversation.suggestedUrgency = triage.urgency();
            conversation.suggestedUrgencyReason = triage.urgencyReason();
            conversation.serviceId = context.serviceDetector().detect(text, context.services().findAll())
                    .map(found -> found.service().getId()).orElse(null);
            return SERVICE;
        }
    },

    /** Which service: confirms the one recognised from the words, or offers the list. */
    SERVICE {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<ITService> all = context.services().findAllByOrderByCriticalityDescNameAsc();
            List<Reply> replies = new ArrayList<>();
            if (conversation.serviceId != null) {
                ITService guess = all.stream().filter(s -> s.getId().equals(conversation.serviceId)).findFirst().orElseThrow();
                replies.add(new Reply("Yes, " + guess.getName(), "service:" + guess.getId()));
                all.stream().filter(s -> !s.equals(guess))
                        .forEach(s -> replies.add(new Reply(s.getName(), "service:" + s.getId())));
                return new AssistantTurn(List.of("Thanks. It sounds like this is about **" + guess.getName()
                        + "**. Is that right?"), replies, List.of(), "Or type the name of the service");
            }
            all.forEach(s -> replies.add(new Reply(s.getName(), "service:" + s.getId())));
            return new AssistantTurn(List.of("Thanks. Which IT service is it about?"), replies, List.of(),
                    "Or type the name of the service");
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Optional<ITService> chosen = serviceFrom(text, conversation, context);
            if (chosen.isEmpty()) {
                said.add("I didn't recognise that service. Please choose one of the services below.");
                return this;
            }
            conversation.serviceId = chosen.get().getId();
            conversation.knownIssueCodes = context.statusService().knownIssuesFor(conversation.serviceId).stream()
                    // Nothing to offer if they reported it or already follow it.
                    .filter(known -> !context.employee().equals(known.getReporter())
                            && !known.isAffecting(context.employee()))
                    .map(Incident::getCode)
                    .toList();
            if (!conversation.knownIssueCodes.isEmpty()) {
                return KNOWN_ISSUE;
            }
            return articles(conversation, context).isEmpty() ? WHO : ARTICLES;
        }
    },

    /** There is already a serious open incident: offer to join it instead of reporting it again. */
    KNOWN_ISSUE {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            Incident known = context.incidents().findByCode(conversation.knownIssueCodes.get(0)).orElseThrow();
            int affected = known.getAffectedCount();
            return AssistantTurn.ask(List.of(
                    "Good news: we already know about a problem that may be yours.",
                    "**%s · %s**\n%s · reported %s · %d %s affected · %s"
                            .formatted(known.getCode(), known.getTitle(), known.getService().getName(),
                                    TIME.format(known.getCreatedAt()), affected, affected == 1 ? "person" : "people",
                                    known.getAssignee() == null ? "being assigned" : known.getAssignee().getName() + " is on it"),
                    "Is this your problem? If so, I'll add you to it and you'll be told the moment it is fixed."),
                    List.of(new Reply("Yes, that's my problem", "yes"), new Reply("No, mine is different", "no")));
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Boolean yes = yesOrNo(text);
            if (yes == null) {
                said.add("Is it the same problem? Please answer yes or no.");
                return this;
            }
            if (yes) {
                String code = conversation.knownIssueCodes.get(0);
                Incident incident = context.incidentService().joinAsAffected(code, context.employee());
                conversation.createdCode = code;
                int others = incident.getAffectedCount() - 1;
                said.add("Done. You are following **%s** with %d other %s, and you'll be told as soon as it is fixed. No need to report it again."
                        .formatted(code, others, others == 1 ? "person" : "people"));
                return DONE;
            }
            return articles(conversation, context).isEmpty() ? WHO : ARTICLES;
        }
    },

    /** Knowledge articles that may solve it in a minute. */
    ARTICLES {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<Link> links = articles(conversation, context).stream()
                    .map(match -> new Link(match.item().getTitle() + " · " + match.percent() + "% match",
                            "/kb/" + match.item().getId(), "bi-journal-bookmark"))
                    .toList();
            return new AssistantTurn(List.of(
                    "Before I report it: these articles from our knowledge base may solve it right away. They open in a new tab."),
                    List.of(new Reply("That fixed it, thanks!", "yes"), new Reply("I still need help", "no")),
                    links, null);
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Boolean fixed = yesOrNo(text);
            if (fixed == null) {
                said.add("Did one of the articles solve it? Please answer yes or no.");
                return this;
            }
            if (fixed) {
                said.add("Great, glad that helped! No incident needed. If it comes back, I'm here.");
                return DONE;
            }
            return WHO;
        }
    },

    /** Impact, in plain words. The smart-triage guess comes first when there is one. */
    WHO {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<String> messages = new ArrayList<>();
            messages.add("Who is affected?");
            if (conversation.suggestedImpact != null) {
                messages.add("You wrote \"" + conversation.suggestedImpactReason + "\", so my guess is marked as suggested.");
            }
            return AssistantTurn.ask(messages, suggestedFirst(conversation.suggestedImpact, List.of(
                    new Reply("Just me", "impact:LOW"),
                    new Reply("My team or department", "impact:MEDIUM"),
                    new Reply("Many people or customers", "impact:HIGH"))));
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Impact impact = Optional.ofNullable(valueFrom(text, "impact:", Impact.class))
                    .orElseGet(() -> impactIn(text, context));
            if (impact == null) {
                said.add("Sorry, I didn't get that. Is it just you, your team, or many people?");
                return this;
            }
            conversation.impact = impact;
            return URGENCY;
        }
    },

    /** Urgency, in plain words. */
    URGENCY {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<String> messages = new ArrayList<>();
            messages.add("How much does it stop your work?");
            if (conversation.suggestedUrgency != null) {
                messages.add("You wrote \"" + conversation.suggestedUrgencyReason + "\", so my guess is marked as suggested.");
            }
            return AssistantTurn.ask(messages, suggestedFirst(conversation.suggestedUrgency, List.of(
                    new Reply("It can wait", "urgency:LOW"),
                    new Reply("It slows me down", "urgency:MEDIUM"),
                    new Reply("I can't work at all", "urgency:HIGH"))));
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Urgency urgency = Optional.ofNullable(valueFrom(text, "urgency:", Urgency.class))
                    .orElseGet(() -> urgencyIn(text, context));
            if (urgency == null) {
                said.add("Sorry, I didn't get that. Can it wait, does it slow you down, or can you not work at all?");
                return this;
            }
            conversation.urgency = urgency;
            conversation.categoryId = category(conversation, context).map(Category::getId).orElse(null);
            return conversation.categoryId == null ? CATEGORY : CONFIRM;
        }
    },

    /** Only when the words gave no clue at all: what kind of problem is it? */
    CATEGORY {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<Reply> replies = context.categories().findAllByOrderByNameAsc().stream()
                    .map(category -> new Reply(category.getName(), "category:" + category.getId()))
                    .toList();
            return AssistantTurn.ask(List.of("One last thing: what kind of problem is it? If you're not sure, pick the closest."),
                    replies);
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            Optional<Category> chosen = Optional.ofNullable(idFrom(text, "category:"))
                    .flatMap(context.categories()::findById)
                    .or(() -> context.categories().findAll().stream()
                            .filter(category -> category.getName().equalsIgnoreCase(text.strip()))
                            .findFirst());
            if (chosen.isEmpty()) {
                said.add("Please choose one of the categories below.");
                return this;
            }
            conversation.categoryId = chosen.get().getId();
            return CONFIRM;
        }
    },

    /** A summary to check before the incident is created. */
    CONFIRM {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            ITService service = context.services().findById(conversation.serviceId).orElseThrow();
            Category category = context.categories().findById(conversation.categoryId).orElseThrow();
            return AssistantTurn.ask(List.of(
                    "Here's what I'll send to the service desk:",
                    "**%s**\nService: %s · Category: %s\nAffected: %s · Urgency: %s"
                            .formatted(conversation.title, service.getName(), category.getName(),
                                    conversation.impact.getLabel(), conversation.urgency.getLabel()),
                    "Shall I report it?"),
                    List.of(new Reply("Report it", "yes"), new Reply("Start again", "restart")));
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            if (!Boolean.TRUE.equals(yesOrNo(text))) {
                said.add("Press \"Report it\" to send it, or \"Start again\" to change something.");
                return this;
            }
            Incident incident = context.incidentService().report(context.employee(), conversation.title,
                    conversation.description, conversation.serviceId, conversation.categoryId, conversation.impact,
                    conversation.urgency);
            conversation.createdCode = incident.getCode();
            said.add("Reported as **%s** with priority **%s**. %s".formatted(incident.getCode(),
                    incident.getPriority(), incident.getAssignee() == null
                            ? "An administrator will assign a technician shortly."
                            : "**" + incident.getAssignee().getName() + "** will look at it."));
            if (incident.getResponseDue() != null) {
                said.add("Someone will respond by **" + TIME.format(incident.getResponseDue())
                        + "**. You'll get a notification at every step.");
            }
            return DONE;
        }
    },

    /** The end: a link to the incident, and a fresh start. */
    DONE {
        @Override
        AssistantTurn enter(Conversation conversation, AssistantContext context) {
            List<Link> links = conversation.createdCode == null ? List.of()
                    : List.of(new Link("Open " + conversation.createdCode,
                    "/employee/incidents/" + conversation.createdCode, "bi-box-arrow-up-right"));
            return new AssistantTurn(List.of("Is there anything else I can help with?"),
                    List.of(new Reply("Report another problem", "restart")), links, null);
        }

        @Override
        Step handle(Conversation conversation, String text, AssistantContext context, List<String> said) {
            said.add("To report another problem, press \"Report another problem\".");
            return this;
        }
    };

    /** Articles below this match are left out: a wrong suggestion costs the person more than none. */
    static final int ARTICLE_MATCH_PERCENT = 30;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);

    private static final Pattern YES = Pattern.compile(
            "^(y|yes|yeah|yep|yup|sure|ok|okay|correct|right|it is|that's it|that is it|send|report|report it|go|do it|please|fixed|solved|it worked)\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NO = Pattern.compile(
            "^(n|no|nope|nah|not really|different|it's different|not quite|wrong|still|didn't|did not|doesn't|does not)\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLY_ME = Pattern.compile("\\b(just|only) (me|myself|my (laptop|computer|account))\\b|^me\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM = Pattern.compile("\\b(team|department|office|floor|colleagues|several)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MANY = Pattern.compile("\\b(everyone|everybody|all|company|customers|many|whole)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANNOT_WORK = Pattern.compile("\\b(can't|cannot|can not|unable|blocked|stopped|urgent|asap|critical)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOWS = Pattern.compile("\\b(slow|slows|annoying|workaround|partly|sometimes)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAN_WAIT = Pattern.compile("\\b(wait|later|whenever|not urgent|no rush|minor)\\b",
            Pattern.CASE_INSENSITIVE);

    /** What to say when the conversation arrives at this step. */
    abstract AssistantTurn enter(Conversation conversation, AssistantContext context);

    /**
     * Handles the person's answer (typed text or the value of a button) and
     * returns the next step, or this one to ask again. Anything to say before
     * the next step's own messages goes into {@code said}.
     */
    abstract Step handle(Conversation conversation, String text, AssistantContext context, List<String> said);

    /** A title for the incident: the first sentence, cut at a word if it is long. */
    static String shortTitle(String text) {
        String first = text.strip().split("(?<=[.!?])\\s+|\\n")[0].strip().replaceAll("[.!?]+$", "");
        if (first.length() > 80) {
            String cut = first.substring(0, 80);
            int space = cut.lastIndexOf(' ');
            first = (space > 40 ? cut.substring(0, space) : cut).strip() + "…";
        }
        return first.isEmpty() ? first : Character.toUpperCase(first.charAt(0)) + first.substring(1);
    }

    /** True for yes, false for no, null when the answer is neither. */
    static Boolean yesOrNo(String text) {
        String answer = text.strip();
        if (YES.matcher(answer).matches()) {
            return true;
        }
        if (NO.matcher(answer).matches()) {
            return false;
        }
        return null;
    }

    private static Optional<ITService> serviceFrom(String text, Conversation conversation, AssistantContext context) {
        Long id = idFrom(text, "service:");
        if (id != null) {
            return context.services().findById(id);
        }
        if (conversation.serviceId != null && Boolean.TRUE.equals(yesOrNo(text))) {
            return context.services().findById(conversation.serviceId);
        }
        List<ITService> all = context.services().findAll();
        String lower = text.toLowerCase(Locale.ROOT);
        return all.stream().filter(s -> lower.contains(s.getName().toLowerCase(Locale.ROOT))).findFirst()
                .or(() -> context.serviceDetector().detect(text, all).map(ServiceDetector.Detection::service));
    }

    private static Impact impactIn(String text, AssistantContext context) {
        if (ONLY_ME.matcher(text).find()) {
            return Impact.LOW;
        }
        if (MANY.matcher(text).find()) {
            return Impact.HIGH;
        }
        if (TEAM.matcher(text).find()) {
            return Impact.MEDIUM;
        }
        return context.triage().advise(text).impact();
    }

    private static Urgency urgencyIn(String text, AssistantContext context) {
        if (CAN_WAIT.matcher(text).find()) {
            return Urgency.LOW;
        }
        if (CANNOT_WORK.matcher(text).find()) {
            return Urgency.HIGH;
        }
        if (SLOWS.matcher(text).find()) {
            return Urgency.MEDIUM;
        }
        return context.triage().advise(text).urgency();
    }

    /** Articles that match the description well enough to be worth a look. */
    private static List<SimilarityIndex.Match<KnowledgeArticle>> articles(Conversation conversation,
                                                                          AssistantContext context) {
        return context.recommender().articlesFor(conversation.description, 3).stream()
                .filter(match -> match.percent() >= ARTICLE_MATCH_PERCENT)
                .toList();
    }

    /** The category from the keywords, or else the learning classifier's best guess; empty if no word is known. */
    private static Optional<Category> category(Conversation conversation, AssistantContext context) {
        List<Category> all = context.categories().findAll();
        Optional<CategorySuggestion> byKeywords = context.categorySuggester().suggest(conversation.description, all);
        if (byKeywords.isPresent()) {
            return Optional.of(byKeywords.get().category());
        }
        return context.classifier().predict(conversation.description)
                .map(CategoryPrediction::categoryId)
                .flatMap(context.categories()::findById);
    }

    /** The replies, with the suggested one moved to the front and marked. */
    private static <E extends Enum<E>> List<Reply> suggestedFirst(E suggested, List<Reply> replies) {
        if (suggested == null) {
            return replies;
        }
        List<Reply> result = new ArrayList<>();
        for (Reply reply : replies) {
            if (reply.value().endsWith(":" + suggested.name())) {
                result.addFirst(new Reply(reply.label() + " (suggested)", reply.value()));
            } else {
                result.add(reply);
            }
        }
        return result;
    }

    private static Long idFrom(String text, String prefix) {
        if (!text.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.valueOf(text.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E valueFrom(String text, String prefix, Class<E> type) {
        if (!text.startsWith(prefix)) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
