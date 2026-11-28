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
    /**
     * Presentation name (0.15.0 phase 5): the one place Java code takes the human-readable name from. Changing it is a
     * presentation-only rename (docs/REBRANDING.md); {@link #ID} is technical identity and must stay stable.
     */
    public static final String NAME = "Calamidade_Awakening";
    public static final RimuMod RIMU = RimulibApi.connect(ID, CalamidadeAwakening.class);

    /**
     * Build kind: "release" on a release candidate or a published release. A development build says what it is in the
     * log, in the mod list description and in F7, so nobody mistakes it for a published file.
     */
    public static final String BUILD = "release";

    public CalamidadeAwakening(IEventBus bus, ModContainer container) {
        if (!"release".equals(BUILD))
            com.mojang.logging.LogUtils.getLogger().warn("{}: {} build. Do not distribute this JAR.", NAME, BUILD);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // 0.10.0: limits by source (per player, per mob, per block, slots reserved for players), per world.
        container.registerConfig(ModConfig.Type.SERVER, dev.calamidade_awakening.config.ServerConfig.SPEC);
        AwakeningSkills.register(bus);
        StageNetwork.register();
        // 0.13.0: boss controllers (state saved on the entity).
        dev.calamidade_awakening.boss.BossServer.register(bus);
    }
}
