package me.chinq.teamsurvival.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Settings {

    // NOTE: više nisu final da bi mogli reload bez restartovanja servisa
    public int maxTeamSize;
    public List<String> allowedWorldsLower;

    public int inviteExpireSeconds;

    public int homeCooldownSeconds;
    public int homeWarmupSeconds;
    public int homeTeleportCooldownSeconds;

    public boolean respawnToTeamHome;

    public int tpaCooldownSeconds;
    public int tpaExpireSeconds;
    public int tpaWarmupSeconds;

    public int combatTagSeconds;

    // ECONOMY
    public long startingCoins;

    // Anti-abuse za starter coins (da nema create/disband glitch)
    public boolean starterCoinsOnceEver;
    public int starterCoinsCooldownHours;

    // NEW: earnings (coins while playing)
    public EarningsSettings earnings;

    public double warmupCancelMoveDistance;
    public double warmupCancelMoveDistanceSquared;

    public boolean friendlyFireEnabled;
    public boolean friendlyFireStrictExplosions;

    public boolean showCoordinatesInPing;

    public Locator locator;
    public Scoreboard scoreboard;
    public Persistence persistence;
    public TeamName teamName;

    private Settings(
            int maxTeamSize,
            List<String> allowedWorldsLower,
            int inviteExpireSeconds,
            int homeCooldownSeconds,
            int homeWarmupSeconds,
            int homeTeleportCooldownSeconds,
            boolean respawnToTeamHome,
            int tpaCooldownSeconds,
            int tpaExpireSeconds,
            int tpaWarmupSeconds,
            int combatTagSeconds,
            long startingCoins,
            boolean starterCoinsOnceEver,
            int starterCoinsCooldownHours,
            EarningsSettings earnings,
            double warmupCancelMoveDistance,
            boolean friendlyFireEnabled,
            boolean friendlyFireStrictExplosions,
            boolean showCoordinatesInPing,
            Locator locator,
            Scoreboard scoreboard,
            Persistence persistence,
            TeamName teamName
    ) {
        this.maxTeamSize = maxTeamSize;
        this.allowedWorldsLower = allowedWorldsLower;
        this.inviteExpireSeconds = inviteExpireSeconds;
        this.homeCooldownSeconds = homeCooldownSeconds;
        this.homeWarmupSeconds = homeWarmupSeconds;
        this.homeTeleportCooldownSeconds = homeTeleportCooldownSeconds;
        this.respawnToTeamHome = respawnToTeamHome;
        this.tpaCooldownSeconds = tpaCooldownSeconds;
        this.tpaExpireSeconds = tpaExpireSeconds;
        this.tpaWarmupSeconds = tpaWarmupSeconds;
        this.combatTagSeconds = combatTagSeconds;

        this.startingCoins = Math.max(0L, startingCoins);

        this.starterCoinsOnceEver = starterCoinsOnceEver;
        this.starterCoinsCooldownHours = Math.max(0, starterCoinsCooldownHours);

        this.earnings = earnings;

        this.warmupCancelMoveDistance = warmupCancelMoveDistance;
        this.warmupCancelMoveDistanceSquared = warmupCancelMoveDistance * warmupCancelMoveDistance;

        this.friendlyFireEnabled = friendlyFireEnabled;
        this.friendlyFireStrictExplosions = friendlyFireStrictExplosions;

        this.showCoordinatesInPing = showCoordinatesInPing;

        this.locator = locator;
        this.scoreboard = scoreboard;
        this.persistence = persistence;
        this.teamName = teamName;
    }

    public static Settings load(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();

        int maxTeamSize = c.getInt("teams.maxTeamSize", 3);

        List<String> allowedWorldsLower = c.getStringList("teams.allowedWorlds").stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        TeamName teamName = new TeamName(
                c.getInt("teams.teamNameMinLength", 2),
                c.getInt("teams.teamNameMaxLength", 16),
                Pattern.compile(c.getString("teams.teamNamePattern", "^[A-Za-z0-9_\\-]+$"))
        );

        int inviteExpireSeconds = c.getInt("invites.inviteExpireSeconds", 60);

        int homeCooldownSeconds = c.getInt("home.homeCooldownSeconds", 600);
        int homeWarmupSeconds = c.getInt("home.homeWarmupSeconds", 5);
        int homeTeleportCooldownSeconds = c.getInt("home.homeTeleportCooldownSeconds", 120);

        boolean respawnToTeamHome = c.getBoolean("respawn.respawnToTeamHome", true);

        int tpaCooldownSeconds = c.getInt("tpa.tpaCooldownSeconds", 180);
        int tpaExpireSeconds = c.getInt("tpa.tpaExpireSeconds", 60);
        int tpaWarmupSeconds = c.getInt("tpa.tpaWarmupSeconds", 5);

        int combatTagSeconds = c.getInt("combat.combatTagSeconds", 15);

        // ECONOMY: starting coins
        long startingCoins = c.getLong("economy.starting-coins", 0L);
        if (startingCoins < 0) startingCoins = 0;

        // Anti-abuse
        boolean starterCoinsOnceEver = c.getBoolean("economy.starter-abuse.once-ever", true);
        int starterCoinsCooldownHours = c.getInt("economy.starter-abuse.cooldown-hours", 72);
        if (starterCoinsCooldownHours < 0) starterCoinsCooldownHours = 0;

        // NEW: earnings settings
        EarningsSettings earnings = loadEarnings(c);

        double moveCancel = c.getDouble("warmup.cancelMoveDistanceBlocks", 0.2);

        boolean friendlyFireEnabled = c.getBoolean("friendlyFire.friendlyFireEnabled", false);
        boolean friendlyFireStrictExplosions = c.getBoolean("friendlyFire.friendlyFireStrictExplosions", true);

        boolean showCoordinatesInPing = c.getBoolean("ping.showCoordinatesInPing", false);

        Locator locator = new Locator(
                c.getBoolean("locator.enabled", true),
                c.getInt("locator.intervalTicks", 20),
                c.getInt("locator.maxEntries", 4)
        );

        Scoreboard scoreboard = new Scoreboard(
                c.getBoolean("scoreboard.enabled", true),
                c.getBoolean("scoreboard.resetOnEnable", true),
                c.getBoolean("scoreboard.syncOnJoin", true),
                c.getString("scoreboard.teamPrefix", "ts_"),
                c.getStringList("scoreboard.colorPalette").stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.toUpperCase(Locale.ROOT))
                        .toList()
        );

        Persistence persistence = new Persistence(
                c.getInt("persistence.debounceSaveTicks", 40),
                c.getInt("persistence.periodicSaveTicks", 6000)
        );

        return new Settings(
                maxTeamSize,
                allowedWorldsLower,
                inviteExpireSeconds,
                homeCooldownSeconds,
                homeWarmupSeconds,
                homeTeleportCooldownSeconds,
                respawnToTeamHome,
                tpaCooldownSeconds,
                tpaExpireSeconds,
                tpaWarmupSeconds,
                combatTagSeconds,
                startingCoins,
                starterCoinsOnceEver,
                starterCoinsCooldownHours,
                earnings,
                moveCancel,
                friendlyFireEnabled,
                friendlyFireStrictExplosions,
                showCoordinatesInPing,
                locator,
                scoreboard,
                persistence,
                teamName
        );
    }

    private static EarningsSettings loadEarnings(FileConfiguration c) {
        EarningsSettings es = new EarningsSettings();

        es.enabled = c.getBoolean("economy.earnings.enabled", true);
        es.globalMultiplier = c.getDouble("economy.earnings.global-multiplier", 1.0);

        // actionbar
        es.actionbar.enabled = c.getBoolean("economy.earnings.actionbar.enabled", true);
        es.actionbar.minCoinsToShow = c.getLong("economy.earnings.actionbar.min-coins-to-show", 1L);

        // diminishing
        es.diminishing.enabled = c.getBoolean("economy.earnings.diminishing.enabled", true);
        es.diminishing.windowSeconds = c.getInt("economy.earnings.diminishing.window-seconds", 600);
        es.diminishing.halfLifeActions = c.getDouble("economy.earnings.diminishing.half-life-actions", 80.0);
        es.diminishing.minFactor = c.getDouble("economy.earnings.diminishing.min-factor", 0.15);
        es.diminishing.perCategory = c.getBoolean("economy.earnings.diminishing.per-category", true);

        // anti-farm placed blocks
        es.antiFarm.placedBlockTtlSeconds = c.getInt("economy.earnings.anti-farm.placed-block-ttl-seconds", 1800);
        es.antiFarm.placedBlockFactor = c.getDouble("economy.earnings.anti-farm.placed-block-factor", 0.0);

        // mining
        es.mining.enabled = c.getBoolean("economy.earnings.mining.enabled", true);
        es.mining.def = c.getDouble("economy.earnings.mining.default", 0.05);
        EarningsSettings.loadMaterialMap(c, "economy.earnings.mining.materials", es.mining.materials);

        // building
        es.building.enabled = c.getBoolean("economy.earnings.building.enabled", true);
        es.building.def = c.getDouble("economy.earnings.building.default", 0.02);
        EarningsSettings.loadMaterialMap(c, "economy.earnings.building.materials", es.building.materials);

        // farming
        es.farming.enabled = c.getBoolean("economy.earnings.farming.enabled", true);
        es.farming.def = c.getDouble("economy.earnings.farming.mature-crop", 0.5);
        EarningsSettings.loadMaterialMap(c, "economy.earnings.farming.crops", es.farming.materials);

        // hunting
        es.hunting.enabled = c.getBoolean("economy.earnings.hunting.enabled", true);
        es.hunting.def = c.getDouble("economy.earnings.hunting.default", 0.2);
        EarningsSettings.loadEntityMap(c, "economy.earnings.hunting.entities", es.hunting.entities);

        // fishing
        es.fishing.enabled = c.getBoolean("economy.earnings.fishing.enabled", true);
        es.fishing.perCatch = c.getDouble("economy.earnings.fishing.per-catch", 0.6);

        // crafting
        es.crafting.enabled = c.getBoolean("economy.earnings.crafting.enabled", true);
        es.crafting.def = c.getDouble("economy.earnings.crafting.default", 0.05);
        EarningsSettings.loadMaterialMap(c, "economy.earnings.crafting.items", es.crafting.materials);

        // smelting
        es.smelting.enabled = c.getBoolean("economy.earnings.smelting.enabled", true);
        es.smelting.def = c.getDouble("economy.earnings.smelting.default", 0.05);
        EarningsSettings.loadMaterialMap(c, "economy.earnings.smelting.items", es.smelting.materials);

        // exploring
        es.exploring.enabled = c.getBoolean("economy.earnings.exploring.enabled", true);
        es.exploring.blocksPerReward = c.getInt("economy.earnings.exploring.blocks-per-reward", 120);
        es.exploring.coinsPerReward = c.getDouble("economy.earnings.exploring.coins-per-reward", 0.4);

        return es;
    }

    /** Update-uje postojeći Settings objekt da svi servisi vide nove vrednosti bez rekreiranja. */
    public void copyFrom(Settings s) {
        if (s == null) return;

        this.maxTeamSize = s.maxTeamSize;
        this.allowedWorldsLower = s.allowedWorldsLower;

        this.inviteExpireSeconds = s.inviteExpireSeconds;

        this.homeCooldownSeconds = s.homeCooldownSeconds;
        this.homeWarmupSeconds = s.homeWarmupSeconds;
        this.homeTeleportCooldownSeconds = s.homeTeleportCooldownSeconds;

        this.respawnToTeamHome = s.respawnToTeamHome;

        this.tpaCooldownSeconds = s.tpaCooldownSeconds;
        this.tpaExpireSeconds = s.tpaExpireSeconds;
        this.tpaWarmupSeconds = s.tpaWarmupSeconds;

        this.combatTagSeconds = s.combatTagSeconds;

        // ECONOMY
        this.startingCoins = s.startingCoins;

        // Anti-abuse
        this.starterCoinsOnceEver = s.starterCoinsOnceEver;
        this.starterCoinsCooldownHours = s.starterCoinsCooldownHours;

        // NEW
        this.earnings = s.earnings;

        this.warmupCancelMoveDistance = s.warmupCancelMoveDistance;
        this.warmupCancelMoveDistanceSquared = s.warmupCancelMoveDistanceSquared;

        this.friendlyFireEnabled = s.friendlyFireEnabled;
        this.friendlyFireStrictExplosions = s.friendlyFireStrictExplosions;

        this.showCoordinatesInPing = s.showCoordinatesInPing;

        this.locator = s.locator;
        this.scoreboard = s.scoreboard;
        this.persistence = s.persistence;
        this.teamName = s.teamName;
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorldsLower.isEmpty()) return true;
        if (worldName == null) return false;
        return allowedWorldsLower.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public record Locator(boolean enabled, int intervalTicks, int maxEntries) { }

    public record Scoreboard(
            boolean enabled,
            boolean resetOnEnable,
            boolean syncOnJoin,
            String teamPrefix,
            List<String> colorPalette
    ) { }

    public record Persistence(int debounceSaveTicks, int periodicSaveTicks) { }

    public static final class TeamName {
        public final int minLen;
        public final int maxLen;
        public final Pattern pattern;

        public TeamName(int minLen, int maxLen, Pattern pattern) {
            this.minLen = minLen;
            this.maxLen = maxLen;
            this.pattern = pattern;
        }

        public boolean isValid(String name) {
            if (name == null) return false;
            if (name.length() < minLen || name.length() > maxLen) return false;
            return pattern.matcher(name).matches();
        }
    }
}