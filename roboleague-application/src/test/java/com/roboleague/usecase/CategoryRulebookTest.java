package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.roboleague.usecase.RoundFixtures.round;
import static org.assertj.core.api.Assertions.assertThat;

class CategoryRulebookTest {
    private static final ChallengeId CHALLENGE = ChallengeId.of("ch-1");
    private static final CategoryId MAZE = CategoryId.of("maze");

    private final ReadCounts reads = new ReadCounts();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final CategoryRulebook rulebook = new CategoryRulebook(new InMemoryRoundRepository(),
            new CountingAttemptRepository(attempts, reads));
    private final Challenge challenge = Challenge.draft(CHALLENGE, EditionId.of("ed-1"), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                    new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    private final Round first = round(CHALLENGE, EditionId.of("ed-1"), MAZE, "r-1", 1, "team-a", "team-b");
    private final Round second = round(CHALLENGE, EditionId.of("ed-1"), MAZE, "r-2", 2, "team-a");

    CategoryRulebookTest() {
        store("r-2-team-a", second, 1);
        store("r-1-team-b", first, 1);
        store("r-1-team-a", first, 2);
        store("r-1-team-a", first, 1);
    }

    @Test
    void attemptsOfTwoRoundsCostOneReadAndComeByRoundSlotAndNumber() {
        // Rounds given in the opposite order to their ids: the position in the list wins, not the id text.
        List<Attempt> played = rulebook.attemptsOf(List.of(second, first));

        assertThat(played).extracting(Attempt::getId).containsExactly(
                AttemptId.of(SlotId.of("r-2-team-a"), 1),
                AttemptId.of(SlotId.of("r-1-team-a"), 1), AttemptId.of(SlotId.of("r-1-team-a"), 2),
                AttemptId.of(SlotId.of("r-1-team-b"), 1));
        assertThat(reads.of("findByRoundIds")).isEqualTo(1);
        assertThat(reads.of("findByRoundId")).isZero();
        assertThat(reads.total()).isEqualTo(1);
    }

    @Test
    void attemptsOfNoRoundsIsEmpty() {
        assertThat(rulebook.attemptsOf(List.of())).isEmpty();
    }

    private void store(String slot, Round round, int number) {
        attempts.save(Attempt.of(new AttemptIdentity(AttemptId.of(SlotId.of(slot), number), round.getId(),
                TeamId.of(slot.substring(slot.indexOf("team")))), RulebookReference.of(CHALLENGE,
                challenge.currentRulebook())));
    }
}
