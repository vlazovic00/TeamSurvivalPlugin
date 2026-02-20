package me.chinq.teamsurvival.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private String prefix;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
    }

    public void reload() {
        this.cfg = YamlConfiguration.loadConfiguration(file);
        this.prefix = colorize(cfg.getString("prefix", "&8[&bTeamSurvival&8] &7"));
    }

    public String get(String path) {
        return cfg.getString(path, "");
    }

    public List<String> list(String path) {
        List<String> out = cfg.getStringList(path);
        return out == null ? Collections.emptyList() : out;
    }

    public void sendPrefixed(CommandSender sender, String path) {
        sendPrefixed(sender, path, Map.of());
    }

    public void sendPrefixed(CommandSender sender, String path, Map<String, String> placeholders) {
        String raw = get(path);
        if (raw == null || raw.isEmpty()) return;

        String text = prefix + raw;
        text = applyPlaceholders(text, placeholders);
        sender.sendMessage(colorize(text));
    }

    public void sendRaw(CommandSender sender, String rawColored) {
        sender.sendMessage(rawColored);
    }

    public String applyPlaceholders(String text, Map<String, String> placeholders) {
        String out = text.replace("%prefix%", prefix);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", String.valueOf(e.getValue()));
        }
        return out;
    }

    public String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String getPrefix() {
        return prefix;
    }
}
