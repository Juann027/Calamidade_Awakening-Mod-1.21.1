package dev.calamidade_awakening;

import cyou.rimuru.rimulib.mc1211.RimuMod;
import cyou.rimuru.rimulib.mc1211.api.RimulibApi;
import dev.calamidade_awakening.net.StageNetwork;
import dev.calamidade_awakening.item.AwakeningSkills;
import dev.calamidade_awakening.config.ClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CalamidadeAwakening.ID)
public final class CalamidadeAwakening {
    public static final String ID = "calamidade_awakening";
    public static final RimuMod RIMU = RimulibApi.connect(ID, CalamidadeAwakening.class);

    /**
     * Build kind (0.14 in development): "release" only on a published release. A development build of the custom sky
     * branch keeps the version number of the last release (0.12.0) but is not that JAR; it says so in the log, in the
     * mod list description and in F7, so nobody mistakes it for the published file.
     */
    public static final String BUILD = "desenvolvimento 0.14 (céu personalizado, fase 3) — não é o release v0.12.0";

    public CalamidadeAwakening(IEventBus bus, ModContainer container) {
        if (!"release".equals(BUILD))
            com.mojang.logging.LogUtils.getLogger().warn("Calamidade_Awakening: build de {}. Não distribua este JAR.", BUILD);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // 0.10.0: limits by source (per player, per mob, per block, slots reserved for players), per world.
        container.registerConfig(ModConfig.Type.SERVER, dev.calamidade_awakening.config.ServerConfig.SPEC);
        AwakeningSkills.register(bus);
        StageNetwork.register();
    }
}
