package com.afterlands.core.actions.dialect;

import com.afterlands.core.actions.ActionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ActionDialect {
    boolean supports(@NotNull String line);

    @Nullable ActionSpec parse(@NotNull String line);
}

