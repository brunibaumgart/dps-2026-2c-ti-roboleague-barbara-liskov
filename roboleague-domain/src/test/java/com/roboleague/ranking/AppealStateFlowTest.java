package com.roboleague.ranking;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppealStateFlowTest {

    @Test
    @DisplayName("Appeal follows valid state transitions: Pending -> UnderReview -> Accepted")
    void validAcceptanceFlow() {
        Appeal appeal = Appeal.of(AppealId.of("app-1"), AttemptId.parse("att-1"), TeamId.of("team-1"), "Error en medicion de tiempo", "Video de camara 2", TIME);

        assertThat(appeal.getStatusName()).isEqualTo("PENDING");
        assertThat(appeal.isPending()).isTrue();

        // Cannot accept while PENDING without review
        assertThatThrownBy(() -> appeal.accept("Ok", RawMetrics.of(40.0, 1, 0), ActorId.of("judge-arb"), TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be placed under review");

        // Begin review
        appeal.beginReview(ActorId.of("arb-chief"));
        assertThat(appeal.getStatusName()).isEqualTo("UNDER_REVIEW");
        assertThat(appeal.getReviewerId()).isEqualTo(ActorId.of("arb-chief"));

        // Cannot begin review again
        assertThatThrownBy(() -> appeal.beginReview(ActorId.of("another-arb")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already under review");

        // Accept with revised metrics
        RawMetrics revisedMetrics = RawMetrics.of(42.0, 1, 0);
        appeal.accept("Se comprueba cronometro defectuoso en 5s", revisedMetrics, ActorId.of("arb-chief"), TIME);

        assertThat(appeal.getStatusName()).isEqualTo("ACCEPTED");
        assertThat(appeal.isAccepted()).isTrue();
        assertThat(appeal.getRevisedMetrics()).isEqualTo(revisedMetrics);
        assertThat(appeal.getSubmittedAt()).isEqualTo(TIME);
        assertThat(appeal.getResolvedAt()).isEqualTo(TIME);

        // Terminal state: cannot accept or reject again
        assertThatThrownBy(() -> appeal.accept("Again", revisedMetrics, ActorId.of("arb-chief"), TIME))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appeal.reject("Now reject", ActorId.of("arb-chief"), TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Appeal follows rejection flow: Pending -> UnderReview -> Rejected")
    void rejectionFlow() {
        Appeal appeal = Appeal.of(AppealId.of("app-2"), AttemptId.parse("att-2"), TeamId.of("team-2"), "Falta no cometida", "Ninguna", TIME);

        appeal.beginReview(ActorId.of("arb-1"));
        appeal.reject("Las grabaciones confirman que el robot salio de la pista", ActorId.of("arb-1"), TIME);

        assertThat(appeal.getStatusName()).isEqualTo("REJECTED");
        assertThat(appeal.isRejected()).isTrue();
        assertThat(appeal.getRevisedMetrics()).isNull();
        assertThat(appeal.getResolvedAt()).isEqualTo(TIME);

        // Terminal state: cannot begin review or accept
        assertThatThrownBy(() -> appeal.beginReview(ActorId.of("arb-2")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appeal.accept("Changed mind", RawMetrics.of(10, 0, 0), ActorId.of("arb-2"), TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Only the reviewer who took the appeal resolves it, and it stays under review otherwise")
    void onlyTheReviewerResolvesTheAppeal() {
        Appeal appeal = Appeal.of(AppealId.of("app-3"), AttemptId.parse("att-3"), TeamId.of("team-3"), "Falta no cometida", "Video", TIME);
        appeal.beginReview(ActorId.of("arb-1"));

        assertThatThrownBy(() -> appeal.reject("No corresponde", ActorId.of("arb-2"), TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Appeal app-3 is under review by arb-1; arb-2 cannot resolve it");
        assertThatThrownBy(() -> appeal.accept("Corresponde", RawMetrics.of(10, 0, 0), ActorId.of("arb-2"), TIME))
                .isInstanceOf(IllegalStateException.class);
        assertThat(appeal.getStatusName()).isEqualTo("UNDER_REVIEW");
        assertThat(appeal.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("An appeal needs a reason")
    void anAppealNeedsAReason() {
        assertThatThrownBy(() -> Appeal.of(AppealId.of("app-4"), AttemptId.parse("att-4"), TeamId.of("team-4"), " ", "Video", TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
