package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.HomeLocation;
import me.chinq.teamsurvival.model.Team;

import java.util.*;

public final class TeamService {

    private final Settings settings;
    private final StarterCoinsService starterCoinsService; // NEW: anti-abuse starter coins

    private final Map<String, Team> teamsById = new HashMap<>();
    private final Map<String, String> teamIdByLowerName = new HashMap<>();
    private final Map<UUID, String> teamIdByMember = new HashMap<>();

    // runtime cooldown for /tim sethome (per team)
    private final Map<String, Long> lastHomeSetAtMillis = new HashMap<>();

    private Runnable onTeamsChanged = () -> {};

    // Called AFTER a team is removed from TeamService (useful for cleanup in other services)
    private java.util.function.Consumer<String> onTeamDisbanded = (id) -> {};

    // NEW ctor signature
    public TeamService(Settings settings, StarterCoinsService starterCoinsService, Map<String, Team> initialTeams) {
        this.settings = settings;
        this.starterCoinsService = starterCoinsService;
        if (initialTeams != null) teamsById.putAll(initialTeams);
        rebuildIndexes();
    }

    public void bindOnTeamsChanged(Runnable onTeamsChanged) {
        this.onTeamsChanged = (onTeamsChanged == null) ? () -> {} : onTeamsChanged;
    }

    public void bindOnTeamDisbanded(java.util.function.Consumer<String> onTeamDisbanded) {
        this.onTeamDisbanded = (onTeamDisbanded == null) ? (id) -> {} : onTeamDisbanded;
    }

    private void rebuildIndexes() {
        teamIdByLowerName.clear();
        teamIdByMember.clear();

        for (Team t : teamsById.values()) {
            teamIdByLowerName.put(t.getName().toLowerCase(Locale.ROOT), t.getId());
            for (UUID u : t.getMembersOrdered()) {
                teamIdByMember.put(u, t.getId());
            }
        }
    }

    public Collection<Team> allTeams() {
        return Collections.unmodifiableCollection(teamsById.values());
    }

    public Team getTeamById(String id) {
        return teamsById.get(id);
    }

    public Team getTeamByName(String name) {
        if (name == null) return null;
        String id = teamIdByLowerName.get(name.toLowerCase(Locale.ROOT));
        return (id == null) ? null : teamsById.get(id);
    }

    public Team getTeamOf(UUID player) {
        String id = teamIdByMember.get(player);
        return (id == null) ? null : teamsById.get(id);
    }

    public Collection<Team> getAllTeams() {
        return teamsById.values();
    }

    public Team createTeam(UUID leader, String name) {
        String id = generateId();

        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leader);

        // NEW: starter coins anti-abuse
        long startCoins;
        if (starterCoinsService != null) {
            startCoins = starterCoinsService.claimStartingCoins(leader);
        } else {
            startCoins = settings.startingCoins;
        }
        if (startCoins < 0) startCoins = 0;

        Team team = new Team(id, name, leader, System.currentTimeMillis(), members, null, null, startCoins);

        teamsById.put(id, team);
        teamIdByLowerName.put(name.toLowerCase(Locale.ROOT), id);
        teamIdByMember.put(leader, id);

        onTeamsChanged.run();
        return team;
    }

    public long getCoins(Team team) {
        return (team == null) ? 0L : Math.max(0L, team.getCoins());
    }

    public void addCoins(Team team, long amount) {
        if (team == null) return;
        if (amount <= 0) return;
        team.addCoins(amount);
        onTeamsChanged.run();
    }

    public void addCoins(String teamId, long amount) {
        Team t = teamsById.get(teamId);
        addCoins(t, amount);
    }

    public boolean tryRemoveCoins(Team team, long amount) {
        if (team == null) return false;
        if (amount <= 0) return true;
        boolean ok = team.tryRemoveCoins(amount);
        if (ok) onTeamsChanged.run();
        return ok;
    }

    public boolean addMember(String teamId, UUID member) {
        Team t = teamsById.get(teamId);
        if (t == null) return false;

        if (t.size() >= settings.maxTeamSize) return false;
        if (teamIdByMember.containsKey(member)) return false;

        t.addMember(member);
        teamIdByMember.put(member, teamId);

        onTeamsChanged.run();
        return true;
    }

    public void kickMember(String teamId, UUID member) {
        Team t = teamsById.get(teamId);
        if (t == null) return;

        t.removeMember(member);
        teamIdByMember.remove(member);

        if (t.size() <= 0) {
            disbandTeam(teamId);
        } else {
            onTeamsChanged.run();
        }
    }

    public static final class LeaveOutcome {
        public final String teamId;
        public final String teamName;
        public final boolean disbanded;
        public final UUID newLeader;

        public LeaveOutcome(String teamId, String teamName, boolean disbanded, UUID newLeader) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.disbanded = disbanded;
            this.newLeader = newLeader;
        }
    }

    public LeaveOutcome leaveTeam(UUID player) {
        Team t = getTeamOf(player);
        if (t == null) return new LeaveOutcome("", "", false, null);

        String teamId = t.getId();
        String teamName = t.getName();

        boolean wasLeader = t.isLeader(player);

        t.removeMember(player);
        teamIdByMember.remove(player);

        UUID newLeader = null;
        boolean disbanded = false;

        if (t.size() <= 0) {
            disbandTeam(teamId);
            disbanded = true;
        } else if (wasLeader) {
            newLeader = t.getOldestNonLeaderMemberOrNull();
            if (newLeader != null) {
                t.setLeader(newLeader);
            }
            onTeamsChanged.run();
        } else {
            onTeamsChanged.run();
        }

        return new LeaveOutcome(teamId, teamName, disbanded, newLeader);
    }

    private void disbandTeam(String teamId) {
        Team t = teamsById.remove(teamId);
        if (t != null) {
            teamIdByLowerName.remove(t.getName().toLowerCase(Locale.ROOT));
            for (UUID u : t.getMembersOrdered()) {
                teamIdByMember.remove(u);
            }
        }

        // notify other services (claims cleanup etc.)
        try {
            onTeamDisbanded.accept(teamId);
        } catch (Exception ignored) {}

        onTeamsChanged.run();
    }

    public void setHome(String teamId, HomeLocation home) {
        Team t = teamsById.get(teamId);
        if (t == null) return;
        t.setHome(home);
        onTeamsChanged.run();
    }

    public long getHomeSetCooldownRemainingSeconds(String teamId) {
        long last = lastHomeSetAtMillis.getOrDefault(teamId, 0L);
        long readyAt = last + settings.homeCooldownSeconds * 1000L;
        long now = System.currentTimeMillis();
        long remMillis = readyAt - now;
        if (remMillis <= 0) return 0;
        return (remMillis + 999) / 1000;
    }

    public void recordHomeSet(String teamId) {
        lastHomeSetAtMillis.put(teamId, System.currentTimeMillis());
    }

    private String generateId() {
        for (int i = 0; i < 5; i++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (!teamsById.containsKey(id)) return id;
        }
        // fallback
        return Long.toHexString(System.nanoTime()).substring(0, 8);
    }
}