package com.roboleague.api.demo;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsPublication;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.AddChallengeCommand;
import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.CreateEditionCommand;
import com.roboleague.usecase.CreateEditionUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.AppealAcceptance;
import com.roboleague.usecase.Publication;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.PublishStandingsUseCase;
import com.roboleague.usecase.RecalculateStandingsUseCase;
import com.roboleague.usecase.ReceiveResultCommand;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.Reception;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.RoundLifecycleUseCase;
import com.roboleague.usecase.ScheduleRoundCommand;
import com.roboleague.usecase.ScheduleRoundUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the demo through the use cases, the same way a user would through the API: an edition with three
 * challenges (maze, line follower and the mixed rescue) and a new rulebook version for the line follower.
 * <p>
 * On the maze (best 3 of 5 rounds) three teams run four rounds; NovaTeam does not show up for the fourth one and
 * its turn is cancelled. The provisional standings put TitanTeam last, with its second round discarded because of
 * four penalties; its appeal removes them, the recalculation discards its fourth round instead and the three teams
 * tie on 717 points: TitanTeam wins on time, and CyberTeam and NovaTeam, with the same best time, split on what the
 * deductions took. That version is published as official. On the rescue (F3), one attempt is scored with the
 * measurements and the judge panel, and another one still awaits the panel.
 * <p>
 * Every load starts its {@link DemoClock} and {@link DemoIds} over, so a run over an empty database always ends
 * with the same ids, times and totals.
 */
class DemoFixture implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoFixture.class);
    /** Fixed dates, so every run of the demo ends in the same state whatever day it starts. */
    private static final DateRange EDITION_DATES = new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
    /** Maze round n starts n - 1 hours after the first one; the rescue round goes after the maze is published. */
    private static final LocalDateTime ROUND_START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final LocalDateTime RESCUE_START = LocalDateTime.of(2026, 11, 10, 14, 0);
    private static final int MAZE_ROUNDS = 4;

    private final DemoUseCases useCases;
    private final DemoClock clock;
    private final DemoIds ids;

    DemoFixture(DemoUseCases useCases, DemoClock clock, DemoIds ids) {
        this.useCases = useCases;
        this.clock = clock;
        this.ids = ids;
    }

    record DemoUseCases(CreateEditionUseCase createEdition,
                        AddChallengeUseCase addChallenge,
                        PublishRulebookUseCase publishRulebook,
                        RegisterTeamUseCase registerTeam,
                        ScheduleRoundUseCase scheduleRound,
                        ReceiveResultUseCase receiveResult,
                        RecalculateStandingsUseCase recalculateStandings,
                        FileAppealUseCase fileAppeal,
                        ReviewAppealUseCase reviewAppeal,
                        ResolveAppealUseCase resolveAppeal,
                        PublishStandingsUseCase publishStandings,
                        RoundLifecycleUseCase roundLifecycle) {
    }

    @Override
    public void run(ApplicationArguments args) {
        clock.restart();
        ids.restart();
        Category junior = Category.of(CategoryId.of("cat-junior"), "Junior", 2, 4, 13, null, 2500.0, 300.0, 300.0, 300.0);
        Edition edition = createEdition(junior);
        Challenge maze = addChallenge(edition, "ch-maze", "Laberinto", DemoRulebooks.maze());
        addChallenge(edition, "ch-line", "Seguidor de línea", DemoRulebooks.lineFollower(Points.of(25)));
        Challenge rescue = addChallenge(edition, "ch-rescue", "Rescate", DemoRulebooks.rescue());
        published(useCases.publishRulebook().execute(ChallengeId.of("ch-line"),
                DemoRulebooks.lineFollower(Points.of(30))));

        Team cyber = team("t-a", "CyberTeam", new Robot(RobotId.of("r-a"), "CyberBot", robotSpec()),
                TeamMember.of(ParticipantId.of("m-1"), "Alice Leader", LocalDate.of(2004, 1, 1), "LEADER", clock.today()),
                TeamMember.of(ParticipantId.of("m-2"), "Bob Builder", LocalDate.of(2004, 2, 2), "DEV", clock.today()));
        Team titan = team("t-b", "TitanTeam", new Robot(RobotId.of("r-b"), "TitanBot", robotSpec()),
                TeamMember.of(ParticipantId.of("m-3"), "Charlie Cap", LocalDate.of(2003, 3, 3), "LEADER", clock.today()),
                TeamMember.of(ParticipantId.of("m-4"), "Dave Dev", LocalDate.of(2003, 4, 4), "DEV", clock.today()));
        Team nova = team("t-c", "NovaTeam", new Robot(RobotId.of("r-c"), "NovaBot", robotSpec()),
                TeamMember.of(ParticipantId.of("m-5"), "Eve Engineer", LocalDate.of(2005, 5, 5), "LEADER", clock.today()),
                TeamMember.of(ParticipantId.of("m-6"), "Frank Fixer", LocalDate.of(2005, 6, 6), "DEV", clock.today()));
        for (Team team : List.of(cyber, titan, nova)) {
            useCases.registerTeam().execute(edition.getId(), junior.id(), team);
        }

        List<Round> mazeRounds = new ArrayList<>();
        for (int number = 1; number <= MAZE_ROUNDS; number++) {
            mazeRounds.add(scheduleRound(maze, edition, number, "Ronda Clasificatoria " + number,
                    Track.active(TrackId.of("trk-1"), "Laberinto 1", "Madera"), ROUND_START.plusHours(number - 1L)));
        }
        Round rescueRound = scheduleRound(rescue, edition, 1, "Ronda de Rescate",
                Track.active(TrackId.of("trk-2"), "Rescate 1", "Madera"), RESCUE_START);

        // Points of a maze round: 140 + 1.5 per second under 60 + 20 per objective - 15 per penalty
        // - 0.5 per battery unit over 80 (the bonuses always reach their cap of 40).
        playRound(maze, mazeRounds.get(0), Map.of(cyber.getId(), mazeRun(50.0, 4, 0, 70.0),
                titan.getId(), mazeRun(52.0, 4, 0, 80.0), nova.getId(), mazeRun(50.0, 4, 1, 80.0)));
        Attempt titanAppealed = playRound(maze, mazeRounds.get(1), Map.of(cyber.getId(), mazeRun(54.0, 4, 0, 80.0),
                titan.getId(), mazeRun(45.0, 5, 4, 90.0), nova.getId(), mazeRun(53.0, 5, 0, 80.0)))
                .get(titan.getId());
        playRound(maze, mazeRounds.get(2), Map.of(cyber.getId(), mazeRun(50.0, 5, 0, 84.0),
                titan.getId(), mazeRun(55.0, 4, 0, 80.0), nova.getId(), mazeRun(55.0, 5, 0, 82.0)));
        Round lastMazeRound = mazeRounds.get(MAZE_ROUNDS - 1);
        Slot noShow = slotOf(lastMazeRound, nova);
        clock.advanceTo(noShow.getStartTime());
        useCases.roundLifecycle().cancelSlot(lastMazeRound.getId(), noShow.getSlotId(), ActorId.of("j-1"),
                "El equipo no se presentó");
        playRound(maze, lastMazeRound, Map.of(cyber.getId(), mazeRun(59.0, 3, 1, 80.0),
                titan.getId(), mazeRun(58.0, 4, 1, 80.0)));

        StandingsId mazeTable = new StandingsId(maze.getId(), junior.id());
        LocalDateTime afterMaze = ROUND_START.plusHours(MAZE_ROUNDS);
        clock.advanceTo(afterMaze);
        useCases.recalculateStandings().execute(mazeTable);

        clock.advanceTo(afterMaze.plusMinutes(10));
        Appeal appeal = useCases.fileAppeal().execute(
                titanAppealed.getId(), titan.getId(), "Penalizacion inexistente", "Video pista");
        useCases.reviewAppeal().execute(appeal.getAppealId(), ActorId.of("j-arb"));
        clock.advanceTo(afterMaze.plusMinutes(30));
        AppealAcceptance acceptance = useCases.resolveAppeal().acceptAppeal(appeal.getAppealId(),
                AuditNote.of(ActorId.of("j-arb"), "Penalizaciones corregidas tras revision"),
                List.of(mazeRun(45.0, 5, 0, 90.0)));
        int officialVersion = switch (acceptance) {
            case AppealAcceptance.Accepted accepted -> accepted.standings().number();
            case AppealAcceptance.Invalid invalid ->
                    throw new IllegalStateException("Demo appeal rejected: " + invalid.problems());
        };
        clock.advanceTo(afterMaze.plusMinutes(45));
        switch (useCases.publishStandings().execute(mazeTable, officialVersion,
                AuditNote.of(ActorId.of("org-1"), "Publicacion definitiva post-arbitraje"))) {
            case StandingsPublication.Published published -> {
            }
            case StandingsPublication.Blocked blocked ->
                    throw new IllegalStateException("Demo publication blocked: " + blocked.reasons());
        }

        clock.advanceTo(endOf(rescueRound));
        Slot cyberRescue = slotOf(rescueRound, cyber);
        receive(rescue, attemptIn(cyberRescue), new SourceDelivery(rescueRun(120.0, 3), judgeOf(cyberRescue)));
        receive(rescue, attemptIn(cyberRescue), new SourceDelivery(rescuePanel(8.0, 7.0, 3), judgeOf(cyberRescue)));
        Slot titanRescue = slotOf(rescueRound, titan);
        Attempt awaitingPanel = receive(rescue, attemptIn(titanRescue),
                new SourceDelivery(rescueRun(110.0, 4), judgeOf(titanRescue)));

        log.info("Demo loaded: edition {}, challenges ch-maze/ch-line/ch-rescue, maze rounds {}, appeal {}, "
                        + "official standings v{}, rescue attempt {} awaiting the judge panel",
                edition.getId(), mazeRounds.stream().map(Round::getId).toList(), appeal.getAppealId(),
                officialVersion, awaitingPanel.getId());
    }

    /**
     * Records, when the round ends, the result of each team that has one, in the order of the turns.
     */
    private Map<TeamId, Attempt> playRound(Challenge challenge, Round round, Map<TeamId, Measurements> runs) {
        clock.advanceTo(endOf(round));
        Map<TeamId, Attempt> attempts = new HashMap<>();
        for (Slot slot : round.getSlots()) {
            Measurements run = runs.get(slot.getTeamId());
            if (run != null) {
                attempts.put(slot.getTeamId(),
                        receive(challenge, attemptIn(slot), new SourceDelivery(run, judgeOf(slot))));
            }
        }
        return attempts;
    }

    private Round scheduleRound(Challenge challenge, Edition edition, int number, String name, Track track,
                                LocalDateTime start) {
        return useCases.scheduleRound().execute(ScheduleRoundCommand.of(
                challenge.getId(), edition.getId(), edition.getCategories().getFirst().id(), number, name, List.of(track),
                List.of(Judge.of(JudgeId.of("j-1"), "Chief Judge", "Principal"), Judge.of(JudgeId.of("j-2"), "Field Judge", "Pista")),
                start, Duration.ofMinutes(10), Duration.ofMinutes(2)));
    }

    private Edition createEdition(Category category) {
        Tournament tournament = Tournament.of("t-1", "RoboLeague Championship", "Torneo Nacional",
                new Season("s-2026", 2026, "Temporada 2026"));
        return useCases.createEdition().execute(new CreateEditionCommand(
                new EditionContext(tournament, new EditionHeader(EditionId.of("ed-1"), "Edicion Inaugural", 1)),
                EDITION_DATES, List.of(category)));
    }

    private Challenge addChallenge(Edition edition, String id, String name, RulebookDefinition rulebook) {
        return published(useCases.addChallenge().execute(new AddChallengeCommand(
                Challenge.draft(ChallengeId.of(id), edition.getId(), name), rulebook)));
    }

    private static <T> T published(Publication<T> publication) {
        return switch (publication) {
            case Publication.Published<T> published -> published.value();
            case Publication.Rejected<T> rejected ->
                    throw new IllegalStateException("Demo rulebook rejected: " + rejected.problems());
        };
    }

    private Attempt receive(Challenge challenge, AttemptId attemptId, SourceDelivery delivery) {
        return switch (useCases.receiveResult().execute(new ReceiveResultCommand(challenge.getId(), attemptId, delivery))) {
            case Reception.Received received -> received.attempt();
            case Reception.Rejected rejected ->
                    throw new IllegalStateException("Demo result rejected: " + rejected.problems());
        };
    }

    private static Slot slotOf(Round round, Team team) {
        return round.getSlots().stream().filter(slot -> slot.getTeamId().equals(team.getId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No turn for " + team.getId() + " in " + round.getId()));
    }

    private static LocalDateTime endOf(Round round) {
        return round.getSlots().stream().map(Slot::getEndTime).max(Comparator.naturalOrder()).orElseThrow();
    }

    private static AttemptId attemptIn(Slot slot) {
        return AttemptId.of(slot.getSlotId(), 1);
    }

    private static JudgeId judgeOf(Slot slot) {
        return slot.getAssignedJudges().getFirst().id();
    }

    private static Measurements mazeRun(double seconds, int objectives, int penalties, double batteryUsed) {
        return new Measurements(new TrackPerformance(seconds, objectives, penalties), batteryUsed,
                Map.of(DemoRulebooks.COLLISIONS.name(), 1.0, DemoRulebooks.CHECKPOINT.name(), 1.0,
                        DemoRulebooks.LAPS.name(), 1.0));
    }

    private static Measurements rescueRun(double seconds, int clearedZones) {
        return new Measurements(new TrackPerformance(seconds, clearedZones, 0), 0.0, Map.of());
    }

    private static JudgeScores rescuePanel(double chiefScore, double fieldScore, int rescued) {
        return new JudgeScores(Map.of(JudgeId.of("j-1"), chiefScore, JudgeId.of("j-2"), fieldScore),
                Map.of(DemoRulebooks.RESCUED.name(), (double) rescued, DemoRulebooks.FULL_RESCUE.name(), 0.0));
    }

    private Team team(String id, String name, Robot robot, TeamMember... members) {
        Documentation documentation = new Documentation().withDocument("DOC", "doc.pdf")
                .verify(ActorId.of("Official Inspector"), clock.now());
        return Team.of(TeamId.of(id), name, "ITBA", robot, List.of(members), documentation);
    }

    private static RobotSpecification robotSpec() {
        return RobotSpecification.of(2000.0, 200.0, 200.0, 200.0, 2, Set.of("LIDAR"));
    }
}
