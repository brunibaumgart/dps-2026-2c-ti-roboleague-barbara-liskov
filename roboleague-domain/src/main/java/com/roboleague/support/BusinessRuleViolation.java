package com.roboleague.support;

/**
 * A well-formed request that a business rule refuses, such as a team appealing an attempt that is not its own.
 */
public class BusinessRuleViolation extends DomainException {

    public BusinessRuleViolation(String message) {
        super(message);
    }
}
