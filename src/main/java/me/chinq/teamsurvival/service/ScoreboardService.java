package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public final class ScoreboardService {

    private final Settings settings;
    private final TeamService teamService;
    private final PersistenceService persistence;

    public ScoreboardService(Settings settings, MessageService msg, TeamService teamService, PersistenceService persistence) {
        this.settings = settings;
        this.teamService = teamService;
        this.persistence = persistence;
    }

    public void resetAllTsTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String prefix = settings.scoreboard.teamPrefix();

        // copy to avoid concurrent mod
        List<org.bukkit.scoreboard.Team> toRemove = new ArrayList<>();
        for (org.bukkit.scoreboard.Team t : sb.getTeams()) {
            if (t.getName().startsWith(prefix)) toRemove.add(t);
        }
        for (org.bukkit.scoreboard.Team t : toRemove) {
            try { t.unregister(); } catch (Exception ignored) { }
        }
    }

    public void syncAll() {
        if (!settings.scoreboard.enabled()) return;

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String prefix = settings.scoreboard.teamPrefix();

        // Remove scoreboard teams that no longer exist
        Set<String> validSbNames = new HashSet<>();
        for (Team t : teamService.allTeams()) {
            validSbNames.add(prefix + t.getId());
        }
        for (org.bukkit.scoreboard.Team t : new ArrayList<>(sb.getTeams())) {
            if (!t.getName().startsWith(prefix)) continue;
            if (!validSbNames.contains(t.getName())) {
                try { t.unregister(); } catch (Exception ignored) { }
            }
        }

        // Build per-team members entries list
        for (Team t : teamService.allTeams()) {
            String sbName = prefix + t.getId();
            org.bukkit.scoreboard.Team sbTeam = sb.getTeam(sbName);
            if (sbTeam == null) {
                sbTeam = sb.registerNewTeam(sbName);
            }

            ChatColor color = resolveOrAssignColor(t);
            try { sbTeam.setColor(color); } catch (Throwable ignored) { }

            // friendly fire setting on scoreboard team
            try { sbTeam.setAllowFriendlyFire(settings.friendlyFireEnabled); } catch (Throwable ignored) { }

            Set<String> desiredEntries = new HashSet<>();
            for (UUID u : t.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                if (off.getName() != null) desiredEntries.add(off.getName());
            }

            // add missing
            for (String entry : desiredEntries) {
                if (!sbTeam.hasEntry(entry)) sbTeam.addEntry(entry);
            }
            // remove extra
            for (String entry : new HashSet<>(sbTeam.getEntries())) {
                if (!desiredEntries.contains(entry)) sbTeam.removeEntry(entry);
            }
        }

        // Cleanup players who are not in any team from all ts_ teams
        Set<String> playersInAnyTeam = new HashSet<>();
        for (Team t : teamService.allTeams()) {
            for (UUID u : t.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                if (off.getName() != null) playersInAnyTeam.add(off.getName());
            }
        }

        for (org.bukkit.scoreboard.Team sbTeam : sb.getTeams()) {
            if (!sbTeam.getName().startsWith(prefix)) continue;
            for (String entry : new HashSet<>(sbTeam.getEntries())) {
                if (!playersInAnyTeam.contains(entry)) sbTeam.removeEntry(entry);
            }
        }
    }

    private ChatColor resolveOrAssignColor(Team team) {
        String raw = team.getColorName();
        ChatColor parsed = null;

        if (raw != null && !raw.isBlank()) {
            try {
                parsed = ChatColor.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) { }
        }

        if (parsed != null) return parsed;

        List<String> palette = settings.scoreboard.colorPalette();
        if (palette == null || palette.isEmpty()) return ChatColor.AQUA;

        int idx = Math.floorMod(team.getId().hashCode(), palette.size());
        String chosen = palette.get(idx);

        try {
            ChatColor c = ChatColor.valueOf(chosen.toUpperCase(Locale.ROOT));
            team.setColorName(c.name());
            persistence.requestSaveDebounced();
            return c;
        } catch (Exception ignored) {
            return ChatColor.AQUA;
        }
    }
}