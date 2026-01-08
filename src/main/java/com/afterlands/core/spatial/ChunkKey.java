package com.afterlands.core.spatial;

/**
 * Utility para pack/unpack de coordenadas de chunk em um long.
 *
 * <p>Compatível com o util existente no AfterBlockState.</p>
 */
public final class ChunkKey {
    private ChunkKey() {}

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    public static int blockToChunk(int blockCoord) {
        return blockCoord >> 4;
    }

    public static String toString(long packed) {
        return "[" + unpackX(packed) + "," + unpackZ(packed) + "]";
    }
}

