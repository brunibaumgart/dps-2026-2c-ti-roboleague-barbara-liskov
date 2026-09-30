package com.roboleague.api;

import com.roboleague.PostgresContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for API tests: the whole application (real wiring, real Postgres in Testcontainers) driven through MockMvc.
 * The context is shared by every subclass, so each test uses its own ids instead of relying on an empty database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainer.class)
public abstract class ApiTest {

    @Autowired
    protected MockMvc mvc;
}
