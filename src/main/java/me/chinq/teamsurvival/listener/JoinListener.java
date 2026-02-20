package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.service.ScoreboardService;
import me.chinq.teamsurvival.service.Settings;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class JoinListener implements Listener {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final ScoreboardService scoreboard;

    public JoinListener(JavaPlugin plugin, Settings settings, ScoreboardService scoreboard) {
        this.plugin = plugin;
        this.settings = settings;
        this.scoreboard = scoreboard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!settings.scoreboard.enabled()) return;
        if (!settings.scoreboard.syncOnJoin()) return;

        Bukkit.getScheduler().runTaskLater(plugin, scoreboard::syncAll, 1L);
    }
}