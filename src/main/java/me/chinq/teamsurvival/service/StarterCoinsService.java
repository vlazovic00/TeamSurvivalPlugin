package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.storage.PlayersYamlStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Anti-abuse starter coins:
 * - optional once-ever per player
 * - optional cooldown (hours) between claims
 *
 * TeamService zove claimStartingCoins(...) prilikom kreiranja tima.
 */
public final class StarterCoinsService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final PlayersYamlStorage storage;

    public StarterCoinsService(JavaPlugin plugin, Settings settings, PlayersYamlStorage storage) {
        this.plugin = plugin;
        this.settings = settings;
        this.storage = storage;
    }

    /**
     * Returns how many starting coins the new team should get for this leader,
     * and persists claim if coins > 0.
     */
    public long claimStartingCoins(UUID leader) {
        if (leader == null) return 0L;

        long starting = Math.max(0L, settings.startingCoins);
        if (starting <= 0) return 0L;

        PlayersYamlStorage.PlayerEcoData d = storage.getOrCreate(leader);

        long now = System.currentTimeMillis();

        if (settings.starterCoinsOnceEver && d.starterClaims > 0) {
            return 0L;
        }

        if (settings.starterCoinsCooldownHours > 0) {
            long cdMillis = settings.starterCoinsCooldownHours * 3600L * 1000L;
            long next = d.lastStarterClaimAtMillis + cdMillis;
            if (d.lastStarterClaimAtMillis > 0 && now < next) {
                return 0L;
            }
        }

        // claim
        d.starterClaims += 1;
        d.lastStarterClaimAtMillis = now;

        // save immediately (small file)
        storage.save();

        return starting;
    }

    /** For /timreload: settings object is updated, no need to recreate service. */
    public void reloadStorage() {
        try {
            storage.load();
        } catch (Exception e) {
            plugin.getLogger().warning("StarterCoinsService: load failed: " + e.getMessage());
        }
    }
}