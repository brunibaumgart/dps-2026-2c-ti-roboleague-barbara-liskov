package com.roboleague.api.graphql.view;

/**
 * GraphQL view of one document of a team: its kind and where it is.
 */
public record DocumentView(String kind, String reference) {
}
