package me.chinq.teamsurvival;

import me.chinq.teamsurvival.command.TimCommand;
import me.chinq.teamsurvival.command.TimReloadCommand;
import me.chinq.teamsurvival.listener.BountyAndShopListener;
import me.chinq.teamsurvival.listener.ChatListener;
import me.chinq.teamsurvival.listener.GameplayListener;
import me.chinq.teamsurvival.listener.JoinListener;
import me.chinq.teamsurvival.listener.SupplyDropListener;
import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.*;
import me.chinq.teamsurvival.storage.PlayersYamlStorage;
import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import me.chinq.teamsurvival.util.StartupLogger;

import java.util.Map;

public final class TeamSurvivalPlugin extends JavaPlugin {

    private Settings settings;
    private MessageService messages;

    private TeamsYamlStorage teamsStorage;
    private PlayersYamlStorage playersStorage;          // NEW
    private StarterCoinsService starterCoinsService;    // NEW

    private SupplyDropService supplyDropService;

    private TeamService teamService;
    private PersistenceService persistenceService;

    private TeamChatService teamChatService;
    private CombatTagService combatTagService;
    private TeleportService teleportService;
    private InviteService inviteService;
    private EarningsService earningsService;
    private TpaService tpaService;

    private ScoreboardService scoreboardService;
    private LocatorService locatorService;

    private RewardService rewardService;

    // NEW
    private BountyService bountyService;
    private ShopService shopService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // services that depend on config (and have schedulers)
        this.supplyDropService = new SupplyDropService(this);
        this.supplyDropService.start();
        Bukkit.getPluginManager().registerEvents(new SupplyDropListener(supplyDropService), this);

        this.settings = Settings.load(this);
        new StartupLogger(this, settings).printStartup();
        this.messages = new MessageService(this);

        this.teamsStorage = new TeamsYamlStorage(this);
        Map<String, Team> loadedTeams = teamsStorage.loadTeams();

        // NEW: players.yml + anti-abuse starter coins
        this.playersStorage = new PlayersYamlStorage(this);
        this.playersStorage.load();
        this.starterCoinsService = new StarterCoinsService(this, settings, playersStorage);

        // NEW ctor signature
        this.earningsService = new EarningsService(settings, teamService);
        Bukkit.getPluginManager().registerEvents(new me.chinq.teamsurvival.listener.EarningsListener(earningsService), this);
        this.teamService = new TeamService(settings, starterCoinsService, loadedTeams);
        this.persistenceService = new PersistenceService(this, settings, teamsStorage, teamService);

        this.teamChatService = new TeamChatService();
        this.combatTagService = new CombatTagService(settings);
        this.teleportService = new TeleportService(this, settings, messages);
        this.inviteService = new InviteService(settings, messages, teamService);
        this.tpaService = new TpaService(this, settings, messages, teamService, teleportService, combatTagService);

        this.scoreboardService = new ScoreboardService(settings, messages, teamService, persistenceService);
        this.locatorService = new LocatorService(this, settings, messages, teamService);

        // NEW: bounty + shop
        this.bountyService = new BountyService(this, teamService);
        this.shopService = new ShopService(this, teamService, messages);

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
        Bukkit.getPluginManager().registerEvents(
                new GameplayListener(settings, messages, teamService, teleportService, combatTagService),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new ChatListener(messages, teamService, teamChatService),
                this
        );

        // JoinListener sada prima bountyService da apply bounty nick na join
        Bukkit.getPluginManager().registerEvents(
                new JoinListener(this, settings, scoreboardService, bountyService),
                this
        );

        // NEW: bounty + head drop + shop GUI
        Bukkit.getPluginManager().registerEvents(
                new BountyAndShopListener(teamService, bountyService, shopService),
                this
        );

        // apply bounty names for online players (ako radiš /reload ili plugman)
        bountyService.applyAllOnline();

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
                    tpaService,
                    bountyService,
                    shopService
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

        // NEW: reload players.yml cache (starter anti-abuse)
        try { this.playersStorage.load(); } catch (Exception ignored) { }

        // NEW: reload shop.yml
        try { this.shopService.reloadShopConfig(); } catch (Exception ignored) { }

        // 4) restart servise koji zavise od Settings tickova
        try { locatorService.start(); } catch (Exception ignored) {}
        try { persistenceService.restartTimers(); } catch (Exception ignored) {}
        try { scoreboardService.syncAll(); } catch (Exception ignored) {}

        // 5) restart servise koji zavise od config.yml (interval/loot/rewards...)
        try { rewardService.reloadFromConfig(); } catch (Exception ignored) {}
        try { supplyDropService.reloadFromConfig(); } catch (Exception ignored) {}

        // NEW: refresh bounty names (ako se desi reload)
        try { bountyService.applyAllOnline(); } catch (Exception ignored) {}
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

        // NEW: save players.yml (safety)
        try {
            if (playersStorage != null) playersStorage.save();
        } catch (Exception ignored) {}

        getLogger().info("TeamSurvival isključen.");
    }
}