package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.model.TpaRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TpaService {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;
    private final TeleportService teleports;
    private final CombatTagService combat;

    // target -> request
    private final Map<UUID, TpaRequest> requests = new HashMap<>();

    // requester -> cooldownUntil
    private final Map<UUID, Long> requesterCooldownUntil = new HashMap<>();

    public TpaService(org.bukkit.plugin.java.JavaPlugin plugin,
                      Settings settings,
                      MessageService msg,
                      TeamService teamService,
                      TeleportService teleports,
                      CombatTagService combat) {
        this.plugin = plugin;
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
        this.teleports = teleports;
        this.combat = combat;
    }

    public void request(Player requester, Player target) {
        Team tr = teamService.getTeamOf(requester.getUniqueId());
        Team tt = teamService.getTeamOf(target.getUniqueId());

        if (tr == null || tt == null || !tr.getId().equals(tt.getId())) {
            msg.sendPrefixed(requester, "tpa.notSameTeam");
            return;
        }

        long now = System.currentTimeMillis();
        long cdUntil = requesterCooldownUntil.getOrDefault(requester.getUniqueId(), 0L);
        long rem = cdUntil - now;
        if (rem > 0) {
            long sec = (rem + 999) / 1000;
            msg.sendPrefixed(requester, "cooldown.tpa", Map.of("seconds", String.valueOf(sec)));
            return;
        }

        TpaRequest existing = requests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired(now)) {
            msg.sendPrefixed(requester, "tpa.targetAlreadyHasRequest", Map.of("player", target.getName()));
            return;
        }

        TpaRequest req = new TpaRequest(
                requester.getUniqueId(),
                target.getUniqueId(),
                now,
                now + settings.tpaExpireSeconds * 1000L
        );
        requests.put(target.getUniqueId(), req);

        msg.sendPrefixed(requester, "tpa.sent", Map.of("player", target.getName(), "seconds", String.valueOf(settings.tpaExpireSeconds)));
        msg.sendPrefixed(target, "tpa.received", Map.of("player", requester.getName(), "seconds", String.valueOf(settings.tpaExpireSeconds)));
    }

    public void accept(Player target) {
        TpaRequest req = requests.get(target.getUniqueId());
        if (req == null) {
            msg.sendPrefixed(target, "tpa.none");
            return;
        }

        long now = System.currentTimeMillis();
        if (req.isExpired(now)) {
            requests.remove(target.getUniqueId());
            msg.sendPrefixed(target, "tpa.expired");
            return;
        }

        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null) {
            requests.remove(target.getUniqueId());
            msg.sendPrefixed(target, "tpa.requesterOffline");
            return;
        }

        Team tr = teamService.getTeamOf(requester.getUniqueId());
        Team tt = teamService.getTeamOf(target.getUniqueId());
        if (tr == null || tt == null || !tr.getId().equals(tt.getId())) {
            requests.remove(target.getUniqueId());
            msg.sendPrefixed(target, "tpa.notSameTeam");
            msg.sendPrefixed(requester, "tpa.notSameTeam");
            return;
        }

        if (combat.isTagged(requester.getUniqueId())) {
            msg.sendPrefixed(target, "combat.blockTeleportOther", Map.of(
                    "player", requester.getName(),
                    "seconds", String.valueOf(combat.remainingSeconds(requester.getUniqueId()))
            ));
            msg.sendPrefixed(requester, "combat.blockTeleport", Map.of("seconds", String.valueOf(combat.remainingSeconds(requester.getUniqueId()))));
            return;
        }

        if (teleports.isInWarmup(requester.getUniqueId())) {
            msg.sendPrefixed(target, "warmup.otherAlready", Map.of("player", requester.getName()));
            return;
        }

        requests.remove(target.getUniqueId());

        msg.sendPrefixed(target, "warmup.startTpaTarget", Map.of("player", requester.getName(), "seconds", String.valueOf(settings.tpaWarmupSeconds)));
        msg.sendPrefixed(requester, "warmup.startTpaRequester", Map.of("seconds", String.valueOf(settings.tpaWarmupSeconds)));

        teleports.startWarmup(
                requester,
                settings.tpaWarmupSeconds,
                () -> {
                    if (!requester.isOnline() || !target.isOnline()) return;

                    // re-check team + combat at the end
                    Team tr2 = teamService.getTeamOf(requester.getUniqueId());
                    Team tt2 = teamService.getTeamOf(target.getUniqueId());
                    if (tr2 == null || tt2 == null || !tr2.getId().equals(tt2.getId())) {
                        msg.sendPrefixed(requester, "tpa.notSameTeam");
                        return;
                    }
                    if (combat.isTagged(requester.getUniqueId())) {
                        msg.sendPrefixed(requester, "warmup.cancelCombat");
                        return;
                    }

                    Location dest = target.getLocation();
                    requester.teleport(dest);

                    requesterCooldownUntil.put(
                            requester.getUniqueId(),
                            System.currentTimeMillis() + settings.tpaCooldownSeconds * 1000L
                    );

                    msg.sendPrefixed(requester, "tpa.teleported", Map.of("player", target.getName(), "cooldown", String.valueOf(settings.tpaCooldownSeconds)));
                    msg.sendPrefixed(target, "tpa.teleportedOther", Map.of("player", requester.getName()));
                },
                reason -> {
                    switch (reason) {
                        case MOVE -> msg.sendPrefixed(requester, "warmup.cancelMove");
                        case DAMAGE -> msg.sendPrefixed(requester, "warmup.cancelDamage");
                        case COMBAT -> msg.sendPrefixed(requester, "warmup.cancelCombat");
                        default -> { /* silent */ }
                    }
                }
        );
    }

    public void deny(Player target) {
        TpaRequest req = requests.remove(target.getUniqueId());
        if (req == null) {
            msg.sendPrefixed(target, "tpa.none");
            return;
        }

        Player requester = Bukkit.getPlayer(req.requester());
        msg.sendPrefixed(target, "tpa.denied");

        if (requester != null) {
            msg.sendPrefixed(requester, "tpa.deniedByTarget", Map.of("player", target.getName()));
        }
    }
}
