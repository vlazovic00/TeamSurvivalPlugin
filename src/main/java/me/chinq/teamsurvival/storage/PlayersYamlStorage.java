package me.chinq.teamsurvival.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * players.yml - čuva anti-abuse podatke (starter coins claim).
 *
 * Struktura:
 * players:
 *   <uuid>:
 *     starterClaims: 1
 *     lastStarterClaimAt: 1700000000000
 */
public final class PlayersYamlStorage {

    public static final class PlayerEcoData {
        public long starterClaims;
        public long lastStarterClaimAtMillis;

        public PlayerEcoData(long starterClaims, long lastStarterClaimAtMillis) {
            this.starterClaims = Math.max(0L, starterClaims);
            this.lastStarterClaimAtMillis = Math.max(0L, lastStarterClaimAtMillis);
        }
    }

    private final JavaPlugin plugin;
    private final File file;

    private final Map<UUID, PlayerEcoData> cache = new HashMap<>();

    public PlayersYamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");

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
            plugin.getLogger().severe("Ne mogu da kreiram players.yml: " + e.getMessage());
        }
    }

    public void load() {
        cache.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (!cfg.isConfigurationSection("players")) return;

        for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                long claims = cfg.getLong("players." + key + ".starterClaims", 0L);
                long lastAt = cfg.getLong("players." + key + ".lastStarterClaimAt", 0L);
                cache.put(u, new PlayerEcoData(claims, lastAt));
            } catch (Exception ignored) {}
        }
    }

    public PlayerEcoData getOrCreate(UUID u) {
        return cache.computeIfAbsent(u, __ -> new PlayerEcoData(0L, 0L));
    }

    public void save() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerEcoData> e : cache.entrySet()) {
                String k = e.getKey().toString();
                PlayerEcoData d = e.getValue();
                cfg.set("players." + k + ".starterClaims", d.starterClaims);
                cfg.set("players." + k + ".lastStarterClaimAt", d.lastStarterClaimAtMillis);
            }
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Ne mogu da snimim players.yml: " + e.getMessage());
        }
    }
}