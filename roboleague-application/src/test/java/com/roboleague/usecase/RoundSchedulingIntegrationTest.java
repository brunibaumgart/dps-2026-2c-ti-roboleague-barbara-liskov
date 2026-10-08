package com.roboleague.usecase;

import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.*;
import com.roboleague.scheduling.*;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundSchedulingIntegrationTest {
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final AtomicInteger generated = new AtomicInteger();
    private final IdGenerator ids = () -> "generated-" + generated.incrementAndGet();
    private final ScheduleRoundUseCase useCase = new ScheduleRoundUseCase(challenges, editions, teams, rounds,
            new RoundSchedulerService(ids), ids);
    private final Category category = RegistrationFixtures.category("category", 1, 4, 10, 25, 2000);
    private final EditionId editionId = EditionId.of("edition");
    private final ChallengeId challengeId = ChallengeId.of("challenge");

    @BeforeEach
    void setUp() {
        Edition edition = RegistrationFixtures.edition(editionId.value(), DATE, category);
        // Deliberately insert in the opposite order to the required stable schedule.
        for (String id : List.of("D", "C", "B", "A")) {
            Team team = RegistrationFixtures.team(id);
            teams.save(team);
            edition = edition.registerTeam(team, category.id(), id.equals("D") ? TIME.plusMinutes(1) : TIME);
        }
        editions.save(edition);
        challenges.save(Challenge.draft(challengeId, editionId, "Challenge").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objectives", 1)), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
    }

    @Test
    void ordersByRegistrationTimeThenTeamIdAndAvoidsStoredRounds() {
        Round first = useCase.execute(command(challengeId, category.id(), 1, tracks(), judges(), Duration.ofMinutes(5), Duration.ofMinutes(1)));
        assertThat(first.getSlots()).extracting(slot -> slot.getTeamId().value()).containsExactly("A", "B", "C", "D");
        assertThat(first.getSlots()).extracting(Slot::getStartTime)
                .containsExactly(TIME, TIME, TIME.plusMinutes(6), TIME.plusMinutes(6));
        Round second = useCase.execute(command(challengeId, category.id(), 2, tracks(), judges(), Duration.ofMinutes(5), Duration.ofMinutes(1)));
        assertThat(second.getSlots()).extracting(Slot::getStartTime)
                .containsExactly(TIME.plusMinutes(12), TIME.plusMinutes(12), TIME.plusMinutes(18), TIME.plusMinutes(18));
        assertThat(rounds.findByChallengeId(challengeId)).hasSize(2);
    }

    @Test
    void rejectsDuplicateScopeBeforeGeneratingIdsOrSaving() {
        Round first = useCase.execute(validCommand());
        int before = generated.get();
        assertThatThrownBy(() -> useCase.execute(validCommand())).isInstanceOf(IllegalStateException.class);
        assertThat(generated).hasValue(before);
        assertThat(rounds.findAll()).containsExactly(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-challenge", "foreign-edition", "foreign-category", "duplicate-track", "duplicate-judge", "inactive-track", "no-judge", "zero-duration", "negative-interval", "invalid-number"})
    void invalidRequestsNeverGenerateOrSaveAPartialRound(String invalid) {
        ChallengeId challenge = challengeId;
        CategoryId categoryId = category.id();
        List<Track> tracks = tracks();
        List<Judge> judges = judges();
        Duration duration = Duration.ofMinutes(5);
        Duration interval = Duration.ofMinutes(1);
        int number = 1;
        switch (invalid) {
            case "missing-challenge" -> challenge = ChallengeId.of("missing");
            case "foreign-edition" -> {
                challenges.save(Challenge.draft(ChallengeId.of("foreign"), EditionId.of("foreign-edition"), "Foreign").publish(
                        ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objectives", 1)), List.of()),
                        new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
                challenge = ChallengeId.of("foreign");
            }
            case "foreign-category" -> categoryId = CategoryId.of("foreign");
            case "duplicate-track" -> tracks = List.of(tracks.getFirst(), Track.active(tracks.getFirst().id(), "Other", "Other"));
            case "duplicate-judge" -> judges = List.of(judges.getFirst(), Judge.of(judges.getFirst().id(), "Other", "Other"));
            case "inactive-track" -> tracks = List.of(Track.of(tracks.getFirst().info(), false));
            case "no-judge" -> judges = List.of();
            case "zero-duration" -> duration = Duration.ZERO;
            case "negative-interval" -> interval = Duration.ofMinutes(-1);
            case "invalid-number" -> number = 0;
            default -> throw new AssertionError(invalid);
        }
        ScheduleRoundCommand command = command(challenge, categoryId, number, tracks, judges, duration, interval);
        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(IllegalArgumentException.class);
        assertThat(generated).hasValue(0);
        assertThat(rounds.findAll()).isEmpty();
        assertThat(editions.findById(editionId).orElseThrow().getRegistrations()).hasSize(4);
    }

    private ScheduleRoundCommand validCommand() {
        return command(challengeId, category.id(), 1, tracks(), judges(), Duration.ofMinutes(5), Duration.ofMinutes(1));
    }
    private ScheduleRoundCommand command(ChallengeId challenge, CategoryId category, int number, List<Track> tracks,
                                         List<Judge> judges, Duration duration, Duration interval) {
        return ScheduleRoundCommand.of(challenge, editionId, category, number, "Round", tracks, judges, TIME, duration, interval);
    }
    private static List<Track> tracks() {
        return List.of(Track.active(TrackId.of("p1"), "P1", "Wood"), Track.active(TrackId.of("p2"), "P2", "Wood"));
    }
    private static List<Judge> judges() {
        return List.of(Judge.of(JudgeId.of("j1"), "J1", "General"), Judge.of(JudgeId.of("j2"), "J2", "General"));
    }
}
