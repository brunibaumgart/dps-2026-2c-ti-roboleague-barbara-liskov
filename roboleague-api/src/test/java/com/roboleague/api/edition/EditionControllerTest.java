package com.roboleague.api.edition;

import com.roboleague.api.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EditionControllerTest extends ApiTest {

    private static final String SEASON = """
            {"id": "s-2026", "year": 2026, "name": "Temporada 2026"}""";
    private static final String TOURNAMENT = """
            {"id": "t-api", "name": "RoboLeague", "season": %s}""".formatted(SEASON);
    private static final String CATEGORY = """
            {"id": "cat-junior", "name": "Junior", "minMembers": 2, "maxMembers": 4,
             "minAge": 12, "maxAge": 17, "maxWeightGrams": 2500}""";

    private static String edition(String id, String tournament, String category) {
        return """
                {"id": "%s", "name": "RoboLeague 2026", "editionNumber": 1, "tournament": %s,
                 "startDate": "2026-11-10", "endDate": "2026-11-12", "categories": [%s]}
                """.formatted(id, tournament, category);
    }

    private static RequestBuilder create(String body) {
        return post("/editions").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editionsMissingARequiredField")
    @DisplayName("Una edición sin un dato obligatorio es un pedido inválido, no un error del servidor")
    void anEditionMissingARequiredFieldIsABadRequest(String caseName, String body, String error) throws Exception {
        mvc.perform(create(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(error));
    }

    static Stream<Arguments> editionsMissingARequiredField() {
        return Stream.of(
                Arguments.of("torneo sin id",
                        edition("api-ed-missing-1", """
                                {"name": "RoboLeague", "season": %s}""".formatted(SEASON), CATEGORY),
                        "a tournament needs id, name and season"),
                Arguments.of("temporada sin nombre",
                        edition("api-ed-missing-2", """
                                {"id": "t-api", "name": "RoboLeague", "season": {"id": "s-2026", "year": 2026}}""",
                                CATEGORY),
                        "a season needs id and name"),
                Arguments.of("categoría sin id",
                        edition("api-ed-missing-3", TOURNAMENT, """
                                {"name": "Junior", "minMembers": 2, "maxMembers": 4,
                                 "minAge": 12, "maxAge": 17, "maxWeightGrams": 2500}"""),
                        "a category needs id and name"));
    }

    @Test
    @DisplayName("Dos categorías con el mismo id son un pedido inválido")
    void twoCategoriesWithTheSameIdAreABadRequest() throws Exception {
        String sameIdOtherLimits = """
                {"id": "cat-junior", "name": "Junior libre", "minMembers": 1, "maxMembers": 9,
                 "minAge": 5, "maxAge": 90, "maxWeightGrams": 9000}""";

        mvc.perform(create(edition("api-ed-repeated-category", TOURNAMENT, CATEGORY + ", " + sameIdOtherLimits)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Category already offered in this edition: cat-junior"));
    }
}
