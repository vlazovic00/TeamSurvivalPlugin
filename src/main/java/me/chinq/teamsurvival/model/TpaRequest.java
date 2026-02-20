package me.chinq.teamsurvival.model;

import java.util.UUID;

public record TpaRequest(
        UUID requester,
        UUID target,
        long createdAtMillis,
        long expiresAtMillis
) {
    public boolean isExpired(long nowMillis) {
        return nowMillis > expiresAtMillis;
    }
}
