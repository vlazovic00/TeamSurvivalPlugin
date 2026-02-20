package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.model.HomeLocation;
import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.CombatTagService;
import me.chinq.teamsurvival.service.MessageService;
import me.chinq.teamsurvival.service.Settings;
import me.chinq.teamsurvival.service.TeamService;
import me.chinq.teamsurvival.service.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class GameplayListener implements Listener {

    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;
    private final TeleportService teleports;
    private final CombatTagService combat;

    public GameplayListener(Settings settings, MessageService msg, TeamService teamService, TeleportService teleports, CombatTagService combat) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
        this.teleports = teleports;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (settings.friendlyFireEnabled) return;
        if (e.isCancelled()) return;

        Player victim = (e.getEntity() instanceof Player p) ? p : null;
        if (victim == null) return;

        Player attacker = findResponsiblePlayer(e.getDamager());
        if (attacker == null) return;

        Team tv = teamService.getTeamOf(victim.getUniqueId());
        Team ta = teamService.getTeamOf(attacker.getUniqueId());
        if (tv == null || ta == null) return;

        if (!tv.getId().equals(ta.getId())) return;

        // strict explosions: TNTPrimed with player source
        if (settings.friendlyFireStrictExplosions) {
            if (e.getDamager() instanceof TNTPrimed tnt) {
                Entity src = tnt.getSource();
                if (src instanceof Player srcPlayer) {
                    // same team already verified
                    e.setCancelled(true);
                    msg.sendPrefixed(attacker, "friendlyFire.blocked");
                    return;
                }
            }
        }

        // normal FF block
        e.setCancelled(true);
        msg.sendPrefixed(attacker, "friendlyFire.blocked");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageVictimTag(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        combat.tag(p.getUniqueId());
        // cancel warmup on ANY damage during warmup
        teleports.cancelWarmup(p.getUniqueId(), TeleportService.CancelReason.DAMAGE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageAttackerTag(EntityDamageByEntityEvent e) {
        Player attacker = findResponsiblePlayer(e.getDamager());
        if (attacker == null) return;

        combat.tag(attacker.getUniqueId());
        // if attacker enters combat while warming up, cancel too
        teleports.cancelWarmup(attacker.getUniqueId(), TeleportService.CancelReason.COMBAT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        teleports.handleMove(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        teleports.cancelWarmup(e.getPlayer().getUniqueId(), TeleportService.CancelReason.QUIT);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!settings.respawnToTeamHome) return;

        UUID u = e.getPlayer().getUniqueId();
        Team team = teamService.getTeamOf(u);
        if (team == null) return;

        HomeLocation home = team.getHome();
        if (home == null) return;

        World w = Bukkit.getWorld(home.world());
        if (w == null) return;
        if (!settings.isWorldAllowed(w.getName())) return;

        Location dest = home.toBukkitLocation(w);
        Location safe = makeBasicSafe(dest);
        e.setRespawnLocation(safe);
    }

    private Location makeBasicSafe(Location l) {
        Location out = l.clone();

        // if solid block at respawn, go up 1
        if (out.getBlock().getType().isSolid()) {
            out.add(0, 1, 0);
        }

        // if block below is lava, go up 2
        Material below = out.clone().add(0, -1, 0).getBlock().getType();
        if (below == Material.LAVA) {
            out.add(0, 2, 0);
        }

        return out;
    }

    private Player findResponsiblePlayer(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof Player p) return p;
        }

        if (damager instanceof TNTPrimed tnt) {
            Entity src = tnt.getSource();
            if (src instanceof Player p) return p;
        }

        return null;
    }
}
