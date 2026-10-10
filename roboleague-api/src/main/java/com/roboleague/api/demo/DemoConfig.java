package com.roboleague.api.demo;

import com.roboleague.support.Clock;
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
    DemoFixture demoFixture(CreateEditionUseCase createEdition, AddChallengeUseCase addChallenge,
                            PublishRulebookUseCase publishRulebook, RegisterTeamUseCase registerTeam,
                            ScheduleRoundUseCase scheduleRound, ReceiveResultUseCase receiveResult,
                            RecalculateStandingsUseCase recalculateStandings, FileAppealUseCase fileAppeal,
                            ReviewAppealUseCase reviewAppeal, ResolveAppealUseCase resolveAppeal,
                            PublishStandingsUseCase publishStandings, Clock clock) {
        return new DemoFixture(new DemoFixture.DemoUseCases(
                createEdition, addChallenge, publishRulebook, registerTeam, scheduleRound, receiveResult,
                recalculateStandings, fileAppeal, reviewAppeal, resolveAppeal, publishStandings), clock);
    }
}
