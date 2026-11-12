package com.itsm.domain;

/**
 * Something the SLA check noticed that people should be told about: a
 * deadline that is getting close, or one that has been missed.
 */
public record SlaSignal(Type type, SlaTarget target) {

    public enum Type { AT_RISK, BREACHED }

    public static SlaSignal atRisk(SlaTarget target) {
        return new SlaSignal(Type.AT_RISK, target);
    }

    public static SlaSignal breached(SlaTarget target) {
        return new SlaSignal(Type.BREACHED, target);
    }
}
