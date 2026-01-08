package com.afterlands.core.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Snapshot de estatísticas do ProtocolService.
 */
public record ProtocolStats(
        int providerCount,
        long chunksProcessed,
        long mutationsApplied,
        long conflictsTotal,
        long packetsQueued,
        @NotNull List<ProviderStat> providers) {
    public record ProviderStat(
            @NotNull String id,
            int priority,
            long mutationsProvided,
            long conflicts) {
    }
}
