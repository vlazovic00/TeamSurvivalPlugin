package me.chinq.teamsurvival.listener;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.MessageService;
import me.chinq.teamsurvival.service.TeamChatService;
import me.chinq.teamsurvival.service.TeamService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChatListener implements Listener {

    private final MessageService msg;
    private final TeamService teamService;
    private final TeamChatService teamChat;

    public ChatListener(MessageService msg, TeamService teamService, TeamChatService teamChat) {
        this.msg = msg;
        this.teamService = teamService;
        this.teamChat = teamChat;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();

        if (!teamChat.isEnabled(u)) return;

        Team team = teamService.getTeamOf(u);
        if (team == null) {
            // auto-disable if player no longer in team
            teamChat.setEnabled(u, false);
            return;
        }

        e.setCancelled(true);

        String message = e.getMessage();
        Map<String, String> ph = new HashMap<>();
        ph.put("player", p.getName());
        ph.put("message", message);

        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("TeamSurvival"), () -> {
            for (UUID m : team.getMembers()) {
                Player pl = Bukkit.getPlayer(m);
                if (pl != null) msg.sendRaw(pl, msg.colorize(msg.applyPlaceholders(msg.get("chat.format"), ph)));
            }
        });
    }
}
