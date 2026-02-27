package me.chinq.teamsurvival.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Settings {

    // NOTE: nisu final da bi reload radio bez restarta
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
    public boolean starterCoinsOnceEver;
    public int starterCoinsCooldownHours;

    // Earnings (coins while playing)
    public EarningsSettings earnings;

    public double warmupCancelMoveDistance;
    public double warmupCancelMoveDistanceSquared;

    public boolean friendlyFireEnabled;
    public boolean friendlyFireStrictExplosions;

    public boolean showCoordinatesInPing;

    // Claims
    public Claims claims;

    public Locator locator;
    public Scoreboard scoreboard;
    public Persistence persistence;
    public TeamName teamName;

    // =========================================================
    // ANTI SPAWN CAMP
    // =========================================================
    public boolean antiSpawnCampEnabled;
    public int antiSpawnCampWindowSeconds;
    public int antiSpawnCampKillsThreshold;
    public boolean antiSpawnCampOnlyIfTeamHome;

    // NEW: koristi random unutar worldborder-a
    public boolean antiSpawnCampRandomWithinWorldBorder;

    public int antiSpawnCampBorderMarginBlocks;
    public int antiSpawnCampMaxTries;

    private Settings() {}

    public static Settings load(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        Settings s = new Settings();

        // ---------------- TEAM ----------------
        s.maxTeamSize = c.getInt("teams.maxTeamSize", 3);

        s.allowedWorldsLower = c.getStringList("teams.allowedWorlds").stream()
                .filter(x -> x != null && !x.isBlank())
                .map(x -> x.toLowerCase(Locale.ROOT))
                .toList();

        s.teamName = new TeamName(
                c.getInt("teams.teamNameMinLength", 2),
                c.getInt("teams.teamNameMaxLength", 16),
                Pattern.compile(c.getString("teams.teamNamePattern", "^[A-Za-z0-9_\\-]+$"))
        );

        // ---------------- INVITES ----------------
        s.inviteExpireSeconds = c.getInt("invites.inviteExpireSeconds", 60);

        // ---------------- HOME ----------------
        s.homeCooldownSeconds = c.getInt("home.homeCooldownSeconds", 600);
        s.homeWarmupSeconds = c.getInt("home.homeWarmupSeconds", 5);
        s.homeTeleportCooldownSeconds = c.getInt("home.homeTeleportCooldownSeconds", 120);

        // ---------------- RESPAWN ----------------
        // IMPORTANT: ovaj key mora biti u configu
        s.respawnToTeamHome = c.getBoolean("respawn.respawnToTeamHome", true);

        // anti spawn camp
        s.antiSpawnCampEnabled = c.getBoolean("respawn.anti_spawn_camp.enabled", true);
        s.antiSpawnCampWindowSeconds = c.getInt("respawn.anti_spawn_camp.window_seconds", 300);
        s.antiSpawnCampKillsThreshold = c.getInt("respawn.anti_spawn_camp.kills_threshold", 3);
        s.antiSpawnCampOnlyIfTeamHome = c.getBoolean("respawn.anti_spawn_camp.only_if_team_home", true);

        s.antiSpawnCampRandomWithinWorldBorder =
                c.getBoolean("respawn.anti_spawn_camp.random_within_worldborder", true);

        s.antiSpawnCampBorderMarginBlocks = c.getInt("respawn.anti_spawn_camp.border_margin_blocks", 32);
        s.antiSpawnCampMaxTries = c.getInt("respawn.anti_spawn_camp.max_tries", 40);

        // ---------------- TPA ----------------
        s.tpaCooldownSeconds = c.getInt("tpa.tpaCooldownSeconds", 180);
        s.tpaExpireSeconds = c.getInt("tpa.tpaExpireSeconds", 60);
        s.tpaWarmupSeconds = c.getInt("tpa.tpaWarmupSeconds", 5);

        // ---------------- COMBAT ----------------
        s.combatTagSeconds = c.getInt("combat.combatTagSeconds", 15);

        // ---------------- ECONOMY ----------------
        s.startingCoins = Math.max(0L, c.getLong("economy.starting-coins", 0L));
        s.starterCoinsOnceEver = c.getBoolean("economy.starter-abuse.once-ever", true);
        s.starterCoinsCooldownHours = Math.max(0, c.getInt("economy.starter-abuse.cooldown-hours", 72));

        // earnings
        s.earnings = EarningsSettings.load(c);

        // ---------------- WARMUP ----------------
        s.warmupCancelMoveDistance = c.getDouble("warmup.cancelMoveDistanceBlocks", 0.2);
        s.warmupCancelMoveDistanceSquared = s.warmupCancelMoveDistance * s.warmupCancelMoveDistance;

        // ---------------- FRIENDLY FIRE ----------------
        s.friendlyFireEnabled = c.getBoolean("friendlyFire.friendlyFireEnabled", false);
        s.friendlyFireStrictExplosions = c.getBoolean("friendlyFire.friendlyFireStrictExplosions", true);

        // ---------------- PING ----------------
        s.showCoordinatesInPing = c.getBoolean("ping.showCoordinatesInPing", false);

        // ---------------- CLAIMS ----------------
        s.claims = new Claims(
                c.getBoolean("claims.enabled", true),
                Math.max(0L, c.getLong("claims.price-per-chunk", 200L)),
                c.getBoolean("claims.only-leader-can-claim", true),
                Math.max(0, c.getInt("claims.max-claims-per-team", 0)),
                c.getBoolean("claims.foreign-open-enabled", true),
                c.getBoolean("claims.protection.block-break", true),
                c.getBoolean("claims.protection.block-place", true),
                c.getBoolean("claims.protection.block-explosions", true),
                c.getBoolean("claims.protection.pistons", true),
                c.getBoolean("claims.protection.enderman-grief", true),
                c.getBoolean("claims.protection.hanging-break", true)
        );

        // ---------------- LOCATOR ----------------
        s.locator = new Locator(
                c.getBoolean("locator.enabled", true),
                c.getInt("locator.intervalTicks", 20),
                c.getInt("locator.maxEntries", 4)
        );

        // ---------------- SCOREBOARD ----------------
        s.scoreboard = new Scoreboard(
                c.getBoolean("scoreboard.enabled", true),
                c.getBoolean("scoreboard.resetOnEnable", true),
                c.getBoolean("scoreboard.syncOnJoin", true),
                c.getString("scoreboard.teamPrefix", "ts_"),
                c.getStringList("scoreboard.colorPalette").stream()
                        .filter(x -> x != null && !x.isBlank())
                        .map(x -> x.toUpperCase(Locale.ROOT))
                        .toList()
        );

        // ---------------- PERSISTENCE ----------------
        s.persistence = new Persistence(
                c.getInt("persistence.debounceSaveTicks", 40),
                c.getInt("persistence.periodicSaveTicks", 6000)
        );

        return s;
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

        this.startingCoins = s.startingCoins;

        this.starterCoinsOnceEver = s.starterCoinsOnceEver;
        this.starterCoinsCooldownHours = s.starterCoinsCooldownHours;

        this.earnings = s.earnings;

        this.warmupCancelMoveDistance = s.warmupCancelMoveDistance;
        this.warmupCancelMoveDistanceSquared = s.warmupCancelMoveDistanceSquared;

        this.friendlyFireEnabled = s.friendlyFireEnabled;
        this.friendlyFireStrictExplosions = s.friendlyFireStrictExplosions;

        this.showCoordinatesInPing = s.showCoordinatesInPing;

        this.claims = s.claims;

        this.locator = s.locator;
        this.scoreboard = s.scoreboard;
        this.persistence = s.persistence;
        this.teamName = s.teamName;

        // anti spawn camp reload
        this.antiSpawnCampEnabled = s.antiSpawnCampEnabled;
        this.antiSpawnCampWindowSeconds = s.antiSpawnCampWindowSeconds;
        this.antiSpawnCampKillsThreshold = s.antiSpawnCampKillsThreshold;
        this.antiSpawnCampOnlyIfTeamHome = s.antiSpawnCampOnlyIfTeamHome;
        this.antiSpawnCampRandomWithinWorldBorder = s.antiSpawnCampRandomWithinWorldBorder;
        this.antiSpawnCampBorderMarginBlocks = s.antiSpawnCampBorderMarginBlocks;
        this.antiSpawnCampMaxTries = s.antiSpawnCampMaxTries;
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorldsLower.isEmpty()) return true;
        if (worldName == null) return false;
        return allowedWorldsLower.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public record Claims(
            boolean enabled,
            long pricePerChunk,
            boolean onlyLeaderCanClaim,
            int maxClaimsPerTeam,
            boolean foreignOpenEnabled,
            boolean protectBlockBreak,
            boolean protectBlockPlace,
            boolean protectExplosions,
            boolean protectPistons,
            boolean preventEndermanGrief,
            boolean protectHangingBreak
    ) { }

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