package com.roboleague.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapters (REST controllers, GraphQL resolvers) reach the core only through the input ports of
 * {@code com.roboleague.port.in}. Only the composition root ({@code config}) and the demo fixture know the use case
 * classes of {@code com.roboleague.usecase}.
 */
class InputPortBoundaryTest {
    private static final Path API = Path.of("src/main/java/com/roboleague/api");
    private static final List<Path> COMPOSITION = List.of(API.resolve("config"), API.resolve("demo"));

    @Test
    void noAdapterDependsOnAUseCaseImplementation() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(API)) {
            offenders = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> COMPOSITION.stream().noneMatch(file::startsWith))
                    .filter(InputPortBoundaryTest::importsUseCasePackage)
                    .map(file -> API.relativize(file).toString())
                    .toList();
        }
        assertThat(offenders).as("adapters importing com.roboleague.usecase").isEmpty();
    }

    @Test
    void theCheckLooksAtTheRealSources() throws IOException {
        try (Stream<Path> files = Files.walk(API)) {
            assertThat(files.filter(file -> file.toString().endsWith("Controller.java"))).isNotEmpty();
        }
    }

    private static boolean importsUseCasePackage(Path file) {
        try {
            return Files.readAllLines(file).stream().anyMatch(line -> line.startsWith("import com.roboleague.usecase."));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
