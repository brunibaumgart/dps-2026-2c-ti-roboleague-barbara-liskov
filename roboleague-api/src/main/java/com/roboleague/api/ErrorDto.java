package com.roboleague.api;

import java.util.List;

/**
 * Body of every error response: what went wrong and, when there are several reasons, each one.
 */
public record ErrorDto(String error, List<String> details) {

    public ErrorDto(String error) {
        this(error, List.of());
    }
}
