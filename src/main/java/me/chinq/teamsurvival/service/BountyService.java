package me.chinq.teamsurvival.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BountyService {

    // ===== tunables (kasnije lako prebaciš u config) =====
    public static final long MIN_BOUNTY = 1;
    public static final long MAX_BOUNTY = 1_000_000;

    // U nicku/tabu: PlayerName §c[123]
    private static final String BOUNTY_SUFFIX_COLOR = ChatColor.RED.toString();
    private static final String BOUNTY_BRACKET_COLOR = ChatColor.DARK_RED.toString();

    private final JavaPlugin plugin;
    private final TeamService teamService;

    private final File file;
    private final Map<UUID, Long> bountyByPlayer = new ConcurrentHashMap<>();

    private BukkitTask pendingSave;

    public BountyService(JavaPlugin plugin, TeamService teamService) {
        this.plugin = plugin;
        this.teamService = teamService;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        try {
            if (!file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da kreiram bounties.yml: " + e.getMessage());
        }

        load();
    }

    public long getBounty(UUID target) {
        if (target == null) return 0L;
        return Math.max(0L, bountyByPlayer.getOrDefault(target, 0L));
    }

    public Map<UUID, Long> snapshotBounties() {
        return Map.copyOf(bountyByPlayer);
    }

    /** /tim bounty <igrac> <amount> */
    public PlaceOutcome placeBounty(Player placer, OfflinePlayer target, long amount) {
        if (placer == null || target == null) return PlaceOutcome.ERROR;

        if (amount < MIN_BOUNTY) return PlaceOutcome.TOO_SMALL;
        if (amount > MAX_BOUNTY) return PlaceOutcome.TOO_LARGE;

        UUID placerId = placer.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (placerId.equals(targetId)) return PlaceOutcome.SELF;

        var placerTeam = teamService.getTeamOf(placerId);
        if (placerTeam == null) return PlaceOutcome.NO_TEAM;

        var targetTeam = teamService.getTeamOf(targetId);
        if (targetTeam != null && targetTeam.getId().equals(placerTeam.getId())) {
            return PlaceOutcome.SAME_TEAM;
        }

        // skini iz team coins (stake)
        boolean ok = teamService.tryRemoveCoins(placerTeam, amount);
        if (!ok) return PlaceOutcome.NOT_ENOUGH_COINS;

        // dodaj bounty
        bountyByPlayer.merge(targetId, amount, Long::sum);

        requestSaveDebounced();

        // update nick ako je online
        Player tOnline = Bukkit.getPlayer(targetId);
        if (tOnline != null) applyBountyName(tOnline);

        return PlaceOutcome.OK;
    }

    /** Na kill: tim ubice dobija bounty coins, bounty se briše. */
    public long claimBountyIfPresent(Player victim, Player killer) {
        if (victim == null || killer == null) return 0L;

        UUID vId = victim.getUniqueId();
        long bounty = getBounty(vId);
        if (bounty <= 0) return 0L;

        var killerTeam = teamService.getTeamOf(killer.getUniqueId());
        var victimTeam = teamService.getTeamOf(vId);

        // ne dodeljuj ako nema team ili je isti team (anti-abuse basic)
        if (killerTeam == null) return 0L;
        if (victimTeam != null && victimTeam.getId().equals(killerTeam.getId())) return 0L;

        // dodaj timu ubice
        teamService.addCoins(killerTeam, bounty);

        // clear bounty
        bountyByPlayer.remove(vId);
        requestSaveDebounced();

        // refresh nick
        applyBountyName(victim);

        return bounty;
    }

    public void applyBountyName(Player p) {
        if (p == null) return;
        long b = getBounty(p.getUniqueId());

        // reset
        if (b <= 0) {
            try {
                p.setDisplayName(p.getName());
                p.setPlayerListName(p.getName());
            } catch (Throwable ignored) { }
            return;
        }

        String suffix = " " + BOUNTY_BRACKET_COLOR + "[" + BOUNTY_SUFFIX_COLOR + b + BOUNTY_BRACKET_COLOR + "]";
        String newName = p.getName() + suffix;

        try {
            p.setDisplayName(newName);
            // tab list limit može biti restriktivan; ako pukne, samo ignoriši
            if (newName.length() <= 64) p.setPlayerListName(newName);
            else p.setPlayerListName(p.getName());
        } catch (Throwable ignored) { }
    }

    public void applyAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyBountyName(p);
        }
    }

    private void load() {
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            bountyByPlayer.clear();

            var root = cfg.getConfigurationSection("bounties");
            if (root == null) return;

            for (String key : root.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(key);
                    long val = root.getLong(key, 0L);
                    if (val > 0) bountyByPlayer.put(u, val);
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da ucitam bounties.yml: " + e.getMessage());
        }
    }

    private void saveNow() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            var root = cfg.createSection("bounties");

            for (var e : bountyByPlayer.entrySet()) {
                root.set(e.getKey().toString(), e.getValue());
            }

            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim bounties.yml: " + e.getMessage());
        }
    }

    private void requestSaveDebounced() {
        if (pendingSave != null) pendingSave.cancel();
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, 20L); // 1s debounce
    }

    public enum PlaceOutcome {
        OK,
        NO_TEAM,
        SAME_TEAM,
        SELF,
        TOO_SMALL,
        TOO_LARGE,
        NOT_ENOUGH_COINS,
        ERROR
    }
}