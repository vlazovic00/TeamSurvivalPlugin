package me.chinq.teamsurvival.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Team {

    private final String id;
    private final String name;
    private UUID leader;
    private final long createdAt;

    private final LinkedHashSet<UUID> members; // preserves join order
    private HomeLocation home; // nullable
    private String colorName;  // nullable

    public Team(String id, String name, UUID leader, long createdAt, LinkedHashSet<UUID> members, HomeLocation home, String colorName) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.leader = Objects.requireNonNull(leader);
        this.createdAt = createdAt;

        this.members = (members == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(members);
        this.members.add(leader);

        this.home = home;
        this.colorName = colorName;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public long getCreatedAt() { return createdAt; }

    public Set<UUID> getMembers() { return Set.copyOf(members); }
    public LinkedHashSet<UUID> getMembersOrdered() { return members; }

    public boolean hasMember(UUID u) { return members.contains(u); }
    public int size() { return members.size(); }

    public boolean isLeader(UUID u) { return leader.equals(u); }

    public void setLeader(UUID newLeader) {
        this.leader = Objects.requireNonNull(newLeader);
        this.members.add(newLeader);
    }

    public HomeLocation getHome() { return home; }
    public void setHome(HomeLocation home) { this.home = home; }

    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }

    public void addMember(UUID u) {
        members.add(Objects.requireNonNull(u));
    }

    public void removeMember(UUID u) {
        members.remove(Objects.requireNonNull(u));
    }

    public UUID getOldestNonLeaderMemberOrNull() {
        for (UUID u : members) {
            if (!u.equals(leader)) return u;
        }
        return null;
    }
}
