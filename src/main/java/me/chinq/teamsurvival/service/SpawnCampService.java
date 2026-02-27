package me.chinq.teamsurvival.service;

import java.util.*;

public final class SpawnCampService {

    private final Settings settings;

    // victim -> (killer -> timestampsMillis)
    private final Map<UUID, Map<UUID, Deque<Long>>> kills = new HashMap<>();

    public SpawnCampService(Settings settings) {
        this.settings = settings;
    }

    public void recordKill(UUID victim, UUID killer) {
        if (!settings.antiSpawnCampEnabled) return;
        if (victim == null || killer == null) return;
        if (victim.equals(killer)) return;

        long now = System.currentTimeMillis();
        long windowMs = settings.antiSpawnCampWindowSeconds * 1000L;

        Map<UUID, Deque<Long>> byKiller = kills.computeIfAbsent(victim, k -> new HashMap<>());
        Deque<Long> times = byKiller.computeIfAbsent(killer, k -> new ArrayDeque<>());

        times.addLast(now);

        // purge old
        while (!times.isEmpty() && (now - times.peekFirst()) > windowMs) {
            times.removeFirst();
        }

        // čisti prazne
        cleanupVictim(victim);
    }

    /**
     * @return killer UUID koji je prešao threshold, ili null ako nema.
     * Ako okine, resetuje taj counter da ne spamuje svako naredno umiranje.
     */
    public UUID consumeTriggerIfAny(UUID victim) {
        if (!settings.antiSpawnCampEnabled) return null;

        Map<UUID, Deque<Long>> byKiller = kills.get(victim);
        if (byKiller == null || byKiller.isEmpty()) return null;

        long now = System.currentTimeMillis();
        long windowMs = settings.antiSpawnCampWindowSeconds * 1000L;

        UUID triggeredKiller = null;
        for (Map.Entry<UUID, Deque<Long>> e : byKiller.entrySet()) {
            Deque<Long> times = e.getValue();
            // purge old
            while (!times.isEmpty() && (now - times.peekFirst()) > windowMs) {
                times.removeFirst();
            }
            if (times.size() >= settings.antiSpawnCampKillsThreshold) {
                triggeredKiller = e.getKey();
                break;
            }
        }

        if (triggeredKiller != null) {
            // resetuje baš ovog killera za ovu žrtvu (da okida tek kad se opet nakupi)
            byKiller.remove(triggeredKiller);
            cleanupVictim(victim);
            return triggeredKiller;
        }

        cleanupVictim(victim);
        return null;
    }

    private void cleanupVictim(UUID victim) {
        Map<UUID, Deque<Long>> byKiller = kills.get(victim);
        if (byKiller == null) return;

        byKiller.entrySet().removeIf(en -> en.getValue() == null || en.getValue().isEmpty());
        if (byKiller.isEmpty()) kills.remove(victim);
    }
}