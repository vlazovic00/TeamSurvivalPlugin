package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PersistenceService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final TeamsYamlStorage storage;
    private final TeamService teamService;

    private BukkitTask pending;
    private BukkitTask periodic;

    public PersistenceService(JavaPlugin plugin, Settings settings, TeamsYamlStorage storage, TeamService teamService) {
        this.plugin = plugin;
        this.settings = settings;
        this.storage = storage;
        this.teamService = teamService;

        startPeriodic();
    }

    private void startPeriodic() {
        stopPeriodic();
        int period = Math.max(1, settings.persistence.periodicSaveTicks());
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::saveNow, period, period);
    }

    private void stopPeriodic() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
    }

    /** Pozovi posle reload settings/config da promeni debounce/periodic tickove. */
    public void restartTimers() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        startPeriodic();
    }

    public void requestSaveDebounced() {
        if (pending != null) pending.cancel();
        int delay = Math.max(1, settings.persistence.debounceSaveTicks());
        pending = Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, delay);
    }

    public void saveNow() {
        try {
            storage.saveTeams(teamService.allTeams());
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim teams.yml: " + e.getMessage());
        }
    }
}