package me.chinq.teamsurvival.storage;

import me.chinq.teamsurvival.model.ClaimedChunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ClaimsYamlStorage {

    private final JavaPlugin plugin;
    private final File file;

    public ClaimsYamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");

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
            plugin.getLogger().severe("Ne mogu da kreiram claims.yml: " + e.getMessage());
        }
    }

    /** Map<ClaimedChunk, teamId> */
    public Map<ClaimedChunk, String> loadClaims() {
        Map<ClaimedChunk, String> out = new HashMap<>();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = cfg.getConfigurationSection("claims");
        if (root == null) return out;

        // claims:
        //   world:
        //     "x,z": "teamId"
        for (String world : root.getKeys(false)) {
            ConfigurationSection ws = root.getConfigurationSection(world);
            if (ws == null) continue;

            for (String key : ws.getKeys(false)) {
                String teamId = ws.getString(key, null);
                if (teamId == null || teamId.isBlank()) continue;

                String[] parts = key.split(",");
                if (parts.length != 2) continue;

                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    out.put(ClaimedChunk.of(world, x, z), teamId);
                } catch (Exception ignored) {}
            }
        }

        return out;
    }

    public void saveClaims(Map<ClaimedChunk, String> claims) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("claims");

        if (claims != null) {
            for (Map.Entry<ClaimedChunk, String> e : claims.entrySet()) {
                ClaimedChunk c = e.getKey();
                String teamId = e.getValue();
                if (c == null || teamId == null) continue;

                ConfigurationSection ws = root.getConfigurationSection(c.worldLower());
                if (ws == null) ws = root.createSection(c.worldLower());

                ws.set(c.x() + "," + c.z(), teamId);
            }
        }

        cfg.save(file);
    }
}