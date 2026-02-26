package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class EarningsService {

    public enum CategoryKey {
        MINING, BUILDING, FARMING, HUNTING, CRAFTING, SMELTING, FISHING, EXPLORING
    }

    private final Settings settings;
    private final TeamService teamService;

    private final PlacedBlockTracker placedBlockTracker;
    private final ActivityDecay activityDecay;
    private final DistanceTracker distanceTracker;

    public EarningsService(Settings settings, TeamService teamService) {
        this.settings = settings;
        this.teamService = teamService;

        this.placedBlockTracker = new PlacedBlockTracker(settings);
        this.activityDecay = new ActivityDecay(settings);
        this.distanceTracker = new DistanceTracker();
    }

    public PlacedBlockTracker placedBlocks() { return placedBlockTracker; }
    public DistanceTracker distance() { return distanceTracker; }

    public void award(Player p, CategoryKey cat, double baseCoins, String reason) {
        if (p == null) return;
        if (!settings.earnings.enabled) return;

        Team t = teamService.getTeamOf(p.getUniqueId());
        if (t == null) return;

        // allowed worlds check (re-using your teams.allowedWorlds)
        if (!settings.allowedWorldsLower.isEmpty()) {
            String w = p.getWorld().getName().toLowerCase();
            if (!settings.allowedWorldsLower.contains(w)) return;
        }

        double mult = settings.earnings.globalMultiplier;

        double dimFactor = activityDecay.factor(p.getUniqueId(), cat);
        double coins = baseCoins * mult * dimFactor;

        long add = Math.max(0L, Math.round(coins));
        if (add <= 0) return;

        teamService.addCoins(t, add);

        if (settings.earnings.actionbar.enabled && add >= settings.earnings.actionbar.minCoinsToShow) {
            p.sendActionBar("§6+" + add + " §eCoins §7(" + reason + " §8x" + String.format(java.util.Locale.US, "%.2f", dimFactor) + "§7)");
        }
    }

    // ------- helpers for reading config rewards -------

    public double miningCoins(Material m) {
        EarningsSettings es = settings.earnings;
        if (!es.mining.enabled) return 0.0;
        if (m == null) return es.mining.def;
        return es.mining.materials.getOrDefault(m, es.mining.def);
    }

    public double buildingCoins(Material m) {
        EarningsSettings es = settings.earnings;
        if (!es.building.enabled) return 0.0;
        if (m == null) return es.building.def;
        return es.building.materials.getOrDefault(m, es.building.def);
    }

    public double farmingMatureCoins(Material cropBlock) {
        EarningsSettings es = settings.earnings;
        if (!es.farming.enabled) return 0.0;
        if (cropBlock == null) return es.farming.def;
        return es.farming.materials.getOrDefault(cropBlock, es.farming.def);
    }

    public double huntingCoins(EntityType type) {
        EarningsSettings es = settings.earnings;
        if (!es.hunting.enabled) return 0.0;
        if (type == null) return es.hunting.def;
        return es.hunting.entities.getOrDefault(type, es.hunting.def);
    }

    public double craftingCoins(Material itemType) {
        EarningsSettings es = settings.earnings;
        if (!es.crafting.enabled) return 0.0;
        if (itemType == null) return es.crafting.def;
        return es.crafting.materials.getOrDefault(itemType, es.crafting.def);
    }

    public double smeltingCoins(Material itemType) {
        EarningsSettings es = settings.earnings;
        if (!es.smelting.enabled) return 0.0;
        if (itemType == null) return es.smelting.def;
        return es.smelting.materials.getOrDefault(itemType, es.smelting.def);
    }

    public double fishingCoins() {
        return settings.earnings.fishing.enabled ? settings.earnings.fishing.perCatch : 0.0;
    }

    public void onExploreMove(Player p, Location from, Location to) {
        if (!settings.earnings.exploring.enabled) return;
        if (p == null || from == null || to == null) return;
        if (from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;

        double delta = distanceTracker.addDistance(p.getUniqueId(), from, to);
        if (delta <= 0) return;

        int threshold = settings.earnings.exploring.blocksPerReward;
        if (threshold <= 0) return;

        int steps = distanceTracker.consumeSteps(p.getUniqueId(), threshold);
        if (steps <= 0) return;

        double base = steps * settings.earnings.exploring.coinsPerReward;
        award(p, CategoryKey.EXPLORING, base, "explore");
    }

    // ---------------- internal classes ----------------

    public static final class ActivityDecay {
        private final Settings settings;

        // per player → per key decayed count
        private final Map<UUID, Map<CategoryKey, DecayedCounter>> map = new HashMap<>();

        ActivityDecay(Settings settings) {
            this.settings = settings;
        }

        double factor(UUID u, CategoryKey cat) {
            EarningsSettings.Diminishing d = settings.earnings.diminishing;
            if (!d.enabled) return 1.0;

            long now = System.currentTimeMillis();

            Map<CategoryKey, DecayedCounter> per = map.computeIfAbsent(u, k -> new EnumMap<>(CategoryKey.class));
            CategoryKey key = d.perCategory ? cat : CategoryKey.MINING; // collapse if not per category

            DecayedCounter c = per.computeIfAbsent(key, k -> new DecayedCounter());
            c.bump(now, d.windowSeconds);

            double hl = Math.max(1.0, d.halfLifeActions);
            double raw = Math.pow(0.5, c.value / hl);

            return Math.max(d.minFactor, raw);
        }

        static final class DecayedCounter {
            double value = 0.0;
            long lastAt = 0L;

            void bump(long now, int windowSeconds) {
                if (lastAt == 0L) {
                    lastAt = now;
                    value = 1.0;
                    return;
                }

                double tau = Math.max(1.0, windowSeconds) * 1000.0;
                long dt = Math.max(0L, now - lastAt);

                // half-life style decay: after windowSeconds → ~50% (approx)
                double decay = Math.pow(0.5, dt / tau);
                value = value * decay + 1.0;

                lastAt = now;
            }
        }
    }

    /**
     * Tracks blocks placed by players to prevent "place & break" farming.
     *
     * PROBLEM (before): the map was only cleaned when the same block was later checked on break.
     * If players build a lot and do NOT break those blocks, the map grows forever → RAM + GC issues.
     *
     * FIX: add sweepOld() which removes entries older than TTL; plus a hard-cap safety for infinite TTL.
     */
    public static final class PlacedBlockTracker {
        private final Settings settings;

        // Pre-size a bit to reduce rehashing under normal load.
        private final Map<BlockKey, Long> placedAt = new HashMap<>(8192);

        // Safety cap (especially important if TTL <= 0 meaning "infinite").
        // If you want, we can move this to config later.
        private static final int HARD_CAP_ENTRIES = 250_000;

        // When hard cap is exceeded, remove at least this many oldest-ish entries in one pass.
        private static final int HARD_CAP_EVICT_MIN = 25_000;

        PlacedBlockTracker(Settings settings) {
            this.settings = settings;
        }

        public void markPlaced(BlockKey k) {
            if (k == null) return;
            placedAt.put(k, System.currentTimeMillis());

            // If TTL is infinite (<=0), the map would grow forever. Protect memory.
            if (placedAt.size() > HARD_CAP_ENTRIES) {
                enforceHardCap();
            }
        }

        public boolean wasPlacedRecently(BlockKey k) {
            if (k == null) return false;
            Long at = placedAt.get(k);
            if (at == null) return false;

            long ttlMs = Math.max(0L, settings.earnings.antiFarm.placedBlockTtlSeconds) * 1000L;

            // TTL <= 0 means "treat placed blocks as always placed" (old behavior),
            // but we rely on hard-cap eviction to prevent infinite growth.
            if (ttlMs <= 0) return true;

            long now = System.currentTimeMillis();
            if (now - at > ttlMs) {
                placedAt.remove(k);
                return false;
            }
            return true;
        }

        public double placedFactor() {
            return settings.earnings.antiFarm.placedBlockFactor;
        }

        /**
         * Call this periodically (e.g., every 60s) to remove old entries.
         * This is the main RAM/GC optimization.
         */
        public void sweepOld() {
            long ttlMs = Math.max(0L, settings.earnings.antiFarm.placedBlockTtlSeconds) * 1000L;
            if (ttlMs <= 0) return; // "infinite" TTL -> no time-based sweep

            long now = System.currentTimeMillis();
            Iterator<Map.Entry<BlockKey, Long>> it = placedAt.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockKey, Long> e = it.next();
                Long at = e.getValue();
                if (at == null) {
                    it.remove();
                    continue;
                }
                if (now - at > ttlMs) {
                    it.remove();
                }
            }
        }

        /**
         * If TTL is infinite (or misconfigured huge), prevent memory runaway.
         * We evict the oldest entries we can find in a single scan.
         */
        private void enforceHardCap() {
            if (placedAt.isEmpty()) return;

            // Evict at least HARD_CAP_EVICT_MIN, or enough to go under cap.
            int needToRemove = Math.max(HARD_CAP_EVICT_MIN, placedAt.size() - HARD_CAP_ENTRIES);
            long now = System.currentTimeMillis();

            // One pass: remove the oldest entries first-ish by using an age threshold that we tighten.
            // Simple & cheap approach: remove entries older than a moving cutoff until we remove enough.
            // (Good enough for safety; not perfect LRU.)
            long cutoff = now - 60_000L; // start with 1 minute old
            int removed = 0;

            // Try a few rounds with expanding cutoff window.
            for (int round = 0; round < 6 && removed < needToRemove; round++) {
                Iterator<Map.Entry<BlockKey, Long>> it = placedAt.entrySet().iterator();
                while (it.hasNext() && removed < needToRemove) {
                    Map.Entry<BlockKey, Long> e = it.next();
                    Long at = e.getValue();
                    if (at == null || at < cutoff) {
                        it.remove();
                        removed++;
                    }
                }
                // expand: 1m -> 5m -> 30m -> 2h -> 12h -> 48h
                cutoff -= switch (round) {
                    case 0 -> 4 * 60_000L;
                    case 1 -> 25 * 60_000L;
                    case 2 -> 90 * 60_000L;
                    case 3 -> 10 * 60 * 60_000L;
                    case 4 -> 36 * 60 * 60_000L;
                    default -> 0L;
                };
            }

            // If still not enough removed (e.g. almost all entries are very new),
            // remove arbitrary entries until we're safe.
            if (removed < needToRemove) {
                Iterator<BlockKey> it = placedAt.keySet().iterator();
                while (it.hasNext() && removed < needToRemove) {
                    it.next();
                    it.remove();
                    removed++;
                }
            }
        }
    }

    public static final class BlockKey {
        public final String world;
        public final int x, y, z;

        public BlockKey(String world, int x, int y, int z) {
            this.world = world;
            this.x = x; this.y = y; this.z = z;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey k)) return false;
            return x == k.x && y == k.y && z == k.z && world.equals(k.world);
        }

        @Override public int hashCode() {
            int r = world.hashCode();
            r = 31 * r + x;
            r = 31 * r + y;
            r = 31 * r + z;
            return r;
        }
    }

    public static final class DistanceTracker {
        private final Map<UUID, Double> accum = new HashMap<>();
        private final Map<UUID, Location> last = new HashMap<>();

        double addDistance(UUID u, Location from, Location to) {
            if (u == null) return 0.0;
            if (from == null || to == null) return 0.0;
            if (to.getWorld() == null) return 0.0;

            Location prev = last.put(u, to.clone());
            if (prev == null) {
                // first move
                return 0.0;
            }
            if (!prev.getWorld().equals(to.getWorld())) return 0.0;

            double d = prev.distance(to);
            if (Double.isNaN(d) || Double.isInfinite(d)) return 0.0;
            if (d > 10.0) return 0.0; // anti-teleport spike

            accum.put(u, accum.getOrDefault(u, 0.0) + d);
            return d;
        }

        int consumeSteps(UUID u, int threshold) {
            double a = accum.getOrDefault(u, 0.0);
            if (a < threshold) return 0;
            int steps = (int) Math.floor(a / threshold);
            accum.put(u, a - (steps * threshold));
            return steps;
        }
    }
}