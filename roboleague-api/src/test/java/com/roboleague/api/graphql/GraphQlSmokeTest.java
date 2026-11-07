package com.roboleague.api.graphql;

import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 of the GraphQL API: the endpoint answers over MockMvc and {@code Query.editions} reaches the use case.
 */
class GraphQlSmokeTest extends ApiTest {

    @Test
    void editionsAnswersWithTheStoredEditions() throws Exception {
        String id = "gql-smoke-" + System.nanoTime();
        mvc.perform(EditionRequests.edition(id)).andExpect(status().isCreated());

        List<String> ids = graphQl.document("{ editions { id name editionNumber startDate endDate } }")
                .execute()
                .path("editions[*].id").entityList(String.class).get();

        assertThat(ids).contains(id);
    }
}
