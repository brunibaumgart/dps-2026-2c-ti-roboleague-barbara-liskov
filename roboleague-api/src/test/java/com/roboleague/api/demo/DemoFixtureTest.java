package com.roboleague.api.demo;

import com.roboleague.PostgresContainer;
import com.roboleague.ranking.Ranking;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.RankingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
@Import(PostgresContainer.class)
class DemoFixtureTest {

    @Autowired
    private RankingRepository rankings;

    @Autowired
    private AppealRepository appeals;

    @Test
    void theDemoEndsWithAnAcceptedAppealAndAnOfficialRanking() {
        assertThat(appeals.findAll()).singleElement().satisfies(appeal -> assertThat(appeal.isAccepted()).isTrue());

        Ranking official = rankings.findLatestByEditionAndCategory("ed-1", "cat-sumo").orElseThrow();
        assertThat(official.isOfficial()).isTrue();
        assertThat(official.getEntries().getFirst().teamScore().teamId()).isEqualTo("t-b");
    }
}
