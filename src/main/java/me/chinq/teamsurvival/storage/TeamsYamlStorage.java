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

            Team t = new Team(id, name, leader, createdAt, members, home, colorName);
            out.put(id, t);
        }

        return out;
    }

    public void saveTeams(Collection<Team> teams) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("teams");

        for (Team t : teams) {
            ConfigurationSection s = root.createSection(t.getId());
            s.set("id", t.getId());
            s.set("name", t.getName());
            s.set("leader", t.getLeader().toString());
            s.set("createdAt", t.getCreatedAt());

            List<String> members = new ArrayList<>();
            for (UUID u : t.getMembersOrdered()) members.add(u.toString());
            s.set("members", members);

            if (t.getHome() != null) {
                ConfigurationSection hs = s.createSection("home");
                hs.set("world", t.getHome().world());
                hs.set("x", t.getHome().x());
                hs.set("y", t.getHome().y());
                hs.set("z", t.getHome().z());
                hs.set("yaw", t.getHome().yaw());
                hs.set("pitch", t.getHome().pitch());
            } else {
                s.set("home", null);
            }

            if (t.getColorName() != null) {
                s.set("colorName", t.getColorName());
            } else {
                s.set("colorName", null);
            }
        }

        cfg.save(file);
    }
}
