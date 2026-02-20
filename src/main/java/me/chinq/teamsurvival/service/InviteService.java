package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Invite;
import me.chinq.teamsurvival.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InviteService {

    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;

    // target -> invite
    private final Map<UUID, Invite> invites = new HashMap<>();

    public InviteService(Settings settings, MessageService msg, TeamService teamService) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
    }

    public void sendInvite(Player inviter, Player target) {
        Team team = teamService.getTeamOf(inviter.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(inviter, "team.notInTeam");
            return;
        }
        if (!team.isLeader(inviter.getUniqueId())) {
            msg.sendPrefixed(inviter, "team.notLeader");
            return;
        }
        if (team.size() >= settings.maxTeamSize) {
            msg.sendPrefixed(inviter, "team.full", Map.of("max", String.valueOf(settings.maxTeamSize)));
            return;
        }
        if (teamService.getTeamOf(target.getUniqueId()) != null) {
            msg.sendPrefixed(inviter, "invite.targetAlreadyInTeam", Map.of("player", target.getName()));
            return;
        }

        long now = System.currentTimeMillis();
        Invite existing = invites.get(target.getUniqueId());
        if (existing != null && !existing.isExpired(now)) {
            msg.sendPrefixed(inviter, "invite.targetAlreadyInvited", Map.of("player", target.getName()));
            return;
        }

        Invite invite = new Invite(
                team.getId(),
                inviter.getUniqueId(),
                now,
                now + settings.inviteExpireSeconds * 1000L
        );
        invites.put(target.getUniqueId(), invite);

        msg.sendPrefixed(inviter, "invite.sent", Map.of(
                "player", target.getName(),
                "seconds", String.valueOf(settings.inviteExpireSeconds)
        ));
        msg.sendPrefixed(target, "invite.received", Map.of(
                "team", team.getName(),
                "leader", inviter.getName(),
                "seconds", String.valueOf(settings.inviteExpireSeconds)
        ));
    }

    public void acceptInvite(Player target) {
        Invite inv = invites.get(target.getUniqueId());
        if (inv == null) {
            msg.sendPrefixed(target, "invite.none");
            return;
        }
        long now = System.currentTimeMillis();
        if (inv.isExpired(now)) {
            invites.remove(target.getUniqueId());
            msg.sendPrefixed(target, "invite.expired");
            return;
        }
        Team team = teamService.getTeamById(inv.teamId());
        if (team == null) {
            invites.remove(target.getUniqueId());
            msg.sendPrefixed(target, "invite.teamMissing");
            return;
        }
        if (team.size() >= settings.maxTeamSize) {
            invites.remove(target.getUniqueId());
            msg.sendPrefixed(target, "team.full", Map.of("max", String.valueOf(settings.maxTeamSize)));
            return;
        }
        if (teamService.getTeamOf(target.getUniqueId()) != null) {
            invites.remove(target.getUniqueId());
            msg.sendPrefixed(target, "team.alreadyInTeam");
            return;
        }

        boolean ok = teamService.addMember(team.getId(), target.getUniqueId());
        invites.remove(target.getUniqueId());

        if (!ok) {
            msg.sendPrefixed(target, "errors.generic");
            return;
        }

        msg.sendPrefixed(target, "invite.accepted", Map.of("team", team.getName()));

        Player inviterOnline = Bukkit.getPlayer(inv.inviter());
        if (inviterOnline != null) {
            msg.sendPrefixed(inviterOnline, "invite.acceptedByTarget", Map.of(
                    "player", target.getName(),
                    "team", team.getName()
            ));
        }
    }

    public void declineInvite(Player target) {
        Invite inv = invites.remove(target.getUniqueId());
        if (inv == null) {
            msg.sendPrefixed(target, "invite.none");
            return;
        }

        msg.sendPrefixed(target, "invite.declined");

        Player inviterOnline = Bukkit.getPlayer(inv.inviter());
        if (inviterOnline != null) {
            msg.sendPrefixed(inviterOnline, "invite.declinedByTarget", Map.of("player", target.getName()));
        }
    }
}
