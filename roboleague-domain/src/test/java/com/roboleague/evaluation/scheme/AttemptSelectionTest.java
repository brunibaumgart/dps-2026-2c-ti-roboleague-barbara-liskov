package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.audit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F1: how many attempts a turn admits and which of them counts for its round.
 */
class AttemptSelectionTest {

    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final Rulebook RULEBOOK = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(
            List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
            new RankingScheme(new AllRounds(), new BestAttempt(3), List.of(new HigherTotal())));

    @Test
    @DisplayName("The best attempt of the turn counts, however late it arrived")
    void theBestAttemptCounts() {
        Attempt first = scored(1, 1);
        Attempt second = scored(2, 4);
        Attempt third = scored(3, 2);

        assertThat(new BestAttempt(3).counted(List.of(first, second, third))).containsSame(second);
    }

    @Test
    @DisplayName("Between two attempts with the same score, the earlier one counts")
    void betweenEqualScoresTheEarlierOneCounts() {
        Attempt first = scored(1, 3);
        Attempt second = scored(2, 3);

        assertThat(new BestAttempt(2).counted(List.of(first, second))).containsSame(first);
    }

    @Test
    @DisplayName("Disqualified attempts and attempts awaiting a source do not count as the best one")
    void attemptsThatDoNotCountAreSkippedByTheBest() {
        Attempt disqualified = disqualified(scored(1, 5));
        Attempt counted = scored(2, 1);

        assertThat(new BestAttempt(3).counted(List.of(disqualified, counted, awaiting(3)))).containsSame(counted);
        assertThat(new BestAttempt(3).counted(List.of(disqualified, awaiting(2)))).isEmpty();
    }

    @Test
    @DisplayName("The last attempt with an outcome counts, even if an earlier one scored more")
    void theLastAttemptCounts() {
        Attempt best = scored(1, 5);
        Attempt last = scored(2, 1);

        assertThat(new LastAttempt(3).counted(List.of(best, last, awaiting(3)))).containsSame(last);
    }

    @Test
    @DisplayName("When the last attempt was disqualified the round gives nothing; an earlier one does not replace it")
    void aDisqualifiedLastAttemptGivesNothing() {
        assertThat(new LastAttempt(2).counted(List.of(scored(1, 5), disqualified(scored(2, 1))))).isEmpty();
    }

    @Test
    @DisplayName("A turn admits up to the attempts its rulebook declares")
    void aTurnAdmitsUpToItsAttempts() {
        assertThat(new BestAttempt(2).allowsAttempt(2)).isTrue();
        assertThat(new BestAttempt(2).allowsAttempt(3)).isFalse();
        assertThat(new LastAttempt(1).allowsAttempt(2)).isFalse();
        assertThat(BestAttempt.single()).isEqualTo(new BestAttempt(1));
    }

    @ParameterizedTest(name = "perTurn = {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("A turn admits at least one attempt")
    void aTurnAdmitsAtLeastOneAttempt(int perTurn) {
        assertThatThrownBy(() -> new BestAttempt(perTurn)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a turn admits at least one attempt: " + perTurn);
        assertThatThrownBy(() -> new LastAttempt(perTurn)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a turn admits at least one attempt: " + perTurn);
    }

    @Test
    @DisplayName("Each selection explains itself and round-trips through its definition")
    void selectionsDescribeThemselvesAndRoundTrip() {
        assertThat(BestAttempt.single().describe()).isEqualTo("1 intento por turno");
        assertThat(new BestAttempt(3).describe()).isEqualTo("el mejor de 3 intentos por turno");
        assertThat(new LastAttempt(2).describe()).isEqualTo("el último de 2 intentos por turno");
        assertThat(new BestAttempt(3).definition())
                .isEqualTo(new StrategyDefinition(BestAttempt.TYPE, Parameters.none().with("perTurn", 3)));
        assertThat(BestAttempt.from(new BestAttempt(3).definition())).isEqualTo(new BestAttempt(3));
        assertThat(LastAttempt.from(new LastAttempt(2).definition())).isEqualTo(new LastAttempt(2));
    }

    @Test
    @DisplayName("A ranking scheme without an attempt selection admits one attempt per turn")
    void aSchemeWithoutAttemptSelectionAdmitsOne() {
        RankingScheme scheme = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));

        assertThat(scheme.attemptSelection()).isEqualTo(BestAttempt.single());
        assertThat(scheme.definition().attemptSelection()).isEqualTo(BestAttempt.single().definition());
    }

    private static Attempt opened(int number) {
        return Attempt.of(AttemptIdentity.of(AttemptId.of(SlotId.of("slot-1"), number), RoundId.of("r-1"),
                TeamId.of("t-a")), RulebookReference.of(ChallengeId.of("ch-1"), RULEBOOK));
    }

    private static Attempt scored(int number, int objectives) {
        Attempt attempt = opened(number);
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(60.0, objectives, 0), 0.0, Map.of()),
                JUDGE), RULEBOOK, audit());
        return attempt;
    }

    private static Attempt disqualified(Attempt attempt) {
        attempt.disqualify(AuditNote.of(JUDGE.asActorId(), "Robot fuera de reglamento"), audit());
        return attempt;
    }

    /**
     * An attempt opened with a rulebook that also needs the judge panel, so after the measurements it still waits.
     */
    private static Attempt awaiting(int number) {
        Rulebook mixed = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(List.of(
                new ObjectivesRule("Objetivos", Points.of(20)), new JudgeSubjectiveRule("Panel", 5.0)), List.of()),
                RULEBOOK.rankingScheme());
        Attempt attempt = Attempt.of(AttemptIdentity.of(AttemptId.of(SlotId.of("slot-1"), number), RoundId.of("r-1"),
                TeamId.of("t-a")), RulebookReference.of(ChallengeId.of("ch-1"), mixed));
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(60.0, 9, 0), 0.0, Map.of()), JUDGE),
                mixed, audit());
        return attempt;
    }
}
