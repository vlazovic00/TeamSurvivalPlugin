package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.service.SupplyDropService;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

public final class SupplyDropListener implements Listener {

    private final SupplyDropService supplyDropService;

    public SupplyDropListener(SupplyDropService supplyDropService) {
        this.supplyDropService = supplyDropService;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;

        if (!supplyDropService.isSupplyChest(chest.getBlock())) return;

        if (e.getPlayer() instanceof Player p) {
            supplyDropService.onChestOpened(chest, p);
        }
    }
}