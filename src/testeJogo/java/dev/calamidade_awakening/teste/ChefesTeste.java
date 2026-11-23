package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Boss;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chefes do teste em jogo (0.13.0): definições usadas pelo {@link Roteiro} (servidor dedicado) e pelo
 * {@code RoteiroChefe} (cliente). Nada aparece sozinho: só os roteiros anexam controladores.
 */
final class ChefesTeste {
    private ChefesTeste() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(CalamidadeTeste.ID, path); }

    /** Efeito curto de entrada (40 ticks). */
    static final ResourceLocation FX = id("chefe_fx");
    /** Aura em laço (sustain de 20 a 180) solta no fim da fase. */
    static final ResourceLocation AURA = id("chefe_aura");
    /** Sequência de 30 ticks antes da fase "c". */
    static final ResourceLocation SEQ = id("chefe_seq");
    /** Feixe saindo do anchor "olhos" (cliente: anchor capturado x estático). */
    static final ResourceLocation OLHAR = id("chefe_olhar");
    /** Efeito cujo centro segue o anchor "olhos". */
    static final ResourceLocation BRILHO = id("chefe_brilho");

    /** Chefe principal: a (invulnerável, 40 ticks) -> b (aura) -sinal "golpe" + sequência-> c -vida 50%-> d; morte -> morte. */
    static final ResourceLocation CHEFE = id("chefe");
    /** Uma fase só, sem efeitos: medição de maxPerBoss. */
    static final ResourceLocation LIMITE = id("limite");
    /** Ação periódica que sempre falha: o controlador é removido depois de 3 falhas. */
    static final ResourceLocation FALHO = id("falho");

    /** Entradas em fases do chefe principal, "fase@tick", na ordem. */
    static final List<String> ENTRADAS = new CopyOnWriteArrayList<>();

    static void registrar() {
        var def = new Effects.Definition(40, 3, 0xffff8040, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        Effects.register(FX, def);
        Effects.register(AURA, new Effects.Definition(200, 3, 0xff8040ff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.timeline(AURA, Timeline.builder(200).sustain(20, 180).build());
        Effects.register(SEQ, new Effects.Definition(30, 3, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.register(OLHAR, new Effects.Definition(400, 24, 0xffff3020, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(96));
        Effects.beams(OLHAR, Beam.builder(Point.socket("olhos", 0, 0, .3), Point.socketAim("olhos", 20)).width(.25f, .9f)
            .color(0xffffffff, 0xffff4020).build());
        Effects.register(BRILHO, new Effects.Definition(400, 2, 0xffff4020, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.anchorSocket(BRILHO, "olhos");
        RigAnchors.define(EntityType.IRON_GOLEM, "olhos", RigAnchors.Spec.head(0, .05, .4));

        Bosses.define(Boss.builder(CHEFE)
            .bar(Boss.Bar.of(Component.literal("Chefe de teste")).color(Boss.BarColor.PURPLE).segments(true))
            .phase("a", p -> p.enter(FX).invulnerable().label("calamidade_teste.fase_a").onEnter(c -> entrou(c)))
            .phase("b", p -> p.sustain(AURA).onEnter(c -> entrou(c)))
            .phase("c", p -> p.color(Boss.BarColor.RED).onEnter(c -> entrou(c)))
            .phase("d", p -> p.onEnter(c -> entrou(c)))
            .phase("morte", p -> p.onEnter(c -> entrou(c)))
            .transition("a", "b", Boss.Trigger.afterTicks(40))
            .transition("b", "c", Boss.Trigger.signal("golpe"), SEQ)
            .transition("c", "d", Boss.Trigger.healthBelow(.5f))
            .transition(Boss.ANY, "morte", Boss.Trigger.death())
            .build());
        Bosses.define(Boss.builder(LIMITE).phase("unica").build());
        Bosses.define(Boss.builder(FALHO).phase("unica", p -> p.every(1, c -> {
            throw new IllegalStateException("falha proposital do teste");
        })).build());
    }

    private static void entrou(Boss.Context c) { ENTRADAS.add(c.phase() + "@" + c.level().getGameTime()); }
}
