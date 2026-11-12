package com.itsm.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The reporter's verdict on a resolution. */
public class RatingForm {

    @NotNull(message = "Please choose a rating from 1 to 5")
    @Min(value = 1, message = "The rating must be between 1 and 5")
    @Max(value = 5, message = "The rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 1000, message = "The comment can be at most 1000 characters")
    private String comment;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
