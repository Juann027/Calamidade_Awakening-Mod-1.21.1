package dev.calamidade_awakening.teste;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Duração de cada tick do servidor (de ServerTickEvent.Pre, prioridade mais alta, a Post, mais baixa), enquanto ligado. */
final class Medidor {
    private static volatile boolean ligado;
    private static long inicio;
    private static double pior;
    private static final java.util.List<String> ticks = new java.util.concurrent.CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerTickEvent.Pre event) -> inicio = System.nanoTime());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerTickEvent.Post event) -> {
            if (!ligado) return;
            double ms = (System.nanoTime() - inicio) / 1e6;
            pior = Math.max(pior, ms);
            if (ms > 5) ticks.add(String.format(java.util.Locale.ROOT, "%d:%.1f", event.getServer().getTickCount(), ms));
        });
    }

    private Medidor() {}

    static void ligar() {
        pior = 0;
        ticks.clear();
        ligado = true;
    }

    /** Desliga e devolve o pior tick, em ms. */
    static double desligar() {
        ligado = false;
        System.out.println("[TESTE] ticks acima de 5 ms (tick:ms): " + ticks);
        return pior;
    }
}
