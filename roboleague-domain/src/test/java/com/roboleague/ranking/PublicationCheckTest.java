package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.roboleague.ranking.StandingsFixtures.*;
import static com.roboleague.support.TestValues.TIME;
import static com.roboleague.support.TestValues.audit;
import static org.assertj.core.api.Assertions.assertThat;

class PublicationCheckTest {

    private static final StandingsTable EMPTY = StandingsTable.rank(BEST_2_OF_3, List.of());

    @Test
    @DisplayName("A turn without an attempt, or whose attempt awaits a source, has no outcome yet")
    void turnsWithoutAnOutcomeAreCounted() {
        Round round = round("t-a", "t-b", "t-c");

        PublicationCheck check = PublicationCheck.of(EMPTY, List.of(round),
                List.of(scored("t-a", "r-1", 50, 3, 0), opened("t-b", "r-1", 1)));

        assertThat(check.unfinishedTurns()).isEqualTo(2);
        assertThat(check.openAppeals()).isZero();
    }

    @Test
    @DisplayName("A cancelled turn does not hold the publication back")
    void aCancelledTurnIsNotCounted() {
        Round round = round("t-a", "t-b").cancelSlot(slotOf("t-b", "r-1"));

        PublicationCheck check = PublicationCheck.of(EMPTY, List.of(round), List.of(scored("t-a", "r-1", 50, 3, 0)));

        assertThat(check.unfinishedTurns()).isZero();
    }

    @Test
    @DisplayName("A turn under appeal has an outcome, but its open appeals are counted")
    void aTurnUnderAppealCountsItsOpenAppeals() {
        Attempt appealed = scored("t-a", "r-1", 50, 3, 0);
        appealed.markUnderAppeal();
        appealed.markUnderAppeal();

        PublicationCheck check = PublicationCheck.of(EMPTY, List.of(round("t-a")), List.of(appealed));

        assertThat(check.openAppeals()).isEqualTo(2);
        assertThat(check.unfinishedTurns()).isZero();
    }

    @Test
    @DisplayName("A disqualified turn has an outcome")
    void aDisqualifiedTurnHasAnOutcome() {
        Attempt disqualified = scored("t-a", "r-1", 50, 3, 0);
        disqualified.disqualify("Robot fuera de reglamento", JUDGE, audit());

        assertThat(PublicationCheck.of(EMPTY, List.of(round("t-a")), List.of(disqualified)).unfinishedTurns())
                .isZero();
    }

    private static Round round(String... teams) {
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1",
                RoundScope.of(CHALLENGE, EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-1"), "Pista", "Madera"),
                List.of(Judge.of(JUDGE, "Juez Uno", "General")));
        for (int index = 0; index < teams.length; index++) {
            String team = teams[index];
            round = round.addSlot(Slot.of(new SlotIdentity(slotOf(team, "r-1"), round.getId(), TeamId.of(team)), assignment,
                    new TimeWindow(TIME.plusMinutes(10L * index), TIME.plusMinutes(10L * index + 5))));
        }
        return round;
    }
}
