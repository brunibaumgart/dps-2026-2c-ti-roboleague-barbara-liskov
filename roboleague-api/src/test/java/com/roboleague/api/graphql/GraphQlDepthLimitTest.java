package com.roboleague.api.graphql;

import com.roboleague.api.ApiTest;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.introspection.IntrospectionQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.test.tester.GraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maximum query depth (D8): the schema has cycles, so past {@value GraphQlConfig#MAX_DEPTH} levels the query is
 * aborted before any field runs. Introspection does not count, or GraphiQL could not load its docs.
 */
class GraphQlDepthLimitTest extends ApiTest {

    @Autowired
    private GraphQlSource source;

    /** {@code challenge > edition > challenges > ...}: one level per field, the leaf {@code id} included. */
    private static String challengeCycle(int depth) {
        StringBuilder query = new StringBuilder("challenge(id: \"ch-gql-rank-depth\") {");
        for (int level = 2; level < depth; level++) {
            query.append(level % 2 == 0 ? " edition {" : " challenges {");
        }
        query.append(" id").append(" }".repeat(depth - 1));
        return query.toString();
    }

    @Test
    void aQueryTenLevelsDeepRuns() {
        // The challenge does not exist: the only error is NOT_FOUND, so the depth limit let it through.
        graphQl.document("{ " + challengeCycle(GraphQlConfig.MAX_DEPTH) + " }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND)
                .verify()
                .path("challenge").valueIsNull();
    }

    @Test
    void aQueryElevenLevelsDeepIsAbortedWithoutData() {
        GraphQlTester.Response response = graphQl.document("{ " + challengeCycle(GraphQlConfig.MAX_DEPTH + 1) + " }")
                .execute();

        response.errors()
                .expect(error -> error.getErrorType() == graphql.ErrorType.ExecutionAborted
                        && error.getMessage().equals("maximum query depth exceeded 11 > 10"))
                .verify();
        GraphQlResponse raw = response.returnResponse();
        assertThat(raw.getErrors()).hasSize(1);
        assertThat((Object) raw.getData()).isNull();
    }

    @Test
    void addingAnIntrospectionFieldDoesNotSkipTheLimit() {
        GraphQlTester.Response response = graphQl.document(
                "{ __typename " + challengeCycle(GraphQlConfig.MAX_DEPTH + 1) + " }").execute();

        response.errors()
                .expect(error -> error.getErrorType() == graphql.ErrorType.ExecutionAborted)
                .verify();
        assertThat((Object) response.returnResponse().getData()).isNull();
    }

    @Test
    void theIntrospectionQueryOfGraphiQlIsAcceptedAlthoughItIsDeeperThanTheLimit() {
        graphQl.document(IntrospectionQuery.INTROSPECTION_QUERY)
                .execute()
                .path("__schema.queryType.name").entity(String.class).isEqualTo("Query")
                .path("__schema.types[?(@.name == 'Challenge')].fields[*].name").entityList(String.class)
                .contains("edition", "standings", "appeals");
    }

    @Test
    void withoutTheExceptionTheSameLimitWouldRejectTheIntrospectionQuery() {
        // Proves the exception is needed: the plain instrumentation aborts GraphiQL's query on this schema.
        GraphQL plain = GraphQL.newGraphQL(source.schema())
                .instrumentation(new MaxQueryDepthInstrumentation(GraphQlConfig.MAX_DEPTH))
                .build();

        assertThat(plain.execute(IntrospectionQuery.INTROSPECTION_QUERY).getErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).startsWith("maximum query depth exceeded"));
    }
}
