package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Documentation;

import java.util.List;
import java.util.Map;

/**
 * GraphQL view of a team documentation. Documents are sorted by kind; verification fields are null until verified.
 */
public record DocumentationView(List<DocumentView> documents, boolean verified, String verifiedAt, String verifiedBy,
                                String revocationReason) {

    public static DocumentationView from(Documentation documentation) {
        List<DocumentView> documents = documentation.getDocuments().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DocumentView(entry.getKey(), entry.getValue()))
                .toList();
        return new DocumentationView(documents, documentation.isVerified(), Dates.iso(documentation.getVerifiedAt()),
                documentation.getVerifiedBy() == null ? null : documentation.getVerifiedBy().value(),
                documentation.getRevocationReason());
    }
}
