package me.chinq.teamsurvival.model;

import java.util.UUID;

public record Invite(
        String teamId,
        UUID inviter,
        long createdAtMillis,
        long expiresAtMillis
) {
    public boolean isExpired(long nowMillis) {
        return nowMillis > expiresAtMillis;
    }
}
