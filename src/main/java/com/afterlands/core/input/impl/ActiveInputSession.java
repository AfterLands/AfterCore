package com.afterlands.core.input.impl;

import com.afterlands.core.input.InputRequest;
import com.afterlands.core.input.InputResult;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sessão de input ativa para um jogador.
 */
final class ActiveInputSession {

    private final UUID playerId;
    private final InputRequest request;
    private final CompletableFuture<InputResult> future;
    private final BukkitTask timeoutTask;
    private final long createdAt;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    ActiveInputSession(UUID playerId,
                       InputRequest request,
                       CompletableFuture<InputResult> future,
                       BukkitTask timeoutTask) {
        this.playerId = playerId;
        this.request = request;
        this.future = future;
        this.timeoutTask = timeoutTask;
        this.createdAt = System.currentTimeMillis();
    }

    UUID playerId() { return playerId; }
    InputRequest request() { return request; }
    CompletableFuture<InputResult> future() { return future; }
    BukkitTask timeoutTask() { return timeoutTask; }
    long createdAt() { return createdAt; }

    /** Returns current count, then increments. */
    int getAndIncrementRetry() { return retryCount.getAndIncrement(); }
    int retryCount() { return retryCount.get(); }
}
