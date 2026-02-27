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
        loadMaterialCategory(c, "economy.earnings.mining", "materials", es.mining);
        loadMaterialCategory(c, "economy.earnings.building", "materials", es.building);

        // farming ima svoj key: "mature-crop" + "crops"
        loadFarmingCategory(c, "economy.earnings.farming", es.farming);

        // hunting ima svoj key: "entities" (OVO JE PRE FALILO)
        loadHuntingCategory(c, "economy.earnings.hunting", es.hunting);

        // crafting/smelting koriste "items" (ali podržavamo i stari "materials")
        loadMaterialCategory(c, "economy.earnings.crafting", "items", es.crafting);
        loadMaterialCategory(c, "economy.earnings.smelting", "items", es.smelting);

        // FISHING
        es.fishing.enabled = c.getBoolean("economy.earnings.fishing.enabled", true);
        es.fishing.perCatch = c.getDouble("economy.earnings.fishing.per-catch", 0.6);

        // EXPLORING
        es.exploring.enabled = c.getBoolean("economy.earnings.exploring.enabled", true);
        es.exploring.blocksPerReward = c.getInt("economy.earnings.exploring.blocks-per-reward", 120);
        es.exploring.coinsPerReward = c.getDouble("economy.earnings.exploring.coins-per-reward", 0.4);

        return es;
    }

    /**
     * Učitava kategoriju gde su nagrade mapirane po Material (mining/building/crafting/smelting...).
     * sectionKey je obično "materials" ili "items".
     *
     * Kompatibilnost:
     *  - ako sectionKey ne postoji, proba i "materials" (jer je stariji format)
     */
    private static void loadMaterialCategory(FileConfiguration c, String path, String sectionKey, Category cat) {
        cat.enabled = c.getBoolean(path + ".enabled", true);
        cat.def = c.getDouble(path + ".default", 0.05);

        ConfigurationSection sec = c.getConfigurationSection(path + "." + sectionKey);
        if (sec == null && !"materials".equalsIgnoreCase(sectionKey)) {
            sec = c.getConfigurationSection(path + ".materials");
        }
        if (sec == null) return;

        loadMaterialsFromSection(sec, cat);
    }

    /**
     * Farming koristi:
     *  - enabled
     *  - mature-crop (default za sve zrele cropove)
     *  - crops: { WHEAT: 0.5, ... }
     *
     * Takođe podržava stari key "materials" (ako neko koristi stariji config).
     */
    private static void loadFarmingCategory(FileConfiguration c, String path, Category cat) {
        cat.enabled = c.getBoolean(path + ".enabled", true);

        // u tvom configu default je mature-crop, ali ostavljamo fallback na ".default" za kompatibilnost
        double mature = c.getDouble(path + ".mature-crop", c.getDouble(path + ".default", 0.05));
        cat.def = mature;

        // prvo učitaj "crops"
        ConfigurationSection crops = c.getConfigurationSection(path + ".crops");
        if (crops != null) {
            loadMaterialsFromSection(crops, cat);
        }

        // fallback: "materials" (stari format)
        ConfigurationSection mats = c.getConfigurationSection(path + ".materials");
        if (mats != null) {
            loadMaterialsFromSection(mats, cat);
        }
    }

    /**
     * Hunting koristi:
     *  - enabled
     *  - default
     *  - entities: { ZOMBIE: 0.3, ... }
     */
    private static void loadHuntingCategory(FileConfiguration c, String path, Category cat) {
        cat.enabled = c.getBoolean(path + ".enabled", true);
        cat.def = c.getDouble(path + ".default", 0.2);

        ConfigurationSection sec = c.getConfigurationSection(path + ".entities");
        if (sec != null) {
            loadEntitiesFromSection(sec, cat);
        }

        // (opciono) fallback ako neko koristi "mobs" umesto "entities"
        ConfigurationSection maybeOld = c.getConfigurationSection(path + ".mobs");
        if (maybeOld != null) {
            loadEntitiesFromSection(maybeOld, cat);
        }
    }

    private static void loadMaterialsFromSection(ConfigurationSection sec, Category cat) {
        for (String key : sec.getKeys(false)) {
            try {
                Material m = Material.valueOf(key.toUpperCase());
                cat.materials.put(m, sec.getDouble(key));
            } catch (Exception ignored) { }
        }
    }

    private static void loadEntitiesFromSection(ConfigurationSection sec, Category cat) {
        for (String key : sec.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                cat.entities.put(type, sec.getDouble(key));
            } catch (Exception ignored) { }
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