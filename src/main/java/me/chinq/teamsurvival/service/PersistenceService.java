package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.storage.ClaimsYamlStorage;
import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PersistenceService {

    private final JavaPlugin plugin;
    private final Settings settings;

    private final TeamsYamlStorage teamsStorage;
    private final TeamService teamService;

    // NEW: claims
    private final ClaimsYamlStorage claimsStorage;
    private final ClaimService claimService;

    private BukkitTask pending;
    private BukkitTask periodic;

    public PersistenceService(
            JavaPlugin plugin,
            Settings settings,
            TeamsYamlStorage teamsStorage,
            TeamService teamService,
            ClaimsYamlStorage claimsStorage,
            ClaimService claimService
    ) {
        this.plugin = plugin;
        this.settings = settings;

        this.teamsStorage = teamsStorage;
        this.teamService = teamService;

        this.claimsStorage = claimsStorage;
        this.claimService = claimService;

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
            if (teamsStorage != null && teamService != null) {
                teamsStorage.saveTeams(teamService.allTeams());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim teams.yml: " + e.getMessage());
        }

        try {
            if (claimsStorage != null && claimService != null) {
                claimsStorage.saveClaims(claimService.snapshotAll());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim claims.yml: " + e.getMessage());
        }
    }
}