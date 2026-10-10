package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerDeductions;
import com.roboleague.evaluation.scheme.LowerTime;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.audit;

/**
 * A maze rulebook for the standings tests: 20 points per objective, 10 off per fault, best 2 of 3 rounds and a
 * chain of three criteria (total, time, deductions).
 */
final class StandingsFixtures {
    static final ChallengeId CHALLENGE = ChallengeId.of("ch-maze");
    static final JudgeId JUDGE = JudgeId.of("j-1");
    static final RankingScheme BEST_2_OF_3 = new RankingScheme(new BestNOfM(2, 3),
            List.of(new HigherTotal(), new LowerTime(), new LowerDeductions()));
    static final Rulebook RULEBOOK = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(
            List.of(new ObjectivesRule("Objetivos", 20.0)), List.of(new PenaltyRule("Faltas", 10.0))), BEST_2_OF_3);

    private StandingsFixtures() {
    }

    static TeamIdentity team(String id) {
        return TeamIdentity.of(TeamId.of(id), "Team " + id);
    }

    /**
     * A scored attempt of the team in the round, on a slot named after both.
     */
    static Attempt scored(String teamId, String roundId, double seconds, int objectives, int faults) {
        Attempt attempt = opened(teamId, roundId, 1);
        attempt.receive(new SourceDelivery(run(seconds, objectives, faults), JUDGE), RULEBOOK, audit());
        return attempt;
    }

    static Attempt opened(String teamId, String roundId, int number) {
        AttemptId id = AttemptId.of(slotOf(teamId, roundId), number);
        return Attempt.of(AttemptIdentity.of(id, RoundId.of(roundId), TeamId.of(teamId)),
                RulebookReference.of(CHALLENGE, RULEBOOK));
    }

    static SlotId slotOf(String teamId, String roundId) {
        return SlotId.of("slot-" + roundId + "-" + teamId);
    }

    static Measurements run(double seconds, int objectives, int faults) {
        return new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of());
    }
}
