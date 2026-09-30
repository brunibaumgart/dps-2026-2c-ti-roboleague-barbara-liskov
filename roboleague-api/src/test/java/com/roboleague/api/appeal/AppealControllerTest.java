package com.roboleague.api.appeal;

import com.roboleague.api.ApiTest;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppealControllerTest extends ApiTest {

    @Autowired
    private AppealRepository appeals;

    @Test
    void reviewMovesTheAppealUnderReview() throws Exception {
        appeals.save(Appeal.of("api-app-1", "att-1", "team-1", "Penalizacion inexistente", "Video"));

        mvc.perform(review("api-app-1", "arb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("api-app-1"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerId").value("arb-1"));
    }

    @Test
    void reviewingTwiceIsAConflict() throws Exception {
        appeals.save(Appeal.of("api-app-2", "att-2", "team-2", "Penalizacion inexistente", "Video"));
        mvc.perform(review("api-app-2", "arb-1")).andExpect(status().isOk());

        mvc.perform(review("api-app-2", "arb-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Appeal is already under review by arb-1"));
    }

    @Test
    void reviewingAnUnknownAppealIsABadRequest() throws Exception {
        mvc.perform(review("does-not-exist", "arb-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Appeal not found: does-not-exist"));
    }

    private static RequestBuilder review(String appealId, String reviewerId) {
        return post("/appeals/{appealId}/review", appealId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reviewerId": "%s"}
                        """.formatted(reviewerId));
    }
}
