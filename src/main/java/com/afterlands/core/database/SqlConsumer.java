package com.afterlands.core.database;

@FunctionalInterface
public interface SqlConsumer<I> {
    void accept(I input) throws Exception;
}

