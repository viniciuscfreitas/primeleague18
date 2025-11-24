package com.primeleague.factions.util;

import java.util.Objects;

/**
 * Immutable key for Chunk identification.
 * Replaces the need for string concatenation or complex objects.
 * Optimized for HashMap usage.
 */
public final class ChunkKey {
    private final String world;
    private final int x;
    private final int z;

    public ChunkKey(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkKey chunkKey = (ChunkKey) o;
        return x == chunkKey.x &&
                z == chunkKey.z &&
                Objects.equals(world, chunkKey.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return "ChunkKey{" +
                "world='" + world + '\'' +
                ", x=" + x +
                ", z=" + z +
                '}';
    }
}
