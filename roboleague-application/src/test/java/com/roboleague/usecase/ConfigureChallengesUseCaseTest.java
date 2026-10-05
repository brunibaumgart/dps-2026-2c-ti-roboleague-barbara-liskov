package com.roboleague.usecase;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Tournament;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigureChallengesUseCaseTest {

    private InMemoryEditionRepository editions;
    private InMemoryChallengeRepository challenges;
    private CreateEditionUseCase createEdition;
    private AddChallengeUseCase addChallenge;
    private PublishRulebookUseCase publishRulebook;

    @BeforeEach
    void setUp() {
        editions = new InMemoryEditionRepository();
        challenges = new InMemoryChallengeRepository();
        RuleCatalog catalog = RuleCatalog.standard();
        createEdition = new CreateEditionUseCase(editions);
        addChallenge = new AddChallengeUseCase(editions, challenges, catalog);
        publishRulebook = new PublishRulebookUseCase(challenges, catalog);
        createEdition.execute(editionCommand("ed-2026"));
    }

    private static CreateEditionCommand editionCommand(String id) {
        Tournament tournament = Tournament.of("t-1", "RoboLeague", new Season("s-2026", 2026, "Temporada 2026"));
        return new CreateEditionCommand(new EditionContext(tournament, new EditionHeader(id, "RoboLeague 2026", 1)),
                new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)),
                List.of(Category.of("cat-junior", "Junior", 2, 4, 12, 17, 2500.0)));
    }

    private static RulebookDefinition penaltyRulebook(double deduction) {
        return new RulebookDefinition(List.of(),
                new RulebookDefinition.Scoring(List.of(new PenaltyRule("Faltas", deduction).definition()), List.of(),
                        new StrategyDefinition(Unlimited.TYPE, Parameters.none())),
                new RulebookDefinition.Ranking(new StrategyDefinition(AllRounds.TYPE, Parameters.none()),
                        List.of("higher-total")));
    }

    private static RulebookDefinition unknownRuleRulebook() {
        return new RulebookDefinition(List.of(),
                new RulebookDefinition.Scoring(List.of(new RuleDefinition("teleport", "Teletransporte",
                        RuleArguments.of(Parameters.none()))), List.of(),
                        new StrategyDefinition(Unlimited.TYPE, Parameters.none())),
                new RulebookDefinition.Ranking(new StrategyDefinition(AllRounds.TYPE, Parameters.none()),
                        List.of("higher-total")));
    }

    private static AddChallengeCommand maze(RulebookDefinition rulebook) {
        return new AddChallengeCommand(Challenge.draft(ChallengeId.of("ch-maze"), "ed-2026", "Laberinto"), rulebook);
    }

    @Test
    @DisplayName("Crear una edición con un id que ya existe se rechaza")
    void givenAnExistingEditionIdThenCreatingItAgainFails() {
        assertThat(editions.findById("ed-2026")).isPresent();
        assertThatThrownBy(() -> createEdition.execute(editionCommand("ed-2026")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Agregar un desafío publica su reglamento v1 y lo guarda")
    void givenAValidRulebookThenTheChallengeIsPublishedAtVersionOneAndSaved() {
        Publication<Challenge> publication = addChallenge.execute(maze(penaltyRulebook(10.0)));

        assertThat(publication).isInstanceOf(Publication.Published.class);
        Challenge saved = challenges.findById(ChallengeId.of("ch-maze")).orElseThrow();
        assertThat(saved.currentRulebook().version()).isEqualTo(RulebookVersion.first());
    }

    @Test
    @DisplayName("Un reglamento inválido no crea el desafío y devuelve el problema")
    void givenAnInvalidRulebookThenTheChallengeIsRejectedAndNotSaved() {
        Publication<Challenge> publication = addChallenge.execute(maze(unknownRuleRulebook()));

        assertThat(publication).isInstanceOf(Publication.Rejected.class);
        assertThat(((Publication.Rejected<Challenge>) publication).problems())
                .anyMatch(problem -> problem.contains("teleport"));
        assertThat(challenges.findById(ChallengeId.of("ch-maze"))).isEmpty();
    }

    @Test
    @DisplayName("Un desafío para una edición que no existe se rechaza")
    void givenAnUnknownEditionThenAddingTheChallengeFails() {
        AddChallengeCommand elsewhere = new AddChallengeCommand(
                Challenge.draft(ChallengeId.of("ch-x"), "ed-none", "X"), penaltyRulebook(10.0));

        assertThatThrownBy(() -> addChallenge.execute(elsewhere)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un desafío con un id que ya existe se rechaza")
    void givenAnExistingChallengeIdThenAddingItAgainFails() {
        addChallenge.execute(maze(penaltyRulebook(10.0)));

        assertThatThrownBy(() -> addChallenge.execute(maze(penaltyRulebook(20.0))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Publicar una versión nueva guarda la v2 y deja la v1 como estaba")
    void givenANewDefinitionThenVersionTwoIsPublishedAndVersionOneIsKept() {
        addChallenge.execute(maze(penaltyRulebook(10.0)));

        Publication<Rulebook> publication = publishRulebook.execute(ChallengeId.of("ch-maze"), penaltyRulebook(20.0));

        assertThat(((Publication.Published<Rulebook>) publication).value().version()).isEqualTo(new RulebookVersion(2));
        Challenge saved = challenges.findById(ChallengeId.of("ch-maze")).orElseThrow();
        assertThat(saved.rulebook(RulebookVersion.first()).orElseThrow().definition()).isEqualTo(penaltyRulebook(10.0));
    }

    @Test
    @DisplayName("Una versión nueva inválida se rechaza y el desafío sigue en la v1")
    void givenAnInvalidNewDefinitionThenNothingIsPublished() {
        addChallenge.execute(maze(penaltyRulebook(10.0)));

        Publication<Rulebook> publication = publishRulebook.execute(ChallengeId.of("ch-maze"), unknownRuleRulebook());

        assertThat(publication).isInstanceOf(Publication.Rejected.class);
        assertThat(challenges.findById(ChallengeId.of("ch-maze")).orElseThrow().currentRulebook().version())
                .isEqualTo(RulebookVersion.first());
    }

    @Test
    @DisplayName("Publicar en un desafío que no existe se rechaza")
    void givenAnUnknownChallengeThenPublishingFails() {
        ChallengeId unknown = ChallengeId.of("ch-none");
        RulebookDefinition definition = penaltyRulebook(10.0);

        assertThatThrownBy(() -> publishRulebook.execute(unknown, definition))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
