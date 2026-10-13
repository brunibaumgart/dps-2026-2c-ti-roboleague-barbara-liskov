package com.roboleague.repository.jpa;

import com.roboleague.repository.ConcurrentModificationConflict;
import com.roboleague.support.ConflictException;
import com.roboleague.PostgresContainer;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.JudgeProfile;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaRoundRepository.class})
class JpaRoundRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 10, 0);

    @Autowired
    private JpaRoundRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("A round comes back with its scope, status and every slot with track, judges, window, pause and state")
    void roundRoundTrip() {
        Round scheduled = round("ch-trip", "r-trip", 1, "s-trip-a", "s-trip-b");
        Round running = scheduled.start().startSlot(SlotId.of("s-trip-a")).cancelSlot(SlotId.of("s-trip-b"));
        repository.save(running);
        entityManager.clear();

        Round stored = repository.findById(RoundId.of("r-trip")).orElseThrow();

        assertThat(stored.getInfo()).isEqualTo(running.getInfo());
        assertThat(stored.getStatus()).isEqualTo(Round.RoundStatus.IN_PROGRESS);
        assertThat(stored.getSlots()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            Slot expected = running.getSlots().get(i);
            Slot slot = stored.getSlots().get(i);
            assertThat(slot.getIdentity()).isEqualTo(expected.getIdentity());
            assertThat(slot.getTrack()).isEqualTo(expected.getTrack());
            assertThat(slot.getAssignedJudges()).isEqualTo(expected.getAssignedJudges());
            assertThat(slot.getTimeWindow()).isEqualTo(expected.getTimeWindow());
            assertThat(slot.getTrackInterval()).isEqualTo(expected.getTrackInterval());
            assertThat(slot.getStatus()).isEqualTo(expected.getStatus());
        }
        assertThat(stored.getSlots()).extracting(Slot::getStatus)
                .containsExactly(Slot.SlotStatus.IN_PROGRESS, Slot.SlotStatus.CANCELLED);
        assertThat(stored.getSlots().getFirst().getAssignedJudges()).extracting(Judge::id)
                .containsExactly(JudgeId.of("j-1"), JudgeId.of("j-3"));
        assertThat(stored.getSlots().get(1).getTrackInterval()).isEqualTo(Duration.ofSeconds(90));
        assertThat(repository.findById(RoundId.of("r-none"))).isEmpty();
    }

    @Test
    @DisplayName("Rounds are found by slot, scope and challenge, ordered by id, without duplicating replaced slots")
    void findsRoundsBySlotScopeAndChallenge() {
        Round second = round("ch-find", "r-find-b", 2, "s-find-b");
        Round first = round("ch-find", "r-find-a", 1, "s-find-a");
        repository.save(second);
        repository.save(first);
        repository.save(first.start());
        repository.save(round("ch-find-other", "r-find-c", 1, "s-find-c"));
        entityManager.clear();

        assertThat(repository.findBySlotId(SlotId.of("s-find-b"))).get()
                .extracting(round -> round.getId().value()).isEqualTo("r-find-b");
        assertThat(repository.findBySlotId(SlotId.of("s-none"))).isEmpty();
        assertThat(repository.findByScope(first.getInfo().scope())).get()
                .satisfies(round -> assertThat(round.getStatus()).isEqualTo(Round.RoundStatus.IN_PROGRESS));
        assertThat(repository.findByScope(new RoundScope(ChallengeId.of("ch-find"), EditionId.of("ed-other"),
                CategoryId.of("cat-1"), 1))).isEmpty();
        assertThat(repository.findByChallengeId(ChallengeId.of("ch-find"))).extracting(round -> round.getId().value())
                .containsExactly("r-find-a", "r-find-b");
        assertThat(repository.findByChallengeId(ChallengeId.of("ch-none"))).isEmpty();
        assertThat(repository.findAll()).extracting(round -> round.getId().value())
                .containsSubsequence("r-find-a", "r-find-b", "r-find-c").doesNotHaveDuplicates();
        assertThat(repository.findById(first.getId()).orElseThrow().getSlots()).hasSize(1);
    }

    @Test
    @DisplayName("A scope or a slot id already used by another round is refused and nothing is stored")
    void rejectsScopeAndSlotCollisionsWithAnotherRound() {
        repository.save(round("ch-clash", "r-clash-a", 1, "s-clash-a"));

        assertThatThrownBy(() -> repository.save(round("ch-clash", "r-clash-b", 2, "s-clash-a")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Slot id");
        assertThatThrownBy(() -> repository.save(round("ch-clash", "r-clash-b", 1, "s-clash-b")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("scope");
        entityManager.clear();

        assertThat(repository.findById(RoundId.of("r-clash-b"))).isEmpty();
        assertThat(repository.findBySlotId(SlotId.of("s-clash-b"))).isEmpty();
        assertThat(repository.findByChallengeId(ChallengeId.of("ch-clash"))).singleElement()
                .satisfies(round -> assertThat(round.getId()).isEqualTo(RoundId.of("r-clash-a")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two transitions over the same stored round: the second save is refused instead of losing the first")
    void givenARoundChangedByAnotherTransactionThenTheStaleSaveIsRefused() {
        RoundId id = RoundId.of("r-race");
        repository.save(round("ch-race", "r-race", 1, "s-race-a", "s-race-b"));
        TransactionTemplate useCase = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        useCase.executeWithoutResult(status -> {
            Round loaded = repository.findById(id).orElseThrow();
            other.executeWithoutResult(inner -> repository.save(
                    repository.findById(id).orElseThrow().cancelSlot(SlotId.of("s-race-a"))));
            assertThatThrownBy(() -> repository.save(loaded.cancelSlot(SlotId.of("s-race-b"))))
                    .isInstanceOf(ConcurrentModificationConflict.class)
                    .hasMessageContaining("was changed by someone else");
            status.setRollbackOnly();
        });

        assertThat(repository.findById(id).orElseThrow().getSlots()).extracting(Slot::getStatus)
                .containsExactly(Slot.SlotStatus.CANCELLED, Slot.SlotStatus.SCHEDULED);
    }

    /** Slots in parallel on their own track, team and judges; the second one pauses its track 90 seconds. */
    private static Round round(String challenge, String id, int number, String... slotIds) {
        RoundInfo info = new RoundInfo(RoundId.of(id), "Ronda " + number,
                new RoundScope(ChallengeId.of(challenge), EditionId.of("ed-1"), CategoryId.of("cat-1"), number));
        Round round = Round.of(info);
        for (int i = 0; i < slotIds.length; i++) {
            List<Judge> judges = i == 0
                    ? List.of(Judge.of(JudgeId.of("j-1"), "Juana Díaz", "MAZE"),
                    new Judge(new JudgeProfile(JudgeId.of("j-3"), "Pablo Ruiz"), "SUMO", "SENIOR"))
                    : List.of(Judge.of(JudgeId.of("j-2"), "Marta Sosa", null));
            round = round.addSlot(new Slot(
                    new SlotIdentity(SlotId.of(slotIds[i]), info.id(), TeamId.of(id + "-team-" + i)),
                    new SlotAssignment(Track.active(TrackId.of("track-" + i), "Pista " + i, i == 0 ? "Madera" : null),
                            judges),
                    new TimeWindow(START, START.plusMinutes(5)), Duration.ofSeconds(i == 0 ? 60 : 90)));
        }
        return round;
    }
}
