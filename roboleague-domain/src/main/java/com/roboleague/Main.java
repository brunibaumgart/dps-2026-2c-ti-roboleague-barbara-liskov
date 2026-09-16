package com.roboleague;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoringPolicy;
import com.roboleague.evaluation.rules.ObjectiveBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.repository.memory.*;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.*;
import com.roboleague.usecase.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Composition Root for RoboLeague.
 * Assembles all dependencies, repositories, services, and use cases.
 */
public class Main {

    private final RegisterTeamUseCase registerTeamUseCase;
    private final ScheduleRoundUseCase scheduleRoundUseCase;
    private final CaptureAttemptResultUseCase captureAttemptResultUseCase;
    private final RecalculateRankingUseCase recalculateRankingUseCase;
    private final FileAppealUseCase fileAppealUseCase;
    private final ReviewAppealUseCase reviewAppealUseCase;
    private final ResolveAppealUseCase resolveAppealUseCase;
    private final PublishOfficialRankingUseCase publishOfficialRankingUseCase;

    public Main(RegisterTeamUseCase registerTeamUseCase,
                ScheduleRoundUseCase scheduleRoundUseCase,
                CaptureAttemptResultUseCase captureAttemptResultUseCase,
                RecalculateRankingUseCase recalculateRankingUseCase,
                FileAppealUseCase fileAppealUseCase,
                ReviewAppealUseCase reviewAppealUseCase,
                ResolveAppealUseCase resolveAppealUseCase,
                PublishOfficialRankingUseCase publishOfficialRankingUseCase) {
        this.registerTeamUseCase = Objects.requireNonNull(registerTeamUseCase);
        this.scheduleRoundUseCase = Objects.requireNonNull(scheduleRoundUseCase);
        this.captureAttemptResultUseCase = Objects.requireNonNull(captureAttemptResultUseCase);
        this.recalculateRankingUseCase = Objects.requireNonNull(recalculateRankingUseCase);
        this.fileAppealUseCase = Objects.requireNonNull(fileAppealUseCase);
        this.reviewAppealUseCase = Objects.requireNonNull(reviewAppealUseCase);
        this.resolveAppealUseCase = Objects.requireNonNull(resolveAppealUseCase);
        this.publishOfficialRankingUseCase = Objects.requireNonNull(publishOfficialRankingUseCase);
    }

    public static void main(String[] args) {
        // 1. Repositories (Infrastructure / Adapters)
        InMemoryTeamRepository teamRepository = new InMemoryTeamRepository();
        InMemoryEditionRepository editionRepository = new InMemoryEditionRepository();
        InMemoryAttemptRepository attemptRepository = new InMemoryAttemptRepository();
        InMemoryRankingRepository rankingRepository = new InMemoryRankingRepository();
        InMemoryAppealRepository appealRepository = new InMemoryAppealRepository();

        // 2. Domain Services & Specifications
        EligibilitySpecification<Team> eligibilitySpec = new AgeLimitSpecification(LocalDate.now())
                .and(new TeamSizeSpecification())
                .and(new RobotSpecificationLimit())
                .and(new DocumentationVerifiedSpecification());

        RoundSchedulerService schedulerService = new RoundSchedulerService();
        RankingCalculatorService rankingCalculatorService = new RankingCalculatorService(TieBreakerChain.defaultRules());

        // 3. Use Cases (Injected with dependencies)
        RegisterTeamUseCase registerTeamUseCase = new RegisterTeamUseCase(teamRepository, editionRepository, eligibilitySpec);
        ScheduleRoundUseCase scheduleRoundUseCase = new ScheduleRoundUseCase(editionRepository, schedulerService);
        CaptureAttemptResultUseCase captureAttemptResultUseCase = new CaptureAttemptResultUseCase(attemptRepository, editionRepository);
        RecalculateRankingUseCase recalculateRankingUseCase = new RecalculateRankingUseCase(
                editionRepository, attemptRepository, rankingRepository, rankingCalculatorService
        );
        FileAppealUseCase fileAppealUseCase = new FileAppealUseCase(attemptRepository, appealRepository);
        ReviewAppealUseCase reviewAppealUseCase = new ReviewAppealUseCase(appealRepository);
        ResolveAppealUseCase resolveAppealUseCase = new ResolveAppealUseCase(
                appealRepository, attemptRepository, editionRepository, recalculateRankingUseCase
        );
        PublishOfficialRankingUseCase publishOfficialRankingUseCase = new PublishOfficialRankingUseCase(
                rankingRepository, appealRepository
        );

        Main app = new Main(
                registerTeamUseCase,
                scheduleRoundUseCase,
                captureAttemptResultUseCase,
                recalculateRankingUseCase,
                fileAppealUseCase,
                reviewAppealUseCase,
                resolveAppealUseCase,
                publishOfficialRankingUseCase
        );

        app.runDemo(editionRepository);
    }

    public void runDemo(InMemoryEditionRepository editionRepository) {
        // Setup Competition Edition
        Season season = new Season("s-2026", 2026, "Temporada 2026");
        Tournament tournament = Tournament.of("t-1", "RoboLeague Championship", "Torneo Nacional", season);
        Category sumoCategory = Category.of("cat-sumo", "Sumo Autonomo", 2, 4, 15, 25, 2500.0, 300.0, 300.0, 300.0);

        ScoringPolicy policy = ScoringPolicy.of("pol-1", "v1.0", "Reglamento Sumo", List.of(
                TimeBasedRule.standard(100.0, 60.0),
                ObjectiveBonusRule.standard(20.0, 5),
                new PenaltyRule("Faltas de pista", 10.0)
        ));

        Edition edition = Edition.of(
                "ed-1", tournament, 1, "Edicion Inaugural",
                LocalDate.now(), LocalDate.now().plusDays(3),
                policy, List.of(sumoCategory)
        );
        editionRepository.save(edition);

        // Register Teams
        RobotSpecification spec = RobotSpecification.of(2000.0, 200.0, 200.0, 200.0, 2, Set.of("LIDAR"));
        Robot robotA = new Robot("r-a", "CyberBot", spec);
        Robot robotB = new Robot("r-b", "TitanBot", spec);

        Team teamA = Team.of("t-a", "CyberTeam", "ITBA", sumoCategory, robotA);
        teamA.addMember(TeamMember.of("m-1", "Alice Leader", LocalDate.of(2004, 1, 1), "LEADER"));
        teamA.addMember(TeamMember.of("m-2", "Bob Builder", LocalDate.of(2004, 2, 2), "DEV"));
        teamA.getDocumentation().addDocument("DOC", "doc.pdf");
        teamA.getDocumentation().verify("Official Inspector");

        Team teamB = Team.of("t-b", "TitanTeam", "ITBA", sumoCategory, robotB);
        teamB.addMember(TeamMember.of("m-3", "Charlie Cap", LocalDate.of(2003, 3, 3), "LEADER"));
        teamB.addMember(TeamMember.of("m-4", "Dave Dev", LocalDate.of(2003, 4, 4), "DEV"));
        teamB.getDocumentation().addDocument("DOC", "doc.pdf");
        teamB.getDocumentation().verify("Official Inspector");

        registerTeamUseCase.execute(edition.getId(), teamA);
        registerTeamUseCase.execute(edition.getId(), teamB);

        // Schedule Round
        List<Track> tracks = List.of(Track.active("trk-1", "Dojo 1", "Madera"));
        List<Judge> judges = List.of(Judge.of("j-1", "Chief Judge", "Principal"), Judge.of("j-2", "Field Judge", "Pista"));

        Round round = scheduleRoundUseCase.execute(
                edition.getId(), sumoCategory.id(), 1, "Ronda Clasificatoria",
                tracks, judges, LocalDateTime.now(), Duration.ofMinutes(10), Duration.ofMinutes(2)
        );

        // Capture Results
        Attempt attA = captureAttemptResultUseCase.execute(
                edition.getId(), "att-a1", teamA.getId(),
                round.getSlots().get(0).getSlotId(), round.getId(), 1,
                RawMetrics.of(50.0, 4, 0), "j-1"
        );
        Attempt attB = captureAttemptResultUseCase.execute(
                edition.getId(), "att-b1", teamB.getId(),
                round.getSlots().get(1).getSlotId(), round.getId(), 1,
                RawMetrics.of(45.0, 5, 2), "j-2"
        );

        // Calculate Provisional Ranking
        Ranking provRanking = recalculateRankingUseCase.execute(edition.getId(), sumoCategory.id(), round.getId());

        // File and Resolve Appeal
        Appeal appeal = fileAppealUseCase.execute(attB.getAttemptId(), teamB.getId(), "Penalizacion inexistente", "Video pista");
        reviewAppealUseCase.execute(appeal.getAppealId(), "j-arb");
        resolveAppealUseCase.acceptAppeal(
                appeal.getAppealId(), edition.getId(), sumoCategory.id(), round.getId(),
                "Penalizaciones corregidas tras revision", RawMetrics.of(45.0, 5, 0), "j-arb"
        );

        // Publish Official Ranking
        Ranking official = publishOfficialRankingUseCase.execute(provRanking.getRankingId(), "Publicacion definitiva post-arbitraje");
    }
}
