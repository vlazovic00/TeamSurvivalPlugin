package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class PersistenceService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final TeamsYamlStorage storage;
    private final TeamService teamService;

    private BukkitTask pending;

    public PersistenceService(JavaPlugin plugin, Settings settings, TeamsYamlStorage storage, TeamService teamService) {
        this.plugin = plugin;
        this.settings = settings;
        this.storage = storage;
        this.teamService = teamService;

        // periodic failsafe save
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveNow,
                settings.persistence.periodicSaveTicks(),
                settings.persistence.periodicSaveTicks()
        );
    }

    public void requestSaveDebounced() {
        if (pending != null) pending.cancel();
        pending = Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, settings.persistence.debounceSaveTicks());
    }

    public void saveNow() {
        try {
            storage.saveTeams(teamService.allTeams());
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim teams.yml: " + e.getMessage());
        }
    }
}
