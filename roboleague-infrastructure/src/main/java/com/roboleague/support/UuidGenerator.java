package com.roboleague.support;

import java.util.UUID;

public final class UuidGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
