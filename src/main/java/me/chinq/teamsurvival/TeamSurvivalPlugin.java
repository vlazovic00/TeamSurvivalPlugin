package me.chinq.teamsurvival;

import me.chinq.teamsurvival.command.TimCommand;
import me.chinq.teamsurvival.command.TimReloadCommand;
import me.chinq.teamsurvival.listener.*;
import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.*;
import me.chinq.teamsurvival.storage.ClaimsYamlStorage;
import me.chinq.teamsurvival.storage.PlayersYamlStorage;
import me.chinq.teamsurvival.storage.TeamsYamlStorage;
import me.chinq.teamsurvival.util.StartupLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class TeamSurvivalPlugin extends JavaPlugin {

    private Settings settings;
    private MessageService messages;

    private TeamsYamlStorage teamsStorage;
    private PlayersYamlStorage playersStorage;
    private StarterCoinsService starterCoinsService;

    private ClaimsYamlStorage claimsStorage;
    private ClaimService claimService;

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

    private BountyService bountyService;
    private ShopService shopService;

    // ✅ NEW: task za periodični sweep (da ga cancelujemo na disable)
    private BukkitTask placedSweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.supplyDropService = new SupplyDropService(this);
        this.supplyDropService.start();
        Bukkit.getPluginManager().registerEvents(new SupplyDropListener(supplyDropService), this);

        this.settings = Settings.load(this);
        new StartupLogger(this, settings).printStartup();
        this.messages = new MessageService(this);

        this.teamsStorage = new TeamsYamlStorage(this);
        Map<String, Team> loadedTeams = teamsStorage.loadTeams();

        this.playersStorage = new PlayersYamlStorage(this);
        this.playersStorage.load();
        this.starterCoinsService = new StarterCoinsService(this, settings, playersStorage);

        this.teamService = new TeamService(settings, starterCoinsService, loadedTeams);

        this.claimsStorage = new ClaimsYamlStorage(this);
        this.claimService = new ClaimService(settings, messages, teamService, claimsStorage.loadClaims());

        this.persistenceService = new PersistenceService(this, settings, teamsStorage, teamService, claimsStorage, claimService);

        // ✅ IMPORTANT: EarningsService mora pre schedulera koji ga koristi
        this.earningsService = new EarningsService(settings, teamService);
        Bukkit.getPluginManager().registerEvents(new EarningsListener(earningsService), this);

        // ✅ NEW: sweep starih "placed block" unosa (anti-farm) na svakih 60 sekundi
        this.placedSweepTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    try {
                        earningsService.placedBlocks().sweepOld();
                    } catch (Throwable ignored) {
                        // ne rušimo scheduler ni plugin zbog edge-case greške
                    }
                },
                60 * 20L,
                60 * 20L
        );

        this.teamChatService = new TeamChatService();
        this.combatTagService = new CombatTagService(settings);
        this.teleportService = new TeleportService(this, settings, messages);
        this.inviteService = new InviteService(settings, messages, teamService);
        this.tpaService = new TpaService(this, settings, messages, teamService, teleportService, combatTagService);

        this.scoreboardService = new ScoreboardService(settings, messages, teamService, persistenceService);
        this.locatorService = new LocatorService(this, settings, messages, teamService);

        this.bountyService = new BountyService(this, teamService);
        this.shopService = new ShopService(this, teamService, messages);

        teamService.bindOnTeamsChanged(() -> {
            persistenceService.requestSaveDebounced();
            scoreboardService.syncAll();
        });
        claimService.bindOnClaimsChanged(() -> persistenceService.requestSaveDebounced());

        if (settings.scoreboard.enabled() && settings.scoreboard.resetOnEnable()) {
            scoreboardService.resetAllTsTeams();
        }
        scoreboardService.syncAll();

        locatorService.start();

        this.rewardService = new RewardService(this, messages);

        Bukkit.getPluginManager().registerEvents(
                new GameplayListener(settings, messages, teamService, teleportService, combatTagService),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new ChatListener(messages, teamService, teamChatService),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new JoinListener(this, settings, scoreboardService, bountyService),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new BountyAndShopListener(teamService, bountyService, shopService),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new ClaimProtectionListener(settings, messages, claimService),
                this
        );

        bountyService.applyAllOnline();

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
                    shopService,
                    claimService
            );
            cmd.setExecutor(timCommand);
            cmd.setTabCompleter(timCommand);
        } else {
            getLogger().severe("Komanda /tim nije registrovana (plugin.yml).");
        }

        PluginCommand reloadCmd = getCommand("timreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(new TimReloadCommand(this, messages));
        } else {
            getLogger().severe("Komanda /timreload nije registrovana (plugin.yml).");
        }
    }

    public void reloadAllConfigs() {
        reloadConfig();

        Settings fresh = Settings.load(this);
        this.settings.copyFrom(fresh);

        this.messages.reload();

        try { this.playersStorage.load(); } catch (Exception ignored) { }
        try { this.shopService.reloadShopConfig(); } catch (Exception ignored) { }

        try { locatorService.start(); } catch (Exception ignored) {}
        try { persistenceService.restartTimers(); } catch (Exception ignored) {}
        try { scoreboardService.syncAll(); } catch (Exception ignored) {}

        try { rewardService.reloadFromConfig(); } catch (Exception ignored) {}
        try { supplyDropService.reloadFromConfig(); } catch (Exception ignored) {}

        try { bountyService.applyAllOnline(); } catch (Exception ignored) {}

        // ✅ placedSweepTask ne mora restart jer koristi isti earningsService,
        // a sweepOld() čita settings svaki put, a settings radi copyFrom(fresh).
    }

    @Override
    public void onDisable() {
        if (supplyDropService != null) supplyDropService.stop();
        if (rewardService != null) rewardService.stop();

        try { locatorService.stop(); } catch (Exception ignored) { }

        // ✅ NEW: cancel sweep task
        try { if (placedSweepTask != null) placedSweepTask.cancel(); } catch (Exception ignored) { }

        try { if (persistenceService != null) persistenceService.saveNow(); }
        catch (Exception e) { getLogger().severe("Greška pri snimanju: " + e.getMessage()); }

        try { if (playersStorage != null) playersStorage.save(); } catch (Exception ignored) {}

        getLogger().info("TeamSurvival isključen.");
    }
}