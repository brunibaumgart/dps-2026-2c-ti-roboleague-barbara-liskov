package com.roboleague.api.demo;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.usecase.CaptureAttemptResultUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.PublishOfficialRankingUseCase;
import com.roboleague.usecase.RecalculateRankingUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.ScheduleRoundUseCase;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Active with the "demo" profile: empties the database and loads {@link DemoFixture}, so every run starts the same.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
class DemoConfig {

    @Bean
    FlywayMigrationStrategy cleanBeforeMigrating() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }

    @Bean
    DemoFixture demoFixture(EditionRepository editions, ChallengeRepository challenges, RankingRepository rankings,
                            RegisterTeamUseCase registerTeam, ScheduleRoundUseCase scheduleRound,
                            CaptureAttemptResultUseCase captureResult, RecalculateRankingUseCase recalculateRanking,
                            FileAppealUseCase fileAppeal, ReviewAppealUseCase reviewAppeal,
                            ResolveAppealUseCase resolveAppeal, PublishOfficialRankingUseCase publishRanking) {
        return new DemoFixture(new DemoFixture.DemoRepositories(editions, challenges, rankings), new DemoFixture.DemoUseCases(
                registerTeam, scheduleRound, captureResult, recalculateRanking,
                fileAppeal, reviewAppeal, resolveAppeal, publishRanking));
    }
}
