package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestAttempt;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.audit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The domain numbers the attempts of a turn in the order they arrive; whoever captures never picks the number.
 */
class TurnAttemptsTest {

    private static final ChallengeId CHALLENGE = ChallengeId.of("ch-maze");
    private static final SlotId SLOT = SlotId.of("slot-1");
    private static final RoundId ROUND = RoundId.of("r-1");
    private static final TeamId TEAM = TeamId.of("t-a");
    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final Rulebook ONE_PER_TURN = sensorsOnly(1);
    private static final Rulebook TWO_PER_TURN = sensorsOnly(2);

    @Test
    @DisplayName("The first result of a turn opens its attempt 1 with the given rulebook")
    void theFirstResultOpensAttemptOne() {
        Attempt attempt = TurnAttempts.of(SLOT, List.of()).receiving(ROUND, TEAM, CHALLENGE, TWO_PER_TURN);

        assertThat(attempt.getId()).isEqualTo(AttemptId.of(SLOT, 1));
        assertThat(attempt.getIdentity()).isEqualTo(AttemptIdentity.of(AttemptId.of(SLOT, 1), ROUND, TEAM));
        assertThat(attempt.getRulebookReference()).isEqualTo(RulebookReference.of(CHALLENGE, TWO_PER_TURN));
    }

    @Test
    @DisplayName("A scored attempt makes the next result open the following number, in the order they arrive")
    void theNextResultOpensTheFollowingNumber() {
        List<Attempt> stored = new ArrayList<>();
        for (int expected = 1; expected <= 2; expected++) {
            Attempt next = TurnAttempts.of(SLOT, stored).receiving(ROUND, TEAM, CHALLENGE, TWO_PER_TURN);
            next.receive(run(expected), TWO_PER_TURN, audit());
            stored.add(next);
            assertThat(next.getId().number()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("A disqualified attempt also has an outcome, so the next result opens another attempt")
    void aDisqualifiedAttemptLetsTheNextOneOpen() {
        Attempt first = scored(TWO_PER_TURN, 1);
        first.disqualify(AuditNote.of(JUDGE.asActorId(), "Robot fuera de reglamento"), audit());

        Attempt next = TurnAttempts.of(SLOT, List.of(first)).receiving(ROUND, TEAM, CHALLENGE, TWO_PER_TURN);

        assertThat(next.getId().number()).isEqualTo(2);
    }

    @Test
    @DisplayName("F3: while the last attempt awaits a source, the next result goes to it with its own rulebook")
    void anAttemptAwaitingASourceTakesTheNextResult() {
        Rulebook mixed = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(List.of(
                new ObjectivesRule("Objetivos", Points.of(20)), new JudgeSubjectiveRule("Panel", 5.0)), List.of()),
                new RankingScheme(new AllRounds(), new BestAttempt(3), List.of(new HigherTotal())));
        Attempt awaiting = TurnAttempts.of(SLOT, List.of()).receiving(ROUND, TEAM, CHALLENGE, mixed);
        awaiting.receive(run(2), mixed, audit());

        Attempt next = TurnAttempts.of(SLOT, List.of(awaiting)).receiving(ROUND, TEAM, CHALLENGE, ONE_PER_TURN);

        assertThat(next).isSameAs(awaiting);
        assertThat(next.getRulebookReference().version()).isEqualTo(mixed.version());
        assertThat(TurnAttempts.of(SLOT, List.of(awaiting)).acceptsResult(ONE_PER_TURN)).isTrue();
    }

    @Test
    @DisplayName("A turn that already ran every attempt its rulebook admits refuses another one")
    void aTurnRefusesMoreAttemptsThanItsRulebookAdmits() {
        TurnAttempts turn = TurnAttempts.of(SLOT, List.of(scored(ONE_PER_TURN, 1)));

        assertThat(turn.acceptsResult(ONE_PER_TURN)).isFalse();
        assertThat(turn.acceptsResult(TWO_PER_TURN)).isTrue();
        assertThat(TurnAttempts.of(SLOT, List.of()).acceptsResult(ONE_PER_TURN)).isTrue();
        assertThatThrownBy(() -> turn.receiving(ROUND, TEAM, CHALLENGE, ONE_PER_TURN))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessage("Turn slot-1 already ran 1 attempt(s); rulebook v1 admits 1 intento por turno");
    }

    @Test
    @DisplayName("The stored attempts are ordered by number, whatever order the repository gives them in")
    void storedAttemptsAreOrderedByNumber() {
        Attempt first = scored(TWO_PER_TURN, 1);
        Attempt second = TurnAttempts.of(SLOT, List.of(first)).receiving(ROUND, TEAM, CHALLENGE, TWO_PER_TURN);

        assertThat(TurnAttempts.of(SLOT, List.of(second, first)).attempts()).containsExactly(first, second);
    }

    @Test
    @DisplayName("An attempt of another turn is not one of its attempts")
    void anAttemptOfAnotherTurnIsRejected() {
        Attempt elsewhere = Attempt.of(AttemptIdentity.of(AttemptId.of(SlotId.of("slot-2"), 1), ROUND, TEAM),
                RulebookReference.of(CHALLENGE, ONE_PER_TURN));

        assertThatThrownBy(() -> TurnAttempts.of(SLOT, List.of(elsewhere)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attempt slot-2-1 does not belong to turn slot-1");
    }

    @Test
    @DisplayName("Stored attempts that skip a number are inconsistent: the domain never numbers that way")
    void storedAttemptsThatSkipANumberAreInconsistent() {
        Attempt seventh = Attempt.of(AttemptIdentity.of(AttemptId.of(SLOT, 7), ROUND, TEAM),
                RulebookReference.of(CHALLENGE, ONE_PER_TURN));

        assertThatThrownBy(() -> TurnAttempts.of(SLOT, List.of(seventh)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the attempts of turn slot-1 skip number 1");
    }

    private static Attempt scored(Rulebook rulebook, int objectives) {
        Attempt attempt = TurnAttempts.of(SLOT, List.of()).receiving(ROUND, TEAM, CHALLENGE, rulebook);
        attempt.receive(run(objectives), rulebook, audit());
        return attempt;
    }

    private static SourceDelivery run(int objectives) {
        return new SourceDelivery(new Measurements(new TrackPerformance(60.0, objectives, 0), 0.0, Map.of()), JUDGE);
    }

    private static Rulebook sensorsOnly(int attemptsPerTurn) {
        return new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(
                List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                new RankingScheme(new AllRounds(), new BestAttempt(attemptsPerTurn), List.of(new HigherTotal())));
    }
}
