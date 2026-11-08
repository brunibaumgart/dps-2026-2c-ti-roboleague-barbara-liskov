package com.roboleague.api;

import com.roboleague.PostgresContainer;
import com.roboleague.support.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for API tests: the whole application (real wiring, real Postgres in Testcontainers) driven through MockMvc,
 * and through {@link HttpGraphQlTester} over the same MockMvc for the GraphQL endpoint.
 * The context is shared by every subclass, so each test uses its own ids instead of relying on an empty database.
 * The GraphQL tester lives here on purpose: a differently annotated subclass would be a second Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
@Import({PostgresContainer.class, ApiTest.FixedClock.class})
public abstract class ApiTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return () -> com.roboleague.support.TestValues.TIME;
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected HttpGraphQlTester graphQl;
}
