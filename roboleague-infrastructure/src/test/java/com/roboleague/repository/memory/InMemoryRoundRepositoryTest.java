package com.roboleague.repository.memory;

import com.roboleague.scheduling.*;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRoundRepositoryTest {
    private final InMemoryRoundRepository repository = new InMemoryRoundRepository();

    @Test
    void savesAndLoadsTheCompleteAggregateWithoutDuplicatingSlotsOnReplacement() {
        Round scheduled = round("r1", "s1", 1);
        Round completed = scheduled.start().startSlot(SlotId.of("s1")).completeSlot(SlotId.of("s1")).complete();
        repository.save(scheduled);
        repository.save(completed);
        repository.save(completed);
        Round stored = repository.findById(completed.getId()).orElseThrow();
        assertThat(stored.getInfo()).isEqualTo(completed.getInfo());
        assertThat(stored.getStatus()).isEqualTo(Round.RoundStatus.COMPLETED);
        assertThat(stored.getSlots()).hasSize(1);
        Slot slot = stored.getSlots().getFirst();
        assertThat(slot.getIdentity()).isEqualTo(completed.getSlots().getFirst().getIdentity());
        assertThat(slot.getAssignedJudges()).isEqualTo(completed.getSlots().getFirst().getAssignedJudges());
        assertThat(slot.getTimeWindow()).isEqualTo(completed.getSlots().getFirst().getTimeWindow());
        assertThat(slot.getTrackInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(slot.getStatus()).isEqualTo(Slot.SlotStatus.COMPLETED);
        assertThat(repository.findBySlotId(slot.getSlotId())).containsSame(stored);
        assertThat(repository.findByScope(stored.getInfo().scope())).containsSame(stored);
        assertThat(repository.findByChallengeId(stored.getChallengeId())).containsExactly(stored);
        assertThat(repository.findAll()).containsExactly(stored);
        assertThat(scheduled.getStatus()).isEqualTo(Round.RoundStatus.SCHEDULED);
    }

    @Test
    void rejectsGlobalSlotIdentityAndScopeCollisionsWithoutPartialReplacement() {
        Round first = round("r1", "s1", 1);
        repository.save(first);
        assertThatThrownBy(() -> repository.save(round("r2", "s1", 2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Slot id");
        assertThatThrownBy(() -> repository.save(round("r2", "s2", 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("scope");
        assertThat(repository.findById(RoundId.of("r2"))).isEmpty();
        assertThat(repository.findAll()).containsExactly(first);
        assertThat(repository.findBySlotId(SlotId.of("s2"))).isEmpty();
    }

    @Test
    void queriesDistinguishChallengesAndScopesAndCollectionsCannotChangeStorage() {
        Round first = round("r1", "s1", 1);
        Round second = round("r2", "s2", 2);
        repository.save(second);
        repository.save(first);
        assertThat(repository.findByScope(second.getInfo().scope())).containsSame(second);
        assertThat(repository.findByChallengeId(ChallengeId.of("other"))).isEmpty();
        assertThat(repository.findById(RoundId.of("missing"))).isEmpty();
        assertThatThrownBy(() -> repository.findAll().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(repository.findAll()).containsExactly(first, second);
    }

    private static Round round(String id, String slotId, int number) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 8, 10, 0);
        RoundInfo info = new RoundInfo(RoundId.of(id), id,
                new RoundScope(ChallengeId.of("challenge"), EditionId.of("edition"), CategoryId.of("category"), number));
        return Round.of(info).addSlot(new Slot(new SlotIdentity(SlotId.of(slotId), info.id(), TeamId.of("team")),
                new SlotAssignment(Track.active(TrackId.of("track"), "Track", "Wood"),
                        List.of(Judge.of(JudgeId.of("judge"), "Judge", "General"))),
                new TimeWindow(start, start.plusMinutes(5)), Duration.ofMinutes(1)));
    }
}
