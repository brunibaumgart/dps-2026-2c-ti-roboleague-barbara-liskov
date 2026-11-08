package com.roboleague.usecase;

import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.util.List;

import static com.roboleague.support.TestValues.TIME;

/** Builds rounds whose slot ids are {@code <round id>-<team id>}. */
final class RoundFixtures {
    static final JudgeId JUDGE = JudgeId.of("j-1");

    private RoundFixtures() {
    }

    static Round round(ChallengeId challenge, EditionId edition, CategoryId category, String id, int number,
                       String... teamIds) {
        Round round = Round.of(RoundInfo.of(RoundId.of(id), id, RoundScope.of(challenge, edition, category, number)));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-" + id), "Pista", "Madera"),
                List.of(Judge.of(JUDGE, "Juez", "General")));
        for (int index = 0; index < teamIds.length; index++) {
            round = round.addSlot(Slot.of(new SlotIdentity(SlotId.of(id + "-" + teamIds[index]), round.getId(),
                    TeamId.of(teamIds[index])), assignment,
                    new TimeWindow(TIME.plusMinutes(10L * index), TIME.plusMinutes(10L * index + 5))));
        }
        return round;
    }
}
