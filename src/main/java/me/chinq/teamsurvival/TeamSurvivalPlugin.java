package me.chinq.teamsurvival;

import me.chinq.teamsurvival.command.TimCommand;
import me.chinq.teamsurvival.listener.ChatListener;
import me.chinq.teamsurvival.listener.GameplayListener;
import me.chinq.teamsurvival.listener.JoinListener;
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
    private TeamService teamService;
    private PersistenceService persistenceService;

    private TeamChatService teamChatService;
    private CombatTagService combatTagService;
    private TeleportService teleportService;
    private InviteService inviteService;
    private TpaService tpaService;

    private ScoreboardService scoreboardService;
    private LocatorService locatorService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        // listeners
        Bukkit.getPluginManager().registerEvents(new GameplayListener(settings, messages, teamService, teleportService, combatTagService), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(messages, teamService, teamChatService), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, settings, scoreboardService), this);

        // command
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

        getLogger().info("========================================");
        getLogger().info(" TeamSurvival uključen ✅");
        getLogger().info(" Creator: Chinq");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
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
