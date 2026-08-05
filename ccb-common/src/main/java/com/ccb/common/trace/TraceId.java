package com.ccb.common.trace;

import java.util.UUID;

public final class TraceId {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() {
    }

    public static String getOrCreate() {
        String value = CURRENT.get();
        if (value == null) {
            value = UUID.randomUUID().toString();
            CURRENT.set(value);
        }
        return value;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
