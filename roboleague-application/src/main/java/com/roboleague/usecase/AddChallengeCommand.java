package com.roboleague.usecase;

import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.tournament.Challenge;

import java.util.Objects;

public record AddChallengeCommand(Challenge.Draft draft, RulebookDefinition rulebook) {
    public AddChallengeCommand {
        Objects.requireNonNull(draft, "draft cannot be null");
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
    }
}
