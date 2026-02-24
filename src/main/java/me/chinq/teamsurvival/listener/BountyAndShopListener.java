package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.BountyService;
import me.chinq.teamsurvival.service.ShopService;
import me.chinq.teamsurvival.service.TeamService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class BountyAndShopListener implements Listener {

    private final TeamService teamService;
    private final BountyService bountyService;
    private final ShopService shopService;

    public BountyAndShopListener(TeamService teamService, BountyService bountyService, ShopService shopService) {
        this.teamService = teamService;
        this.bountyService = bountyService;
        this.shopService = shopService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // 1) Always drop tracked head
        e.getDrops().add(createTrackedHead(victim));

        // 2) Claim bounty if exists
        if (killer != null) {
            Team kt = teamService.getTeamOf(killer.getUniqueId());
            Team vt = teamService.getTeamOf(victim.getUniqueId());
            if (kt != null && (vt == null || !vt.getId().equals(kt.getId()))) {
                long claimed = bountyService.claimBountyIfPresent(victim, killer);
                if (claimed > 0) {
                    killer.sendMessage(ChatColor.GREEN + "Uzeo si bounty: " + ChatColor.GOLD + claimed + ChatColor.GREEN + " coins!");
                }
            }
        }

        // refresh victim name (ako je bounty skinut)
        bountyService.applyBountyName(victim);
    }

    private ItemStack createTrackedHead(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(victim);
        meta.setDisplayName(ChatColor.RED + "Glava: " + ChatColor.WHITE + victim.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Prodaj u /tim shop",
                ChatColor.DARK_GRAY + "Vrednost zavisi od coins tima žrtve."
        ));

        meta.getPersistentDataContainer().set(
                shopService.headOwnerKey(),
                PersistentDataType.STRING,
                victim.getUniqueId().toString()
        );

        head.setItemMeta(meta);
        return head;
    }

    // ===== SHOP GUI =====

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        shopService.handleClick(e);
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        shopService.handleDrag(e);
    }
}