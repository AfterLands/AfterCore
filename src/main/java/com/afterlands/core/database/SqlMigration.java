package com.afterlands.core.database;

import java.sql.Connection;

@FunctionalInterface
public interface SqlMigration {
    void apply(Connection connection) throws Exception;
}

