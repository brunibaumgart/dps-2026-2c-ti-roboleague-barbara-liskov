package com.roboleague.api;

import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebFrontendTest extends ApiTest {
    @Autowired
    private ChallengeRepository challenges;

    @Test
    void servesTheFrontendAndItsModulesOnTheApiPort() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Gestión de competencia"))
                .andExpect(content().string(containsString("/js/app.js")));
        mvc.perform(get("/styles.css")).andExpect(status().isOk()).andExpect(content().string(containsString(".app-shell")));
        for (String module : List.of("app", "api", "ui", "models", "registration", "scheduling", "results")) {
            mvc.perform(get("/js/" + module + ".js")).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("<!doctype html>"))));
        }
        // Spring's welcome mapping forwards to the served index rather than requiring another frontend server.
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void readsHistoricalSourcesAfterANewVersionWithoutChangingTheChallenge() throws Exception {
        ChallengeId id = ChallengeId.of("web06-historical");
        var ranking = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
        var challenge = Challenge.draft(id, EditionId.of("web06-edition"), "Historical challenge").publish(
                ScoringScheme.withoutBonuses(List.of(new JudgeSubjectiveRule("Panel", 5)), List.of()), ranking);
        challenge.publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objectives", 20)), List.of()), ranking);
        challenges.save(challenge);
        mvc.perform(get("/challenges/{id}/rulebook/versions/1", id.value())).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.requiredSources", contains("JUDGE_PANEL")))
                .andExpect(jsonPath("$.scoring.rules[0].type").value("judges"));
        mvc.perform(get("/challenges/{id}", id.value())).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRulebook.version").value(2))
                .andExpect(jsonPath("$.currentRulebook.requiredSources", contains("AUTOMATIC_MEASUREMENTS")));
        for (String version : List.of("0", "3", "not-a-number")) {
            mvc.perform(get("/challenges/{id}/rulebook/versions/{version}", id.value(), version)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/challenges/unknown-web06/rulebook/versions/1")).andExpect(status().isBadRequest());
    }
}
