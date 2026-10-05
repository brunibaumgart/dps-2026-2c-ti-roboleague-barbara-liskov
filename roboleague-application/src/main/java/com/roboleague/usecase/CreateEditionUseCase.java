package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Edition;

import java.util.Objects;

/**
 * Configures an event: a new edition of a tournament with its dates and categories.
 */
public class CreateEditionUseCase {
    private final EditionRepository editionRepository;

    public CreateEditionUseCase(EditionRepository editionRepository) {
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
    }

    public Edition execute(CreateEditionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        String id = command.context().header().id();
        if (editionRepository.findById(id).isPresent()) {
            throw new IllegalStateException("Edition already exists: " + id);
        }
        Edition edition = Edition.of(command.context(), command.dates());
        for (Category category : command.categories()) {
            edition.addCategory(category);
        }
        editionRepository.save(edition);
        return edition;
    }
}
