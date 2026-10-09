package com.roboleague.support;

/** Generates new opaque identities; client-supplied and derived identities do not use this port. */
@FunctionalInterface
public interface IdGenerator {
    String nextId();
}
