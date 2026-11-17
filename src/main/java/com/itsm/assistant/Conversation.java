package com.itsm.assistant;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.itsm.assistant.AssistantTurn.Reply;
import com.itsm.domain.Impact;
import com.itsm.domain.Urgency;

/**
 * One person's conversation with the reporting assistant: the step it is at,
 * what has been learned so far, and the transcript to show. Kept in the
 * person's session, so it survives a page reload but not signing out.
 */
public class Conversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The transcript keeps this many messages; older ones scroll away. */
    static final int TRANSCRIPT_LIMIT = 80;

    Step step = Step.DESCRIBE;
    String title;
    String description = "";
    Long serviceId;
    Long categoryId;
    Impact impact;
    Urgency urgency;
    Impact suggestedImpact;
    String suggestedImpactReason;
    Urgency suggestedUrgency;
    String suggestedUrgencyReason;
    List<String> knownIssueCodes = new ArrayList<>();
    String createdCode;

    final List<ChatMessage> transcript = new ArrayList<>();
    List<Reply> replies = List.of();
    String placeholder;

    /** Forgets everything, as if the page had been opened for the first time. */
    void reset() {
        step = Step.DESCRIBE;
        title = null;
        description = "";
        serviceId = null;
        categoryId = null;
        impact = null;
        urgency = null;
        suggestedImpact = null;
        suggestedImpactReason = null;
        suggestedUrgency = null;
        suggestedUrgencyReason = null;
        knownIssueCodes = new ArrayList<>();
        createdCode = null;
        transcript.clear();
        replies = List.of();
        placeholder = null;
    }

    void add(ChatMessage message) {
        transcript.add(message);
        if (transcript.size() > TRANSCRIPT_LIMIT) {
            transcript.subList(0, transcript.size() - TRANSCRIPT_LIMIT).clear();
        }
    }

    public Step getStep() {
        return step;
    }

    public List<ChatMessage> getTranscript() {
        return List.copyOf(transcript);
    }

    public List<Reply> getReplies() {
        return replies;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    /** The incident created (or joined) at the end, or null. */
    public String getCreatedCode() {
        return createdCode;
    }

    public boolean isFinished() {
        return step == Step.DONE;
    }
}
