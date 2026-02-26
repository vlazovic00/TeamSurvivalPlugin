package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.service.ClaimService;
import me.chinq.teamsurvival.service.MessageService;
import me.chinq.teamsurvival.service.Settings;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ClaimProtectionListener implements Listener {

    private final Settings settings;
    private final MessageService msg;
    private final ClaimService claims;

    public ClaimProtectionListener(Settings settings, MessageService msg, ClaimService claims) {
        this.settings = settings;
        this.msg = msg;
        this.claims = claims;
    }

    private boolean isProtectedChunk(Block b) {
        if (b == null) return false;
        if (!claims.isEnabled()) return false;
        if (!settings.isWorldAllowed(b.getWorld().getName())) return false;
        return claims.isChunkClaimed(b.getChunk());
    }

    private boolean isOwner(Player p, Chunk c) {
        return claims.canBuild(p, c);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!claims.isEnabled()) return;
        if (!settings.claims.protectBlockBreak()) return;

        Block b = e.getBlock();
        if (!settings.isWorldAllowed(b.getWorld().getName())) return;

        Chunk c = b.getChunk();
        if (!claims.isChunkClaimed(c)) return;

        Player p = e.getPlayer();
        if (isOwner(p, c)) return;

        e.setCancelled(true);
        msg.sendPrefixed(p, "claims.noBreak");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!claims.isEnabled()) return;
        if (!settings.claims.protectBlockPlace()) return;

        Block b = e.getBlockPlaced();
        if (!settings.isWorldAllowed(b.getWorld().getName())) return;

        Chunk c = b.getChunk();
        if (!claims.isChunkClaimed(c)) return;

        Player p = e.getPlayer();
        if (isOwner(p, c)) return;

        e.setCancelled(true);
        msg.sendPrefixed(p, "claims.noPlace");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!claims.isEnabled()) return;

        Block b = e.getEntity().getLocation().getBlock();
        if (!settings.isWorldAllowed(b.getWorld().getName())) return;

        Chunk c = b.getChunk();
        if (!claims.isChunkClaimed(c)) return;

        Player attacker = null;
        if (e.getRemover() instanceof Player p) attacker = p;
        else if (e.getRemover() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;

        // if not a player, deny by default
        if (attacker == null || !isOwner(attacker, c)) {
            e.setCancelled(true);
            if (attacker != null) msg.sendPrefixed(attacker, "claims.noBreak");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!claims.isEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        if (e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!settings.isWorldAllowed(b.getWorld().getName())) return;

        Chunk c = b.getChunk();
        if (!claims.isChunkClaimed(c)) return;

        Player p = e.getPlayer();
        if (isOwner(p, c)) return;

        // allow “steal gameplay” if enabled and block is door/container
        if (settings.claims.foreignOpenEnabled() && isForeignOpenBlock(b)) return;

        e.setCancelled(true);
        msg.sendPrefixed(p, "claims.noInteract");
    }

    private boolean isForeignOpenBlock(Block b) {
        Material m = b.getType();
        String n = m.name();

        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE_GATE")) return true;

        BlockState st = b.getState();
        if (st instanceof Container) return true;

        return m == Material.LECTERN;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!claims.isEnabled()) return;
        if (!settings.claims.protectExplosions()) return;

        e.blockList().removeIf(this::isProtectedChunk);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!claims.isEnabled()) return;
        if (!settings.claims.protectExplosions()) return;

        e.blockList().removeIf(this::isProtectedChunk);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanGrief(EntityChangeBlockEvent e) {
        if (!claims.isEnabled()) return;
        if (!(e.getEntity() instanceof Enderman)) return;

        Block b = e.getBlock();
        if (!settings.isWorldAllowed(b.getWorld().getName())) return;

        if (!claims.isChunkClaimed(b.getChunk())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!claims.isEnabled()) return;

        for (Block moved : e.getBlocks()) {
            if (isProtectedChunk(moved)) {
                e.setCancelled(true);
                return;
            }
        }
        Block dest = e.getBlock().getRelative(e.getDirection(), e.getLength() + 1);
        if (isProtectedChunk(dest)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!claims.isEnabled()) return;

        for (Block moved : e.getBlocks()) {
            if (isProtectedChunk(moved)) {
                e.setCancelled(true);
                return;
            }
        }
        Block dest = e.getBlock().getRelative(e.getDirection());
        if (isProtectedChunk(dest)) e.setCancelled(true);
    }
}