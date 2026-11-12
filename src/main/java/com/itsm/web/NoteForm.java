package com.itsm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note added to an incident without changing its status. */
public class NoteForm {

    @NotBlank(message = "Please write the note first")
    @Size(max = 1000, message = "The note can be at most 1000 characters")
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
