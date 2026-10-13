package com.roboleague.support;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What every typed identity built on {@link Identifiers} guarantees; each package checks its own identities with it.
 */
public final class IdentityContract {
    private IdentityContract() { }

    /** Rejects missing or blank text and keeps historical text exactly as it was stored. */
    public static void rejectsMissingValuesAndPreservesHistoricalText(Function<String, Object> identity) {
        assertThatThrownBy(() -> identity.apply(null)).isInstanceOf(NullPointerException.class);
        for (String blank : List.of("", " ", "\t\n")) {
            assertThatThrownBy(() -> identity.apply(blank)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageEndingWith("cannot be blank");
        }
        String historical = " Legacy-Id 01 ";
        assertThat(identity.apply(historical)).hasToString(historical).isEqualTo(identity.apply(historical));
        assertThat(identity.apply(historical)).isNotEqualTo(identity.apply(historical.toLowerCase()));
    }

    /** Accepts up to the stored column length and rejects longer text. */
    public static void acceptsUpToTheStoredColumnLength(Function<String, Object> identity) {
        String longest = "x".repeat(Identifiers.MAX_LENGTH);
        assertThat(identity.apply(longest)).hasToString(longest);
        assertThatThrownBy(() -> identity.apply(longest + "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("cannot be longer than 64 characters");
    }
}
