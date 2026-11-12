package com.itsm.web;

import com.itsm.domain.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A press on one of the status buttons of the incident page: where to move to, and the note typed with it. */
public class TransitionForm {

    @NotNull(message = "Please choose what to do")
    private Status target;

    @Size(max = 1000, message = "The note can be at most 1000 characters")
    private String note;

    public Status getTarget() {
        return target;
    }

    public void setTarget(Status target) {
        this.target = target;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
