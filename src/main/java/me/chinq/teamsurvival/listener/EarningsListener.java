package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.service.EarningsService;
import me.chinq.teamsurvival.service.EarningsService.BlockKey;
import me.chinq.teamsurvival.service.EarningsService.CategoryKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public final class EarningsListener implements Listener {

    private final EarningsService eco;

    public EarningsListener(EarningsService eco) {
        this.eco = eco;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();

        // mark placed (anti-farm)
        eco.placedBlocks().markPlaced(new BlockKey(
                b.getWorld().getName(), b.getX(), b.getY(), b.getZ()
        ));

        double base = eco.buildingCoins(b.getType());
        if (base <= 0) return;

        eco.award(p, CategoryKey.BUILDING, base, "build");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        // FARMING: zreli crop check
        if (b.getBlockData() instanceof Ageable age) {
            if (age.getAge() >= age.getMaximumAge()) {
                double base = eco.farmingMatureCoins(b.getType());
                if (base > 0) eco.award(p, CategoryKey.FARMING, base, "farm");
                return;
            }
        }

        // MINING / BREAKING
        double base = eco.miningCoins(b.getType());
        if (base <= 0) return;

        // anti-farm: if placed recently -> multiply
        boolean placed = eco.placedBlocks().wasPlacedRecently(new BlockKey(
                b.getWorld().getName(), b.getX(), b.getY(), b.getZ()
        ));
        if (placed) base = base * eco.placedBlocks().placedFactor();

        if (base <= 0) return;
        eco.award(p, CategoryKey.MINING, base, "mine");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        double base = eco.huntingCoins(e.getEntityType());
        if (base <= 0) return;

        eco.award(killer, CategoryKey.HUNTING, base, "hunt");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        double base = eco.fishingCoins();
        if (base <= 0) return;

        eco.award(p, CategoryKey.FISHING, base, "fish");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;

        double base = eco.craftingCoins(it.getType());
        if (base <= 0) return;

        // mala zaštita: craft može da “spamma”
        // nagrađujemo samo final craft (shift-click i sl. opet prolazi, ali diminishing će ga ubiti)
        eco.award(p, CategoryKey.CRAFTING, base, "craft");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmeltTake(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        double basePer = eco.smeltingCoins(e.getItemType());
        if (basePer <= 0) return;

        double base = basePer * Math.max(1, e.getItemAmount());
        eco.award(p, CategoryKey.SMELTING, base, "smelt");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom() == null || e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        eco.onExploreMove(e.getPlayer(), e.getFrom(), e.getTo());
    }
}