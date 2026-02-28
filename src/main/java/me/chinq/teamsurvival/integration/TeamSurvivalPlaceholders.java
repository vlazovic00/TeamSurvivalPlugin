package me.chinq.teamsurvival.integration;

import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.BountyService;
import me.chinq.teamsurvival.service.ClaimService;
import me.chinq.teamsurvival.service.Settings;
import me.chinq.teamsurvival.service.TeamService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

public final class TeamSurvivalPlaceholders extends PlaceholderExpansion {

    private final String version;
    private final Settings settings;
    private final TeamService teamService;
    private final ClaimService claimService;
    private final BountyService bountyService;

    public TeamSurvivalPlaceholders(
            String version,
            Settings settings,
            TeamService teamService,
            ClaimService claimService,
            BountyService bountyService
    ) {
        this.version = (version == null) ? "unknown" : version;
        this.settings = settings;
        this.teamService = teamService;
        this.claimService = claimService;
        this.bountyService = bountyService;
    }

    @Override public String getIdentifier() { return "teamsurvival"; } // %teamsurvival_xxx%
    @Override public String getAuthor() { return "Chinq"; }
    @Override public String getVersion() { return version; }

    /** Obavezno za “internal” expansion da ne nestane na /papi reload. */
    @Override public boolean persist() { return true; }  // :contentReference[oaicite:1]{index=1}

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) return "";

        UUID u = player.getUniqueId();
        Team t = teamService.getTeamOf(u);

        String p = params.toLowerCase(Locale.ROOT);

        // --- team basic ---
        switch (p) {
            case "has_team": return (t != null) ? "yes" : "no";
            case "team_id": return (t != null) ? t.getId() : "";
            case "team_name": return (t != null) ? t.getName() : "";
            case "team_color": return (t != null && t.getColorName() != null) ? t.getColorName() : "";
            case "team_leader": {
                if (t == null) return "";
                OfflinePlayer leader = Bukkit.getOfflinePlayer(t.getLeader());
                String name = leader.getName();
                return (name == null) ? "" : name;
            }
            case "team_members": return (t != null) ? String.valueOf(t.size()) : "0";
            case "team_online": {
                if (t == null) return "0";
                int online = 0;
                for (UUID m : t.getMembers()) {
                    if (Bukkit.getPlayer(m) != null) online++;
                }
                return String.valueOf(online);
            }

            // --- economy ---
            case "team_coins": return (t != null) ? String.valueOf(teamService.getCoins(t)) : "0";

            // --- claims ---
            case "claims": {
                if (t == null) return "0";
                return String.valueOf(claimService.getClaimCount(t.getId()));
            }
            case "claims_max": {
                int max = settings.claims.maxClaimsPerTeam();
                return (max <= 0) ? "∞" : String.valueOf(max);
            }
            case "claims_left": {
                if (t == null) return "0";
                int max = settings.claims.maxClaimsPerTeam();
                if (max <= 0) return "∞";
                int used = claimService.getClaimCount(t.getId());
                return String.valueOf(Math.max(0, max - used));
            }

            // --- bounty (player) ---
            case "bounty": return String.valueOf(Math.max(0L, bountyService.getBounty(u)));
        }

        return null; // nepoznat placeholder
    }
}