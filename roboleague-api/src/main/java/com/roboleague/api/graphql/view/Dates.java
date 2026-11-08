package com.roboleague.api.graphql.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dates travel as ISO-8601 text, the same text the REST JSON uses (D7). A missing date stays null.
 */
final class Dates {
    private Dates() {
    }

    static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static String iso(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }
}
