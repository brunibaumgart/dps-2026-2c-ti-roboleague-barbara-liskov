package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.usecase.RegistrationFixtures.category;
import static com.roboleague.usecase.RegistrationFixtures.edition;
import static com.roboleague.usecase.RoundFixtures.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryRoundsUseCaseTest {
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final QueryRoundsUseCase query = new QueryRoundsUseCase(new InMemoryChallengeRepository(),
            new InMemoryEditionRepository(), rounds);

    QueryRoundsUseCaseTest() {
        Category maze = category("maze", 1, 4, 14, 25, 2000);
        Edition edition = edition("ed-1", DATE, maze);
        rounds.save(round(ChallengeId.of("ch-1"), edition.getId(), maze.id(), "r-1", 1, "team-a"));
        rounds.save(round(ChallengeId.of("ch-1"), edition.getId(), maze.id(), "r-2", 2, "team-b"));
    }

    @Test
    void givenASlotThenItsRoundIsReturned() {
        assertThat(query.bySlot(SlotId.of("r-2-team-b")).getId()).isEqualTo(RoundId.of("r-2"));
    }

    @Test
    void givenAnUnknownSlotThenItIsNotFound() {
        assertThatThrownBy(() -> query.bySlot(SlotId.of("none")))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.resource()).isEqualTo("Slot"));
    }

    @Test
    void givenRepeatedIdsThenEachRoundComesOnceKeyedById() {
        Map<RoundId, Round> found = query.byIds(List.of(RoundId.of("r-2"), RoundId.of("r-1"), RoundId.of("r-2")));

        assertThat(found).containsOnlyKeys(RoundId.of("r-1"), RoundId.of("r-2"));
    }

    @Test
    void givenAMissingIdThenTheStoredReferenceIsInconsistent() {
        assertThatThrownBy(() -> query.byIds(List.of(RoundId.of("r-1"), RoundId.of("ghost"))))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining("ghost");
    }

    @Test
    void givenNoIdsThenTheMapIsEmpty() {
        assertThat(query.byIds(List.of())).isEmpty();
    }
}
