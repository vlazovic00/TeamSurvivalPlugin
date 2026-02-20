package me.chinq.teamsurvival.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Settings {

    public final int maxTeamSize;
    public final List<String> allowedWorldsLower;

    public final int inviteExpireSeconds;

    public final int homeCooldownSeconds;
    public final int homeWarmupSeconds;
    public final int homeTeleportCooldownSeconds;

    public final boolean respawnToTeamHome;

    public final int tpaCooldownSeconds;
    public final int tpaExpireSeconds;
    public final int tpaWarmupSeconds;

    public final int combatTagSeconds;

    public final double warmupCancelMoveDistance;
    public final double warmupCancelMoveDistanceSquared;

    public final boolean friendlyFireEnabled;
    public final boolean friendlyFireStrictExplosions;

    public final boolean showCoordinatesInPing;

    public final Locator locator;
    public final Scoreboard scoreboard;
    public final Persistence persistence;
    public final TeamName teamName;

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