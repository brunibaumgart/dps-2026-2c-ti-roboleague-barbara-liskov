package com.roboleague.api.config;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.support.SystemClock;
import com.roboleague.support.UuidGenerator;
import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.ChangeRegistrationCategoryUseCase;
import com.roboleague.usecase.CreateEditionUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.GetAttemptBreakdownUseCase;
import com.roboleague.usecase.GetChallengeUseCase;
import com.roboleague.usecase.PublishOfficialRankingUseCase;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.RecalculateRankingUseCase;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.ScheduleRoundUseCase;
import com.roboleague.usecase.UpdateTeamUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Builds the domain services and use cases. They carry no Spring annotation: if Spring goes away,
 * nothing in roboleague-domain or roboleague-application is recompiled.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    Clock clock(@Value("${roboleague.time-zone}") String zone) {
        return new SystemClock(ZoneId.of(zone));
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidGenerator();
    }

    @Bean
    RuleCatalog ruleCatalog() {
        return RuleCatalog.standard();
    }

    @Bean
    CreateEditionUseCase createEditionUseCase(EditionRepository editions) {
        return new CreateEditionUseCase(editions);
    }

    @Bean
    AddChallengeUseCase addChallengeUseCase(EditionRepository editions, ChallengeRepository challenges,
                                            RuleCatalog catalog) {
        return new AddChallengeUseCase(editions, challenges, catalog);
    }

    @Bean
    PublishRulebookUseCase publishRulebookUseCase(ChallengeRepository challenges, RuleCatalog catalog) {
        return new PublishRulebookUseCase(challenges, catalog);
    }

    @Bean
    GetChallengeUseCase getChallengeUseCase(ChallengeRepository challenges) {
        return new GetChallengeUseCase(challenges);
    }

    @Bean
    RoundSchedulerService roundSchedulerService(IdGenerator ids) {
        return new RoundSchedulerService(ids);
    }

    @Bean
    RankingCalculatorService rankingCalculatorService() {
        return new RankingCalculatorService(TieBreakerChain.defaultRules());
    }

    @Bean
    RegisterTeamUseCase registerTeamUseCase(TeamRepository teams, EditionRepository editions,
                                            Clock clock) {
        return new RegisterTeamUseCase(teams, editions, clock);
    }

    @Bean
    UpdateTeamUseCase updateTeamUseCase(TeamRepository teams, EditionRepository editions) {
        return new UpdateTeamUseCase(teams, editions);
    }

    @Bean
    ChangeRegistrationCategoryUseCase changeRegistrationCategoryUseCase(TeamRepository teams, EditionRepository editions) {
        return new ChangeRegistrationCategoryUseCase(teams, editions);
    }

    @Bean
    ScheduleRoundUseCase scheduleRoundUseCase(ChallengeRepository challenges, EditionRepository editions, TeamRepository teams, RoundRepository rounds,
                                              RoundSchedulerService scheduler, IdGenerator ids) {
        return new ScheduleRoundUseCase(challenges, editions, teams, rounds, scheduler, ids);
    }

    @Bean
    GetAttemptBreakdownUseCase getAttemptBreakdownUseCase(AttemptRepository attempts, ChallengeRepository challenges) {
        return new GetAttemptBreakdownUseCase(attempts, challenges);
    }

    @Bean
    ReceiveResultUseCase receiveResultUseCase(AttemptRepository attempts, RoundRepository rounds,
                                              ChallengeRepository challenges, Clock clock, IdGenerator ids) {
        return new ReceiveResultUseCase(attempts, rounds, challenges, clock, ids);
    }

    @Bean
    RecalculateRankingUseCase recalculateRankingUseCase(EditionRepository editions, TeamRepository teams, AttemptRepository attempts,
                                                        RankingRepository rankings, RankingCalculatorService calculator, Clock clock, IdGenerator ids) {
        return new RecalculateRankingUseCase(editions, teams, attempts, rankings, calculator, clock, ids);
    }

    @Bean
    FileAppealUseCase fileAppealUseCase(AttemptRepository attempts, AppealRepository appeals, Clock clock, IdGenerator ids) {
        return new FileAppealUseCase(attempts, appeals, clock, ids);
    }

    @Bean
    ReviewAppealUseCase reviewAppealUseCase(AppealRepository appeals) {
        return new ReviewAppealUseCase(appeals);
    }

    @Bean
    ResolveAppealUseCase resolveAppealUseCase(AppealRepository appeals, AttemptRepository attempts,
                                              ChallengeRepository challenges, RecalculateRankingUseCase recalculate, Clock clock, IdGenerator ids) {
        return new ResolveAppealUseCase(appeals, attempts, challenges, recalculate, clock, ids);
    }

    @Bean
    PublishOfficialRankingUseCase publishOfficialRankingUseCase(RankingRepository rankings, AppealRepository appeals,
                                                                AttemptRepository attempts, Clock clock) {
        return new PublishOfficialRankingUseCase(rankings, appeals, attempts, clock);
    }
}
