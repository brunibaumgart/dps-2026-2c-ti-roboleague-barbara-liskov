package com.roboleague.api;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HTTP requests that set up an edition with a small and a large category, a challenge and registered teams. Shared by
 * the registration and round controller tests, which build on the same scenario.
 */
public final class EditionRequests {
    private static final String RULEBOOK = """
            {"metrics": [], "scoring": {
               "rules": [{"type": "objectives", "name": "Objectives", "numbers": {"pointsPerObjective": 10}},
                         {"type": "judges", "name": "Panel", "numbers": {"weightMultiplier": 5}}],
               "bonuses": [], "deductions": [], "bonusLimit": {"type": "unlimited"}},
             "ranking": {"roundSelection": {"type": "all-rounds"}, "criteria": ["higher-total"]}}
            """;

    private EditionRequests() {
    }

    public static RequestBuilder edition(String id) {
        return post("/editions").contentType(MediaType.APPLICATION_JSON).content("""
                {"id": "%1$s", "name": "Edition", "editionNumber": 1,
                 "tournament": {"id": "http05-tournament", "name": "RoboLeague",
                   "season": {"id": "http05-season", "year": 2026, "name": "2026"}},
                 "startDate": "2026-11-10", "endDate": "2026-11-12",
                 "categories": [{"id": "%1$s-small", "name": "Small", "minMembers": 1, "maxMembers": 4,
                    "minAge": 10, "maxAge": 20, "maxWeightGrams": 2000, "maxLengthMm": 300, "maxWidthMm": 300, "maxHeightMm": 300},
                   {"id": "%1$s-large", "name": "Large", "minMembers": 1, "maxMembers": 4,
                    "minAge": 10, "maxAge": 20, "maxWeightGrams": 4000}]}
                """.formatted(id));
    }

    public static RequestBuilder challenge(String edition, String id) {
        return post("/editions/{id}/challenges", edition).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + id + "\", \"name\": \"Challenge\", \"rulebook\": " + RULEBOOK + "}");
    }

    /** Registers a new team in the small category of the edition. */
    public static RequestBuilder register(String edition, String id, String name, int weight) {
        return post("/editions/{id}/registrations", edition).contentType(MediaType.APPLICATION_JSON)
                .content(enrollment(edition + "-small", team(id, name, weight)));
    }

    public static String enrollment(String category, String team) {
        return "{\"categoryId\": \"" + category + "\", \"team\": " + team + "}";
    }

    /** A team whose member and robot are its own, so several of them can share an edition. */
    public static String team(String id, String name, int weight) {
        return """
                {"id": "%1$s", "name": "%2$s", "institution": "University",
                 "members": [{"id": "%1$s-member", "fullName": "Student", "birthDate": "2010-01-01", "role": "LEADER"}],
                 "robot": {"id": "%1$s-robot", "name": "Bot", "weightGrams": %3$d,
                   "lengthMm": 100, "widthMm": 100, "heightMm": 100, "actuatorCount": 2, "sensors": ["LIDAR"]},
                 "documentation": {"documents": {"consent": "consent.pdf"}, "verifiedBy": "inspector"}}
                """.formatted(id, name, weight);
    }

    /** A round of the category with one track and one judge named after it, starting on the edition's first day. */
    public static String roundBody(String category, int number) {
        return """
                {"categoryId": "%s", "roundNumber": %d, "roundName": "Round", "startTime": "2026-11-10T10:00:00",
                 "slotDurationSeconds": 300, "intervalSeconds": 60,
                 "tracks": [{"id": "http05-p1", "name": "Track", "surfaceType": "Wood", "isActive": true}],
                 "judges": [{"id": "http05-j1", "fullName": "Judge", "specialty": "General"}]}
                """.formatted(category, number).replace("http05-p1", category + "-p1").replace("http05-j1", category + "-j1");
    }
}
