package com.roboleague.api.demo;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.rules.ObjectiveBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.CaptureAttemptResultCommand;
import com.roboleague.usecase.CaptureAttemptResultUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.PublishOfficialRankingUseCase;
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
import java.util.Set;

/**
 * Loads the demo through the use cases, the same way a user would through the API:
 * registration, a round, two attempts, a provisional ranking, an accepted appeal and the official publication.
 */
class DemoFixture implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoFixture.class);

    private final DemoRepositories repositories;
    private final DemoUseCases useCases;

    DemoFixture(DemoRepositories repositories, DemoUseCases useCases) {
        this.repositories = repositories;
        this.useCases = useCases;
    }

    record DemoRepositories(EditionRepository editions, ChallengeRepository challenges, RankingRepository rankings) {
    }

    record DemoUseCases(RegisterTeamUseCase registerTeam,
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
        Category sumo = Category.of("cat-sumo", "Sumo Autonomo", 2, 4, 15, 25, 2500.0, 300.0, 300.0, 300.0);
        Edition edition = createEdition(sumo);
        Challenge challenge = createChallenge(edition);

        Team cyber = team("t-a", "CyberTeam", sumo, new Robot("r-a", "CyberBot", robotSpec()),
                TeamMember.of("m-1", "Alice Leader", LocalDate.of(2004, 1, 1), "LEADER"),
                TeamMember.of("m-2", "Bob Builder", LocalDate.of(2004, 2, 2), "DEV"));
        Team titan = team("t-b", "TitanTeam", sumo, new Robot("r-b", "TitanBot", robotSpec()),
                TeamMember.of("m-3", "Charlie Cap", LocalDate.of(2003, 3, 3), "LEADER"),
                TeamMember.of("m-4", "Dave Dev", LocalDate.of(2003, 4, 4), "DEV"));
        useCases.registerTeam().execute(edition.getId(), cyber);
        useCases.registerTeam().execute(edition.getId(), titan);

        Round round = useCases.scheduleRound().execute(ScheduleRoundCommand.of(
                edition.getId(), sumo.id(), 1, "Ronda Clasificatoria",
                List.of(Track.active("trk-1", "Dojo 1", "Madera")),
                List.of(Judge.of("j-1", "Chief Judge", "Principal"), Judge.of("j-2", "Field Judge", "Pista")),
                LocalDateTime.now(), Duration.ofMinutes(10), Duration.ofMinutes(2)));

        useCases.captureResult().execute(CaptureAttemptResultCommand.of(challenge.getId(), "att-a1", cyber.getId(),
                round.getSlots().get(0).getSlotId(), round.getId(), 1, RawMetrics.of(50.0, 4, 0), "j-1"));
        Attempt titanAttempt = useCases.captureResult().execute(CaptureAttemptResultCommand.of(
                challenge.getId(), "att-b1", titan.getId(),
                round.getSlots().get(1).getSlotId(), round.getId(), 1, RawMetrics.of(45.0, 5, 2), "j-2"));

        useCases.recalculateRanking().execute(edition.getId(), sumo.id(), round.getId());

        Appeal appeal = useCases.fileAppeal().execute(
                titanAttempt.getAttemptId(), titan.getId(), "Penalizacion inexistente", "Video pista");
        useCases.reviewAppeal().execute(appeal.getAppealId(), "j-arb");
        useCases.resolveAppeal().acceptAppeal(appeal.getAppealId(), challenge.getId(), sumo.id(), round.getId(),
                "Penalizaciones corregidas tras revision", RawMetrics.of(45.0, 5, 0), "j-arb");

        Ranking latest = repositories.rankings().findLatestByEditionAndCategory(edition.getId(), sumo.id()).orElseThrow();
        useCases.publishRanking().execute(latest.getRankingId(), "Publicacion definitiva post-arbitraje");

        log.info("Demo loaded: edition {}, round {}, appeal {}, official ranking {}",
                edition.getId(), round.getId(), appeal.getAppealId(), latest.getRankingId());
    }

    // There is no use case to configure an edition or a challenge yet (front 1), so the fixture stores them directly.
    private Edition createEdition(Category category) {
        Season season = new Season("s-2026", 2026, "Temporada 2026");
        Tournament tournament = Tournament.of("t-1", "RoboLeague Championship", "Torneo Nacional", season);
        Edition edition = Edition.of("ed-1", tournament, 1, "Edicion Inaugural",
                LocalDate.now(), LocalDate.now().plusDays(3), List.of(category));
        repositories.editions().save(edition);
        return edition;
    }

    private Challenge createChallenge(Edition edition) {
        Challenge challenge = Challenge.draft(ChallengeId.of("ch-sumo"), edition.getId(), "Sumo").publish(List.of(
                TimeBasedRule.standard(100.0, 60.0),
                ObjectiveBonusRule.standard(20.0, 5),
                new PenaltyRule("Faltas de pista", 10.0)));
        repositories.challenges().save(challenge);
        return challenge;
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
