package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Team;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public final class LocatorService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;

    private BukkitTask task;

    public LocatorService(JavaPlugin plugin, Settings settings, MessageService msg, TeamService teamService) {
        this.plugin = plugin;
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
    }

    public void start() {
        stop();
        if (!settings.locator.enabled()) return;

        final int interval = Math.max(1, settings.locator.intervalTicks());

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Team t = teamService.getTeamOf(p.getUniqueId());
                if (t == null) continue;

                List<Player> teammates = t.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(pl -> !pl.getUniqueId().equals(p.getUniqueId()))
                        .toList();

                if (teammates.isEmpty()) {
                    sendActionBar(p, "");
                    continue;
                }

                Location pl = p.getLocation();
                List<Entry> entries = new ArrayList<>();
                for (Player mate : teammates) {
                    Location ml = mate.getLocation();
                    if (ml.getWorld() != pl.getWorld()) {
                        entries.add(Entry.otherWorld(mate.getName(), ml.getWorld() != null ? ml.getWorld().getName() : "?"));
                    } else {
                        double dist = pl.distance(ml);
                        String arrow = arrowFor(p, ml);
                        entries.add(Entry.sameWorld(mate.getName(), (int) Math.round(dist), arrow));
                    }
                }

                entries.sort(Comparator
                        .comparing((Entry e) -> e.otherWorld ? 1 : 0)
                        .thenComparingInt(e -> e.distance)
                        .thenComparing(e -> e.name)
                );

                int max = Math.max(1, settings.locator.maxEntries());
                entries = entries.stream().limit(max).collect(Collectors.toList());

                String entrySameTpl = msg.get("actionbar.locator.entrySameWorld");
                String entryOtherTpl = msg.get("actionbar.locator.entryOtherWorld");

                List<String> parts = new ArrayList<>();
                for (Entry e : entries) {
                    if (e.otherWorld) {
                        parts.add(msg.applyPlaceholders(entryOtherTpl, Map.of(
                                "name", e.name,
                                "world", e.world
                        )));
                    } else {
                        parts.add(msg.applyPlaceholders(entrySameTpl, Map.of(
                                "arrow", e.arrow,
                                "name", e.name,
                                "distance", String.valueOf(e.distance)
                        )));
                    }
                }

                String format = msg.get("actionbar.locator.format");
                String sep = msg.get("actionbar.locator.separator");
                String built = msg.applyPlaceholders(format, Map.of("entries", String.join(sep, parts)));
                sendActionBar(p, msg.colorize(built));
            }
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void sendActionBar(Player p, String coloredText) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredText));
    }

    private String arrowFor(Player p, Location target) {
        Location pl = p.getLocation();
        double dx = target.getX() - pl.getX();
        double dz = target.getZ() - pl.getZ();

        double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double yaw = pl.getYaw();

        double diff = normalizeDegrees(angleToTarget - yaw);
        int sector = (int) Math.round(diff / 45.0);

        return switch (sector) {
            case 0 -> "↑";
            case 1 -> "↗";
            case 2 -> "→";
            case 3 -> "↘";
            case 4, -4 -> "↓";
            case -3 -> "↙";
            case -2 -> "←";
            case -1 -> "↖";
            default -> "↑";
        };
    }

    private double normalizeDegrees(double deg) {
        deg = (deg + 180) % 360;
        if (deg < 0) deg += 360;
        return deg - 180;
    }

    private static final class Entry {
        final boolean otherWorld;
        final String name;
        final int distance;
        final String arrow;
        final String world;

        private Entry(boolean otherWorld, String name, int distance, String arrow, String world) {
            this.otherWorld = otherWorld;
            this.name = name;
            this.distance = distance;
            this.arrow = arrow;
            this.world = world;
        }

        static Entry sameWorld(String name, int distance, String arrow) {
            return new Entry(false, name, distance, arrow, "");
        }

        static Entry otherWorld(String name, String world) {
            return new Entry(true, name, Integer.MAX_VALUE, "", world);
        }
    }
}