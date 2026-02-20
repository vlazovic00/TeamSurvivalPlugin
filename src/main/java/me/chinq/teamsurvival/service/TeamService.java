package me.chinq.teamsurvival.service;

import me.chinq.teamsurvival.model.HomeLocation;
import me.chinq.teamsurvival.model.Team;

import java.util.*;

public final class TeamService {

    private final Settings settings;

    private final Map<String, Team> teamsById = new HashMap<>();
    private final Map<String, String> teamIdByLowerName = new HashMap<>();
    private final Map<UUID, String> teamIdByMember = new HashMap<>();

    // runtime cooldown for /tim sethome (per team)
    private final Map<String, Long> lastHomeSetAtMillis = new HashMap<>();

    private Runnable onTeamsChanged = () -> {};

    public TeamService(Settings settings, Map<String, Team> initialTeams) {
        this.settings = settings;
        if (initialTeams != null) teamsById.putAll(initialTeams);
        rebuildIndexes();
    }

    public void bindOnTeamsChanged(Runnable onTeamsChanged) {
        this.onTeamsChanged = (onTeamsChanged == null) ? () -> {} : onTeamsChanged;
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

    public Team createTeam(UUID leader, String name) {
        String id = generateId();

        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leader);

        Team team = new Team(id, name, leader, System.currentTimeMillis(), members, null, null);

        teamsById.put(id, team);
        teamIdByLowerName.put(name.toLowerCase(Locale.ROOT), id);
        teamIdByMember.put(leader, id);

        onTeamsChanged.run();
        return team;
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
