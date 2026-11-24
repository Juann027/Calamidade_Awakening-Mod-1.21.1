package dev.bossestesting;

import dev.bossestesting.command.BtCommands;
import dev.bossestesting.encounter.BossDefs;
import dev.bossestesting.encounter.Encounters;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.JarGolem;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.net.Net;
import dev.bossestesting.registry.ModEntities;
import dev.bossestesting.registry.ModParticles;
import dev.bossestesting.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * BossesTesting 0.1.0: addon of Calamidade Awakening with the boss El Guilhermino (two forms and a missile).
 * Everything the fight changes in the world goes through the engine's public API (effects, Terrain API, Boss API,
 * sky, lights, beams); the addon owns the entities, AI, music, bars and shaders.
 */
@Mod(BossesTesting.MODID)
public final class BossesTesting {
    public static final String MODID = "bossestesting";

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }

    public BossesTesting(IEventBus modBus, ModContainer container) {
        ModEntities.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, BtConfig.SPEC);
        modBus.addListener(BossesTesting::attributes);
        modBus.addListener(Net::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(dev.bossestesting.fx.Anchors::define));
        // Calamidade: effects, rig anchors and boss controllers are registered at mod construction (both sides).
        Fx.register();
        BossDefs.register();
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BtCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Encounters.tick(e.getServer()));
        NeoForge.EVENT_BUS.register(Encounters.Events.class);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.JAR_GOLEM.get(), JarGolem.attributes().build());
        event.put(ModEntities.FINALE.get(), GuilherminoFinale.attributes().build());
    }
}
