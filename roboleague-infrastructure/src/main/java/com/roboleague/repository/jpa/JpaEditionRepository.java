package com.roboleague.repository.jpa;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link EditionRepository} on Postgres.
 * <p>
 * Editions are immutable, so the instance being saved is never the one that was loaded and the version cannot be
 * kept per instance as {@link JpaAttemptRepository} does. Inside the use case's transaction the persistence
 * context still holds the row as it was read: saving sends back that version, so if another request saved the
 * edition in between (two registrations at the same time) Postgres refuses the write instead of losing one.
 * An edition not read in the same transaction is written over its current version.
 */
@Repository
public class JpaEditionRepository implements EditionRepository {

    private final SpringDataEditions jpa;

    JpaEditionRepository(SpringDataEditions jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Edition edition) {
        Objects.requireNonNull(edition, "edition cannot be null");
        EditionJpaEntity entity = EditionMapper.toEntity(edition);
        entity.version = jpa.findById(entity.id).map(read -> read.version).orElse(null);
        WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Edition " + edition.getId()
                + " was changed by someone else while it was being updated; load it again and retry");
    }

    @Override
    public Optional<Edition> findById(EditionId id) {
        return jpa.findById(id.value()).map(EditionMapper::toDomain);
    }

    @Override
    public List<Edition> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(EditionMapper::toDomain).toList();
    }

    @Override
    public List<Edition> findByTeamId(TeamId teamId) {
        return jpa.findByTeamId(teamId.value()).stream().map(EditionMapper::toDomain).toList();
    }
}
