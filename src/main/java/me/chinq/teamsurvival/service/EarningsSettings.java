package me.chinq.teamsurvival.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

public final class EarningsSettings {

    public boolean enabled = true;
    public double globalMultiplier = 1.0;

    public Actionbar actionbar = new Actionbar();
    public Diminishing diminishing = new Diminishing();
    public AntiFarm antiFarm = new AntiFarm();

    public Category mining = new Category();
    public Category building = new Category();
    public Category farming = new Category();
    public Category hunting = new Category();
    public Category crafting = new Category();
    public Category smelting = new Category();
    public Exploring exploring = new Exploring();
    public Fishing fishing = new Fishing();

    public static final class Actionbar {
        public boolean enabled = true;
        public long minCoinsToShow = 1;
    }

    public static final class Diminishing {
        public boolean enabled = true;
        public int windowSeconds = 600;
        public double halfLifeActions = 80.0;
        public double minFactor = 0.15;
        public boolean perCategory = true;
    }

    public static final class AntiFarm {
        public int placedBlockTtlSeconds = 1800;
        public double placedBlockFactor = 0.0;
    }

    public static final class Category {
        public boolean enabled = true;
        public double def = 0.05;

        public final EnumMap<Material, Double> materials = new EnumMap<>(Material.class);
        public final EnumMap<EntityType, Double> entities = new EnumMap<>(EntityType.class);
    }

    public static final class Exploring {
        public boolean enabled = true;
        public int blocksPerReward = 120;
        public double coinsPerReward = 0.4;
    }

    public static final class Fishing {
        public boolean enabled = true;
        public double perCatch = 0.6;
    }

    // ---------------- loader helpers ----------------

    static double getDouble(ConfigurationSection s, String path, double def) {
        if (s == null) return def;
        return s.getDouble(path, def);
    }

    static int getInt(ConfigurationSection s, String path, int def) {
        if (s == null) return def;
        return s.getInt(path, def);
    }

    static boolean getBool(ConfigurationSection s, String path, boolean def) {
        if (s == null) return def;
        return s.getBoolean(path, def);
    }

    static void loadMaterialMap(ConfigurationSection root, String path, EnumMap<Material, Double> out) {
        out.clear();
        if (root == null) return;
        ConfigurationSection sec = root.getConfigurationSection(path);
        if (sec == null) return;

        for (String k : sec.getKeys(false)) {
            try {
                Material m = Material.valueOf(k.toUpperCase());
                out.put(m, sec.getDouble(k));
            } catch (Exception ignored) { }
        }
    }

    static void loadEntityMap(ConfigurationSection root, String path, EnumMap<EntityType, Double> out) {
        out.clear();
        if (root == null) return;
        ConfigurationSection sec = root.getConfigurationSection(path);
        if (sec == null) return;

        for (String k : sec.getKeys(false)) {
            try {
                EntityType t = EntityType.valueOf(k.toUpperCase());
                out.put(t, sec.getDouble(k));
            } catch (Exception ignored) { }
        }
    }
}