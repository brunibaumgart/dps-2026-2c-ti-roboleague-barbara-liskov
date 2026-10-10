package com.roboleague.api.standings;

import com.roboleague.api.ApiTest;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
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
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.RegisterTeamUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StandingsControllerTest extends ApiTest {

    private static final String EDITION = "ed-api-std";
    private static final String CATEGORY = "cat-api-std";
    private static final String TEAM_A = "team-api-std-a";
    private static final String TEAM_B = "team-api-std-b";
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final AtomicInteger NEXT = new AtomicInteger();

    @Autowired
    private EditionRepository editions;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private RoundRepository rounds;
    @Autowired
    private RegisterTeamUseCase registerTeam;

    @BeforeEach
    void openTheCategory() {
        if (editions.findById(EditionId.of(EDITION)).isPresent()) {
            return;
        }
        Category junior = Category.of(CategoryId.of(CATEGORY), "Junior", 2, 4, 13, null, 2500);
        editions.save(Edition.of(EditionId.of(EDITION), Tournament.of("tor-api-std", "Torneo", new Season("s-api-std", 2026, "2026")),
                1, "Edicion API", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), List.of(junior)));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_A, "Alpha"));
        registerTeam.execute(EditionId.of(EDITION), CategoryId.of(CATEGORY), team(TEAM_B, "Beta"));
    }

    @Test
    void standingsAreEmptyBeforeTheFirstCalculation() throws Exception {
        String challenge = openChallenge("empty");

        mvc.perform(get("/challenges/{id}/standings", challenge).param("categoryId", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challenge))
                .andExpect(jsonPath("$.versions", hasSize(0)))
                .andExpect(jsonPath("$.latest").value(nullValue()))
                .andExpect(jsonPath("$.official").value(nullValue()))
                .andExpect(jsonPath("$.pending.unfinishedTurns").value(2))
                .andExpect(jsonPath("$.pending.outdated").value(true));
    }

    @Test
    void recalculatingCreatesAProvisionalVersionAndPublishingNeedsEveryTurnScored() throws Exception {
        String challenge = openChallenge("publish");
        String slotA = "slot-api-std-publish-a";
        String slotB = "slot-api-std-publish-b";

        score(challenge, slotA + "-1", 3);

        mvc.perform(post("/challenges/{id}/standings/versions", challenge).param("categoryId", CATEGORY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("PROVISIONAL"))
                .andExpect(jsonPath("$.entries[*].teamId", contains(TEAM_A, TEAM_B)))
                .andExpect(jsonPath("$.entries[0].total").value(60.0))
                .andExpect(jsonPath("$.entries[1].total").value(0.0));

        mvc.perform(publish(challenge, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Publication blocked"))
                .andExpect(jsonPath("$.details", contains("1 turn(s) without an outcome yet")));

        score(challenge, slotB + "-1", 4);
        mvc.perform(publish(challenge, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0]").value("results changed since version 1 was calculated; recalculate first"));

        mvc.perform(post("/challenges/{id}/standings/versions", challenge).param("categoryId", CATEGORY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.entries[0].teamId").value(TEAM_B));

        mvc.perform(publish(challenge, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0]").value("only the latest version (2) can be published; version 1 is outdated"));

        mvc.perform(publish(challenge, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.official.number").value(2))
                .andExpect(jsonPath("$.official.status").value("OFFICIAL"))
                .andExpect(jsonPath("$.latest.status").value("OFFICIAL"));

        mvc.perform(get("/challenges/{id}/standings/versions/{version}", challenge, 1).param("categoryId", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVISIONAL"))
                .andExpect(jsonPath("$.entries[0].teamId").value(TEAM_A));
    }

    @Test
    void aMissingChallengeOrVersionIsABadRequest() throws Exception {
        String challenge = openChallenge("missing");
        mvc.perform(get("/challenges/{id}/standings", "ch-missing-std").param("categoryId", CATEGORY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Challenge not found: ch-missing-std"));
        mvc.perform(get("/challenges/{id}/standings/versions/{version}", challenge, 9).param("categoryId", CATEGORY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Standings not calculated yet: " + challenge + "/" + CATEGORY));
    }

    private String openChallenge(String suffix) {
        String challenge = "ch-api-std-" + suffix + "-" + NEXT.incrementAndGet();
        challenges.save(Challenge.draft(ChallengeId.of(challenge), EditionId.of(EDITION), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", 20.0)), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        schedule(challenge, "slot-api-std-" + suffix + "-a", TEAM_A, 0);
        schedule(challenge, "slot-api-std-" + suffix + "-b", TEAM_B, 1);
        return challenge;
    }

    private void schedule(String challenge, String slotId, String teamId, int index) {
        String roundId = "round-" + slotId;
        Round round = Round.of(RoundInfo.of(RoundId.of(roundId), "Ronda",
                RoundScope.of(ChallengeId.of(challenge), EditionId.of(EDITION), CategoryId.of(CATEGORY), NEXT.incrementAndGet())));
        rounds.save(round.addSlot(Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of(roundId), TeamId.of(teamId)),
                SlotAssignment.of(Track.active(TrackId.of("trk-api-std"), "Pista", "Madera"),
                        List.of(Judge.of(JudgeId.of("j-api-std"), "Juez", "General"))),
                new TimeWindow(START.plusMinutes(index * 10L), START.plusMinutes(index * 10L + 5)))));
    }

    private void score(String challenge, String attemptId, int objectives) throws Exception {
        mvc.perform(put("/attempts/{attemptId}/measurements", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "judgeId": "j-api-std", "timeSeconds": 60, "objectives": %d,
                                 "penalties": 0, "consumption": 0, "measurements": {}}
                                """.formatted(challenge, objectives)))
                .andExpect(status().isOk());
    }

    private static RequestBuilder publish(String challenge, int version) {
        return post("/challenges/{id}/standings/versions/{version}/publication", challenge, version)
                .param("categoryId", CATEGORY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publishedBy": "org-1", "notes": "Cierre"}
                        """);
    }

    private static Team team(String id, String name) {
        return Team.of(TeamId.of(id), name, "ITBA",
                new Robot(RobotId.of("r-" + id), name + "-Bot", RobotSpecification.of(2000, 200, 200, 200, 2, Set.of("LIDAR"))),
                List.of(TeamMember.of(ParticipantId.of("m1-" + id), name + " A", LocalDate.of(2004, 1, 1), "LEADER", DATE),
                        TeamMember.of(ParticipantId.of("m2-" + id), name + " B", LocalDate.of(2004, 2, 2), "DEV", DATE)),
                new Documentation().withDocument("DOC", "doc.pdf").verify(ActorId.of("inspector"), TIME));
    }
}
