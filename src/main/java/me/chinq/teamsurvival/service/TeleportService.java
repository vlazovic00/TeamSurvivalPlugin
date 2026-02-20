package me.chinq.teamsurvival.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class TeleportService {

    public enum CancelReason { MOVE, DAMAGE, COMBAT, QUIT, OTHER }

    private final JavaPlugin plugin;
    private final Settings settings;
    private final MessageService msg;

    private final Map<UUID, BukkitTask> warmupTaskByPlayer = new HashMap<>();
    private final Map<UUID, Location> startLocByPlayer = new HashMap<>();
    private final Map<UUID, Consumer<CancelReason>> cancelCallbackByPlayer = new HashMap<>();

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public TeleportService(JavaPlugin plugin, Settings settings, MessageService msg) {
        this.plugin = plugin;
        this.settings = settings;
        this.msg = msg;
    }

    public boolean isInWarmup(UUID u) {
        return warmupTaskByPlayer.containsKey(u);
    }

    public boolean startWarmup(Player p, int seconds, Runnable onSuccess, Consumer<CancelReason> onCancel) {
        UUID u = p.getUniqueId();
        if (isInWarmup(u)) return false;

        startLocByPlayer.put(u, p.getLocation().clone());
        cancelCallbackByPlayer.put(u, onCancel);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            warmupTaskByPlayer.remove(u);
            startLocByPlayer.remove(u);
            cancelCallbackByPlayer.remove(u);
            try {
                onSuccess.run();
            } catch (Exception ignored) { }
        }, seconds * 20L);

        warmupTaskByPlayer.put(u, task);
        return true;
    }

    public void cancelWarmup(UUID u, CancelReason reason) {
        BukkitTask t = warmupTaskByPlayer.remove(u);
        if (t == null) return;

        t.cancel();
        startLocByPlayer.remove(u);

        Consumer<CancelReason> cb = cancelCallbackByPlayer.remove(u);
        if (cb != null) {
            try { cb.accept(reason); } catch (Exception ignored) { }
        }
    }

    public void handleMove(Player p, Location to) {
        UUID u = p.getUniqueId();
        if (!isInWarmup(u)) return;

        Location start = startLocByPlayer.get(u);
        if (start == null || to == null) return;

        if (start.getWorld() != to.getWorld()) {
            cancelWarmup(u, CancelReason.MOVE);
            return;
        }

        double dx = start.getX() - to.getX();
        double dy = start.getY() - to.getY();
        double dz = start.getZ() - to.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > settings.warmupCancelMoveDistanceSquared) {
            cancelWarmup(u, CancelReason.MOVE);
        }
    }

    public void setCooldownSeconds(UUID u, String key, int seconds) {
        cooldowns.computeIfAbsent(u, __ -> new HashMap<>())
                .put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    public long cooldownRemainingSeconds(UUID u, String key) {
        long now = System.currentTimeMillis();
        Long until = cooldowns.getOrDefault(u, Map.of()).get(key);
        if (until == null) return 0;
        long rem = until - now;
        if (rem <= 0) {
            cooldowns.getOrDefault(u, Map.of()).remove(key);
            return 0;
        }
        return (rem + 999) / 1000;
    }
}
