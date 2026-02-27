package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.ClaimService;
import me.chinq.teamsurvival.service.MessageService;
import me.chinq.teamsurvival.service.Settings;
import me.chinq.teamsurvival.service.TeamService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClaimEnterListener implements Listener {

    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;
    private final ClaimService claims;

    // uuid -> last owner teamId (null = wilderness)
    private final Map<UUID, String> lastOwnerByPlayer = new HashMap<>();

    public ClaimEnterListener(Settings settings, MessageService msg, TeamService teamService, ClaimService claims) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        // samo kad promeni chunk (da ne radi svake sekunde)
        if (sameChunk(e.getFrom(), e.getTo())) return;
        handle(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        handle(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastOwnerByPlayer.remove(e.getPlayer().getUniqueId());
    }

    private boolean sameChunk(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return (a.getBlockX() >> 4) == (b.getBlockX() >> 4) && (a.getBlockZ() >> 4) == (b.getBlockZ() >> 4);
    }

    private void handle(Player p, Location to) {
        if (!claims.isEnabled()) return;
        if (to.getWorld() == null) return;
        if (!settings.isWorldAllowed(to.getWorld().getName())) return;

        Chunk c = to.getChunk();
        String newOwnerTeamId = claims.getOwnerTeamId(c); // null = wilderness

        UUID u = p.getUniqueId();
        String oldOwnerTeamId = lastOwnerByPlayer.get(u);

        // nema promene ownera -> ne spamujemo
        if (equalsNullable(oldOwnerTeamId, newOwnerTeamId)) return;

        lastOwnerByPlayer.put(u, newOwnerTeamId);

        Team myTeam = teamService.getTeamOf(u);

        if (newOwnerTeamId == null) {
            // ušao u wilderness
            String raw = msg.get("claims.enterWilderness");
            if (raw != null && !raw.isEmpty()) sendActionBar(p, msg.colorize(raw));
            return;
        }

        // ušao u claim nekog tima
        if (myTeam != null && newOwnerTeamId.equals(myTeam.getId())) {
            String raw = msg.get("claims.enterYours");
            if (raw != null && !raw.isEmpty()) sendActionBar(p, msg.colorize(raw));
            return;
        }

        Team owner = teamService.getTeamById(newOwnerTeamId);
        String ownerName = (owner != null) ? owner.getName() : newOwnerTeamId;

        String tpl = msg.get("claims.enterOther");
        if (tpl != null && !tpl.isEmpty()) {
            String built = msg.applyPlaceholders(tpl, Map.of("team", ownerName));
            sendActionBar(p, msg.colorize(built));
        }
    }

    private boolean equalsNullable(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void sendActionBar(Player p, String coloredText) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredText));
    }
}