package com.roboleague.support;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Application calendar time, independent of the host's default time zone. */
@FunctionalInterface
public interface Clock {
    LocalDateTime now();

    default LocalDate today() {
        return now().toLocalDate();
    }
}
