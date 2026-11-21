package dev.calamidade_awakening.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Recarga autoritativa do servidor, separada por fonte e por efeito. Desde a 0.10.0 a fonte é qualquer chave com
 * equals/hashCode (no motor, um {@code EffectSource}: jogador, entidade, bloco ou script); a 0.5 usava o UUID do jogador.
 * Classe pura (sem tipos do Minecraft) para poder ser verificada em FrameBudgetCheck.
 */
public final class Cooldowns {
    private record Key(Object source, int visual) {}
    private final Map<Key, Long> expirations = new HashMap<>();

    /** True quando o efeito desta fonte já pode começar no tick informado. */
    public boolean ready(Object source, int visual, long now) {
        return now >= expirations.getOrDefault(new Key(source, visual), Long.MIN_VALUE);
    }

    /** Inicia a recarga de um único efeito desta fonte. Duração em ticks; valores menores que 1 não registram nada. */
    public void start(Object source, int visual, long now, int ticks) {
        if (ticks < 1) return;
        expirations.put(new Key(source, visual), now + ticks);
    }

    /** Encerra a recarga de um efeito desta fonte (0.10.0). */
    public void cancel(Object source, int visual) {
        expirations.remove(new Key(source, visual));
    }

    /** Ticks restantes, ou 0 quando pronta. */
    public long remaining(Object source, int visual, long now) {
        return Math.max(0, expirations.getOrDefault(new Key(source, visual), now) - now);
    }

    /** Remove entradas vencidas. Chamado uma vez por tick do servidor. */
    public void prune(long now) {
        expirations.values().removeIf(expiration -> expiration <= now);
    }

    public void clear() { expirations.clear(); }
    public int size() { return expirations.size(); }
}
