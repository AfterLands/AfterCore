package com.afterlands.core.protocol;

/**
 * Chave de posição de bloco para uso no merge de mutations.
 * Packing eficiente de (x, y, z) em um long para uso em HashMap.
 */
public record BlockPosKey(int x, int y, int z) {

    /**
     * Pack (x, y, z) into a single long for efficient hashing.
     * Y is limited to 0-255, X and Z can be any int but we use 21 bits each.
     */
    public long packed() {
        // X: bits 0-20 (21 bits, signed)
        // Z: bits 21-41 (21 bits, signed)
        // Y: bits 42-49 (8 bits, 0-255)
        return ((long) (x & 0x1FFFFF))
                | ((long) (z & 0x1FFFFF) << 21)
                | ((long) (y & 0xFF) << 42);
    }

    public static BlockPosKey fromPacked(long packed) {
        int x = (int) (packed & 0x1FFFFF);
        if ((x & 0x100000) != 0)
            x |= 0xFFE00000; // Sign extend

        int z = (int) ((packed >> 21) & 0x1FFFFF);
        if ((z & 0x100000) != 0)
            z |= 0xFFE00000; // Sign extend

        int y = (int) ((packed >> 42) & 0xFF);

        return new BlockPosKey(x, y, z);
    }
}
