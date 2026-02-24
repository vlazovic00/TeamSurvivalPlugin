package me.chinq.teamsurvival.command;

import me.chinq.teamsurvival.TeamSurvivalPlugin;
import me.chinq.teamsurvival.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class TimReloadCommand implements CommandExecutor {

    private final TeamSurvivalPlugin plugin;
    private final MessageService messages;

    public TimReloadCommand(TeamSurvivalPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("teamsurvival.reload")) {
            sender.sendMessage("§cNemaš dozvolu za ovu komandu.");
            return true;
        }

        plugin.reloadAllConfigs();
        sender.sendMessage("§aTeamSurvival config reloadovan.");
        return true;
    }
}