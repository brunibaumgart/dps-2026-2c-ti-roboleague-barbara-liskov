package com.roboleague.support;

import com.roboleague.ranking.RankingId;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.RobotId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedIdentitiesTest {
    @TempDir
    Path output;

    static Stream<Function<String, Object>> identities() {
        return Stream.of(TeamId::of, JudgeId::of, SlotId::of, RoundId::of, EditionId::of,
                CategoryId::of, AppealId::of, RankingId::of, ParticipantId::of, RobotId::of,
                TrackId::of, ActorId::of, ChallengeId::of);
    }

    @ParameterizedTest
    @MethodSource("identities")
    void rejectsMissingValuesAndPreservesHistoricalText(Function<String, Object> identity) {
        assertThatThrownBy(() -> identity.apply(null)).isInstanceOf(NullPointerException.class);
        for (String blank : List.of("", " ", "\t\n")) {
            assertThatThrownBy(() -> identity.apply(blank)).isInstanceOf(IllegalArgumentException.class);
        }
        String historical = " Legacy-Id 01 ";
        assertThat(identity.apply(historical)).hasToString(historical).isEqualTo(identity.apply(historical));
        assertThat(identity.apply(historical)).isNotEqualTo(identity.apply(historical.toLowerCase()));
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
