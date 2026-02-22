package me.chinq.teamsurvival.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Jedna nagrada iz config-a. Moze biti single item (min/max) ili kit (vise itema).
 */
public final class Reward {

    public final String id;
    public final RewardRarity rarity;
    public final int weight;
    public final String display;
    public final String message;

    private final List<Entry> entries;

    public Reward(String id, RewardRarity rarity, int weight, String display, String message, List<Entry> entries) {
        this.id = id;
        this.rarity = rarity;
        this.weight = Math.max(1, weight);
        this.display = display;
        this.message = message;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public List<ItemStack> rollItems(Random rng) {
        List<ItemStack> out = new ArrayList<>();
        for (Entry e : entries) {
            int min = Math.max(0, e.min);
            int max = Math.max(min, e.max);
            int amount = min;
            if (max > min) {
                amount = min + rng.nextInt(max - min + 1);
            }
            if (amount <= 0) continue;
            out.add(new ItemStack(e.material, amount));
        }
        return out;
    }

    public String defaultDisplay() {
        if (display != null && !display.isBlank()) return display;

        if (entries.size() == 1) {
            Entry e = entries.get(0);
            String name = e.material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            String[] parts = name.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isBlank()) continue;
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
            }
            return sb.toString().trim();
        }
        return "Reward";
    }

    public boolean isValid() {
        return !entries.isEmpty();
    }

    public static final class Entry {
        public final Material material;
        public final int min;
        public final int max;

        public Entry(Material material, int min, int max) {
            this.material = material;
            this.min = min;
            this.max = max;
        }
    }
}