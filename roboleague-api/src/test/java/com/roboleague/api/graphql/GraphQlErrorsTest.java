package com.roboleague.api.graphql;

import com.roboleague.api.ApiTest;
import com.roboleague.api.EditionRequests;
import com.roboleague.port.in.InconsistentStateException;
import graphql.Scalars;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error table of the GraphQL API (D6): what a client sees when an entity does not exist, a value is malformed or a
 * field is not in the schema, and that technical failures never leak their cause. In every case {@code data} holds
 * nothing made up: a failed field is null, an invalid document has no data at all.
 */
class GraphQlErrorsTest extends ApiTest {

    private static final String EDITION = "ed-gql-rank-err";
    private static final String CHALLENGE = "ch-gql-rank-err";

    @Autowired
    private GraphQlErrorResolver resolver;

    @Test
    void aMissingChallengeIsNullWithANotFoundErrorAndTheRestOfTheResponseStays() {
        GraphQlTester.Response response = graphQl.document("""
                        { editions { id } challenge(id: "ch-gql-rank-missing") { id name } }
                        """)
                .execute();

        response.errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Challenge not found: ch-gql-rank-missing")
                        && error.getParsedPath().equals(List.of("challenge")))
                .verify()
                .path("challenge").valueIsNull()
                .path("editions").hasValue();
        assertThat(response.returnResponse().getErrors()).hasSize(1);
    }

    @Test
    void aMissingAppealIsNullWithANotFoundError() {
        graphQl.document("{ appeal(id: \"ap-gql-rank-missing\") { id status } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && error.getMessage().equals("Appeal not found: ap-gql-rank-missing"))
                .verify()
                .path("appeal").valueIsNull();
    }

    @Test
    void aBlankIdIsABadRequestWithTheMessageOfTheDomain() {
        graphQl.document("{ appeal(id: \" \") { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getMessage().equals("appealId cannot be blank"))
                .verify()
                .path("appeal").valueIsNull();
    }

    @Test
    void standingsOfACategoryTheEditionDoesNotOfferAreABadRequestWithoutData() throws Exception {
        mvc.perform(EditionRequests.edition(EDITION)).andExpect(status().isCreated());
        mvc.perform(EditionRequests.challenge(EDITION, CHALLENGE)).andExpect(status().isCreated());

        GraphQlTester.Response response = graphQl.document("""
                        { standings(challengeId: "%s", categoryId: "cat-gql-rank-other") { challengeId } }
                        """.formatted(CHALLENGE))
                .execute();

        response.errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && error.getMessage().equals(
                        "Category cat-gql-rank-other is not offered in edition " + EDITION))
                .verify();
        // standings is non-null in the schema, so the failure nulls the whole data instead of making a table up.
        assertThat((Object) response.returnResponse().getData()).isNull();
    }

    @Test
    void aFieldThatIsNotInTheSchemaIsRejectedBeforeRunningAnything() {
        GraphQlTester.Response response = graphQl.document("{ editions { nombre } }").execute();

        response.errors()
                .expect(error -> error.getErrorType() == graphql.ErrorType.ValidationError
                        && error.getMessage().contains("'nombre'"))
                .verify();
        GraphQlResponse raw = response.returnResponse();
        assertThat(raw.isValid()).isFalse();
        assertThat((Object) raw.getData()).isNull();
    }

    @Test
    void anUnavailableDatabaseIsAnInternalErrorThatSaysSoWithoutTheCause() {
        for (RuntimeException down : List.of(new DataAccessResourceFailureException("connection refused to db:5432"),
                new CannotCreateTransactionException("pool exhausted"))) {
            GraphQLError error = resolve(down);

            assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
            assertThat(error.getMessage()).isEqualTo(GraphQlErrorResolver.UNAVAILABLE);
            assertThat(error.getPath()).isEqualTo(List.of("challenge"));
            assertThat(error.getLocations()).containsExactly(new SourceLocation(1, 3));
        }
    }

    @Test
    void anythingElseIsAGenericInternalErrorWithoutTheCause() {
        for (RuntimeException failure : List.of(new InconsistentStateException("Stored edition not found: ed-x"),
                new IllegalStateException("two versions scored the category"), new NullPointerException("bug"))) {
            GraphQLError error = resolve(failure);

            assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
            assertThat(error.getMessage()).isEqualTo(GraphQlErrorResolver.INTERNAL_ERROR);
        }
    }

    @Test
    void theTechnicalMessagesAreTheSameAsTheRestOnes() throws ReflectiveOperationException {
        // Copied on purpose (see GraphQlErrorResolver); this keeps the two copies from drifting apart.
        assertThat(GraphQlErrorResolver.INTERNAL_ERROR).isEqualTo(restMessage("INTERNAL_ERROR"));
        assertThat(GraphQlErrorResolver.UNAVAILABLE).isEqualTo(restMessage("UNAVAILABLE"));
    }

    /** Resolves a failure as if it came from {@code Query.challenge}, which sits at line 1, column 3. */
    private GraphQLError resolve(Throwable failure) {
        DataFetchingEnvironment env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(MergedField.newMergedField(
                        Field.newField("challenge").sourceLocation(new SourceLocation(1, 3)).build()).build())
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo()
                        .type(Scalars.GraphQLString)
                        .path(ResultPath.rootPath().segment("challenge"))
                        .build())
                .build();
        List<GraphQLError> errors = resolver.resolveException(failure, env).block();
        assertThat(errors).hasSize(1);
        return errors.getFirst();
    }

    /** The constant is package-private in com.roboleague.api; reflection reads it without widening it. */
    private static String restMessage(String name) throws ReflectiveOperationException {
        java.lang.reflect.Field constant = Class.forName("com.roboleague.api.ApiExceptionHandler").getDeclaredField(name);
        constant.setAccessible(true);
        return (String) constant.get(null);
    }
}
