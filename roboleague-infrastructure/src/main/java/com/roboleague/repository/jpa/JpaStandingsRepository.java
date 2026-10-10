package com.roboleague.repository.jpa;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.StandingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Driven adapter: implements the domain port {@link StandingsRepository} on Postgres.
 */
@Repository
public class JpaStandingsRepository implements StandingsRepository {

    private final SpringDataStandings jpa;
    /**
     * The stored version each loaded standings were read with, so two recalculations or publications at the same
     * time cannot both append a version with the same number: Postgres refuses the second write and it surfaces
     * in {@link #save}. Same mechanism as {@link JpaAttemptRepository}.
     */
    private final Map<Standings, Long> loadedVersions = Collections.synchronizedMap(new WeakHashMap<>());

    JpaStandingsRepository(SpringDataStandings jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Standings standings) {
        StandingsJpaEntity entity = StandingsMapper.toEntity(standings);
        entity.version = loadedVersions.get(standings);
        try {
            loadedVersions.put(standings, jpa.saveAndFlush(entity).version);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrent) {
            throw new IllegalStateException("Standings " + standings.getId()
                    + " were changed by someone else while they were being updated; load them again and retry");
        }
    }

    @Override
    public Optional<Standings> findById(StandingsId id) {
        return jpa.findById(id.value()).map(entity -> {
            Standings standings = StandingsMapper.toDomain(entity);
            loadedVersions.put(standings, entity.version);
            return standings;
        });
    }
}
