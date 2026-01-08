package com.afterlands.core.database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum DatabaseType {
    MYSQL,
    SQLITE;

    @NotNull
    public static DatabaseType fromString(@Nullable String raw) {
        if (raw == null) return MYSQL;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "sqlite" -> SQLITE;
            case "mysql" -> MYSQL;
            default -> MYSQL;
        };
    }
}

