package me.chinq.teamsurvival.storage;

import me.chinq.teamsurvival.model.HomeLocation;
import me.chinq.teamsurvival.model.Team;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class TeamsYamlStorage {

    private final JavaPlugin plugin;
    private final File file;

    public TeamsYamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");

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
            plugin.getLogger().severe("Ne mogu da kreiram teams.yml: " + e.getMessage());
        }
    }

    public Map<String, Team> loadTeams() {
        Map<String, Team> out = new HashMap<>();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = cfg.getConfigurationSection("teams");
        if (root == null) return out;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("name");
            String leaderStr = s.getString("leader");
            long createdAt = s.getLong("createdAt", System.currentTimeMillis());

            if (name == null || leaderStr == null) continue;

            UUID leader;
            try { leader = UUID.fromString(leaderStr); }
            catch (Exception ex) { continue; }

            LinkedHashSet<UUID> members = new LinkedHashSet<>();
            for (String m : s.getStringList("members")) {
                try { members.add(UUID.fromString(m)); } catch (Exception ignored) { }
            }
            members.add(leader);

            HomeLocation home = null;
            ConfigurationSection hs = s.getConfigurationSection("home");
            if (hs != null) {
                String world = hs.getString("world");
                if (world != null) {
                    home = new HomeLocation(
                            world,
                            hs.getDouble("x"),
                            hs.getDouble("y"),
                            hs.getDouble("z"),
                            (float) hs.getDouble("yaw"),
                            (float) hs.getDouble("pitch")
                    );
                }
            }

            String colorName = s.getString("colorName", null);
            long coins = s.getLong("coins", 0L);
            Team t = new Team(id, name, leader, createdAt, members, home, colorName, coins);
            out.put(id, t);
        }

        return out;
    }

    public void saveTeams(Collection<Team> teams) throws Exception {

        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("teams");

        for (Team t : teams) {

            // svaka sekcija = jedan team
            ConfigurationSection s = root.createSection(t.getId());

            // osnovni podaci
            s.set("name", t.getName());
            s.set("leader", t.getLeader().toString());
            s.set("createdAt", t.getCreatedAt());

            // members lista
            List<String> members = t.getMembersOrdered().stream()
                    .map(UUID::toString)
                    .toList();
            s.set("members", members);

            // HOME (ako postoji)
            if (t.getHome() != null) {
                s.set("home.world", t.getHome().world());
                s.set("home.x", t.getHome().x());
                s.set("home.y", t.getHome().y());
                s.set("home.z", t.getHome().z());
                s.set("home.yaw", t.getHome().yaw());
                s.set("home.pitch", t.getHome().pitch());
            }

            // TEAM COLOR (ako postoji)
            if (t.getColorName() != null) {
                s.set("color", t.getColorName());
            }

            // ⭐⭐⭐ TEAM COINS (NOVO)
            s.set("coins", t.getCoins());
        }

        // snimanje u fajl
        cfg.save(file);
    }
}
