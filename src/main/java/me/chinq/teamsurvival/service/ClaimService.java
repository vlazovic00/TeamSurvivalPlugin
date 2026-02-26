package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.ClaimedChunk;
import me.chinq.teamsurvival.model.Team;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class ClaimService {

    private final Settings settings;
    private final MessageService msg;
    private final TeamService teamService;

    // chunk -> teamId
    private final Map<ClaimedChunk, String> ownerByChunk = new HashMap<>();
    // teamId -> set of chunks (for fast counting/listing)
    private final Map<String, Set<ClaimedChunk>> chunksByTeam = new HashMap<>();

    private Runnable onClaimsChanged = () -> {};

    public ClaimService(Settings settings, MessageService msg, TeamService teamService, Map<ClaimedChunk, String> initial) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;

        if (initial != null) ownerByChunk.putAll(initial);
        rebuildIndexAndCleanup();
    }

    public void bindOnClaimsChanged(Runnable r) {
        this.onClaimsChanged = (r == null) ? () -> {} : r;
    }

    private void rebuildIndexAndCleanup() {
        chunksByTeam.clear();

        // cleanup: remove claims for missing/unknown teams
        Iterator<Map.Entry<ClaimedChunk, String>> it = ownerByChunk.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ClaimedChunk, String> e = it.next();
            String teamId = e.getValue();
            if (teamId == null || teamService.getTeamById(teamId) == null) {
                it.remove();
                continue;
            }
            chunksByTeam.computeIfAbsent(teamId, k -> new HashSet<>()).add(e.getKey());
        }
    }

    public boolean isEnabled() {
        return settings.claims.enabled();
    }

    public String getOwnerTeamId(Chunk chunk) {
        if (chunk == null) return null;
        if (!isEnabled()) return null;

        World w = chunk.getWorld();
        if (w == null) return null;
        if (!settings.isWorldAllowed(w.getName())) return null;

        return ownerByChunk.get(ClaimedChunk.of(w.getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean isChunkClaimed(Chunk chunk) {
        return getOwnerTeamId(chunk) != null;
    }

    public int getClaimCount(String teamId) {
        if (teamId == null) return 0;
        Set<ClaimedChunk> s = chunksByTeam.get(teamId);
        return (s == null) ? 0 : s.size();
    }

    public List<ClaimedChunk> getClaimsOf(String teamId) {
        if (teamId == null) return List.of();
        Set<ClaimedChunk> s = chunksByTeam.get(teamId);
        if (s == null) return List.of();
        return s.stream()
                .sorted(Comparator.comparing(ClaimedChunk::worldLower)
                        .thenComparingInt(ClaimedChunk::x)
                        .thenComparingInt(ClaimedChunk::z))
                .collect(Collectors.toList());
    }

    /** True if player is allowed to build/break inside the chunk. */
    public boolean canBuild(Player p, Chunk chunk) {
        if (!isEnabled()) return true;
        if (p == null || chunk == null) return true;

        String ownerTeamId = getOwnerTeamId(chunk);
        if (ownerTeamId == null) return true;

        Team pt = teamService.getTeamOf(p.getUniqueId());
        return pt != null && ownerTeamId.equals(pt.getId());
    }

    public boolean claimHere(Player p) {
        if (!isEnabled()) {
            msg.sendPrefixed(p, "claims.disabled");
            return false;
        }
        if (p == null) return false;

        if (!settings.isWorldAllowed(p.getWorld().getName())) {
            msg.sendPrefixed(p, "claims.worldNotAllowed");
            return false;
        }

        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "errors.noTeam");
            return false;
        }
        if (settings.claims.onlyLeaderCanClaim() && !team.isLeader(p.getUniqueId())) {
            msg.sendPrefixed(p, "claims.onlyLeader");
            return false;
        }

        Chunk c = p.getLocation().getChunk();
        ClaimedChunk key = ClaimedChunk.of(c.getWorld().getName(), c.getX(), c.getZ());

        String owner = ownerByChunk.get(key);
        if (owner != null) {
            if (owner.equals(team.getId())) msg.sendPrefixed(p, "claims.alreadyYours");
            else msg.sendPrefixed(p, "claims.alreadyClaimed");
            return false;
        }

        int max = settings.claims.maxClaimsPerTeam();
        if (max > 0 && getClaimCount(team.getId()) >= max) {
            msg.sendPrefixed(p, "claims.limitReached", Map.of("max", String.valueOf(max)));
            return false;
        }

        long price = Math.max(0L, settings.claims.pricePerChunk());
        if (price > 0 && !teamService.tryRemoveCoins(team, price)) {
            msg.sendPrefixed(p, "claims.notEnoughCoins", Map.of("price", String.valueOf(price)));
            return false;
        }

        ownerByChunk.put(key, team.getId());
        chunksByTeam.computeIfAbsent(team.getId(), k -> new HashSet<>()).add(key);

        onClaimsChanged.run();
        msg.sendPrefixed(p, "claims.claimed", Map.of(
                "price", String.valueOf(price),
                "x", String.valueOf(key.x()),
                "z", String.valueOf(key.z()),
                "world", c.getWorld().getName()
        ));
        return true;
    }

    public boolean unclaimHere(Player p) {
        if (!isEnabled()) {
            msg.sendPrefixed(p, "claims.disabled");
            return false;
        }
        if (p == null) return false;

        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "errors.noTeam");
            return false;
        }
        if (settings.claims.onlyLeaderCanClaim() && !team.isLeader(p.getUniqueId())) {
            msg.sendPrefixed(p, "claims.onlyLeader");
            return false;
        }

        Chunk c = p.getLocation().getChunk();
        ClaimedChunk key = ClaimedChunk.of(c.getWorld().getName(), c.getX(), c.getZ());

        String owner = ownerByChunk.get(key);
        if (owner == null) {
            msg.sendPrefixed(p, "claims.notClaimed");
            return false;
        }
        if (!owner.equals(team.getId())) {
            msg.sendPrefixed(p, "claims.notYours");
            return false;
        }

        ownerByChunk.remove(key);
        Set<ClaimedChunk> set = chunksByTeam.get(team.getId());
        if (set != null) set.remove(key);

        onClaimsChanged.run();
        msg.sendPrefixed(p, "claims.unclaimed", Map.of(
                "x", String.valueOf(key.x()),
                "z", String.valueOf(key.z()),
                "world", c.getWorld().getName()
        ));
        return true;
    }

    public Map<ClaimedChunk, String> snapshotAll() {
        return Map.copyOf(ownerByChunk);
    }
}