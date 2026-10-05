package com.roboleague.evaluation.definition;

import java.util.Objects;

/**
 * Description of a score rule that can be stored and sent through the API, and rebuilt with {@code RuleCatalog}.
 */
public record RuleDefinition(String type, String name, RuleArguments arguments) {
    public RuleDefinition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("a rule needs a type");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a rule of type '" + type + "' needs a name");
        }
        Objects.requireNonNull(arguments, "arguments cannot be null");
    }
}
