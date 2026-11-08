package com.roboleague.api.graphql;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import graphql.language.SelectionSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limits for the GraphQL endpoint. The schema has cycles ({@code Challenge -> edition -> challenges -> ...}), so a
 * query could nest without end; past {@link #MAX_DEPTH} levels it is rejected before any field is resolved (D8).
 */
@Configuration(proxyBeanMethods = false)
class GraphQlConfig {

    static final int MAX_DEPTH = 10;

    /** Spring for GraphQL registers every {@link Instrumentation} bean in the engine. */
    @Bean
    Instrumentation maxQueryDepth() {
        return new DepthLimitExceptIntrospection(MAX_DEPTH);
    }

    /**
     * {@link MaxQueryDepthInstrumentation} that does not count introspection. The {@code IntrospectionQuery} GraphiQL
     * sends to build its docs nests {@code ofType} under {@code __schema.types.fields.type} past ten levels; rejecting
     * it would leave the console without docs. An operation whose root fields all start with {@code __} only reads
     * the schema, and graphql-java bounds introspection on its own. Any other operation, including one that mixes
     * introspection with data fields, is measured as a whole.
     */
    static final class DepthLimitExceptIntrospection extends MaxQueryDepthInstrumentation {

        DepthLimitExceptIntrospection(int maxDepth) {
            super(maxDepth);
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginExecuteOperation(
                InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
            if (onlyIntrospection(parameters.getExecutionContext().getOperationDefinition().getSelectionSet())) {
                return SimpleInstrumentationContext.noOp();
            }
            return super.beginExecuteOperation(parameters, state);
        }

        /** Fragments at the root are measured: only plain {@code __} fields skip the limit. */
        private static boolean onlyIntrospection(SelectionSet root) {
            return !root.getSelections().isEmpty() && root.getSelections().stream()
                    .allMatch(selection -> selection instanceof Field field && field.getName().startsWith("__"));
        }
    }
}
