package me.chinq.teamsurvival.util;

import me.chinq.teamsurvival.service.Settings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class StartupLogger {

    private final JavaPlugin plugin;
    private final Settings settings;

    public StartupLogger(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    private void log(String msg) {
        plugin.getServer().getConsoleSender().sendMessage(msg);
    }

    public void printStartup() {

        String name = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String mc = Bukkit.getMinecraftVersion();
        String java = System.getProperty("java.version");

        log("");
        log(ChatColor.DARK_GRAY + "====================================================");
        log(ChatColor.GOLD + "   _______                       _____                 ");
        log(ChatColor.GOLD + "  |__   __|                     / ____|                ");
        log(ChatColor.GOLD + "     | | ___  __ _ _ __ ___    | (___   ___ _ ____   __");
        log(ChatColor.YELLOW + "     | |/ _ \\/ _` | '_ ` _ \\    \\___ \\ / _ \\ '__\\ \\ / /");
        log(ChatColor.YELLOW + "     | |  __/ (_| | | | | | |   ____) |  __/ |   \\ V / ");
        log(ChatColor.YELLOW + "     |_|\\___|\\__,_|_| |_| |_|  |_____/ \\___|_|    \\_/  ");
        log(ChatColor.GRAY + "                by chinq ♥");
        log(ChatColor.DARK_GRAY + "====================================================");

        log(ChatColor.AQUA + " Plugin: " + ChatColor.WHITE + name +
                ChatColor.GRAY + " v" + ChatColor.GREEN + version);

        log(ChatColor.AQUA + " Minecraft: " + ChatColor.WHITE + mc);
        log(ChatColor.AQUA + " Java: " + ChatColor.WHITE + java);

        log(ChatColor.DARK_GRAY + "---------------- Modules ----------------");

        module("Teams", true);
        module("Economy", true);
        module("Earnings (play rewards)", settings.earnings.enabled);
        module("Rewards system", plugin.getConfig().getBoolean("rewards.enabled", true));
        module("Supply Drops", plugin.getConfig().getBoolean("supplyDrops.enabled", true));
        module("Locator", settings.locator.enabled());
        module("Scoreboard", settings.scoreboard.enabled());

        log(ChatColor.DARK_GRAY + "-----------------------------------------");

        log(ChatColor.YELLOW + " Team size: " + ChatColor.WHITE + settings.maxTeamSize);
        log(ChatColor.YELLOW + " Starting coins: " + ChatColor.GOLD + settings.startingCoins);

        if (settings.allowedWorldsLower.isEmpty()) {
            log(ChatColor.YELLOW + " Worlds: " + ChatColor.GREEN + "ALL WORLDS");
        } else {
            log(ChatColor.YELLOW + " Worlds: " + ChatColor.WHITE + settings.allowedWorldsLower);
        }

        if (settings.earnings.enabled) {
            log(ChatColor.YELLOW + " Earnings multiplier: "
                    + ChatColor.GREEN + settings.earnings.globalMultiplier);
        }

        log(ChatColor.DARK_GRAY + "====================================================");
        log(ChatColor.GREEN + " TeamSurvival successfully enabled!");
        log(ChatColor.DARK_GRAY + "====================================================");
        log("");
    }

    private void module(String name, boolean enabled) {
        if (enabled) {
            log(ChatColor.GREEN + " ✔ " + ChatColor.WHITE + name);
        } else {
            log(ChatColor.RED + " ✖ " + ChatColor.DARK_GRAY + name);
        }
    }
}