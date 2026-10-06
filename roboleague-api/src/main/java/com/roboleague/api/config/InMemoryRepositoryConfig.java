package com.roboleague.api.config;

import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRankingRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ports that do not have a Postgres adapter yet. When an aggregate gets its Jpa*Repository
 * (a @Repository in roboleague-infrastructure), delete its bean here; if both exist the app fails to start.
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
    RoundRepository roundRepository() {
        return new InMemoryRoundRepository();
    }
}
