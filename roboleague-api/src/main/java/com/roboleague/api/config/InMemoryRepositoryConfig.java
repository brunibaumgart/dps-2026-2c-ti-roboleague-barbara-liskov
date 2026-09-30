package com.roboleague.api.config;

import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.repository.memory.InMemoryAppealRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires every repository port to its in-memory adapter until the Postgres adapters exist.
 */
@Configuration(proxyBeanMethods = false)
class InMemoryRepositoryConfig {

    @Bean
    TeamRepository teamRepository() {
        return new InMemoryTeamRepository();
    }

    @Bean
    EditionRepository editionRepository() {
        return new InMemoryEditionRepository();
    }

    @Bean
    AttemptRepository attemptRepository() {
        return new InMemoryAttemptRepository();
    }

    @Bean
    RankingRepository rankingRepository() {
        return new InMemoryRankingRepository();
    }

    @Bean
    AppealRepository appealRepository() {
        return new InMemoryAppealRepository();
    }
}
