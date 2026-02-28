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
import me.chinq.teamsurvival.integration.TeamSurvivalPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class TeamSurvivalPlugin extends JavaPlugin {

    // =========================================================
    // CORE: settings + messages
    // =========================================================
    private Settings settings;
    private MessageService messages;
    private TeamSurvivalPlaceholders placeholders;

    // =========================================================
    // STORAGE
    // =========================================================
    private TeamsYamlStorage teamsStorage;
    private PlayersYamlStorage playersStorage;
    private ClaimsYamlStorage claimsStorage;

    // =========================================================
    // SERVICES: teams, claims, persistence
    // =========================================================
    private StarterCoinsService starterCoinsService;
    private TeamService teamService;

    private ClaimService claimService;
    private PersistenceService persistenceService;

    // =========================================================
    // SERVICES: gameplay / utilities
    // =========================================================
    private TeamChatService teamChatService;
    private CombatTagService combatTagService;
    private TeleportService teleportService;
    private InviteService inviteService;
    private TpaService tpaService;

    private EarningsService earningsService;

    private ScoreboardService scoreboardService;
    private LocatorService locatorService;

    private RewardService rewardService;
    private SupplyDropService supplyDropService;

    private BountyService bountyService;
    private ShopService shopService;

    // =========================================================
    // NEW: anti spawn-camp
    // =========================================================
    private SpawnCampService spawnCampService;

    // =========================================================
    // TASKS
    // =========================================================
    private BukkitTask placedSweepTask;

    // =========================================================
    // ENABLE
    // =========================================================
    @Override
    public void onEnable() {
        // ---------- Files ----------
        saveDefaultConfig();

        // ---------- Settings / Messages ----------
        this.settings = Settings.load(this);
        this.messages = new MessageService(this);
        new StartupLogger(this, settings).printStartup();

        // ---------- Storages ----------
        this.teamsStorage = new TeamsYamlStorage(this);
        Map<String, Team> loadedTeams = teamsStorage.loadTeams();

        this.playersStorage = new PlayersYamlStorage(this);
        this.playersStorage.load();

        this.claimsStorage = new ClaimsYamlStorage(this);

        // ---------- Core services ----------
        this.starterCoinsService = new StarterCoinsService(this, settings, playersStorage);
        this.teamService = new TeamService(settings, starterCoinsService, loadedTeams);

        this.claimService = new ClaimService(settings, messages, teamService, claimsStorage.loadClaims());
        this.persistenceService = new PersistenceService(this, settings, teamsStorage, teamService, claimsStorage, claimService);

        // ---------- Economy (earnings) ----------
        this.earningsService = new EarningsService(settings, teamService);

        // Anti-farm sweep task (placed blocks cleanup)
        startPlacedSweepTask();

        // ---------- Gameplay services ----------
        this.teamChatService = new TeamChatService();
        this.combatTagService = new CombatTagService(settings);

        this.teleportService = new TeleportService(this, settings, messages);
        this.inviteService = new InviteService(settings, messages, teamService);
        this.tpaService = new TpaService(this, settings, messages, teamService, teleportService, combatTagService);

        // ---------- UI services ----------
        this.scoreboardService = new ScoreboardService(settings, messages, teamService, persistenceService);
        this.locatorService = new LocatorService(this, settings, messages, teamService);

        // ---------- Features ----------
        this.rewardService = new RewardService(this, messages);

        this.supplyDropService = new SupplyDropService(this);
        this.supplyDropService.start();

        this.bountyService = new BountyService(this, teamService);
        this.shopService = new ShopService(this, teamService, messages);

        // ---------- NEW: anti spawn camp ----------
        this.spawnCampService = new SpawnCampService(settings);

        // ---------- Bindings ----------
        teamService.bindOnTeamsChanged(() -> {
            persistenceService.requestSaveDebounced();
            scoreboardService.syncAll();
        });

        // When a team disappears (last member left / disband), remove its claims immediately
        teamService.bindOnTeamDisbanded((teamId) -> {
            int removed = claimService.removeAllClaimsOfTeam(teamId);
            if (removed > 0) {
                getLogger().info("Removed " + removed + " claims of disbanded team " + teamId);
            }
        });

        claimService.bindOnClaimsChanged(() -> persistenceService.requestSaveDebounced());

        // ---------- Scoreboard bootstrap ----------
        if (settings.scoreboard.enabled() && settings.scoreboard.resetOnEnable()) {
            scoreboardService.resetAllTsTeams();
        }
        scoreboardService.syncAll();

        // ---------- Start background loops ----------
        locatorService.start();
        bountyService.applyAllOnline();

        // ---------- Events ----------
        registerAllListeners();

        // ---------- Commands ----------
        registerCommands();

        getLogger().info("TeamSurvival uključen ✅");
    }

    private void registerAllListeners() {
        // Economy
        regEvents(new EarningsListener(earningsService));
        // Supply drops
        regEvents(new SupplyDropListener(supplyDropService));

        // General gameplay
        regEvents(new GameplayListener(settings, messages, teamService, teleportService, combatTagService));
        regEvents(new ChatListener(messages, teamService, teamChatService));
        regEvents(new JoinListener(this, settings, scoreboardService, bountyService));
        regEvents(new BountyAndShopListener(teamService, bountyService, shopService));

        // ---------- PlaceholderAPI (for TAB scoreboard etc.) ----------
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholders = new TeamSurvivalPlaceholders(
                    getDescription().getVersion(),
                    settings,
                    teamService,
                    claimService,
                    bountyService
            );
            this.placeholders.register();
            getLogger().info("PlaceholderAPI registrovan: %teamsurvival_*%");
        } else {
            getLogger().info("PlaceholderAPI nije pronađen (opciono). Preskačem %teamsurvival_*%.");
        }

        // Claims
        regEvents(new ClaimProtectionListener(settings, messages, claimService));
        regEvents(new ClaimEnterListener(settings, messages, teamService, claimService));

        // NEW: anti spawn-camp respawn override
        regEvents(new AntiSpawnCampRespawnListener(settings, messages, teamService, spawnCampService));
    }

    private void registerCommands() {
        // /tim
        PluginCommand tim = getCommand("tim");
        if (tim != null) {
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
            tim.setExecutor(timCommand);
            tim.setTabCompleter(timCommand);
        } else {
            getLogger().severe("Komanda /tim nije registrovana (plugin.yml).");
        }

        // /timreload
        PluginCommand reload = getCommand("timreload");
        if (reload != null) {
            reload.setExecutor(new TimReloadCommand(this, messages));
        } else {
            getLogger().severe("Komanda /timreload nije registrovana (plugin.yml).");
        }
    }

    // =========================================================
    // RELOAD
    // =========================================================
    public void reloadAllConfigs() {
        reloadConfig();

        // settings
        Settings fresh = Settings.load(this);
        this.settings.copyFrom(fresh);

        // messages
        this.messages.reload();

        // soft reloads (best-effort)
        try { this.playersStorage.load(); } catch (Exception ignored) {}
        try { this.shopService.reloadShopConfig(); } catch (Exception ignored) {}

        try { this.locatorService.start(); } catch (Exception ignored) {}
        try { this.persistenceService.restartTimers(); } catch (Exception ignored) {}
        try { this.scoreboardService.syncAll(); } catch (Exception ignored) {}

        try { this.rewardService.reloadFromConfig(); } catch (Exception ignored) {}
        try { this.supplyDropService.reloadFromConfig(); } catch (Exception ignored) {}

        try { this.bountyService.applyAllOnline(); } catch (Exception ignored) {}

        // placedSweepTask ne mora restartovati:
        // koristi isti earningsService, a settings se osveži preko copyFrom(fresh)
        getLogger().info("TeamSurvival reloadovan ✅");
    }

    // =========================================================
    // DISABLE
    // =========================================================
    @Override
    public void onDisable() {
        // Stop feature loops
        try { if (supplyDropService != null) supplyDropService.stop(); } catch (Exception ignored) {}
        try { if (rewardService != null) rewardService.stop(); } catch (Exception ignored) {}
        try { if (locatorService != null) locatorService.stop(); } catch (Exception ignored) {}

        // Tasks
        try { if (placedSweepTask != null) placedSweepTask.cancel(); } catch (Exception ignored) {}

        // Persist
        try { if (persistenceService != null) persistenceService.saveNow(); }
        catch (Exception e) { getLogger().severe("Greška pri snimanju: " + e.getMessage()); }

        try { if (playersStorage != null) playersStorage.save(); } catch (Exception ignored) {}

        getLogger().info("TeamSurvival isključen.");
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private void regEvents(Object listener) {
        Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
    }

    private void startPlacedSweepTask() {
        // sweep starih "placed block" unosa (anti-farm) na svakih 60 sekundi
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
    }
}