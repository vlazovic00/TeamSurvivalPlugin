package me.chinq.teamsurvival.model;

import java.util.Locale;
import java.util.Objects;

/** Immutable chunk key: world + chunkX + chunkZ (world stored lower-case). */
public record ClaimedChunk(String worldLower, int x, int z) {

    public ClaimedChunk {
        Objects.requireNonNull(worldLower, "worldLower");
        worldLower = worldLower.toLowerCase(Locale.ROOT);
    }

    public static ClaimedChunk of(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) throw new IllegalArgumentException("worldName is null");
        return new ClaimedChunk(worldName.toLowerCase(Locale.ROOT), chunkX, chunkZ);
    }
}