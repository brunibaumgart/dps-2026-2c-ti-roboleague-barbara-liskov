package com.roboleague.repository.jpa;

import com.roboleague.PostgresContainer;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.MemberProfile;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import com.roboleague.tournament.TeamProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaTeamRepository.class})
class JpaTeamRepositoryTest {

    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 10, 3, 11, 45, 30, 500_000_000);

    @Autowired
    private JpaTeamRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("A team comes back with its profile, members, robot specification and verified documentation")
    void verifiedTeamRoundTrip() {
        Team team = team("t-trip", Documentation.restore(Map.of("seguro", "files/seguro.pdf", "autorizacion",
                "files/autorizacion.pdf"), true, VERIFIED_AT, ActorId.of("org-1"), null));
        repository.save(team);
        entityManager.clear();

        Team stored = repository.findById(TeamId.of("t-trip")).orElseThrow();

        assertThat(stored.getProfile()).isEqualTo(team.getProfile());
        assertThat(stored.getMembers()).isEqualTo(team.getMembers());
        assertThat(stored.getRobot().getId()).isEqualTo(RobotId.of("rb-t-trip"));
        assertThat(stored.getRobot().getName()).isEqualTo("Rayo");
        assertThat(stored.getRobot().getSpecification()).isEqualTo(team.getRobot().getSpecification());
        assertThat(stored.getRobot().getSpecification().sensors()).containsExactlyInAnyOrder("lidar", "ultrasonido");
        Documentation documentation = stored.getDocumentation();
        assertThat(documentation.getDocuments()).isEqualTo(team.getDocumentation().getDocuments());
        assertThat(documentation.isVerified()).isTrue();
        assertThat(documentation.getVerifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(documentation.getVerifiedBy()).isEqualTo(ActorId.of("org-1"));
        assertThat(documentation.getRevocationReason()).isNull();
        assertThat(repository.findById(TeamId.of("t-none"))).isEmpty();
    }

    @Test
    @DisplayName("Revoked documentation keeps its reason and no verification metadata")
    void revokedDocumentationRoundTrip() {
        Team team = team("t-revoked", Documentation.restore(Map.of("seguro", "files/seguro.pdf"), false, null, null,
                "Seguro vencido"));
        repository.save(team);
        entityManager.clear();

        Documentation stored = repository.findById(team.getId()).orElseThrow().getDocumentation();

        assertThat(stored.isVerified()).isFalse();
        assertThat(stored.getVerifiedAt()).isNull();
        assertThat(stored.getVerifiedBy()).isNull();
        assertThat(stored.getRevocationReason()).isEqualTo("Seguro vencido");
        assertThat(stored.getDocuments()).containsExactly(Map.entry("seguro", "files/seguro.pdf"));
    }

    @Test
    @DisplayName("Saving a changed team replaces it; every team is listed once, ordered by id")
    void savingAChangedTeamReplacesItAndListsByOrderedId() {
        repository.save(team("t-list-b", new Documentation()));
        Team original = team("t-list-a", new Documentation());
        repository.save(original);
        repository.save(original.withProfile(TeamProfile.of(original.getId(), "Nuevo nombre", "Otra escuela")));
        entityManager.clear();

        assertThat(repository.findAll()).extracting(team -> team.getId().value())
                .containsSubsequence("t-list-a", "t-list-b").doesNotHaveDuplicates();
        assertThat(repository.findById(original.getId()).orElseThrow().getProfile())
                .isEqualTo(TeamProfile.of(original.getId(), "Nuevo nombre", "Otra escuela"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two updates over the same stored team: the second save is refused instead of losing the first")
    void givenATeamChangedByAnotherTransactionThenTheStaleSaveIsRefused() {
        TeamId id = TeamId.of("t-race");
        repository.save(team("t-race", new Documentation()));
        TransactionTemplate useCase = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        useCase.executeWithoutResult(status -> {
            Team loaded = repository.findById(id).orElseThrow();
            other.executeWithoutResult(inner -> repository.save(repository.findById(id).orElseThrow()
                    .withProfile(TeamProfile.of(id, "Primero", ""))));
            assertThatThrownBy(() -> repository.save(loaded.withProfile(TeamProfile.of(id, "Segundo", ""))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("was changed by someone else");
            status.setRollbackOnly();
        });

        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Primero");
    }

    private static Team team(String id, Documentation documentation) {
        LocalDate reference = LocalDate.of(2026, 11, 10);
        return Team.of(TeamId.of(id), "Equipo " + id, "Escuela Técnica 1",
                new Robot(RobotId.of("rb-" + id), "Rayo",
                        RobotSpecification.of(1250.5, 200.0, 180.0, 150.25, 4, Set.of("ultrasonido", "lidar"))),
                List.of(new TeamMember(MemberProfile.of(ParticipantId.of(id + "-ana"), "Ana Pérez"),
                                LocalDate.of(2010, 5, 20), "CAPTAIN"),
                        TeamMember.of(ParticipantId.of(id + "-leo"), "Leo Gómez", LocalDate.of(2011, 2, 1),
                                "MEMBER", reference)),
                documentation);
    }
}
