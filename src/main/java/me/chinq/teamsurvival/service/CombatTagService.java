package me.chinq.teamsurvival.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatTagService {

    private final Settings settings;
    private final Map<UUID, Long> expiresAtMillis = new HashMap<>();

    public CombatTagService(Settings settings) {
        this.settings = settings;
    }

    public void tag(UUID u) {
        long now = System.currentTimeMillis();
        long newExp = now + settings.combatTagSeconds * 1000L;
        expiresAtMillis.merge(u, newExp, Math::max);
    }

    public boolean isTagged(UUID u) {
        long now = System.currentTimeMillis();
        long exp = expiresAtMillis.getOrDefault(u, 0L);
        if (exp <= now) {
            expiresAtMillis.remove(u);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID u) {
        long now = System.currentTimeMillis();
        long exp = expiresAtMillis.getOrDefault(u, 0L);
        long rem = exp - now;
        if (rem <= 0) return 0;
        return (rem + 999) / 1000;
    }
}
