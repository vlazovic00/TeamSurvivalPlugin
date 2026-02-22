package me.chinq.teamsurvival.service;

import org.bukkit.ChatColor;

public enum RewardRarity {
    COMMON(ChatColor.GRAY, "&7", 4),
    UNCOMMON(ChatColor.GREEN, "&a", 7),
    RARE(ChatColor.AQUA, "&b", 12),
    EPIC(ChatColor.GOLD, "&6", 18),
    LEGENDARY(ChatColor.LIGHT_PURPLE, "&d", 28),
    MYTHIC(ChatColor.DARK_PURPLE, "&5", 40);

    private final ChatColor chatColor;
    private final String legacyColor;
    private final int defaultFireworks;

    RewardRarity(ChatColor chatColor, String legacyColor, int defaultFireworks) {
        this.chatColor = chatColor;
        this.legacyColor = legacyColor;
        this.defaultFireworks = defaultFireworks;
    }

    public ChatColor chatColor() {
        return chatColor;
    }

    public String legacyColor() {
        return legacyColor;
    }

    public int defaultFireworks() {
        return defaultFireworks;
    }

    public static RewardRarity fromString(String s) {
        if (s == null) return COMMON;
        try {
            return RewardRarity.valueOf(s.trim().toUpperCase());
        } catch (Exception ignored) {
            return COMMON;
        }
    }
}