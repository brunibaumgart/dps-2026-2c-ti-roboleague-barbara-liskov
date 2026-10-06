package com.roboleague.usecase;

import com.roboleague.evaluation.*;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.repository.memory.*;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppealAndRecalculateIntegrationTest {

    private InMemoryTeamRepository teamRepository;
    private InMemoryEditionRepository editionRepository;
    private InMemoryChallengeRepository challengeRepository;
    private InMemoryAttemptRepository attemptRepository;
    private InMemoryRankingRepository rankingRepository;
    private InMemoryAppealRepository appealRepository;

    private RegisterTeamUseCase registerTeamUseCase;
    private ScheduleRoundUseCase scheduleRoundUseCase;
    private CaptureAttemptResultUseCase captureAttemptResultUseCase;
    private FileAppealUseCase fileAppealUseCase;
    private ReviewAppealUseCase reviewAppealUseCase;
    private ResolveAppealUseCase resolveAppealUseCase;
    private RecalculateRankingUseCase recalculateRankingUseCase;
    private PublishOfficialRankingUseCase publishOfficialRankingUseCase;

    private Category mazeCategory;
    private Edition edition2026;
    private Challenge maze;

    @BeforeEach
    void setUp() {
        teamRepository = new InMemoryTeamRepository();
        editionRepository = new InMemoryEditionRepository();
        challengeRepository = new InMemoryChallengeRepository();
        attemptRepository = new InMemoryAttemptRepository();
        rankingRepository = new InMemoryRankingRepository();
        appealRepository = new InMemoryAppealRepository();

        // Eligibility specification
        EligibilitySpecification<Team> eligibilitySpec = new AgeLimitSpecification(LocalDate.of(2026, 11, 1))
                .and(new TeamSizeSpecification())
                .and(new RobotSpecificationLimit())
                .and(new DocumentationVerifiedSpecification());

        registerTeamUseCase = new RegisterTeamUseCase(teamRepository, editionRepository, eligibilitySpec);
        scheduleRoundUseCase = new ScheduleRoundUseCase(editionRepository, new InMemoryRoundRepository(),
                new RoundSchedulerService());
        captureAttemptResultUseCase = new CaptureAttemptResultUseCase(attemptRepository, challengeRepository);

        RankingCalculatorService rankingService = new RankingCalculatorService(TieBreakerChain.defaultRules());
        recalculateRankingUseCase = new RecalculateRankingUseCase(
                editionRepository, attemptRepository, rankingRepository, rankingService
        );

        fileAppealUseCase = new FileAppealUseCase(attemptRepository, appealRepository);
        reviewAppealUseCase = new ReviewAppealUseCase(appealRepository);
        resolveAppealUseCase = new ResolveAppealUseCase(
                appealRepository, attemptRepository, challengeRepository, recalculateRankingUseCase
        );
        publishOfficialRankingUseCase = new PublishOfficialRankingUseCase(rankingRepository, appealRepository, attemptRepository);

        // Setup Domain: Season, Tournament, Category, Edition
        Season season2026 = new Season("s-2026", 2026, "Temporada 2026");
        Tournament tournament = Tournament.of("tourn-latam", "RoboCup Latam", "Torneo regional", season2026);

        mazeCategory = Category.of(
                "cat-maze", "Laberinto Autonomo",
                2, 4, 15, 25, 2000.0, 300.0, 300.0, 300.0
        );

        edition2026 = Edition.of(
                "ed-2026", tournament, 1, "Edición Buenos Aires 2026",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                List.of(mazeCategory)
        );
        editionRepository.save(edition2026);

        // Maze challenge rulebook v1: Time + Objectives + Penalties
        maze = Challenge.draft(ChallengeId.of("ch-maze"), edition2026.getId(), "Laberinto").publish(
                new ScoringScheme(MetricSheet.none(), new ScoreRules(
                        List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0),
                                new ObjectivesRule("Objetivos", 20.0)),
                        List.of(new AllObjectivesBonusRule("Todos los objetivos", 5, 25.0)),
                        List.of(new PenaltyRule("Penalizaciones", 15.0))), new Unlimited()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        challengeRepository.save(maze);
    }

    private Team createTeam(String id, String name) {
        RobotSpecification spec = RobotSpecification.of(1500.0, 200.0, 200.0, 150.0, 2, Set.of("LIDAR"));
        Robot robot = new Robot("rob-" + id, name + "-Bot", spec);
        Team team = Team.of(id, name, "ITBA", mazeCategory, robot);

        team.addMember(TeamMember.of("m1-" + id, name + " Alpha", LocalDate.of(2005, 5, 1), "LEADER"));
        team.addMember(TeamMember.of("m2-" + id, name + " Beta", LocalDate.of(2004, 8, 12), "DEV"));

        team.getDocumentation().addDocument("CONSENT", "consent.pdf");
        team.getDocumentation().verify("Inspector Juez");
        return team;
    }

    private static AttemptIdentity firstAttemptIn(Round round, int slotIndex) {
        Slot slot = round.getSlots().get(slotIndex);
        return new AttemptIdentity(AttemptId.of(slot.getSlotId(), 1), round.getId(), slot.getTeamId());
    }

    @Test
    @DisplayName("Complete end-to-end integration flow: Registration -> Scheduling -> Scoring -> Appeal -> Recalculate -> Publish")
    void fullCompetitionLifecycleWithAppealAndRecalculation() {
        // 1. Register Teams
        Team teamAlpha = registerTeamUseCase.execute(edition2026.getId(), createTeam("t-alpha", "Team Alpha"));
        Team teamBeta = registerTeamUseCase.execute(edition2026.getId(), createTeam("t-beta", "Team Beta"));

        assertThat(teamRepository.findAll()).hasSize(2);

        // 2. Schedule Round
        List<Track> tracks = List.of(Track.active("trk-1", "Pista Principal", "Madera"));
        List<Judge> judges = List.of(Judge.of("j-1", "Dr. Turing", "Autonomia"), Judge.of("j-2", "Ing. Lovelace", "Control"));

        Round round1 = scheduleRoundUseCase.execute(ScheduleRoundCommand.of(
                edition2026.getId(), mazeCategory.id(), 1, "Ronda Clasificatoria",
                tracks, judges, LocalDateTime.now(), Duration.ofMinutes(15), Duration.ofMinutes(5)
        ));
        assertThat(round1.getSlots()).hasSize(2);

        // 3. Capture Initial Attempt Results
        // Team Alpha: 55s (5s under target => 105), 4 objectives (80 pts), 0 penalties => Total: 185.0
        RawMetrics metricsAlpha = RawMetrics.of(55.0, 4, 0);
        Attempt attemptAlpha = captureAttemptResultUseCase.execute(new CaptureAttemptResultCommand(
                maze.getId(), firstAttemptIn(round1, 0), metricsAlpha, "j-1"
        ));

        // Team Beta: 50s (10s under target => 110), 5 objectives (all done: 100 + 25 = 125 pts),
        // BUT wrongly assigned 4 penalties (-60 pts) => Total: 110 + 125 - 60 = 175.0
        RawMetrics initialMetricsBeta = RawMetrics.of(50.0, 5, 4);
        Attempt attemptBeta = captureAttemptResultUseCase.execute(new CaptureAttemptResultCommand(
                maze.getId(), firstAttemptIn(round1, 1), initialMetricsBeta, "j-2"
        ));

        // 4. Initial Ranking Calculation (Provisional)
        Ranking provisionalRanking = recalculateRankingUseCase.execute(edition2026.getId(), mazeCategory.id(), round1.getId());
        assertThat(provisionalRanking.getStatus()).isEqualTo(Ranking.RankingStatus.PROVISIONAL);

        // Position 1: Team Alpha (185.0)
        // Position 2: Team Beta (175.0)
        assertThat(provisionalRanking.getEntries().get(0).teamScore().teamId()).isEqualTo("t-alpha");
        assertThat(provisionalRanking.getEntries().get(0).teamScore().totalScore()).isEqualTo(185.0);
        assertThat(provisionalRanking.getEntries().get(1).teamScore().teamId()).isEqualTo("t-beta");
        assertThat(provisionalRanking.getEntries().get(1).teamScore().totalScore()).isEqualTo(175.0);

        // 5. Team Beta files an Appeal regarding wrongly counted penalties
        Appeal appealBeta = fileAppealUseCase.execute(
                attemptBeta.getId().value(),
                teamBeta.getId(),
                "Las 4 faltas registradas fueron un error de lectura en los sensores del juez",
                "Video oficial de camara 1 muestra recorrido limpio"
        );
        assertThat(appealBeta.getStatusName()).isEqualTo("PENDING");

        // 6. Attempting to publish official ranking while an appeal is unresolved MUST FAIL
        assertThatThrownBy(() -> publishOfficialRankingUseCase.execute(provisionalRanking.getRankingId(), "Intento de cierre"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appeal(s) remain unresolved");

        // 7. Arbitration Committee reviews appeal
        reviewAppealUseCase.execute(appealBeta.getAppealId(), "arbitro-jefe");
        assertThat(appealBeta.getStatusName()).isEqualTo("UNDER_REVIEW");

        // 8. Arbitrator accepts appeal: penalties were indeed 0, not 4!
        // Revised metrics: 50s, 5 objectives, 0 penalties => Total: 110 + 125 - 0 = 235.0!
        RawMetrics revisedMetricsBeta = RawMetrics.of(50.0, 5, 0);
        resolveAppealUseCase.acceptAppeal(
                appealBeta.getAppealId(),
                mazeCategory.id(),
                round1.getId(),
                "Video revisado por unanimidad. Se anulan las 4 faltas inexistentes.",
                revisedMetricsBeta,
                "arbitro-jefe"
        );

        // 9. Verify Attempt Beta Audit Trail
        Attempt auditedAttemptBeta = attemptRepository.findById(attemptBeta.getId()).orElseThrow();
        assertThat(auditedAttemptBeta.getRevisionHistory()).hasSize(2);
        assertThat(auditedAttemptBeta.getOriginalSnapshot().breakdown().totalScore()).isEqualTo(175.0);
        assertThat(auditedAttemptBeta.getLatestSnapshot().breakdown().totalScore()).isEqualTo(235.0);
        assertThat(auditedAttemptBeta.getFinalScore()).isEqualTo(235.0);

        // 10. Verify Ranking Recalculation after appeal acceptance
        Ranking updatedRanking = rankingRepository.findLatestByEditionAndCategory(edition2026.getId(), mazeCategory.id()).orElseThrow();

        // Team Beta is now #1 with 235.0 pts!
        // Team Alpha is now #2 with 185.0 pts!
        assertThat(updatedRanking.getEntries().get(0).position()).isEqualTo(1);
        assertThat(updatedRanking.getEntries().get(0).teamScore().teamId()).isEqualTo("t-beta");
        assertThat(updatedRanking.getEntries().get(0).teamScore().totalScore()).isEqualTo(235.0);

        assertThat(updatedRanking.getEntries().get(1).position()).isEqualTo(2);
        assertThat(updatedRanking.getEntries().get(1).teamScore().teamId()).isEqualTo("t-alpha");
        assertThat(updatedRanking.getEntries().get(1).teamScore().totalScore()).isEqualTo(185.0);

        // 11. Publish Official Ranking now succeeds because all appeals are resolved
        Ranking officialRanking = publishOfficialRankingUseCase.execute(updatedRanking.getRankingId(), "Resultados definitivos validados por el comite");

        assertThat(officialRanking.getStatus()).isEqualTo(Ranking.RankingStatus.OFFICIAL);
        assertThat(officialRanking.isOfficial()).isTrue();
        assertThat(officialRanking.getPublishedAt()).isNotNull();
        assertThat(officialRanking.getPublicationNotes()).contains("Resultados definitivos");
    }
}
