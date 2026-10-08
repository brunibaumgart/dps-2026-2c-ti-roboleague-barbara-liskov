package com.roboleague.api;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.usecase.ChangeRegistrationCategoryUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.SaveThenFailUseCase;
import com.roboleague.usecase.UpdateTeamUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({PostgresContainer.class, TransactionalUseCasesTest.Probe.class})
class TransactionalUseCasesTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Probe {
        @Bean
        SaveThenFailUseCase saveThenFailUseCase(ChallengeRepository challenges) {
            return new SaveThenFailUseCase(challenges);
        }
    }

    @Autowired
    private SaveThenFailUseCase saveThenFail;

    @Autowired
    private ResolveAppealUseCase resolveAppeal;

    @Autowired
    private RegisterTeamUseCase registerTeam;

    @Autowired
    private UpdateTeamUseCase updateTeam;

    @Autowired
    private ChangeRegistrationCategoryUseCase changeCategory;

    @Autowired
    private ChallengeRepository challenges;

    @Test
    @DisplayName("Si un caso de uso falla después de guardar, no queda nada guardado")
    void givenAUseCaseThatFailsAfterSavingThenNothingIsSaved() {
        Challenge maze = Challenge.draft(ChallengeId.of("tx-ch-1"), EditionId.of("ed-tx"), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(TimeBasedRule.standard(100.0, 60.0)), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

        assertThatThrownBy(() -> saveThenFail.execute(maze)).hasMessage("the second save failed");

        assertThat(challenges.findById(ChallengeId.of("tx-ch-1"))).isEmpty();
    }

    @Test
    @DisplayName("Los casos de uso de la aplicación corren dentro de una transacción")
    void givenTheApplicationThenItsUseCasesAreTransactional() {
        assertThat(AopUtils.isAopProxy(resolveAppeal)).isTrue();
        assertThat(AopUtils.isAopProxy(registerTeam)).isTrue();
        assertThat(AopUtils.isAopProxy(updateTeam)).isTrue();
        assertThat(AopUtils.isAopProxy(changeCategory)).isTrue();
    }
}
