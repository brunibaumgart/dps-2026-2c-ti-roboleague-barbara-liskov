package com.roboleague.repository.jpa;

import static com.roboleague.support.TestValues.*;
import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.AppealRevision;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.AttemptStage;
import com.roboleague.evaluation.CappedAt;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.audit.AttemptEvent;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaAttemptRepository.class})
class JpaAttemptRepositoryTest {

    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final Rulebook RESCUE = new Rulebook(RulebookVersion.first(), new ScoringScheme(
            new MetricSheet(List.of(new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)))),
            new ScoreRules(List.of(new ObjectivesRule("Zonas despejadas", 15.0),
                    new VictimsRule("Víctimas rescatadas", RESCUED, 25.0), new JudgeSubjectiveRule("Panel técnico", 5.0)),
                    List.of(new AllObjectivesBonusRule("Todas las zonas", 4, 20.0)),
                    List.of(new PenaltyRule("Faltas", 10.0))),
            new CappedAt(10.0)), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    private static final SourceDelivery SENSORS = new SourceDelivery(
            new Measurements(new TrackPerformance(90.0, 4, 1), 12.5, Map.of()), "j-1");
    private static final SourceDelivery PANEL = new SourceDelivery(
            new JudgeScores(Map.of("j-1", 8.0, "j-2", 6.0), Map.of(RESCUED.name(), 2.0)), "j-2");

    @Autowired
    private JpaAttemptRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two sources saved over the same stored attempt: the second save is refused instead of losing the first")
    void givenTwoCopiesOfTheSameAttemptThenTheSecondSaveIsRefused() {
        Attempt awaiting = attempt("slot-race-1", "r-race", "t-race");
        awaiting.receive(SENSORS, RESCUE, audit());
        repository.save(awaiting);
        Attempt first = repository.findById(awaiting.getId()).orElseThrow();
        Attempt second = repository.findById(awaiting.getId()).orElseThrow();

        first.receive(PANEL, RESCUE, audit());
        repository.save(first);
        second.receive(PANEL, RESCUE, audit());

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was changed by someone else");
        assertThat(repository.findById(awaiting.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.EVALUATED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two first sources creating the same attempt: the second one is refused instead of overwriting it")
    void givenTwoNewAttemptsForTheSameTurnThenTheSecondSaveIsRefused() {
        Attempt bySensors = attempt("slot-race-2", "r-race", "t-race");
        bySensors.receive(SENSORS, RESCUE, audit());
        Attempt byPanel = attempt("slot-race-2", "r-race", "t-race");
        byPanel.receive(PANEL, RESCUE, audit());

        repository.save(bySensors);

        assertThatThrownBy(() -> repository.save(byPanel)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(bySensors.getId()).orElseThrow().getDeliveries()).containsExactly(SENSORS);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("An attempt saved inside one transaction can be saved again in the next one without a false conflict")
    void givenSavesInsideTransactionsThenTheRememberedVersionStaysCurrent() {
        TransactionTemplate useCase = new TransactionTemplate(transactionManager);
        repository.save(attempt("slot-race-4", "r-race", "t-race"));
        Attempt loaded = repository.findById(AttemptId.of("slot-race-4", 1)).orElseThrow();

        useCase.executeWithoutResult(status -> {
            loaded.receive(SENSORS, RESCUE, audit());
            repository.save(loaded);
        });
        useCase.executeWithoutResult(status -> {
            loaded.receive(PANEL, RESCUE, audit());
            repository.save(loaded);
        });

        assertThat(repository.findById(loaded.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.EVALUATED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("The same loaded attempt can be saved again after each change")
    void givenOneCopyThenItCanBeSavedAfterEachChange() {
        Attempt attempt = attempt("slot-race-3", "r-race", "t-race");
        attempt.receive(SENSORS, RESCUE, audit());
        repository.save(attempt);

        attempt.receive(PANEL, RESCUE, audit());
        repository.save(attempt);

        assertThat(repository.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.EVALUATED);
    }

    @Test
    @DisplayName("An attempt awaiting the judge panel comes back with what the sensors sent and still takes the panel")
    void awaitingAttemptRoundTrip() {
        Attempt attempt = attempt("slot-1", "r-1", "t-1");
        attempt.receive(SENSORS, RESCUE, audit());

        repository.save(attempt);
        Attempt stored = repository.findById(attempt.getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.AWAITING_SOURCES);
        assertThat(stored.getDeliveries()).containsExactly(SENSORS);
        assertThat(stored.getRulebookReference()).isEqualTo(new RulebookReference("ch-rescue", RulebookVersion.first()));
        stored.receive(PANEL, RESCUE, audit());
        // 4 zones => 60; 2 victims => 50; judges 7 * 5 => 35; bonus 20 capped at 10; 1 fault => -10
        assertThat(stored.getFinalScore()).isEqualTo(145.0);
    }

    @Test
    @DisplayName("An adjusted attempt with appeals open keeps every revision, event and the appeals it counts")
    void appealedAttemptRoundTrip() {
        Attempt attempt = attempt("slot-2", "r-1", "t-1");
        attempt.receive(SENSORS, RESCUE, audit());
        attempt.receive(PANEL, RESCUE, audit());
        attempt.applyPenaltyAdjustment(1, new AuditNote("j-2", "Falta vista en video"), RESCUE, audit());
        attempt.markUnderAppeal();
        attempt.markUnderAppeal();
        attempt.adjustAfterAppeal(new AppealRevision("app-1", attempt.getLatestMetrics().withPenalties(0),
                new AuditNote("arb-1", "Las faltas no existieron")), RESCUE, audit());

        repository.save(attempt);
        Attempt stored = repository.findById(attempt.getId()).orElseThrow();

        assertThat(stored.getStage()).isEqualTo(new AttemptStage(AttemptStatus.UNDER_APPEAL, 1, AttemptStatus.ADJUSTED));
        assertThat(stored.getDeliveries()).isEqualTo(attempt.getDeliveries());
        assertThat(stored.getRevisionHistory()).isEqualTo(attempt.getRevisionHistory());
        assertThat(stored.getEventHistory()).isEqualTo(attempt.getEventHistory());
        assertThat(stored.getEventHistory()).extracting(AttemptEvent::eventType).containsExactly(
                "SOURCE_RECEIVED", "SOURCE_RECEIVED", "RESULT_REGISTERED", "PENALTY_APPLIED", "SCORE_ADJUSTED",
                "APPEAL_ACCEPTED", "SCORE_ADJUSTED");
        assertThat(stored.getFinalScore()).isEqualTo(155.0);
    }

    @Test
    @DisplayName("A disqualified attempt comes back disqualified, with no score that counts")
    void disqualifiedAttemptRoundTrip() {
        Attempt attempt = attempt("slot-3", "r-1", "t-1");
        attempt.receive(SENSORS, RESCUE, audit());
        attempt.receive(PANEL, RESCUE, audit());
        attempt.disqualify("Robot fuera de pista", "j-1", audit());

        repository.save(attempt);
        Attempt stored = repository.findById(attempt.getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.DISQUALIFIED);
        assertThat(stored.countableScore()).isEmpty();
        assertThat(stored.getEventHistory()).last()
                .satisfies(event -> assertThat(event.description()).contains("Robot fuera de pista"));
    }

    @Test
    @DisplayName("Attempts are found by team and by round")
    void findsByTeamAndRound() {
        repository.save(attempt("slot-4", "r-find-1", "t-find-a"));
        repository.save(attempt("slot-5", "r-find-1", "t-find-b"));
        repository.save(attempt("slot-6", "r-find-2", "t-find-a"));

        assertThat(repository.findByTeamId("t-find-a")).extracting(stored -> stored.getId().slotId())
                .containsExactlyInAnyOrder("slot-4", "slot-6");
        assertThat(repository.findByRoundId("r-find-1")).extracting(stored -> stored.getId().slotId())
                .containsExactlyInAnyOrder("slot-4", "slot-5");
        assertThat(repository.findById(AttemptId.of("slot-unknown", 1))).isEmpty();
    }

    private static Attempt attempt(String slotId, String roundId, String teamId) {
        return Attempt.of(new AttemptIdentity(AttemptId.of(slotId, 1), roundId, teamId),
                RulebookReference.of("ch-rescue", RESCUE));
    }
}
