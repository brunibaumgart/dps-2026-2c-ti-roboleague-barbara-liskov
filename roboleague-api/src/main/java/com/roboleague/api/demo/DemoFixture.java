package com.roboleague.api.demo;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.RankingRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.support.ActorId;
import com.roboleague.support.Clock;
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
import com.roboleague.usecase.Publication;
import com.roboleague.usecase.PublishOfficialRankingUseCase;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.RecalculateRankingUseCase;
import com.roboleague.usecase.ReceiveResultCommand;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.Reception;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.ScheduleRoundCommand;
import com.roboleague.usecase.ScheduleRoundUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the demo through the use cases, the same way a user would through the API: an edition with three
 * challenges (maze, line follower and the mixed rescue), a new rulebook version for the line follower, and on the
 * maze two teams, a round, two attempts, a provisional ranking, an accepted appeal and the official publication.
 * On the rescue (F3), one attempt is scored with the measurements and the judge panel, and the other one still
 * awaits the panel.
 */
class DemoFixture implements ApplicationRunner {
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(DemoFixture.class);
    /** Fixed dates, so every run of the demo ends in the same state whatever day it starts. */
    private static final DateRange EDITION_DATES = new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
    /** Round n starts n - 1 hours after the first one. */
    private static final LocalDateTime ROUND_START = LocalDateTime.of(2026, 11, 10, 9, 0);

    private final RankingRepository rankings;
    private final DemoUseCases useCases;

    DemoFixture(RankingRepository rankings, DemoUseCases useCases, Clock clock) {
        this.clock = clock;
        this.rankings = rankings;
        this.useCases = useCases;
    }

    record DemoUseCases(CreateEditionUseCase createEdition,
                        AddChallengeUseCase addChallenge,
                        PublishRulebookUseCase publishRulebook,
                        RegisterTeamUseCase registerTeam,
                        ScheduleRoundUseCase scheduleRound,
                        ReceiveResultUseCase receiveResult,
                        RecalculateRankingUseCase recalculateRanking,
                        FileAppealUseCase fileAppeal,
                        ReviewAppealUseCase reviewAppeal,
                        ResolveAppealUseCase resolveAppeal,
                        PublishOfficialRankingUseCase publishRanking) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Category junior = Category.of(CategoryId.of("cat-junior"), "Junior", 2, 4, 15, 25, 2500.0, 300.0, 300.0, 300.0);
        Edition edition = createEdition(junior);
        Challenge maze = addChallenge(edition, "ch-maze", "Laberinto", DemoRulebooks.maze());
        addChallenge(edition, "ch-line", "Seguidor de línea", DemoRulebooks.lineFollower(25.0));
        Challenge rescue = addChallenge(edition, "ch-rescue", "Rescate", DemoRulebooks.rescue());
        published(useCases.publishRulebook().execute(ChallengeId.of("ch-line"), DemoRulebooks.lineFollower(30.0)));

        Team cyber = team("t-a", "CyberTeam", junior, new Robot(RobotId.of("r-a"), "CyberBot", robotSpec()),
                TeamMember.of(ParticipantId.of("m-1"), "Alice Leader", LocalDate.of(2004, 1, 1), "LEADER", clock.today()),
                TeamMember.of(ParticipantId.of("m-2"), "Bob Builder", LocalDate.of(2004, 2, 2), "DEV", clock.today()));
        Team titan = team("t-b", "TitanTeam", junior, new Robot(RobotId.of("r-b"), "TitanBot", robotSpec()),
                TeamMember.of(ParticipantId.of("m-3"), "Charlie Cap", LocalDate.of(2003, 3, 3), "LEADER", clock.today()),
                TeamMember.of(ParticipantId.of("m-4"), "Dave Dev", LocalDate.of(2003, 4, 4), "DEV", clock.today()));
        useCases.registerTeam().execute(edition.getId(), junior.id(), cyber);
        useCases.registerTeam().execute(edition.getId(), junior.id(), titan);

        Round round = scheduleRound(edition, 1, "Ronda Clasificatoria", Track.active(TrackId.of("trk-1"), "Laberinto 1", "Madera"));

        receive(maze, firstAttempt(round, 0), new SourceDelivery(mazeRun(50.0, 4, 0, 70.0), JudgeId.of("j-1")));
        Attempt titanAttempt = receive(maze, firstAttempt(round, 1),
                new SourceDelivery(mazeRun(45.0, 5, 4, 90.0), JudgeId.of("j-2")));

        useCases.recalculateRanking().execute(edition.getId(), junior.id(), Optional.of(round.getId()));

        Appeal appeal = useCases.fileAppeal().execute(
                titanAttempt.getId(), titan.getId(), "Penalizacion inexistente", "Video pista");
        useCases.reviewAppeal().execute(appeal.getAppealId(), ActorId.of("j-arb"));
        useCases.resolveAppeal().acceptAppeal(appeal.getAppealId(), junior.id(), Optional.of(round.getId()),
                "Penalizaciones corregidas tras revision", mazeRun(45.0, 5, 0, 90.0).addTo(RawMetrics.nothingMeasured()),
                ActorId.of("j-arb"));

        Ranking latest = rankings.findLatestByEditionAndCategory(edition.getId(), junior.id()).orElseThrow();
        useCases.publishRanking().execute(latest.getRankingId(), "Publicacion definitiva post-arbitraje");

        Round rescueRound = scheduleRound(edition, 2, "Ronda de Rescate", Track.active(TrackId.of("trk-2"), "Rescate 1", "Madera"));
        receive(rescue, firstAttempt(rescueRound, 0), new SourceDelivery(rescueRun(120.0, 3), JudgeId.of("j-1")));
        receive(rescue, firstAttempt(rescueRound, 0), new SourceDelivery(rescuePanel(8.0, 7.0, 3), JudgeId.of("j-2")));
        Attempt awaitingPanel = receive(rescue, firstAttempt(rescueRound, 1),
                new SourceDelivery(rescueRun(110.0, 4), JudgeId.of("j-2")));

        log.info("Demo loaded: edition {}, challenges ch-maze/ch-line/ch-rescue, round {}, appeal {}, official ranking {}, "
                        + "rescue attempt {} awaiting the judge panel",
                edition.getId(), round.getId(), appeal.getAppealId(), latest.getRankingId(), awaitingPanel.getId());
    }

    private Round scheduleRound(Edition edition, int number, String name, Track track) {
        return useCases.scheduleRound().execute(ScheduleRoundCommand.of(
                edition.getId(), edition.getCategories().getFirst().id(), number, name, List.of(track),
                List.of(Judge.of(JudgeId.of("j-1"), "Chief Judge", "Principal"), Judge.of(JudgeId.of("j-2"), "Field Judge", "Pista")),
                ROUND_START.plusHours(number - 1L), Duration.ofMinutes(10), Duration.ofMinutes(2)));
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

    private static AttemptId firstAttempt(Round round, int slotIndex) {
        return AttemptId.of(round.getSlots().get(slotIndex).getSlotId(), 1);
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

    private Team team(String id, String name, Category category, Robot robot, TeamMember... members) {
        Documentation documentation = new Documentation().withDocument("DOC", "doc.pdf")
                .verify(ActorId.of("Official Inspector"), clock.now());
        return Team.of(TeamId.of(id), name, "ITBA", robot, List.of(members), documentation);
    }

    private static RobotSpecification robotSpec() {
        return RobotSpecification.of(2000.0, 200.0, 200.0, 200.0, 2, Set.of("LIDAR"));
    }
}
