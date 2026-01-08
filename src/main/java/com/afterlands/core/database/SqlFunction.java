package com.afterlands.core.database;

@FunctionalInterface
public interface SqlFunction<I, O> {
    O apply(I input) throws Exception;
}

