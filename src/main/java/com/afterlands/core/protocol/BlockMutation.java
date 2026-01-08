package com.afterlands.core.protocol;

/**
 * Mutação simples de bloco (id + data) em uma coordenada absoluta.
 */
public record BlockMutation(int x, int y, int z, int blockId, byte blockData) {}

