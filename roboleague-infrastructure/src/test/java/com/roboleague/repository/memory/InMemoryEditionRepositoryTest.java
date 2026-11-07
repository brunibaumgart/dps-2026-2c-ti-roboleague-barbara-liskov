package com.roboleague.repository.memory;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Tournament;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEditionRepositoryTest {
    @Test
    void findsByIdsOnceOrderedByIdSkippingMissingOnesAndEmptyGivesNothing() {
        var repository = new InMemoryEditionRepository();
        for (String id : List.of("b", "a", "c")) {
            repository.save(edition(id));
        }
        assertThat(repository.findByIds(List.of(EditionId.of("c"), EditionId.of("a"), EditionId.of("none"),
                EditionId.of("a")))).extracting(edition -> edition.getId().value()).containsExactly("a", "c");
        assertThat(repository.findByIds(List.of())).isEmpty();
    }

    private static Edition edition(String id) {
        LocalDate start = LocalDate.of(2026, 11, 10);
        return Edition.of(EditionId.of(id), Tournament.of("t-1", "Tournament", new Season("s-1", 2026, "2026")),
                1, "Edition " + id, start, start.plusDays(1),
                List.of(Category.of(CategoryId.of("maze"), "Maze", 1, 4, 14, 25, 2000, 300, 300, 300)));
    }
}
