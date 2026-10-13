package com.roboleague.scheduling;

import com.roboleague.support.ActorId;
import com.roboleague.support.IdentityContract;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identities of scheduling follow the identity contract, and the compiler keeps a judge from standing where a team
 * is expected.
 */
class SchedulingIdentitiesTest {
    @TempDir
    Path output;

    static Stream<Function<String, Object>> identities() {
        return Stream.of(JudgeId::of, SlotId::of, RoundId::of, TrackId::of);
    }

    @ParameterizedTest
    @MethodSource("identities")
    void rejectsMissingValuesAndPreservesHistoricalText(Function<String, Object> identity) {
        IdentityContract.rejectsMissingValuesAndPreservesHistoricalText(identity);
    }

    @ParameterizedTest
    @MethodSource("identities")
    void acceptsUpToTheStoredColumnLengthAndRejectsLongerValues(Function<String, Object> identity) {
        IdentityContract.acceptsUpToTheStoredColumnLength(identity);
    }

    @Test
    void sameTextDoesNotMakeDifferentRolesEqual() {
        assertThat((Object) TeamId.of("shared-1")).isNotEqualTo(JudgeId.of("shared-1"))
                .isNotEqualTo(SlotId.of("shared-1"));
        assertThat(JudgeId.of("shared-1").asActorId()).isEqualTo(ActorId.of("shared-1"));
    }

    @Test
    void compilerRejectsSwappedTeamAndJudgeIdentities() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        Path source = output.resolve("IdentityConsumer.java");
        String consumer = """
                import com.roboleague.scheduling.*;
                import com.roboleague.tournament.*;
                class IdentityConsumer {
                    SlotIdentity slot = new SlotIdentity(SlotId.of("s-1"), RoundId.of("r-1"), %s);
                }
                """;
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            List<String> options = List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                    "-d", output.toString());
            Files.writeString(source, consumer.formatted("JudgeId.of(\"j-1\")"));
            assertThat(compiler.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjects(source)).call()).isFalse();
            assertThat(diagnostics.getDiagnostics()).anyMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR
                    && d.getMessage(java.util.Locale.ROOT).contains("JudgeId")
                    && d.getMessage(java.util.Locale.ROOT).contains("TeamId"));
            Files.writeString(source, consumer.formatted("TeamId.of(\"t-1\")"));
            assertThat(compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjects(source)).call()).isTrue();
        }
    }
}
