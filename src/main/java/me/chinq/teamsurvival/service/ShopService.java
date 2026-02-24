package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Tim Shop (team coins):
 * - GUI main menu + categories from shop.yml
 * - buy items with TEAM coins (coins are stored on Team)
 * - sell tracked player heads (only if player is in a team)
 *
 * Security:
 * - We use custom InventoryHolder + PDC action keys => no title spoof / item spoof.
 * - We always re-check player's team + coins on click.
 */
public final class ShopService {

    // ===== HEAD SELL (existing behavior) =====
    // Vrednost glave = max(1, min(HEAD_MAX_VALUE, victimTeamCoins * HEAD_VALUE_PERCENT))
    public static final double HEAD_VALUE_PERCENT = 0.05; // 5%
    public static final long HEAD_MAX_VALUE = 50_000;

    // ===== GUI keys =====
    private enum Action { OPEN_CATEGORY, BUY_ITEM, SELL_HEADS, BACK_MAIN, CLOSE }

    private static final String DEFAULT_MAIN_TITLE = "&8Tim Shop";

    private final JavaPlugin plugin;
    private final TeamService teamService;
    private final MessageService msg;

    private final NamespacedKey headOwnerKey;

    private final NamespacedKey guiActionKey;
    private final NamespacedKey guiCatKey;
    private final NamespacedKey guiItemKey;

    // ===== shop.yml config cached =====
    private String mainTitleColored = ChatColor.DARK_GRAY + "Tim Shop";
    private int mainSize = 27;

    private final Map<String, Category> categories = new LinkedHashMap<>();

    private static final class Category {
        final String id;
        final String nameColored;
        final Material icon;
        final int slot;
        final int size;
        final Map<String, ShopEntry> entriesById = new LinkedHashMap<>();
        final Map<Integer, String> entryIdBySlot = new HashMap<>();

        Category(String id, String nameColored, Material icon, int slot, int size) {
            this.id = id;
            this.nameColored = nameColored;
            this.icon = icon;
            this.slot = slot;
            this.size = size;
        }
    }

    private static final class ShopEntry {
        final String id;
        final int slot;
        final ItemStack displayItem;
        final long price;

        ShopEntry(String id, int slot, ItemStack displayItem, long price) {
            this.id = id;
            this.slot = slot;
            this.displayItem = displayItem;
            this.price = Math.max(0L, price);
        }

        ItemStack buyItem() {
            return displayItem.clone();
        }
    }

    private static final class ShopHolder implements InventoryHolder {
        final String page; // MAIN or CAT
        final String catId; // nullable
        ShopHolder(String page, String catId) { this.page = page; this.catId = catId; }
        @Override public Inventory getInventory() { return null; } // not used
    }

    public ShopService(JavaPlugin plugin, TeamService teamService, MessageService msg) {
        this.plugin = plugin;
        this.teamService = teamService;
        this.msg = msg;

        this.headOwnerKey = new NamespacedKey(plugin, "ts_head_owner");

        this.guiActionKey = new NamespacedKey(plugin, "ts_shop_action");
        this.guiCatKey = new NamespacedKey(plugin, "ts_shop_cat");
        this.guiItemKey = new NamespacedKey(plugin, "ts_shop_item");

        ensureDefaultShopFile();
        reloadShopConfig();
    }

    // ================== Public API ==================

    public NamespacedKey headOwnerKey() { return headOwnerKey; }

    public void reloadShopConfig() {
        categories.clear();

        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String titleRaw = cfg.getString("shop.title", DEFAULT_MAIN_TITLE);
        this.mainTitleColored = color(titleRaw);

        int size = cfg.getInt("shop.main-size", 27);
        this.mainSize = clampInvSize(size);

        ConfigurationSection cats = cfg.getConfigurationSection("shop.categories");
        if (cats == null) return;

        for (String catId : cats.getKeys(false)) {
            ConfigurationSection c = cats.getConfigurationSection(catId);
            if (c == null) continue;

            String name = color(c.getString("name", "&f" + catId));
            Material icon = parseMaterial(c.getString("icon", "CHEST"), Material.CHEST);
            int slot = c.getInt("slot", 10);

            int catSize = clampInvSize(c.getInt("size", 54));

            Category cat = new Category(catId, name, icon, slot, catSize);

            ConfigurationSection items = c.getConfigurationSection("items");
            if (items != null) {
                for (String itemId : items.getKeys(false)) {
                    ConfigurationSection it = items.getConfigurationSection(itemId);
                    if (it == null) continue;

                    int itemSlot = it.getInt("slot", 10);
                    Material mat = parseMaterial(it.getString("material", "STONE"), Material.STONE);
                    int amount = Math.max(1, it.getInt("amount", 1));
                    long price = Math.max(0L, it.getLong("price", 0L));

                    ItemStack stack = new ItemStack(mat, amount);
                    ItemMeta meta = stack.getItemMeta();

                    String displayName = it.getString("name", null);
                    if (displayName != null) meta.setDisplayName(color(displayName));

                    List<String> loreRaw = it.getStringList("lore");
                    if (loreRaw != null && !loreRaw.isEmpty()) {
                        List<String> lore = new ArrayList<>();
                        for (String lr : loreRaw) {
                            lore.add(color(lr.replace("%price%", String.valueOf(price))));
                        }
                        meta.setLore(lore);
                    } else {
                        meta.setLore(List.of(
                                color("&7Cena: &6" + price),
                                color("&8Plaća se iz team coins")
                        ));
                    }

                    // enchants: ["SHARPNESS:5","UNBREAKING:3"]
                    for (String encRaw : it.getStringList("enchants")) {
                        try {
                            String[] parts = encRaw.split(":");
                            if (parts.length != 2) continue;
                            Enchantment ench = Enchantment.getByName(parts[0].toUpperCase(Locale.ROOT));
                            int lvl = Integer.parseInt(parts[1]);
                            if (ench != null && lvl > 0) meta.addEnchant(ench, lvl, true);
                        } catch (Exception ignored) {}
                    }

                    // flags
                    for (String flagRaw : it.getStringList("flags")) {
                        try {
                            ItemFlag f = ItemFlag.valueOf(flagRaw.toUpperCase(Locale.ROOT));
                            meta.addItemFlags(f);
                        } catch (Exception ignored) {}
                    }

                    // tag as BUY item
                    meta.getPersistentDataContainer().set(guiActionKey, PersistentDataType.STRING, Action.BUY_ITEM.name());
                    meta.getPersistentDataContainer().set(guiCatKey, PersistentDataType.STRING, catId);
                    meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.STRING, itemId);

                    stack.setItemMeta(meta);

                    ShopEntry entry = new ShopEntry(itemId, itemSlot, stack, price);
                    cat.entriesById.put(itemId, entry);
                    cat.entryIdBySlot.put(itemSlot, itemId);
                }
            }

            categories.put(catId, cat);
        }
    }

    /** /tim shop */
    public void openShop(Player p) {
        if (p == null) return;

        Team t = teamService.getTeamOf(p.getUniqueId());
        if (t == null) {
            msg.sendPrefixed(p, "errors.noTeam");
            return;
        }

        Inventory inv = Bukkit.createInventory(new ShopHolder("MAIN", null), mainSize, mainTitleColored);

        // categories
        for (Category c : categories.values()) {
            ItemStack icon = new ItemStack(c.icon);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(c.nameColored);
            meta.setLore(List.of(color("&7Otvori kategoriju"), color("&8Klikni")));
            meta.getPersistentDataContainer().set(guiActionKey, PersistentDataType.STRING, Action.OPEN_CATEGORY.name());
            meta.getPersistentDataContainer().set(guiCatKey, PersistentDataType.STRING, c.id);
            icon.setItemMeta(meta);

            if (c.slot >= 0 && c.slot < inv.getSize()) inv.setItem(c.slot, icon);
        }

        // coin info
        int infoSlot = Math.min(4, inv.getSize() - 1);
        ItemStack info = new ItemStack(Material.SUNFLOWER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(color("&eTeam Coins"));
        im.setLore(List.of(
                color("&7Tvoj tim: &f" + t.getName()),
                color("&7Coins: &6" + t.getCoins()),
                color("&8Coins su zajednički za tim")
        ));
        info.setItemMeta(im);
        inv.setItem(infoSlot, info);

        // sell heads button
        int sellSlot = Math.min(13, inv.getSize() - 1);
        ItemStack sell = new ItemStack(Material.EMERALD);
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(color("&aProdaj sve glave"));
        sm.setLore(List.of(
                color("&7Klikni da prodaš sve"),
                color("&7glave iz inventory."),
                color("&8Vrednost zavisi od coins tima žrtve.")
        ));
        sm.getPersistentDataContainer().set(guiActionKey, PersistentDataType.STRING, Action.SELL_HEADS.name());
        sell.setItemMeta(sm);
        inv.setItem(sellSlot, sell);

        // close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(color("&cZatvori"));
        cm.getPersistentDataContainer().set(guiActionKey, PersistentDataType.STRING, Action.CLOSE.name());
        close.setItemMeta(cm);
        inv.setItem(inv.getSize() - 1, close);

        p.openInventory(inv);
    }

    public void openCategory(Player p, String catId) {
        if (p == null) return;

        Team t = teamService.getTeamOf(p.getUniqueId());
        if (t == null) {
            msg.sendPrefixed(p, "errors.noTeam");
            return;
        }

        Category cat = categories.get(catId);
        if (cat == null) {
            p.sendMessage(color("&cNepoznata kategorija."));
            return;
        }

        String title = mainTitleColored + ChatColor.DARK_GRAY + " » " + cat.nameColored;
        Inventory inv = Bukkit.createInventory(new ShopHolder("CAT", catId), cat.size, title);

        for (ShopEntry e : cat.entriesById.values()) {
            if (e.slot >= 0 && e.slot < inv.getSize()) inv.setItem(e.slot, e.displayItem);
        }

        // back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(color("&fNazad"));
        bm.setLore(List.of(color("&7Povratak na meni")));
        bm.getPersistentDataContainer().set(guiActionKey, PersistentDataType.STRING, Action.BACK_MAIN.name());
        back.setItemMeta(bm);
        inv.setItem(inv.getSize() - 9, back);

        // coin info
        ItemStack info = new ItemStack(Material.SUNFLOWER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(color("&eTeam Coins"));
        im.setLore(List.of(
                color("&7Tvoj tim: &f" + t.getName()),
                color("&7Coins: &6" + t.getCoins())
        ));
        info.setItemMeta(im);
        inv.setItem(inv.getSize() - 5, info);

        p.openInventory(inv);
    }

    // ================== GUI event handlers ==================

    public void handleClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;

        Inventory top = e.getView().getTopInventory();
        if (top == null) return;
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ShopHolder sh)) return;

        if (e.getClickedInventory() == null) return;
        if (e.getRawSlot() < 0) return;

        // cancel clicks in top inventory always
        if (e.getRawSlot() < top.getSize()) {
            e.setCancelled(true);
        } else {
            if (e.isShiftClick()) e.setCancelled(true);
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String actRaw = meta.getPersistentDataContainer().get(guiActionKey, PersistentDataType.STRING);
        if (actRaw == null) return;

        Action act;
        try { act = Action.valueOf(actRaw); } catch (Exception ex) { return; }

        // hard requirement: must be in team
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "errors.noTeam");
            p.closeInventory();
            return;
        }

        switch (act) {
            case OPEN_CATEGORY -> {
                String catId = meta.getPersistentDataContainer().get(guiCatKey, PersistentDataType.STRING);
                if (catId == null) return;
                openCategory(p, catId);
            }
            case BACK_MAIN -> openShop(p);
            case CLOSE -> p.closeInventory();
            case SELL_HEADS -> {
                var res = sellAllHeads(p);
                if (res.soldHeads() <= 0) {
                    p.sendMessage(color("&cNema validnih glava za prodaju (ili timovi žrtava nemaju coins)."));
                } else {
                    p.sendMessage(color("&aProdao si &b" + res.soldHeads() + " &aglava za ukupno &6" + res.earnedCoins() + " &acoins!"));
                }
                p.closeInventory();
            }
            case BUY_ITEM -> {
                String catId = meta.getPersistentDataContainer().get(guiCatKey, PersistentDataType.STRING);
                String itemId = meta.getPersistentDataContainer().get(guiItemKey, PersistentDataType.STRING);
                if (catId == null || itemId == null) return;

                Category cat = categories.get(catId);
                if (cat == null) return;

                ShopEntry entry = cat.entriesById.get(itemId);
                if (entry == null) return;

                long price = entry.price;
                if (price <= 0) {
                    p.sendMessage(color("&cOvaj item nema cenu u shop.yml."));
                    return;
                }

                boolean paid = teamService.tryRemoveCoins(team, price);
                if (!paid) {
                    p.sendMessage(color("&cTvoj tim nema dovoljno coins (&6" + team.getCoins() + "&c). Cena: &6" + price));
                    return;
                }

                ItemStack toGive = entry.buyItem();
                Map<Integer, ItemStack> leftovers = p.getInventory().addItem(toGive);

                if (!leftovers.isEmpty()) {
                    teamService.addCoins(team, price);
                    p.sendMessage(color("&cNemaš mesta u inventory! Refund: &6" + price));
                    return;
                }

                p.sendMessage(color("&aKupljeno: &f" + niceName(toGive) + " &aza &6" + price + " &acoins (team)."));

                if ("CAT".equals(sh.page) && sh.catId != null) openCategory(p, sh.catId);
                else openShop(p);
            }
        }
    }

    public void handleDrag(InventoryDragEvent e) {
        if (e.getView() == null) return;
        Inventory top = e.getView().getTopInventory();
        if (top == null) return;
        if (!(top.getHolder() instanceof ShopHolder)) return;

        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ================== Head sell logic ==================

    /** Prodaje sve validne glave iz player inventory. */
    public SellResult sellAllHeads(Player seller) {
        if (seller == null) return new SellResult(0, 0);

        Team sellerTeam = teamService.getTeamOf(seller.getUniqueId());
        if (sellerTeam == null) {
            msg.sendPrefixed(seller, "errors.noTeam");
            return new SellResult(0, 0);
        }

        ItemStack[] contents = seller.getInventory().getContents();

        int soldCount = 0;
        long totalEarned = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isValidTrackedPlayerHead(it)) continue;

            UUID owner = getHeadOwner(it);
            if (owner == null) continue;

            Team victimTeam = teamService.getTeamOf(owner);
            if (victimTeam == null) continue;

            // ne prodaj glavu od svog tima (basic anti-abuse)
            if (victimTeam.getId().equals(sellerTeam.getId())) continue;

            long value = computeHeadValue(victimTeam);
            if (value <= 0) continue;

            boolean removed = teamService.tryRemoveCoins(victimTeam, value);
            if (!removed) continue;

            teamService.addCoins(sellerTeam, value);

            decrementOne(contents, i);

            soldCount++;
            totalEarned += value;
        }

        seller.getInventory().setContents(contents);

        return new SellResult(soldCount, totalEarned);
    }

    private void decrementOne(ItemStack[] contents, int idx) {
        ItemStack it = contents[idx];
        if (it == null) return;
        int amt = it.getAmount();
        if (amt <= 1) contents[idx] = null;
        else it.setAmount(amt - 1);
    }

    public boolean isValidTrackedPlayerHead(ItemStack it) {
        if (it == null) return false;
        if (it.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(headOwnerKey, PersistentDataType.STRING);
    }

    public UUID getHeadOwner(ItemStack it) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(headOwnerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (Exception ignored) { return null; }
    }

    public long computeHeadValue(Team victimTeam) {
        if (victimTeam == null) return 0;
        long coins = Math.max(0L, victimTeam.getCoins());
        if (coins <= 0) return 0;

        long v = (long) Math.floor(coins * HEAD_VALUE_PERCENT);
        v = Math.max(1, v);
        v = Math.min(HEAD_MAX_VALUE, v);
        return v;
    }

    public record SellResult(int soldHeads, long earnedCoins) {}

    // ================== helpers ==================

    private void ensureDefaultShopFile() {
        try {
            File f = new File(plugin.getDataFolder(), "shop.yml");
            if (!f.exists()) plugin.saveResource("shop.yml", false);
        } catch (Exception e) {
            plugin.getLogger().warning("Ne mogu da kreiram default shop.yml: " + e.getMessage());
        }
    }

    private static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static int clampInvSize(int size) {
        int s = Math.max(9, Math.min(54, size));
        return (s / 9) * 9;
    }

    private static Material parseMaterial(String raw, Material def) {
        if (raw == null) return def;
        try {
            Material m = Material.matchMaterial(raw.trim());
            return (m == null) ? def : m;
        } catch (Exception e) {
            return def;
        }
    }

    private static String niceName(ItemStack it) {
        if (it == null) return "Item";
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) return ChatColor.stripColor(m.getDisplayName());
        return it.getType().name();
    }
}