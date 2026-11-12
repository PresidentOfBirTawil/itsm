package com.itsm.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The two targets of one SLA policy, in minutes. */
public class SlaPolicyForm {

    private static final int ONE_WEEK = 7 * 24 * 60;

    @NotNull(message = "Please enter the response target in minutes")
    @Min(value = 1, message = "The response target must be at least 1 minute")
    @Max(value = ONE_WEEK, message = "The response target can be at most one week")
    private Integer responseMinutes;

    @NotNull(message = "Please enter the resolution target in minutes")
    @Min(value = 1, message = "The resolution target must be at least 1 minute")
    @Max(value = ONE_WEEK, message = "The resolution target can be at most one week")
    private Integer resolutionMinutes;

    public Integer getResponseMinutes() {
        return responseMinutes;
    }

    public void setResponseMinutes(Integer responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    public Integer getResolutionMinutes() {
        return resolutionMinutes;
    }

    public void setResolutionMinutes(Integer resolutionMinutes) {
        this.resolutionMinutes = resolutionMinutes;
    }
}
