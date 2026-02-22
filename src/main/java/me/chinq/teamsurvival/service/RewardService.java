package me.chinq.teamsurvival.service;

import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class RewardService {

    private final JavaPlugin plugin;
    private final MessageService messages;

    private final Random rng = new Random();
    private final List<Reward> rewards = new ArrayList<>();

    // config-driven (reloadable)
    private boolean enabled;
    private int intervalSeconds;
    private boolean giveToAllOnline;

    private boolean fireworksEnabled;
    private int fireworkRadius;

    private BukkitTask task; // <--- bitno da možemo cancel na reload

    public RewardService(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;

        loadFromConfig();
        start();
    }

    /** Pozovi iz /timreload */
    public void reloadFromConfig() {
        loadFromConfig();
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void start() {
        stop();

        if (!enabled) {
            plugin.getLogger().info("RewardService: disabled (rewards.enabled=false)");
            return;
        }

        loadRewards();
        if (rewards.isEmpty()) {
            plugin.getLogger().warning("RewardService: nema nijedne nagrade u config.yml (rewards.items)");
            return;
        }

        long ticks = Math.max(5, intervalSeconds) * 20L;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tickGiveRewards();
            }
        }.runTaskTimer(plugin, ticks, ticks);

        plugin.getLogger().info("RewardService: enabled, intervalSeconds=" + intervalSeconds + ", rewards=" + rewards.size());
    }

    private void loadFromConfig() {
        this.enabled = plugin.getConfig().getBoolean("rewards.enabled", false);
        this.intervalSeconds = Math.max(5, plugin.getConfig().getInt("rewards.intervalSeconds", 600));
        this.giveToAllOnline = plugin.getConfig().getBoolean("rewards.giveToAllOnline", true);

        this.fireworksEnabled = plugin.getConfig().getBoolean("rewards.fireworks.enabled", true);
        this.fireworkRadius = Math.max(1, plugin.getConfig().getInt("rewards.fireworks.radius", 3));
    }

    private void loadRewards() {
        rewards.clear();

        List<Map<?, ?>> list = plugin.getConfig().getMapList("rewards.items");
        if (list == null || list.isEmpty()) return;

        int i = 0;
        for (Map<?, ?> m : list) {
            i++;
            Reward reward = parseReward(m, i);
            if (reward != null && reward.isValid()) {
                rewards.add(reward);
            }
        }
    }

    private Reward parseReward(Map<?, ?> m, int index) {
        String id = str(m.get("id"));
        if (id == null || id.isBlank()) id = "reward_" + index;

        RewardRarity rarity = RewardRarity.fromString(str(m.get("rarity")));
        int weight = toInt(m.get("weight"), 1);
        String display = str(m.get("display"));
        String message = str(m.get("message"));

        String type = str(m.get("type"));
        if (type == null || type.isBlank()) type = "ITEM";
        type = type.trim().toUpperCase(Locale.ROOT);

        List<Reward.Entry> entries = new ArrayList<>();

        if (type.equals("KIT")) {
            Object itemsObj = m.get("items");
            if (itemsObj instanceof List<?> itemsList) {
                for (Object o : itemsList) {
                    if (!(o instanceof Map<?, ?> im)) continue;
                    Material mat = material(str(im.get("material")));
                    if (mat == null) continue;
                    int amount = toInt(im.get("amount"), 1);
                    entries.add(new Reward.Entry(mat, amount, amount));
                }
            }
        } else {
            // ITEM
            Material mat = material(str(m.get("material")));
            if (mat == null) {
                // backward compat: "item" key
                mat = material(str(m.get("item")));
            }
            if (mat == null) return null;

            int min = toInt(m.get("minAmount"), 1);
            int max = toInt(m.get("maxAmount"), min);
            entries.add(new Reward.Entry(mat, min, max));
        }

        Reward r = new Reward(id, rarity, weight, display, message, entries);
        if (!r.isValid()) {
            plugin.getLogger().warning("RewardService: reward '" + id + "' nije validan (prazan items)");
        }
        return r;
    }

    private void tickGiveRewards() {
        if (rewards.isEmpty()) return;

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) return;

        if (giveToAllOnline) {
            for (Player p : online) {
                giveOne(p);
            }
        } else {
            Player[] arr = online.toArray(new Player[0]);
            Player pick = arr[rng.nextInt(arr.length)];
            giveOne(pick);
        }
    }

    private void giveOne(Player player) {
        Reward reward = selectWeighted();
        if (reward == null) return;

        List<ItemStack> items = reward.rollItems(rng);
        if (items.isEmpty()) return;

        for (ItemStack it : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        // Broadcast poruka svima
        broadcastReward(player, reward, items);

        // Vatromet oko dobitnika
        if (fireworksEnabled) {
            int count = plugin.getConfig().getInt(
                    "rewards.fireworks.byRarity." + reward.rarity.name().toLowerCase(Locale.ROOT),
                    reward.rarity.defaultFireworks()
            );
            spawnFireworksBurst(player, reward.rarity, count);
        }
    }

    private void broadcastReward(Player player, Reward reward, List<ItemStack> items) {
        String prefix = messages.getPrefix();

        String rewardName = reward.defaultDisplay();
        String prettyItems = summarizeItems(items);
        String shown = (reward.display != null && !reward.display.isBlank()) ? reward.display : prettyItems;

        String tpl;
        if (reward.message != null && !reward.message.isBlank()) {
            tpl = reward.message;
        } else {
            tpl = defaultMessageTemplate(reward.rarity);
        }

        String msg = prefix + tpl
                .replace("%player%", player.getName())
                .replace("%rarity%", reward.rarity.name())
                .replace("%reward%", shown)
                .replace("%rewardName%", rewardName)
                .replace("%items%", prettyItems);

        Bukkit.getServer().broadcastMessage(messages.colorize(msg));
    }

    private String defaultMessageTemplate(RewardRarity rarity) {
        return switch (rarity) {
            case COMMON -> "&7[&f🎁&7] &f%player% &7dobija &f%items%&7.";
            case UNCOMMON -> "&7[&a🎁&7] &a%player% &7dobija &a%items%&7.";
            case RARE -> "&7[&b✨&7] &b%player% &7je dobio &b%items%&7!";
            case EPIC -> "&7[&6⚡&7] &6&lEPIC! &e%player% &7je osvojio &6%items%&7!";
            case LEGENDARY -> "&7[&d⭐&7] &d&lLEGENDARY! &f%player% &7je dobio &d%items%&7!";
            case MYTHIC -> "&7[&5💎&7] &5&lMYTHIC DROP! &f%player% &7je dobio &5&l%items%&7!!!";
        };
    }

    private String summarizeItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return "";
        if (items.size() == 1) {
            ItemStack it = items.get(0);
            String name = prettifyMaterial(it.getType());
            int amt = it.getAmount();
            return amt > 1 ? (name + " &7x&f" + amt) : name;
        }
        // kit
        String display = plugin.getConfig().getString("rewards.kitDisplayFallback", "&fKit");
        return display;
    }

    private String prettifyMaterial(Material mat) {
        String name = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return "&f" + sb.toString().trim();
    }

    private Reward selectWeighted() {
        int total = 0;
        for (Reward r : rewards) total += Math.max(1, r.weight);
        if (total <= 0) return null;

        int pick = rng.nextInt(total);
        int acc = 0;
        for (Reward r : rewards) {
            acc += Math.max(1, r.weight);
            if (pick < acc) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    private void spawnFireworksBurst(Player player, RewardRarity rarity, int count) {
        if (count <= 0) return;

        // da ne ubijemo server, max cap
        int capped = Math.min(count, 60);

        World w = player.getWorld();
        Location base = player.getLocation();

        Color main = switch (rarity) {
            case COMMON -> Color.WHITE;
            case UNCOMMON -> Color.LIME;
            case RARE -> Color.AQUA;
            case EPIC -> Color.ORANGE;
            case LEGENDARY -> Color.FUCHSIA;
            case MYTHIC -> Color.PURPLE;
        };
        Color fade = switch (rarity) {
            case COMMON -> Color.SILVER;
            case UNCOMMON -> Color.GREEN;
            case RARE -> Color.BLUE;
            case EPIC -> Color.YELLOW;
            case LEGENDARY -> Color.WHITE;
            case MYTHIC -> Color.BLACK;
        };

        int power = switch (rarity) {
            case COMMON, UNCOMMON -> 1;
            case RARE, EPIC -> 1;
            case LEGENDARY, MYTHIC -> 2;
        };

        // rasporedimo u par tickova da ne spawnuje sve odjednom
        int perTick = Math.max(3, capped / 4);
        int ticks = (int) Math.ceil(capped / (double) perTick);

        new BukkitRunnable() {
            int left = capped;
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline() || left <= 0 || t > ticks) {
                    cancel();
                    return;
                }
                int n = Math.min(perTick, left);
                for (int i = 0; i < n; i++) {
                    double angle = rng.nextDouble() * Math.PI * 2;
                    double r = 1.0 + rng.nextDouble() * fireworkRadius;
                    Location loc = base.clone().add(Math.cos(angle) * r, 0.5 + rng.nextDouble() * 1.2, Math.sin(angle) * r);

                    Firework fw = w.spawn(loc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.setPower(power);
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(main)
                            .withFade(fade)
                            .flicker(true)
                            .trail(true)
                            .build());
                    fw.setFireworkMeta(meta);
                }
                left -= n;
                t++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private static int toInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Material material(String s) {
        if (s == null || s.isBlank()) return null;
        return Material.getMaterial(s.trim().toUpperCase(Locale.ROOT));
    }
}