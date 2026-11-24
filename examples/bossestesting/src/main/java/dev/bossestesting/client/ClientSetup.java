package dev.bossestesting.client;

import dev.bossestesting.BossesTesting;
import dev.bossestesting.client.bar.GuilherminoBars;
import dev.bossestesting.client.fx.ClientFx;
import dev.bossestesting.client.particle.BtParticle;
import dev.bossestesting.client.render.BossRenderer;
import dev.bossestesting.client.render.MissileRenderer;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.net.Net;
import dev.bossestesting.registry.ModEntities;
import dev.bossestesting.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** Client wiring: renderers, particles, bar styles, effect layers, music and cosmetics. */
@EventBusSubscriber(modid = BossesTesting.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.JAR_GOLEM.get(), ctx -> new BossRenderer<>(ctx, "jar_golem", false));
        e.registerEntityRenderer(ModEntities.FINALE.get(), ctx -> new BossRenderer<>(ctx, "guilhermino_phase2", true));
        e.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.EMBER.get(), sprites -> (o, l, x, y, z, vx, vy, vz) -> new BtParticle(l, x, y, z, vx, vy, vz, o, sprites, BtParticle.Kind.EMBER));
        e.registerSpriteSet(ModParticles.SMOKE.get(), sprites -> (o, l, x, y, z, vx, vy, vz) -> new BtParticle(l, x, y, z, vx, vy, vz, o, sprites, BtParticle.Kind.SMOKE));
        e.registerSpriteSet(ModParticles.ARC.get(), sprites -> (o, l, x, y, z, vx, vy, vz) -> new BtParticle(l, x, y, z, vx, vy, vz, o, sprites, BtParticle.Kind.ARC));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        ClientFx.register();
        GuilherminoBars.register();
        Net.musicHandler = MusicClient::handle;
        GuilherminoBoss.instructionSink = ClientCosmetics::instruction;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        ClientCosmetics.tick();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (MusicClient.playing()) mc.getMusicManager().stopPlaying();
        if (mc.level == null) MusicClient.stopAll(0);
    }

    /** While the phase 2 track plays, vanilla music does not start. */
    @SubscribeEvent
    public static void music(SelectMusicEvent e) {
        if (MusicClient.playing()) e.setMusic(null);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { MusicClient.stopAll(0); }

    @SubscribeEvent
    public static void respawn(ClientPlayerNetworkEvent.Clone e) {
        // Dimension change or respawn: the server sends a stop when the player left the arena; a respawn in the
        // same arena keeps the track (no restart).
    }
}
