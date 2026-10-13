package com.roboleague.api.demo;

import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.CreateEditionUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.PublishStandingsUseCase;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.RecalculateStandingsUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.RoundLifecycleUseCase;
import com.roboleague.usecase.ScheduleRoundUseCase;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Active with the "demo" profile: empties the database and loads {@link DemoFixture}. The clock and the id generator
 * of this profile replace the system ones, so every run ends with the same ids and times.
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
    DemoClock clock() {
        return new DemoClock();
    }

    @Bean
    DemoIds idGenerator() {
        return new DemoIds();
    }

    @Bean
    DemoFixture demoFixture(CreateEditionUseCase createEdition, AddChallengeUseCase addChallenge,
                            PublishRulebookUseCase publishRulebook, RegisterTeamUseCase registerTeam,
                            ScheduleRoundUseCase scheduleRound, ReceiveResultUseCase receiveResult,
                            RecalculateStandingsUseCase recalculateStandings, FileAppealUseCase fileAppeal,
                            ReviewAppealUseCase reviewAppeal, ResolveAppealUseCase resolveAppeal,
                            PublishStandingsUseCase publishStandings, RoundLifecycleUseCase roundLifecycle,
                            DemoClock clock, DemoIds ids) {
        return new DemoFixture(new DemoFixture.DemoUseCases(
                createEdition, addChallenge, publishRulebook, registerTeam, scheduleRound, receiveResult,
                recalculateStandings, fileAppeal, reviewAppeal, resolveAppeal, publishStandings, roundLifecycle),
                clock, ids);
    }
}
