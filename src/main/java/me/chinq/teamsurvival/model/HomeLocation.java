package me.chinq.teamsurvival.model;

import org.bukkit.Location;
import org.bukkit.World;

public record HomeLocation(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public Location toBukkitLocation(World w) {
        return new Location(w, x, y, z, yaw, pitch);
    }
}
