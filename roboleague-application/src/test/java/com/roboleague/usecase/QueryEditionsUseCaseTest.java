package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.tournament.EditionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.usecase.RegistrationFixtures.category;
import static com.roboleague.usecase.RegistrationFixtures.edition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryEditionsUseCaseTest {
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final QueryEditionsUseCase query = new QueryEditionsUseCase(editions);

    QueryEditionsUseCaseTest() {
        editions.save(edition("ed-b", DATE, category("maze", 1, 4, 14, 25, 2000)));
        editions.save(edition("ed-a", DATE, category("maze", 1, 4, 14, 25, 2000)));
    }

    @Test
    void givenRepeatedIdsThenEachEditionComesOnceKeyedById() {
        assertThat(query.byIds(List.of(EditionId.of("ed-b"), EditionId.of("ed-a"), EditionId.of("ed-b"))))
                .containsOnlyKeys(EditionId.of("ed-a"), EditionId.of("ed-b"));
    }

    @Test
    void givenAMissingIdThenTheStoredReferenceIsInconsistent() {
        assertThatThrownBy(() -> query.byIds(List.of(EditionId.of("ghost"))))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining("ghost");
    }

    @Test
    void givenNoIdsThenTheMapIsEmpty() {
        assertThat(query.byIds(List.of())).isEmpty();
    }

    @Test
    void listsEditionsById() {
        assertThat(query.list()).extracting(edition -> edition.getId().value()).containsExactly("ed-a", "ed-b");
        assertThatThrownBy(() -> query.get(EditionId.of("ghost"))).isInstanceOf(NotFoundException.class);
    }
}
