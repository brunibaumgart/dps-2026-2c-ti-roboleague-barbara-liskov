package com.roboleague.api.challenge;

import com.roboleague.api.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeControllerTest extends ApiTest {

    private static final String EDITION = """
            {"id": "%s", "name": "RoboLeague 2026", "editionNumber": 1,
             "tournament": {"id": "t-api", "name": "RoboLeague", "season": {"id": "s-2026", "year": 2026, "name": "2026"}},
             "startDate": "2026-11-10", "endDate": "2026-11-12",
             "categories": [{"id": "cat-junior", "name": "Junior", "minMembers": 2, "maxMembers": 4,
                             "minAge": 12, "maxAge": 17, "maxWeightGrams": 2500}]}
            """;

    private static String rulebook(String penaltyType, double cap) {
        return """
                {"metrics": [{"name": "checkpoint", "source": "AUTOMATIC_MEASUREMENTS", "unit": "COUNT",
                              "range": {"min": 0, "max": 1}}],
                 "scoring": {
                   "rules": [
                     {"type": "composite", "name": "Desempeño en pista", "rules": [
                       {"type": "time", "name": "Tiempo", "numbers": {"basePoints": 100, "targetTimeSeconds": 60,
                         "pointsPerSecondUnder": 1.5, "deductionPerSecondOver": 2, "minPoints": 0}},
                       {"type": "objectives", "name": "Objetivos", "numbers": {"pointsPerObjective": 20,
                         "totalObjectives": 5, "allCompletedBonus": 25}}]}],
                   "bonuses": [
                     {"type": "milestone", "name": "Checkpoint", "numbers": {"threshold": 1, "bonus": 30},
                      "metrics": {"metric": {"name": "checkpoint", "source": "AUTOMATIC_MEASUREMENTS"}}}],
                   "deductions": [
                     {"type": "%s", "name": "Faltas", "numbers": {"deductionPerPenalty": 15}}],
                   "bonusLimit": {"type": "capped", "numbers": {"maximum": %s}}},
                 "ranking": {"roundSelection": {"type": "best-n-of-m", "numbers": {"considered": 3, "outOf": 5}},
                             "criteria": ["higher-total", "lower-time", "fewer-penalties"]}}
                """.formatted(penaltyType, cap);
    }

    private static RequestBuilder addChallenge(String editionId, String challengeId, String rulebook) {
        return post("/editions/{editionId}/challenges", editionId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": "%s", "name": "Laberinto", "rulebook": %s}
                        """.formatted(challengeId, rulebook));
    }

    private static RequestBuilder publish(String challengeId, String rulebook) {
        return post("/challenges/{challengeId}/rulebook/versions", challengeId)
                .contentType(MediaType.APPLICATION_JSON).content(rulebook);
    }

    @BeforeEach
    void createEdition() throws Exception {
        mvc.perform(post("/editions").contentType(MediaType.APPLICATION_JSON).content(EDITION.formatted("api-ed-ch")));
    }

    @Test
    @DisplayName("Agregar un desafío publica su reglamento v1 y lo devuelve tal como se mandó")
    void addingAChallengePublishesVersionOne() throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-1", rulebook("penalty", 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("api-ch-1"))
                .andExpect(jsonPath("$.editionId").value("api-ed-ch"))
                .andExpect(jsonPath("$.currentRulebook.version").value(1))
                .andExpect(jsonPath("$.currentRulebook.requiredSources[0]").value("AUTOMATIC_MEASUREMENTS"))
                .andExpect(jsonPath("$.currentRulebook.metrics[0].name").value("checkpoint"))
                .andExpect(jsonPath("$.currentRulebook.metrics[0].unit").value("COUNT"))
                .andExpect(jsonPath("$.currentRulebook.metrics[0].range.max").value(1.0))
                .andExpect(jsonPath("$.currentRulebook.scoring.rules[0].type").value("composite"))
                .andExpect(jsonPath("$.currentRulebook.scoring.deductions[0].type").value("penalty"))
                .andExpect(jsonPath("$.currentRulebook.scoring.bonusLimit.numbers.maximum").value(40.0))
                .andExpect(jsonPath("$.currentRulebook.ranking.criteria[1]").value("lower-time"));
    }

    @Test
    @DisplayName("Publicar una versión nueva da la v2 y el desafío la muestra como vigente")
    void publishingANewVersionMakesItCurrent() throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-2", rulebook("penalty", 40))).andExpect(status().isCreated());

        mvc.perform(publish("api-ch-2", rulebook("penalty", 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/challenges/{id}", "api-ch-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRulebook.version").value(2))
                .andExpect(jsonPath("$.currentRulebook.scoring.bonusLimit.numbers.maximum").value(20.0));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "tipo de regla desconocido | teleport | 40 | unknown rule type 'teleport'",
            "tope negativo             | penalty  | -1 | maximum must be a finite, non-negative number"})
    @DisplayName("Un reglamento inválido es 422 con cada problema en details")
    void anInvalidRulebookIsUnprocessable(String caseName, String penaltyType, double cap, String problem)
            throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-bad", rulebook(penaltyType, cap)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("Rulebook rejected"))
                .andExpect(jsonPath("$.details[0]").value(containsString(problem)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "regla vacía      | {\"scoring\": {\"rules\": [null], \"bonusLimit\": {\"type\": \"unlimited\"}}, \"ranking\": {\"roundSelection\": {\"type\": \"all-rounds\"}, \"criteria\": [\"higher-total\"]}}",
            "parámetro nulo   | {\"scoring\": {\"rules\": [{\"type\": \"penalty\", \"name\": \"F\", \"numbers\": {\"deductionPerPenalty\": null}}], \"bonusLimit\": {\"type\": \"unlimited\"}}, \"ranking\": {\"roundSelection\": {\"type\": \"all-rounds\"}, \"criteria\": [\"higher-total\"]}}",
            "criterio nulo    | {\"scoring\": {\"rules\": [{\"type\": \"penalty\", \"name\": \"F\", \"numbers\": {\"deductionPerPenalty\": 5}}], \"bonusLimit\": {\"type\": \"unlimited\"}}, \"ranking\": {\"roundSelection\": {\"type\": \"all-rounds\"}, \"criteria\": [null]}}",
            "sin ranking      | {\"scoring\": {\"rules\": [], \"bonusLimit\": {\"type\": \"unlimited\"}}}",
            "unidad desconocida | {\"metrics\": [{\"name\": \"c\", \"source\": \"AUTOMATIC_MEASUREMENTS\", \"unit\": \"KG\", \"range\": {\"min\": 0}}], \"scoring\": {\"rules\": [{\"type\": \"penalty\", \"name\": \"F\", \"numbers\": {\"deductionPerPenalty\": 5}}], \"bonusLimit\": {\"type\": \"unlimited\"}}, \"ranking\": {\"roundSelection\": {\"type\": \"all-rounds\"}, \"criteria\": [\"higher-total\"]}}",
            "JSON mal formado | {\"scoring\": "})
    @DisplayName("Un cuerpo mal armado es un pedido inválido, no un error del servidor")
    void aMalformedBodyIsABadRequest(String caseName, String rulebook) throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-malformed", rulebook))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Un parámetro mal escrito es 422: no se ignora en silencio")
    void aMisspelledParameterIsUnprocessable() throws Exception {
        String misspelled = rulebook("penalty", 40)
                .replace("\"deductionPerPenalty\": 15", "\"deductionPerPenalty\": 15, \"deductionPerPenaltyy\": 7");

        mvc.perform(addChallenge("api-ed-ch", "api-ch-typo", misspelled))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details[0]").value("rule 'Faltas': unknown parameter 'deductionPerPenaltyy'"));
    }

    @Test
    @DisplayName("Una versión nueva inválida es 422 y no cambia la vigente")
    void anInvalidNewVersionIsUnprocessable() throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-3", rulebook("penalty", 40))).andExpect(status().isCreated());

        mvc.perform(publish("api-ch-3", rulebook("teleport", 40))).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/challenges/{id}", "api-ch-3")).andExpect(jsonPath("$.currentRulebook.version").value(1));
    }

    @Test
    @DisplayName("Un desafío repetido es un conflicto")
    void aRepeatedChallengeIsAConflict() throws Exception {
        mvc.perform(addChallenge("api-ed-ch", "api-ch-4", rulebook("penalty", 40))).andExpect(status().isCreated());

        mvc.perform(addChallenge("api-ed-ch", "api-ch-4", rulebook("penalty", 40)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Challenge already exists: api-ch-4"));
    }

    @Test
    @DisplayName("Una edición o un desafío que no existen son un pedido inválido")
    void unknownEditionOrChallengeIsABadRequest() throws Exception {
        mvc.perform(addChallenge("api-ed-none", "api-ch-5", rulebook("penalty", 40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Edition not found: api-ed-none"));
        mvc.perform(get("/challenges/{id}", "api-ch-none"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Challenge not found: api-ch-none"));
    }

    @Test
    @DisplayName("Crear una edición repetida es un conflicto")
    void aRepeatedEditionIsAConflict() throws Exception {
        mvc.perform(post("/editions").contentType(MediaType.APPLICATION_JSON).content(EDITION.formatted("api-ed-twice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categories[0]").value("cat-junior"));

        mvc.perform(post("/editions").contentType(MediaType.APPLICATION_JSON).content(EDITION.formatted("api-ed-twice")))
                .andExpect(status().isConflict());
    }
}
