package me.chinq.teamsurvival.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public final class EarningsSettings {

    public boolean enabled;
    public double globalMultiplier;

    public Actionbar actionbar = new Actionbar();
    public Diminishing diminishing = new Diminishing();
    public AntiFarm antiFarm = new AntiFarm();

    public Category mining = new Category();
    public Category building = new Category();
    public Category farming = new Category();
    public Category hunting = new Category();
    public Fishing fishing = new Fishing();
    public Category crafting = new Category();
    public Category smelting = new Category();
    public Exploring exploring = new Exploring();

    // =========================================================
    // LOAD FROM CONFIG
    // =========================================================
    public static EarningsSettings load(FileConfiguration c) {
        EarningsSettings es = new EarningsSettings();

        es.enabled = c.getBoolean("economy.earnings.enabled", true);
        es.globalMultiplier = c.getDouble("economy.earnings.global-multiplier", 1.0);

        // ACTIONBAR
        es.actionbar.enabled = c.getBoolean("economy.earnings.actionbar.enabled", true);
        es.actionbar.minCoinsToShow = c.getLong("economy.earnings.actionbar.min-coins-to-show", 1L);

        // DIMINISHING RETURNS
        es.diminishing.enabled = c.getBoolean("economy.earnings.diminishing.enabled", true);
        es.diminishing.windowSeconds = c.getInt("economy.earnings.diminishing.window-seconds", 600);
        es.diminishing.halfLifeActions = c.getDouble("economy.earnings.diminishing.half-life-actions", 80.0);
        es.diminishing.minFactor = c.getDouble("economy.earnings.diminishing.min-factor", 0.15);
        es.diminishing.perCategory = c.getBoolean("economy.earnings.diminishing.per-category", true);

        // ANTI FARM
        es.antiFarm.placedBlockTtlSeconds = c.getInt("economy.earnings.anti-farm.placed-block-ttl-seconds", 1800);
        es.antiFarm.placedBlockFactor = c.getDouble("economy.earnings.anti-farm.placed-block-factor", 0.0);

        // CATEGORIES
        loadCategory(c, "economy.earnings.mining", es.mining);
        loadCategory(c, "economy.earnings.building", es.building);
        loadCategory(c, "economy.earnings.farming", es.farming);
        loadCategory(c, "economy.earnings.crafting", es.crafting);
        loadCategory(c, "economy.earnings.smelting", es.smelting);

        // FISHING
        es.fishing.enabled = c.getBoolean("economy.earnings.fishing.enabled", true);
        es.fishing.perCatch = c.getDouble("economy.earnings.fishing.per-catch", 0.6);

        // EXPLORING
        es.exploring.enabled = c.getBoolean("economy.earnings.exploring.enabled", true);
        es.exploring.blocksPerReward = c.getInt("economy.earnings.exploring.blocks-per-reward", 120);
        es.exploring.coinsPerReward = c.getDouble("economy.earnings.exploring.coins-per-reward", 0.4);

        return es;
    }

    private static void loadCategory(FileConfiguration c, String path, Category cat) {
        cat.enabled = c.getBoolean(path + ".enabled", true);
        cat.def = c.getDouble(path + ".default", 0.05);

        ConfigurationSection sec = c.getConfigurationSection(path + ".materials");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            try {
                Material m = Material.valueOf(key.toUpperCase());
                cat.materials.put(m, sec.getDouble(key));
            } catch (Exception ignored) {}
        }
    }

    // =========================================================
    // INNER CLASSES
    // =========================================================

    public static final class Actionbar {
        public boolean enabled;
        public long minCoinsToShow;
    }

    public static final class Diminishing {
        public boolean enabled;
        public int windowSeconds;
        public double halfLifeActions;
        public double minFactor;
        public boolean perCategory;
    }

    public static final class AntiFarm {
        public int placedBlockTtlSeconds;
        public double placedBlockFactor;
    }

    public static class Category {
        public boolean enabled;
        public double def;
        public Map<Material, Double> materials = new HashMap<>();
        public Map<EntityType, Double> entities = new HashMap<>();
    }

    public static final class Fishing {
        public boolean enabled;
        public double perCatch;
    }

    public static final class Exploring {
        public boolean enabled;
        public int blocksPerReward;
        public double coinsPerReward;
    }
}