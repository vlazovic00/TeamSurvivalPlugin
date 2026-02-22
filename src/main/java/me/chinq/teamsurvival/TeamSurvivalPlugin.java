package me.chinq.teamsurvival;

import me.chinq.teamsurvival.command.TimCommand;
import me.chinq.teamsurvival.command.TimReloadCommand;
import me.chinq.teamsurvival.listener.ChatListener;
import me.chinq.teamsurvival.listener.GameplayListener;
import me.chinq.teamsurvival.listener.JoinListener;
import me.chinq.teamsurvival.listener.SupplyDropListener;
import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.*;
import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class TeamSurvivalPlugin extends JavaPlugin {

    private Settings settings;
    private MessageService messages;

    private TeamsYamlStorage teamsStorage;
    private SupplyDropService supplyDropService;
    private TeamService teamService;
    private PersistenceService persistenceService;

    private TeamChatService teamChatService;
    private CombatTagService combatTagService;
    private TeleportService teleportService;
    private InviteService inviteService;
    private TpaService tpaService;

    private ScoreboardService scoreboardService;
    private LocatorService locatorService;

    private RewardService rewardService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // services that depend on config (and have schedulers)
        this.supplyDropService = new SupplyDropService(this);
        this.supplyDropService.start();
        Bukkit.getPluginManager().registerEvents(new SupplyDropListener(supplyDropService), this);

        this.settings = Settings.load(this);
        this.messages = new MessageService(this);

        this.teamsStorage = new TeamsYamlStorage(this);
        Map<String, Team> loadedTeams = teamsStorage.loadTeams();

        this.teamService = new TeamService(settings, loadedTeams);
        this.persistenceService = new PersistenceService(this, settings, teamsStorage, teamService);

        this.teamChatService = new TeamChatService();
        this.combatTagService = new CombatTagService(settings);
        this.teleportService = new TeleportService(this, settings, messages);
        this.inviteService = new InviteService(settings, messages, teamService);
        this.tpaService = new TpaService(this, settings, messages, teamService, teleportService, combatTagService);

        this.scoreboardService = new ScoreboardService(settings, messages, teamService, persistenceService);
        this.locatorService = new LocatorService(this, settings, messages, teamService);

        // bind cross-service callbacks
        teamService.bindOnTeamsChanged(() -> {
            persistenceService.requestSaveDebounced();
            scoreboardService.syncAll();
        });

        // scoreboard boot fixes
        if (settings.scoreboard.enabled() && settings.scoreboard.resetOnEnable()) {
            scoreboardService.resetAllTsTeams();
        }
        scoreboardService.syncAll();

        // start locator
        locatorService.start();

        // rewards (random rewards on interval)
        this.rewardService = new RewardService(this, messages);

        // listeners
        Bukkit.getPluginManager().registerEvents(new GameplayListener(settings, messages, teamService, teleportService, combatTagService), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(messages, teamService, teamChatService), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, settings, scoreboardService), this);

        // command /tim
        PluginCommand cmd = getCommand("tim");
        if (cmd != null) {
            TimCommand timCommand = new TimCommand(
                    settings,
                    messages,
                    teamService,
                    persistenceService,
                    teamChatService,
                    combatTagService,
                    teleportService,
                    inviteService,
                    tpaService
            );
            cmd.setExecutor(timCommand);
            cmd.setTabCompleter(timCommand);
        } else {
            getLogger().severe("Komanda /tim nije registrovana (plugin.yml).");
        }

        // command /timreload (OP only)
        PluginCommand reloadCmd = getCommand("timreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(new TimReloadCommand(this, messages));
        } else {
            getLogger().severe("Komanda /timreload nije registrovana (plugin.yml).");
        }

        getLogger().info("========================================");
        getLogger().info(" TeamSurvival uključen ✅");
        getLogger().info(" Creator: Chinq");
        getLogger().info("========================================");
    }

    /** Full hot reload: config.yml + messages.yml + restart svih config-driven schedulera. */
    public void reloadAllConfigs() {
        // 1) reload config.yml
        reloadConfig();

        // 2) reload Settings (runtime update)
        Settings fresh = Settings.load(this);
        this.settings.copyFrom(fresh);

        // 3) reload messages.yml
        this.messages.reload();

        // 4) restart servise koji zavise od Settings tickova
        try { locatorService.start(); } catch (Exception ignored) {}
        try { persistenceService.restartTimers(); } catch (Exception ignored) {}
        try { scoreboardService.syncAll(); } catch (Exception ignored) {}

        // 5) restart servise koji zavise od config.yml (interval/loot/rewards...)
        try { rewardService.reloadFromConfig(); } catch (Exception ignored) {}
        try { supplyDropService.reloadFromConfig(); } catch (Exception ignored) {}
    }

    @Override
    public void onDisable() {
        if (supplyDropService != null) {
            supplyDropService.stop();
        }
        if (rewardService != null) {
            rewardService.stop();
        }
        try {
            locatorService.stop();
        } catch (Exception ignored) { }
        try {
            persistenceService.saveNow();
        } catch (Exception e) {
            getLogger().severe("Greška pri snimanju teams.yml: " + e.getMessage());
        }
        getLogger().info("TeamSurvival isključen.");
    }
}