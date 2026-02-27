package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.MessageService;
import me.chinq.teamsurvival.service.Settings;
import me.chinq.teamsurvival.service.SpawnCampService;
import me.chinq.teamsurvival.service.TeamService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class AntiSpawnCampRespawnListener implements Listener {

    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;
    private final SpawnCampService spawnCamp;

    public AntiSpawnCampRespawnListener(Settings settings, MessageService msg, TeamService teamService, SpawnCampService spawnCamp) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
        this.spawnCamp = spawnCamp;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        spawnCamp.recordKill(victim.getUniqueId(), killer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!settings.antiSpawnCampEnabled) return;

        Player p = e.getPlayer();

        // Radi samo ako bi inače respawn bio na team home (po želji)
        if (settings.antiSpawnCampOnlyIfTeamHome) {
            if (!settings.respawnToTeamHome) return;

            Team t = teamService.getTeamOf(p.getUniqueId());
            if (t == null || t.getHome() == null) return;
        }

        UUID killerId = spawnCamp.consumeTriggerIfAny(p.getUniqueId());
        if (killerId == null) return;

        Location rnd = null;

        // NEW: izbor po configu
        if (settings.antiSpawnCampRandomWithinWorldBorder) {
            rnd = findRandomSafeLocationInWorldBorder(p.getWorld());
        } else {
            // Ako isključiš ovu opciju, trenutno samo ostaje normalan respawn (team home / bed / spawn).
            // Ako želiš "random near team home" varijantu, reci i dodaću odmah.
            return;
        }

        if (rnd == null) return;

        e.setRespawnLocation(rnd);

        String killerName = p.getServer().getOfflinePlayer(killerId).getName();
        if (killerName == null) killerName = "Unknown";

        String tpl = msg.get("respawn.relocated");
        if (tpl != null && !tpl.isEmpty()) {
            String built = msg.applyPlaceholders(tpl, Map.of("killer", killerName));
            p.sendMessage(msg.colorize(built));
        }
    }

    private Location findRandomSafeLocationInWorldBorder(World world) {
        if (world == null) return null;

        WorldBorder wb = world.getWorldBorder();
        Location center = wb.getCenter();
        double size = wb.getSize(); // prečnik

        double radius = (size / 2.0) - settings.antiSpawnCampBorderMarginBlocks;
        if (radius < 16) radius = 16;

        int tries = Math.max(5, settings.antiSpawnCampMaxTries);

        for (int i = 0; i < tries; i++) {
            double dx = ThreadLocalRandom.current().nextDouble(-radius, radius);
            double dz = ThreadLocalRandom.current().nextDouble(-radius, radius);

            int x = (int) Math.floor(center.getX() + dx);
            int z = (int) Math.floor(center.getZ() + dz);

            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight()) continue;

            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            if (isSafe(loc)) return loc;
        }

        return null;
    }

    private boolean isSafe(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;

        Block feet = w.getBlockAt(loc);
        Block head = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        Block below = w.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());

        // feet & head moraju biti prazni, below solidan i ne opasan
        if (!feet.isPassable()) return false;
        if (!head.isPassable()) return false;
        if (below.isPassable()) return false;

        // izbegni magma/cactus/campfires (osnovno)
        String type = below.getType().name();
        if (type.contains("MAGMA") || type.contains("CACTUS") || type.contains("CAMPFIRE")) return false;

        return true;
    }
}