package com.roboleague.api.config;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.StandingsRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.support.SystemClock;
import com.roboleague.support.UuidGenerator;
import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.CategoryResultsReader;
import com.roboleague.usecase.ChangeRegistrationCategoryUseCase;
import com.roboleague.usecase.CreateEditionUseCase;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.GetAttemptBreakdownUseCase;
import com.roboleague.usecase.GetChallengeUseCase;
import com.roboleague.usecase.GetRulebookUseCase;
import com.roboleague.usecase.ListEditionChallengesUseCase;
import com.roboleague.usecase.PublishRulebookUseCase;
import com.roboleague.usecase.PublishStandingsUseCase;
import com.roboleague.usecase.QueryAppealsUseCase;
import com.roboleague.usecase.QueryEditionsUseCase;
import com.roboleague.usecase.QueryRegistrationsUseCase;
import com.roboleague.usecase.QueryRoundsUseCase;
import com.roboleague.usecase.QueryStandingsUseCase;
import com.roboleague.usecase.RecalculateStandingsUseCase;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import com.roboleague.usecase.ScheduleRoundUseCase;
import com.roboleague.usecase.UpdateRegistrationUseCase;
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
    CategoryResultsReader categoryResultsReader(ChallengeRepository challenges, EditionRepository editions,
                                                TeamRepository teams, RoundRepository rounds, AttemptRepository attempts) {
        return new CategoryResultsReader(challenges, editions, teams, rounds, attempts);
    }

    @Bean
    RecalculateStandingsUseCase recalculateStandingsUseCase(CategoryResultsReader results, StandingsRepository standings,
                                                            Clock clock) {
        return new RecalculateStandingsUseCase(results, standings, clock);
    }

    @Bean
    PublishStandingsUseCase publishStandingsUseCase(CategoryResultsReader results, StandingsRepository standings,
                                                    Clock clock) {
        return new PublishStandingsUseCase(results, standings, clock);
    }

    @Bean
    QueryStandingsUseCase queryStandingsUseCase(CategoryResultsReader results, StandingsRepository standings) {
        return new QueryStandingsUseCase(results, standings);
    }

    @Bean
    QueryAppealsUseCase queryAppealsUseCase(AppealRepository appeals, AttemptRepository attempts,
                                            CategoryResultsReader results) {
        return new QueryAppealsUseCase(appeals, attempts, results);
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
                                              ChallengeRepository challenges, RoundRepository rounds,
                                              RecalculateStandingsUseCase recalculate, Clock clock, IdGenerator ids) {
        return new ResolveAppealUseCase(appeals, attempts, challenges, rounds, recalculate, clock, ids);
    }

    @Bean
    QueryEditionsUseCase queryEditionsUseCase(EditionRepository editions) { return new QueryEditionsUseCase(editions); }

    @Bean
    ListEditionChallengesUseCase listEditionChallengesUseCase(EditionRepository editions, ChallengeRepository challenges) {
        return new ListEditionChallengesUseCase(editions, challenges);
    }

    @Bean
    QueryRegistrationsUseCase queryRegistrationsUseCase(EditionRepository editions, TeamRepository teams) {
        return new QueryRegistrationsUseCase(editions, teams);
    }

    @Bean
    QueryRoundsUseCase queryRoundsUseCase(ChallengeRepository challenges, EditionRepository editions, RoundRepository rounds) {
        return new QueryRoundsUseCase(challenges, editions, rounds);
    }

    @Bean
    UpdateRegistrationUseCase updateRegistrationUseCase(EditionRepository editions, TeamRepository teams) {
        return new UpdateRegistrationUseCase(editions, teams);
    }
    @Bean
    GetRulebookUseCase getRulebookUseCase(ChallengeRepository challenges) {
        return new GetRulebookUseCase(challenges);
    }
}
