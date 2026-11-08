package com.roboleague.api.graphql.view;

import com.roboleague.scheduling.Judge;

/**
 * GraphQL view of a judge assigned to a slot.
 */
public record JudgeView(String id, String fullName, String specialty) {

    public static JudgeView from(Judge judge) {
        return new JudgeView(judge.id().value(), judge.fullName(), judge.specialty());
    }
}
