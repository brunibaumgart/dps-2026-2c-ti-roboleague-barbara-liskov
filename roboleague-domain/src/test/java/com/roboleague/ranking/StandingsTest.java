package com.roboleague.ranking;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.StandingsVersion.Status;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.roboleague.ranking.StandingsFixtures.*;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandingsTest {

    private static final StandingsId ID = new StandingsId(CHALLENGE, CategoryId.of("cat-1"));
    private static final AuditNote NOTE = AuditNote.of(ActorId.of("org-1"), "Cierre de la fecha");
    private static final StandingsTable FIRST = table(5);
    private static final StandingsTable CORRECTED = table(4);

    @Test
    @DisplayName("Every calculation adds a provisional version and keeps the previous ones as they were")
    void everyCalculationAddsAVersionAndKeepsThePreviousOnes() {
        Standings standings = Standings.first(ID, FIRST, TIME);

        StandingsVersion second = standings.recalculate(CORRECTED, TIME.plusHours(1));

        assertThat(standings.versions()).extracting(StandingsVersion::number).containsExactly(1, 2);
        assertThat(standings.version(1)).map(StandingsVersion::table).contains(FIRST);
        assertThat(standings.latest()).isEqualTo(second);
        assertThat(standings.versions()).allSatisfy(version ->
                assertThat(standings.statusOf(version)).isEqualTo(Status.PROVISIONAL));
        assertThat(standings.official()).isEmpty();
    }

    @Test
    @DisplayName("The latest version is published when nothing blocks it and records who published it and why")
    void theLatestVersionIsPublishedWhenNothingBlocksIt() {
        Standings standings = Standings.first(ID, FIRST, TIME);

        StandingsPublication result = standings.publish(1, clean(FIRST), NOTE, TIME.plusHours(2));

        assertThat(result).isEqualTo(new StandingsPublication.Published(standings.latest()));
        assertThat(standings.official()).contains(standings.latest());
        assertThat(standings.statusOf(standings.latest())).isEqualTo(Status.OFFICIAL);
        assertThat(standings.publicationOf(standings.latest()))
                .contains(new OfficialPublication(1, TIME.plusHours(2), NOTE));
    }

    @Test
    @DisplayName("Publishing a newer version replaces the official one, which keeps its publication record")
    void publishingANewerVersionReplacesTheOfficialOne() {
        Standings standings = Standings.first(ID, FIRST, TIME);
        standings.publish(1, clean(FIRST), NOTE, TIME);
        standings.recalculate(CORRECTED, TIME.plusHours(1));

        standings.publish(2, clean(CORRECTED), NOTE, TIME.plusHours(2));

        assertThat(standings.official()).map(StandingsVersion::number).contains(2);
        assertThat(standings.statusOf(standings.version(1).orElseThrow())).isEqualTo(Status.REPLACED);
        assertThat(standings.publications()).extracting(OfficialPublication::version).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Hallazgo 5: open appeals, turns without an outcome and stale results all block, each with its reason")
    void everyReasonThatBlocksThePublicationComesBack() {
        Standings standings = Standings.first(ID, FIRST, TIME);

        StandingsPublication result = standings.publish(1, new PublicationCheck(CORRECTED, 2, 1), NOTE, TIME);

        assertThat(result).isEqualTo(new StandingsPublication.Blocked(List.of(
                "results changed since version 1 was calculated; recalculate first",
                "2 appeal(s) still open",
                "1 turn(s) without an outcome yet")));
        assertThat(standings.official()).isEmpty();
        assertThat(standings.publications()).isEmpty();
    }

    @Test
    @DisplayName("An outdated version cannot be published once a newer one exists")
    void anOutdatedVersionCannotBePublished() {
        Standings standings = Standings.first(ID, FIRST, TIME);
        standings.recalculate(CORRECTED, TIME.plusHours(1));

        StandingsPublication result = standings.publish(1, clean(CORRECTED), NOTE, TIME);

        assertThat(result).isEqualTo(new StandingsPublication.Blocked(
                List.of("only the latest version (2) can be published; version 1 is outdated")));
    }

    @Test
    @DisplayName("The official version is not published twice")
    void theOfficialVersionIsNotPublishedTwice() {
        Standings standings = Standings.first(ID, FIRST, TIME);
        standings.publish(1, clean(FIRST), NOTE, TIME);

        assertThat(standings.publish(1, clean(FIRST), NOTE, TIME.plusHours(1)))
                .isEqualTo(new StandingsPublication.Blocked(List.of("version 1 is already the official one")));
        assertThat(standings.publications()).hasSize(1);
    }

    @Test
    @DisplayName("A version that does not exist cannot be published")
    void aVersionThatDoesNotExistCannotBePublished() {
        Standings standings = Standings.first(ID, FIRST, TIME);

        assertThatThrownBy(() -> standings.publish(7, clean(FIRST), NOTE, TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Standings ch-maze/cat-1 have no version 7");
    }

    @Test
    @DisplayName("Stored standings come back as they were, and broken records are refused")
    void storedStandingsAreRestoredAndBrokenRecordsRefused() {
        StandingsVersion v1 = new StandingsVersion(1, TIME, FIRST);
        StandingsVersion v2 = new StandingsVersion(2, TIME.plusHours(1), CORRECTED);
        OfficialPublication first = new OfficialPublication(1, TIME, NOTE);
        OfficialPublication second = new OfficialPublication(2, TIME.plusHours(2), NOTE);

        assertThat(Standings.restore(ID, List.of(v1, v2), List.of(first, second)).official()).contains(v2);
        assertThatThrownBy(() -> Standings.restore(ID, List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Standings.restore(ID, List.of(v2), List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("skip version 1");
        assertThatThrownBy(() -> Standings.restore(ID, List.of(v1, v2), List.of(second, first)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("out of order");
    }

    @Test
    @DisplayName("A blocked publication needs at least one reason")
    void aBlockedPublicationNeedsAReason() {
        assertThatThrownBy(() -> new StandingsPublication.Blocked(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StandingsTable table(int objectives) {
        return StandingsTable.rank(BEST_2_OF_3,
                List.of(new TeamAttempts(team("t-a"), List.of(scored("t-a", "r-1", 50, objectives, 0)))));
    }

    private static PublicationCheck clean(StandingsTable current) {
        return new PublicationCheck(current, 0, 0);
    }
}
