package com.roboleague.repository.jpa;

import static com.roboleague.support.TestValues.*;
import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.ranking.appeal.Appeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaAppealRepository.class})
class JpaAppealRepositoryTest {

    @Autowired
    private JpaAppealRepository repository;

    @Test
    @DisplayName("A pending appeal comes back from Postgres with its claim and state")
    void pendingAppealRoundTrip() {
        Appeal appeal = Appeal.of("app-1", "att-1", "team-1", "Error en medicion de tiempo", "Video de camara 2", TIME);

        repository.save(appeal);
        Appeal stored = repository.findById("app-1").orElseThrow();

        assertThat(stored.getAttemptId()).isEqualTo("att-1");
        assertThat(stored.getTeamId()).isEqualTo("team-1");
        assertThat(stored.getReason()).isEqualTo("Error en medicion de tiempo");
        assertThat(stored.getEvidenceDescription()).isEqualTo("Video de camara 2");
        assertThat(stored.getStatusName()).isEqualTo("PENDING");
        assertThat(stored.getSubmittedAt()).isCloseTo(appeal.getSubmittedAt(), within(1, ChronoUnit.MICROS));
        assertThat(stored.getRevisedMetrics()).isNull();
    }

    @Test
    @DisplayName("An accepted appeal keeps its revised metrics and resolution")
    void acceptedAppealRoundTrip() {
        Appeal appeal = Appeal.of("app-2", "att-2", "team-2", "Penalizacion inexistente", "Video pista", TIME);
        appeal.beginReview("arb-1");
        RawMetrics revised = RawMetrics.of(45.0, 5, 0, Map.of("diseño", 8.5));
        appeal.accept("Penalizaciones corregidas", revised, "arb-1", TIME);

        repository.save(appeal);
        Appeal stored = repository.findById("app-2").orElseThrow();

        assertThat(stored.isAccepted()).isTrue();
        assertThat(stored.getReviewerId()).isEqualTo("arb-1");
        assertThat(stored.getResolutionNotes()).isEqualTo("Penalizaciones corregidas");
        assertThat(stored.getRevisedMetrics()).isEqualTo(revised);
        assertThat(stored.getResolvedAt()).isCloseTo(appeal.getResolvedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("A restored appeal keeps enforcing its state machine")
    void restoredAppealKeepsItsBehaviour() {
        Appeal appeal = Appeal.of("app-3", "att-3", "team-3", "Falta no cometida", "", TIME);
        appeal.beginReview("arb-1");
        repository.save(appeal);

        Appeal stored = repository.findById("app-3").orElseThrow();
        stored.reject("Las grabaciones confirman la falta", "arb-1", TIME);
        repository.save(stored);

        assertThat(repository.findById("app-3").orElseThrow().isRejected()).isTrue();
    }

    @Test
    @DisplayName("Appeals can be found by attempt and by pending state")
    void queries() {
        Appeal pending = Appeal.of("app-4", "att-4", "team-4", "Motivo", "", TIME);
        Appeal underReview = Appeal.of("app-5", "att-4", "team-4", "Otro motivo", "", TIME);
        underReview.beginReview("arb-1");
        repository.save(pending);
        repository.save(underReview);

        assertThat(repository.findByAttemptId("att-4")).extracting(Appeal::getAppealId)
                .containsExactlyInAnyOrder("app-4", "app-5");
        assertThat(repository.findPendingAppeals()).extracting(Appeal::getAppealId)
                .containsExactly("app-4");
    }
}
