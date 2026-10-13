package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotCancellation;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.support.ActorId;
import com.roboleague.support.ConflictException;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundLifecycleUseCaseTest {
    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final RoundId ROUND = RoundId.of("r-1");
    private static final ActorId ORGANIZER = ActorId.of("org-1");

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final CategoryResultsReader results = new CategoryResultsReader(challenges, editions, teams, rounds, attempts);
    private final RoundLifecycleUseCase lifecycle = new RoundLifecycleUseCase(rounds, attempts, CLOCK);
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());

    private final Category maze = category("maze", 1, 4, 14, 25, 2000);
    private final Edition edition = edition("ed-1", DATE, maze);
    private final Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), edition.getId(), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", 20.0)), List.of()),
                    new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

    RoundLifecycleUseCaseTest() {
        editions.save(edition);
        RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
        register.execute(edition.getId(), maze.id(), team("team-a"));
        register.execute(edition.getId(), maze.id(), team("team-b"));
        challenges.save(challenge);
        rounds.save(round("team-a", "team-b"));
    }

    @Test
    @DisplayName("A no-show turn is cancelled with its author and reason, and stops holding the publication back")
    void cancellingANoShowTurnUnblocksThePublication() {
        score("team-a");
        StandingsId id = new StandingsId(challenge.getId(), maze.id());
        assertThat(results.read(id).publicationCheck().unfinishedTurns()).isEqualTo(1);

        Round cancelled = lifecycle.cancelSlot(ROUND, slotOf("team-b"), ORGANIZER, "El equipo no se presentó");

        assertThat(cancelled.slot(slotOf("team-b")).orElseThrow().getCancellation())
                .contains(new SlotCancellation(ORGANIZER, "El equipo no se presentó", TIME));
        assertThat(rounds.findById(ROUND).orElseThrow().slot(slotOf("team-b")).orElseThrow().getStatus())
                .isEqualTo(Slot.SlotStatus.CANCELLED);
        assertThat(results.read(id).publicationCheck().unfinishedTurns()).isZero();
    }

    @Test
    @DisplayName("A cancelled turn takes no results, and a turn with results is not cancelled")
    void cancelledTurnsTakeNoResultsAndTurnsWithResultsAreNotCancelled() {
        score("team-a");
        lifecycle.cancelSlot(ROUND, slotOf("team-b"), ORGANIZER, "No se presentó");

        assertThatThrownBy(() -> score("team-b"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Slot r-1-team-b was cancelled; it takes no results");
        assertThatThrownBy(() -> lifecycle.cancelSlot(ROUND, slotOf("team-a"), ORGANIZER, "Tarde"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Slot r-1-team-a already has results; disqualify its attempt instead");
        assertThat(rounds.findById(ROUND).orElseThrow().slot(slotOf("team-a")).orElseThrow().getStatus())
                .isEqualTo(Slot.SlotStatus.SCHEDULED);
    }

    @Test
    @DisplayName("The round and its turns go through their lifecycle; completing needs every turn finished")
    void theRoundAndItsTurnsGoThroughTheirLifecycle() {
        lifecycle.start(ROUND);
        lifecycle.startSlot(ROUND, slotOf("team-a"));
        lifecycle.completeSlot(ROUND, slotOf("team-a"));
        assertThatThrownBy(() -> lifecycle.complete(ROUND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("All slots must be completed or cancelled");
        lifecycle.cancelSlot(ROUND, slotOf("team-b"), ORGANIZER, "No se presentó");

        Round completed = lifecycle.complete(ROUND);

        assertThat(completed.getStatus()).isEqualTo(Round.RoundStatus.COMPLETED);
        assertThat(rounds.findById(ROUND).orElseThrow().getSlots()).extracting(Slot::getStatus)
                .containsExactly(Slot.SlotStatus.COMPLETED, Slot.SlotStatus.CANCELLED);
        assertThatThrownBy(() -> lifecycle.start(ROUND)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("An unknown round, or a slot that is not in the round, is not found and nothing changes")
    void unknownRoundsAndSlotsAreNotFound() {
        assertThatThrownBy(() -> lifecycle.start(RoundId.of("missing")))
                .isInstanceOf(NotFoundException.class).hasMessage("Round not found: missing");
        assertThatThrownBy(() -> lifecycle.cancelSlot(ROUND, SlotId.of("missing"), ORGANIZER, "x"))
                .isInstanceOf(NotFoundException.class).hasMessage("Slot missing not found in round r-1");
        assertThat(rounds.findById(ROUND).orElseThrow().getSlots()).extracting(Slot::getStatus)
                .containsOnly(Slot.SlotStatus.SCHEDULED);
    }

    private void score(String teamId) {
        Reception reception = receive.execute(new ReceiveResultCommand(challenge.getId(),
                AttemptId.of(slotOf(teamId), 1),
                new SourceDelivery(new Measurements(new TrackPerformance(60, 2, 0), 0.0, Map.of()), JUDGE)));
        assertThat(reception).isInstanceOf(Reception.Received.class);
    }

    private static SlotId slotOf(String teamId) {
        return SlotId.of("r-1-" + teamId);
    }

    private Round round(String... teamIds) {
        Round round = Round.of(RoundInfo.of(ROUND, "Ronda 1", RoundScope.of(challenge.getId(), edition.getId(),
                maze.id(), 1)));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-1"), "Pista", "Madera"),
                List.of(Judge.of(JUDGE, "Juez", "General")));
        for (int index = 0; index < teamIds.length; index++) {
            round = round.addSlot(new Slot(new SlotIdentity(slotOf(teamIds[index]), round.getId(),
                    TeamId.of(teamIds[index])), assignment,
                    new TimeWindow(TIME.plusMinutes(10L * index), TIME.plusMinutes(10L * index + 5))));
        }
        return round;
    }
}
