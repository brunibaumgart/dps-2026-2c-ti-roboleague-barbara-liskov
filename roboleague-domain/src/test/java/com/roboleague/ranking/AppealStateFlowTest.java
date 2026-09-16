package com.roboleague.ranking;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.ranking.appeal.Appeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppealStateFlowTest {

    @Test
    @DisplayName("Appeal follows valid state transitions: Pending -> UnderReview -> Accepted")
    void validAcceptanceFlow() {
        Appeal appeal = Appeal.of("app-1", "att-1", "team-1", "Error en medicion de tiempo", "Video de camara 2");

        assertThat(appeal.getStatusName()).isEqualTo("PENDING");
        assertThat(appeal.isPending()).isTrue();

        // Cannot accept while PENDING without review
        assertThatThrownBy(() -> appeal.accept("Ok", RawMetrics.of(40.0, 1, 0), "judge-arb"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be placed under review");

        // Begin review
        appeal.beginReview("arb-chief");
        assertThat(appeal.getStatusName()).isEqualTo("UNDER_REVIEW");
        assertThat(appeal.getReviewerId()).isEqualTo("arb-chief");

        // Cannot begin review again
        assertThatThrownBy(() -> appeal.beginReview("another-arb"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already under review");

        // Accept with revised metrics
        RawMetrics revisedMetrics = RawMetrics.of(42.0, 1, 0);
        appeal.accept("Se comprueba cronometro defectuoso en 5s", revisedMetrics, "arb-chief");

        assertThat(appeal.getStatusName()).isEqualTo("ACCEPTED");
        assertThat(appeal.isAccepted()).isTrue();
        assertThat(appeal.getRevisedMetrics()).isEqualTo(revisedMetrics);
        assertThat(appeal.getResolvedAt()).isNotNull();

        // Terminal state: cannot accept or reject again
        assertThatThrownBy(() -> appeal.accept("Again", revisedMetrics, "arb-chief"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appeal.reject("Now reject", "arb-chief"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Appeal follows rejection flow: Pending -> UnderReview -> Rejected")
    void rejectionFlow() {
        Appeal appeal = Appeal.of("app-2", "att-2", "team-2", "Falta no cometida", "Ninguna");

        appeal.beginReview("arb-1");
        appeal.reject("Las grabaciones confirman que el robot salio de la pista", "arb-1");

        assertThat(appeal.getStatusName()).isEqualTo("REJECTED");
        assertThat(appeal.isRejected()).isTrue();
        assertThat(appeal.getRevisedMetrics()).isNull();

        // Terminal state: cannot begin review or accept
        assertThatThrownBy(() -> appeal.beginReview("arb-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appeal.accept("Changed mind", RawMetrics.of(10, 0, 0), "arb-2"))
                .isInstanceOf(IllegalStateException.class);
    }
}
