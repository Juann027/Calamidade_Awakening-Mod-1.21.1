package dev.calamidade_awakening.api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recarga autoritativa do servidor, separada por jogador e por habilidade.
 * Classe pura (sem tipos do Minecraft) para poder ser verificada em FrameBudgetCheck.
 */
public final class Cooldowns {
    private record Key(UUID player, int visual) {}
    private final Map<Key, Long> expirations = new HashMap<>();

    /** True quando a habilidade deste jogador já pode ser conjurada no tick informado. */
    public boolean ready(UUID player, int visual, long now) {
        return now >= expirations.getOrDefault(new Key(player, visual), Long.MIN_VALUE);
    }

    /** Inicia a recarga de uma única habilidade. Duração em ticks; valores menores que 1 não registram nada. */
    public void start(UUID player, int visual, long now, int ticks) {
        if (ticks < 1) return;
        expirations.put(new Key(player, visual), now + ticks);
    }

    /** Ticks restantes, ou 0 quando pronta. */
    public long remaining(UUID player, int visual, long now) {
        return Math.max(0, expirations.getOrDefault(new Key(player, visual), now) - now);
    }

    /** Remove entradas vencidas. Chamado uma vez por tick do servidor. */
    public void prune(long now) {
        expirations.values().removeIf(expiration -> expiration <= now);
    }

    public void clear() { expirations.clear(); }
    public int size() { return expirations.size(); }
}
