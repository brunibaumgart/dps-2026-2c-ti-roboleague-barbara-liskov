package com.roboleague.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The API port serves the web frontend and its modules (static resources, no controller).
 */
class WebFrontendTest extends ApiTest {
    @Test
    void servesTheFrontendAndItsModulesOnTheApiPort() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Gestión de competencia"))
                .andExpect(content().string(containsString("/js/app.js")));
        mvc.perform(get("/styles.css")).andExpect(status().isOk()).andExpect(content().string(containsString(".app-shell")));
        for (String module : List.of("app", "api", "ui", "models", "registration", "scheduling", "results", "appeals", "standings")) {
            mvc.perform(get("/js/" + module + ".js")).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("<!doctype html>"))));
        }
        // Spring's welcome mapping forwards to the served index rather than requiring another frontend server.
        mvc.perform(get("/")).andExpect(status().isOk());
    }
}
