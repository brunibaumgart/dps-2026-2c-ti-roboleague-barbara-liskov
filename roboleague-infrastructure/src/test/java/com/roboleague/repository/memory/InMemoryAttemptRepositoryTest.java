package com.roboleague.repository.memory;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAttemptRepositoryTest {
    private final InMemoryAttemptRepository repository = new InMemoryAttemptRepository();

    InMemoryAttemptRepositoryTest() {
        repository.save(attempt("s-b", "r-1"));
        repository.save(attempt("s-a", "r-3"));
        repository.save(attempt("s-c", "r-2"));
    }

    @Test
    void findsByIdsOnceOrderedByIdSkippingMissingOnesAndEmptyGivesNothing() {
        assertThat(repository.findByIds(List.of(AttemptId.parse("s-c-1"), AttemptId.parse("s-a-1"),
                AttemptId.parse("s-none-1"), AttemptId.parse("s-a-1"))))
                .extracting(attempt -> attempt.getId().value()).containsExactly("s-a-1", "s-c-1");
        assertThat(repository.findByIds(List.of())).isEmpty();
    }

    @Test
    void findsByRoundIdsOrderedByIdSkippingUnknownRoundsAndEmptyGivesNothing() {
        assertThat(repository.findByRoundIds(List.of(RoundId.of("r-3"), RoundId.of("r-1"), RoundId.of("r-3"),
                RoundId.of("r-none")))).extracting(attempt -> attempt.getId().value()).containsExactly("s-a-1", "s-b-1");
        assertThat(repository.findByRoundIds(List.of())).isEmpty();
    }

    private static Attempt attempt(String slot, String round) {
        return Attempt.of(new AttemptIdentity(AttemptId.of(SlotId.of(slot), 1), RoundId.of(round), TeamId.of("t-1")),
                new RulebookReference(ChallengeId.of("ch-1"), RulebookVersion.first()));
    }
}
