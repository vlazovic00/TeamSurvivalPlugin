package me.chinq.teamsurvival.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TeamChatService {

    private final Set<UUID> enabled = new HashSet<>();

    public boolean isEnabled(UUID u) {
        return enabled.contains(u);
    }

    public void setEnabled(UUID u, boolean value) {
        if (value) enabled.add(u);
        else enabled.remove(u);
    }

    public boolean toggle(UUID u) {
        if (enabled.contains(u)) {
            enabled.remove(u);
            return false;
        }
        enabled.add(u);
        return true;
    }
}
