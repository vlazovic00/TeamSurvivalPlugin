package me.chinq.teamsurvival.service;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SupplyDropService {

    private final JavaPlugin plugin;
    private final NamespacedKey dropKey;

    // config-driven (reloadable)
    private boolean enabled;
    private String worldName;

    private int intervalSeconds;
    private int borderMarginBlocks;

    private int removeAfterOpenSeconds;

    private int maxRadiusBlocks;
    private boolean preferNearPlayers;

    private BukkitTask broadcastTask;

    private int expireSeconds;
    private List<Integer> expireWarningsSeconds;
    private String msgExpireWarning;
    private String msgExpired;

    private String spawnTitle;
    private String spawnSubtitle;
    private int titleFadeInTicks;
    private int titleStayTicks;
    private int titleFadeOutTicks;
    private long nextDropAtMs = 0L;

    private int fireworksDurationSeconds;
    private int fireworksEveryTicks;
    private int fireworksPerBurst;

    private int minItems;
    private int maxItems;
    private final List<LootEntry> loot = new ArrayList<>();

    private BukkitTask scheduleTask;
    private ActiveDrop active;

    public SupplyDropService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dropKey = new NamespacedKey(plugin, "ts_supply_drop");

        loadFromConfig();
    }

    /** Pozovi iz /timreload */
    public void reloadFromConfig() {
        loadFromConfig();
        // restart timer da interval odmah važi
        start();
    }

    private void loadFromConfig() {
        FileConfiguration c = plugin.getConfig();

        this.enabled = c.getBoolean("supplyDrops.enabled", false);
        this.worldName = c.getString("supplyDrops.world", "");

        this.intervalSeconds = Math.max(10, c.getInt("supplyDrops.intervalSeconds", 900));
        this.borderMarginBlocks = Math.max(0, c.getInt("supplyDrops.borderMarginBlocks", 8));

        this.removeAfterOpenSeconds = Math.max(1, c.getInt("supplyDrops.removeAfterOpenSeconds", 10));
        this.expireSeconds = Math.max(10, c.getInt("supplyDrops.expireSeconds", 600));
        this.expireWarningsSeconds = c.getIntegerList("supplyDrops.expireWarningsSeconds");

        this.msgExpireWarning = color(c.getString("supplyDrops.messages.expireWarning",
                "&6&l[SUPPLY] &eSupply chest će biti obrisan za &c%time%&e ako ga niko ne otvori!"));
        this.msgExpired = color(c.getString("supplyDrops.messages.expired",
                "&6&l[SUPPLY] &cSupply chest je istekao i obrisan je."));

        this.maxRadiusBlocks = Math.max(128, c.getInt("supplyDrops.maxRadiusBlocks", 2500));
        this.preferNearPlayers = c.getBoolean("supplyDrops.preferNearPlayers", true);

        this.spawnTitle = color(c.getString("supplyDrops.titles.spawn.title", "&6&lSUPPLY DROP!"));
        this.spawnSubtitle = color(c.getString("supplyDrops.titles.spawn.subtitle", "&ePao je supply u worldborder-u!"));
        this.titleFadeInTicks = c.getInt("supplyDrops.titles.spawn.fadeInTicks", 10);
        this.titleStayTicks = c.getInt("supplyDrops.titles.spawn.stayTicks", 60);
        this.titleFadeOutTicks = c.getInt("supplyDrops.titles.spawn.fadeOutTicks", 10);

        this.fireworksDurationSeconds = Math.max(1, c.getInt("supplyDrops.fireworks.durationSeconds", 6));
        this.fireworksEveryTicks = Math.max(1, c.getInt("supplyDrops.fireworks.everyTicks", 2));
        this.fireworksPerBurst = Math.max(1, c.getInt("supplyDrops.fireworks.perBurst", 2));

        this.minItems = Math.max(1, c.getInt("supplyDrops.loot.minItems", 6));
        this.maxItems = Math.max(this.minItems, c.getInt("supplyDrops.loot.maxItems", 10));

        // loot se uvek učitava iz configa na reload (da i loot odmah važi)
        loot.clear();
        if (enabled) {
            loadLoot();
        }
    }

    private int findSafeY(World world, int x, int z) {
        int max = world.getMaxHeight() - 1;
        int min = world.getMinHeight() + 1;

        for (int y = max; y >= min; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);

            // želimo da chest stoji na solid bloku
            if (below.getType().isSolid() && block.getType().isAir()) {
                return y;
            }
        }
        return -1;
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("SupplyDropService: disabled (supplyDrops.enabled=false)");
            return;
        }

        if (loot.isEmpty()) {
            plugin.getLogger().warning("SupplyDropService: loot je prazan (supplyDrops.loot.items)");
            return;
        }

        stop();

        // schedule spawn task (po novom intervalu)
        scheduleTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnDrop();
            }
        }.runTaskTimer(plugin, intervalSeconds * 20L, intervalSeconds * 20L);

        // zapamti kada dolazi sledeći drop
        this.nextDropAtMs = System.currentTimeMillis() + (intervalSeconds * 1000L);

        plugin.getLogger().info("SupplyDropService: started, intervalSeconds=" + intervalSeconds + ", loot=" + loot.size());
    }

    public void stop() {

        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }

        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }

        // ukloni aktivni chest i cancel taskove
        removeActiveChest();
    }

    public boolean isSupplyChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) return false;
        try {
            Chest chest = (Chest) block.getState();
            Integer v = chest.getPersistentDataContainer().get(dropKey, PersistentDataType.INTEGER);
            return v != null && v == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public void onChestOpened(Chest chest, Player opener) {
        if (active == null) return;
        if (!sameBlock(active.location, chest.getBlock().getLocation())) return;
        if (active.opened) return;

        active.opened = true;
        // stop expire warnings/expire timer, sad važi removeAfterOpenSeconds
        if (active.expireTask != null) {
            active.expireTask.cancel();
            active.expireTask = null;
        }
        for (BukkitTask t : active.warnTasks) {
            if (t != null) t.cancel();
        }
        active.warnTasks.clear();

        long nextSec = intervalSeconds;
        String nextFormatted = formatTime(nextSec);

        String msg = ChatColor.GOLD + "" + ChatColor.BOLD + "[SUPPLY] "
                + ChatColor.YELLOW + opener.getName()
                + ChatColor.WHITE + " je otvorio supply chest! "
                + ChatColor.GRAY + "Briše se za "
                + ChatColor.AQUA + removeAfterOpenSeconds + "s"
                + ChatColor.GRAY + ". Novi supply za "
                + ChatColor.GREEN + nextFormatted + ChatColor.GRAY + ".";

        Bukkit.broadcastMessage(msg);

        // fireworks 5-6s
        startFireworksSpam(chest.getBlock().getLocation().add(0.5, 1.0, 0.5));

        // remove chest after X seconds
        active.removeTask = new BukkitRunnable() {
            @Override
            public void run() {
                removeActiveChest();
            }
        }.runTaskLater(plugin, removeAfterOpenSeconds * 20L);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        if (h > 0)
            return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0)
            return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }

    private void scheduleExpireTasks(World world) {
        if (active == null) return;

        // expiry
        active.expireTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (active == null) return;
                if (active.opened) return; // ako je otvoren, brisanje ide po "removeAfterOpenSeconds"

                Bukkit.broadcastMessage(msgExpired);
                removeActiveChest();
            }
        }.runTaskLater(plugin, expireSeconds * 20L);

        // warnings
        if (expireWarningsSeconds != null && !expireWarningsSeconds.isEmpty()) {
            for (Integer w : expireWarningsSeconds) {
                if (w == null) continue;
                int warnSec = Math.max(1, w);

                int delaySec = expireSeconds - warnSec;
                if (delaySec <= 0) continue;

                BukkitTask t = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (active == null) return;
                        if (active.opened) return;

                        String time = formatTime(warnSec);
                        Bukkit.broadcastMessage(msgExpireWarning.replace("%time%", time));
                    }
                }.runTaskLater(plugin, delaySec * 20L);

                active.warnTasks.add(t);
            }
        }
    }

    // ----------------------------
    // SPAWN LOGIC (NON-BLOCKING)
    // ----------------------------
    private void spawnDrop() {

        World world = pickWorld();
        // dok chest nije otvoren, ne pravimo novi
        if (active != null && !active.opened) {
            return;
        }
        if (world == null) {
            plugin.getLogger().warning("SupplyDropService: world nije pronađen");
            return;
        }

        plugin.getLogger().info("SupplyDropService: trying to spawn supply drop...");

        Location targetXZ = findBorderSafeXZ(world);
        if (targetXZ == null) {
            plugin.getLogger().warning("SupplyDropService: nije pronađena lokacija u borderu.");
            return;
        }

        final int x = targetXZ.getBlockX();
        final int z = targetXZ.getBlockZ();

        plugin.getLogger().info("SupplyDropService: loading chunk async at " + x + "," + z);

        world.getChunkAtAsync(x >> 4, z >> 4, true, true).whenComplete((chunk, throwable) -> {

            if (throwable != null) {
                plugin.getLogger().warning("SupplyDropService: async chunk load FAILED -> retry next cycle");
                throwable.printStackTrace();
                return;
            }

            if (chunk == null) {
                plugin.getLogger().warning("SupplyDropService: chunk returned NULL -> retry next cycle");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    int y = findSafeY(world, x, z);

                    if (y == -1) {
                        plugin.getLogger().warning("SupplyDropService: no safe Y -> retry");
                        return;
                    }

                    Location loc = new Location(world, x, y, z);

                    if (!world.getWorldBorder().isInside(loc.clone().add(0.5, 0.5, 0.5))) {
                        plugin.getLogger().warning("SupplyDropService: outside border -> retry");
                        return;
                    }

                    Block b = loc.getBlock();
                    b.setType(Material.CHEST, false);

                    Chest chest = (Chest) b.getState();
                    chest.getPersistentDataContainer().set(dropKey, PersistentDataType.INTEGER, 1);
                    chest.update(true, false);

                    fillChest(chest.getBlockInventory());
                    active = new ActiveDrop(loc);

                    String coords = ChatColor.YELLOW + "X: " + x + " Z: " + z;

                    for (Player p : world.getPlayers()) {
                        p.sendTitle(
                                spawnTitle,
                                spawnSubtitle + " " + coords,
                                titleFadeInTicks,
                                titleStayTicks,
                                titleFadeOutTicks
                        );
                    }

                    plugin.getLogger().info("SupplyDropService: SUCCESSFULLY SPAWNED at "
                            + x + "," + y + "," + z);

                    scheduleExpireTasks(world);
                    startBroadcastTask(world, x, z);
                    this.nextDropAtMs = System.currentTimeMillis() + (intervalSeconds * 1000L);

                } catch (Exception ex) {
                    plugin.getLogger().warning("SupplyDropService: spawn failed inside sync task");
                    ex.printStackTrace();
                }
            });

        });
    }

    private void startBroadcastTask(World world, int x, int z) {

        if (broadcastTask != null) {
            broadcastTask.cancel();
        }

        broadcastTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (active == null) {
                    cancel();
                    return;
                }

                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[SUPPLY] "
                        + ChatColor.YELLOW + "Supply drop je aktivan na "
                        + ChatColor.AQUA + "X: " + x + " Z: " + z);
            }
        }.runTaskTimer(plugin, 60 * 20L, 60 * 20L); // svakih 60 sekundi
    }

    private void removeActiveChest() {
        if (active == null) return;

        active.cancelAll();

        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }

        Location loc = active.location;
        Block b = loc.getBlock();
        if (b.getType() == Material.CHEST) {
            b.setType(Material.AIR, false);
        }

        active = null;
    }

    // ----------------------------
    // LOCATION PICKING (SAFE)
    // ----------------------------
    private Location findBorderSafeXZ(World world) {
        WorldBorder wb = world.getWorldBorder();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Location base = pickBaseLocation(world);

        double borderRadius = (wb.getSize() / 2.0) - borderMarginBlocks;
        if (borderRadius <= 16) return null;

        double radius = Math.min(borderRadius, maxRadiusBlocks);

        for (int tries = 0; tries < 200; tries++) {
            double dx = r.nextDouble(-radius, radius);
            double dz = r.nextDouble(-radius, radius);

            int x = (int) Math.floor(base.getX() + dx);
            int z = (int) Math.floor(base.getZ() + dz);

            Location test = new Location(world, x + 0.5, base.getY(), z + 0.5);
            if (!wb.isInside(test)) continue;

            return new Location(world, x, 0, z);
        }

        Location center = wb.getCenter();
        for (int tries = 0; tries < 200; tries++) {
            double dx = r.nextDouble(-radius, radius);
            double dz = r.nextDouble(-radius, radius);

            int x = (int) Math.floor(center.getX() + dx);
            int z = (int) Math.floor(center.getZ() + dz);

            Location test = new Location(world, x + 0.5, center.getY(), z + 0.5);
            if (!wb.isInside(test)) continue;

            return new Location(world, x, 0, z);
        }

        return null;
    }

    private Location pickBaseLocation(World world) {
        if (!preferNearPlayers) {
            return world.getSpawnLocation();
        }

        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            return p.getLocation();
        }

        return world.getSpawnLocation();
    }

    private World pickWorld() {
        if (worldName != null && !worldName.isBlank()) {
            return Bukkit.getWorld(worldName);
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!Objects.equals(a.getWorld().getUID(), b.getWorld().getUID())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    // ----------------------------
    // FIREWORKS SPAM
    // ----------------------------
    private void startFireworksSpam(Location at) {
        if (active == null) return;

        int totalTicks = fireworksDurationSeconds * 20;

        active.fireworksTask = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (active == null) {
                    cancel();
                    return;
                }
                if (t >= totalTicks) {
                    cancel();
                    return;
                }

                for (int i = 0; i < fireworksPerBurst; i++) {
                    spawnInstantFirework(at);
                }

                t += fireworksEveryTicks;
            }
        }.runTaskTimer(plugin, 0L, fireworksEveryTicks);
    }

    private void spawnInstantFirework(Location at) {
        World w = at.getWorld();
        if (w == null) return;

        Firework fw = w.spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();

            FireworkEffect.Type type = randomEffectType();
            Color c1 = randomColor();
            Color c2 = randomColor();

            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(c1)
                    .withFade(c2)
                    .trail(true)
                    .flicker(true)
                    .build());

            meta.setPower(ThreadLocalRandom.current().nextInt(1, 3));
            f.setFireworkMeta(meta);

            Vector v = new Vector(
                    ThreadLocalRandom.current().nextDouble(-0.2, 0.2),
                    ThreadLocalRandom.current().nextDouble(0.8, 1.2),
                    ThreadLocalRandom.current().nextDouble(-0.2, 0.2)
            );
            f.setVelocity(v);
        });

        int explodeDelay = ThreadLocalRandom.current().nextInt(20, 40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                fw.detonate();
            } catch (Exception ignored) {}
        }, explodeDelay);
    }

    private FireworkEffect.Type randomEffectType() {
        FireworkEffect.Type[] all = FireworkEffect.Type.values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    private Color randomColor() {
        DyeColor[] dc = DyeColor.values();
        return dc[ThreadLocalRandom.current().nextInt(dc.length)].getColor();
    }

    // ----------------------------
    // CHEST LOOT
    // ----------------------------
    private void fillChest(Inventory inv) {
        inv.clear();

        int count = ThreadLocalRandom.current().nextInt(minItems, maxItems + 1);

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots);

        for (int i = 0; i < count; i++) {
            LootEntry e = loot.get(ThreadLocalRandom.current().nextInt(loot.size()));
            ItemStack it = e.createItem();
            int slot = slots.get(i % slots.size());
            inv.setItem(slot, it);
        }
    }

    private void loadLoot() {
        loot.clear();

        List<Map<?, ?>> list = plugin.getConfig().getMapList("supplyDrops.loot.items");
        if (list == null || list.isEmpty()) return;

        for (Map<?, ?> raw : list) {
            LootEntry e = LootEntry.fromMap(raw);
            if (e != null) loot.add(e);
        }
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static final class ActiveDrop {
        final Location location;
        boolean opened = false;

        BukkitTask removeTask;
        BukkitTask fireworksTask;

        BukkitTask expireTask;
        final List<BukkitTask> warnTasks = new ArrayList<>();

        ActiveDrop(Location location) {
            this.location = location.clone();
        }

        void cancelAll() {
            if (removeTask != null) {
                removeTask.cancel();
                removeTask = null;
            }
            if (fireworksTask != null) {
                fireworksTask.cancel();
                fireworksTask = null;
            }

            if (expireTask != null) {
                expireTask.cancel();
                expireTask = null;
            }

            for (BukkitTask t : warnTasks) {
                if (t != null) t.cancel();
            }
            warnTasks.clear();
        }
    }

    private static final class LootEntry {
        final Material material;
        final int minAmount;
        final int maxAmount;
        final String name;
        final List<String> lore;
        final Map<Enchantment, Integer> enchants;
        final boolean unbreakable;
        final boolean hideFlags;

        LootEntry(Material material, int minAmount, int maxAmount, String name, List<String> lore,
                  Map<Enchantment, Integer> enchants, boolean unbreakable, boolean hideFlags) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.name = name;
            this.lore = lore;
            this.enchants = enchants;
            this.unbreakable = unbreakable;
            this.hideFlags = hideFlags;
        }

        ItemStack createItem() {
            int amt = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
            ItemStack is = new ItemStack(material, amt);

            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                if (name != null && !name.isBlank()) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                if (lore != null && !lore.isEmpty()) {
                    List<String> colored = new ArrayList<>();
                    for (String l : lore) colored.add(ChatColor.translateAlternateColorCodes('&', l));
                    meta.setLore(colored);
                }
                meta.setUnbreakable(unbreakable);
                if (hideFlags) meta.addItemFlags(ItemFlag.values());
                is.setItemMeta(meta);
            }

            if (enchants != null && !enchants.isEmpty()) {
                for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                    is.addUnsafeEnchantment(e.getKey(), e.getValue());
                }
            }

            return is;
        }

        static LootEntry fromMap(Map<?, ?> m) {
            String matS = str(m.get("material"));
            if (matS == null) return null;

            Material mat = Material.matchMaterial(matS.toUpperCase(Locale.ROOT));
            if (mat == null) return null;

            int amount = intv(m.get("amount"), -1);
            int minAmount = intv(m.get("minAmount"), amount > 0 ? amount : 1);
            int maxAmount = intv(m.get("maxAmount"), amount > 0 ? amount : minAmount);
            if (minAmount < 1) minAmount = 1;
            if (maxAmount < minAmount) maxAmount = minAmount;

            String name = str(m.get("name"));
            List<String> lore = listStr(m.get("lore"));

            boolean unbreakable = boolv(m.get("unbreakable"), false);
            boolean hideFlags = boolv(m.get("hideFlags"), true);

            Map<Enchantment, Integer> ench = new HashMap<>();
            Object enchRaw = m.get("enchants");
            if (enchRaw instanceof Map<?, ?> em) {
                for (Map.Entry<?, ?> e : em.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    int lvl = intv(e.getValue(), 1);
                    Enchantment enchantment = Enchantment.getByName(k.toUpperCase(Locale.ROOT));
                    if (enchantment != null) {
                        ench.put(enchantment, lvl);
                    }
                }
            }

            return new LootEntry(mat, minAmount, maxAmount, name, lore, ench, unbreakable, hideFlags);
        }

        private static String str(Object o) { return o == null ? null : String.valueOf(o); }
        private static int intv(Object o, int def) {
            if (o == null) return def;
            if (o instanceof Number n) return n.intValue();
            try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
        }
        private static boolean boolv(Object o, boolean def) {
            if (o == null) return def;
            if (o instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(o));
        }
        private static List<String> listStr(Object o) {
            if (o instanceof List<?> l) {
                List<String> out = new ArrayList<>();
                for (Object x : l) if (x != null) out.add(String.valueOf(x));
                return out;
            }
            return Collections.emptyList();
        }
    }
}