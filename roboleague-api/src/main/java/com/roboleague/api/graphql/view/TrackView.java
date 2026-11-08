package com.roboleague.api.graphql.view;

import com.roboleague.scheduling.Track;

/**
 * GraphQL view of a track.
 */
public record TrackView(String id, String name, String surfaceType, boolean active) {

    public static TrackView from(Track track) {
        return new TrackView(track.id().value(), track.name(), track.surfaceType(), track.isActive());
    }
}
