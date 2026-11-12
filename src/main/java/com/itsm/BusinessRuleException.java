package com.itsm;

/**
 * A request that breaks one of the application's rules, for example an
 * illegal status change or assigning a technician who is already full.
 *
 * <p>Every exception of this kind carries a message written for the person
 * using the application, so a page can show it as it is.</p>
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
