package com.roboleague.api.demo;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.RankingRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.AddChallengeCommand;
import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.CaptureAttemptResultCommand;
import com.roboleague.usecase.CaptureAttemptResultUseCase;
import com.roboleague.usecase.CreateEditionCommand;
import com.roboleague.usecase.CreateEditionUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.Publication;
import com.roboleague.usecase.PublishOfficialRankingUseCase;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.RecalculateRankingUseCase;
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
import java.util.Set;

/**
 * Loads the demo through the use cases, the same way a user would through the API: an edition with three
 * challenges (maze, line follower and the mixed rescue), a new rulebook version for the line follower, and on the
 * maze two teams, a round, two attempts, a provisional ranking, an accepted appeal and the official publication.
 */
class DemoFixture implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoFixture.class);

    private final RankingRepository rankings;
    private final DemoUseCases useCases;

    DemoFixture(RankingRepository rankings, DemoUseCases useCases) {
        this.rankings = rankings;
        this.useCases = useCases;
    }

    record DemoUseCases(CreateEditionUseCase createEdition,
                        AddChallengeUseCase addChallenge,
                        PublishRulebookUseCase publishRulebook,
                        RegisterTeamUseCase registerTeam,
                        ScheduleRoundUseCase scheduleRound,
                        CaptureAttemptResultUseCase captureResult,
                        RecalculateRankingUseCase recalculateRanking,
                        FileAppealUseCase fileAppeal,
                        ReviewAppealUseCase reviewAppeal,
                        ResolveAppealUseCase resolveAppeal,
                        PublishOfficialRankingUseCase publishRanking) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Category junior = Category.of("cat-junior", "Junior", 2, 4, 15, 25, 2500.0, 300.0, 300.0, 300.0);
        Edition edition = createEdition(junior);
        Challenge maze = addChallenge(edition, "ch-maze", "Laberinto", DemoRulebooks.maze());
        addChallenge(edition, "ch-line", "Seguidor de línea", DemoRulebooks.lineFollower(25.0));
        addChallenge(edition, "ch-rescue", "Rescate", DemoRulebooks.rescue());
        published(useCases.publishRulebook().execute(ChallengeId.of("ch-line"), DemoRulebooks.lineFollower(30.0)));

        Team cyber = team("t-a", "CyberTeam", junior, new Robot("r-a", "CyberBot", robotSpec()),
                TeamMember.of("m-1", "Alice Leader", LocalDate.of(2004, 1, 1), "LEADER"),
                TeamMember.of("m-2", "Bob Builder", LocalDate.of(2004, 2, 2), "DEV"));
        Team titan = team("t-b", "TitanTeam", junior, new Robot("r-b", "TitanBot", robotSpec()),
                TeamMember.of("m-3", "Charlie Cap", LocalDate.of(2003, 3, 3), "LEADER"),
                TeamMember.of("m-4", "Dave Dev", LocalDate.of(2003, 4, 4), "DEV"));
        useCases.registerTeam().execute(edition.getId(), cyber);
        useCases.registerTeam().execute(edition.getId(), titan);

        Round round = useCases.scheduleRound().execute(ScheduleRoundCommand.of(
                edition.getId(), junior.id(), 1, "Ronda Clasificatoria",
                List.of(Track.active("trk-1", "Laberinto 1", "Madera")),
                List.of(Judge.of("j-1", "Chief Judge", "Principal"), Judge.of("j-2", "Field Judge", "Pista")),
                LocalDateTime.now(), Duration.ofMinutes(10), Duration.ofMinutes(2)));

        useCases.captureResult().execute(new CaptureAttemptResultCommand(maze.getId(),
                firstAttempt(round, 0), mazeRun(50.0, 4, 0, 70.0), "j-1"));
        Attempt titanAttempt = useCases.captureResult().execute(new CaptureAttemptResultCommand(maze.getId(),
                firstAttempt(round, 1), mazeRun(45.0, 5, 4, 90.0), "j-2"));

        useCases.recalculateRanking().execute(edition.getId(), junior.id(), round.getId());

        Appeal appeal = useCases.fileAppeal().execute(
                titanAttempt.getId().value(), titan.getId(), "Penalizacion inexistente", "Video pista");
        useCases.reviewAppeal().execute(appeal.getAppealId(), "j-arb");
        useCases.resolveAppeal().acceptAppeal(appeal.getAppealId(), maze.getId(), junior.id(), round.getId(),
                "Penalizaciones corregidas tras revision", mazeRun(45.0, 5, 0, 90.0), "j-arb");

        Ranking latest = rankings.findLatestByEditionAndCategory(edition.getId(), junior.id()).orElseThrow();
        useCases.publishRanking().execute(latest.getRankingId(), "Publicacion definitiva post-arbitraje");

        log.info("Demo loaded: edition {}, challenges ch-maze/ch-line/ch-rescue, round {}, appeal {}, official ranking {}",
                edition.getId(), round.getId(), appeal.getAppealId(), latest.getRankingId());
    }

    private Edition createEdition(Category category) {
        Tournament tournament = Tournament.of("t-1", "RoboLeague Championship", "Torneo Nacional",
                new Season("s-2026", 2026, "Temporada 2026"));
        return useCases.createEdition().execute(new CreateEditionCommand(
                new EditionContext(tournament, new EditionHeader("ed-1", "Edicion Inaugural", 1)),
                new DateRange(LocalDate.now(), LocalDate.now().plusDays(3)), List.of(category)));
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

    private static AttemptIdentity firstAttempt(Round round, int slotIndex) {
        Slot slot = round.getSlots().get(slotIndex);
        return new AttemptIdentity(AttemptId.of(slot.getSlotId(), 1), round.getId(), slot.getTeamId());
    }

    private static RawMetrics mazeRun(double seconds, int objectives, int penalties, double batteryUsed) {
        return new RawMetrics(new TrackPerformance(seconds, objectives, penalties), EvaluationFeedback.of(batteryUsed,
                Map.of(), Map.of(DemoRulebooks.COLLISIONS.name(), 1.0, DemoRulebooks.CHECKPOINT.name(), 1.0,
                        DemoRulebooks.LAPS.name(), 1.0)));
    }

    private static Team team(String id, String name, Category category, Robot robot, TeamMember... members) {
        Team team = Team.of(id, name, "ITBA", category, robot);
        for (TeamMember member : members) {
            team.addMember(member);
        }
        team.getDocumentation().addDocument("DOC", "doc.pdf");
        team.getDocumentation().verify("Official Inspector");
        return team;
    }

    private static RobotSpecification robotSpec() {
        return RobotSpecification.of(2000.0, 200.0, 200.0, 200.0, 2, Set.of("LIDAR"));
    }
}
