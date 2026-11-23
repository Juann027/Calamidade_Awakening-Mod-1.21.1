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

    public CalamidadeAwakening(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // 0.10.0: limits by source (per player, per mob, per block, slots reserved for players), per world.
        container.registerConfig(ModConfig.Type.SERVER, dev.calamidade_awakening.config.ServerConfig.SPEC);
        AwakeningSkills.register(bus);
        StageNetwork.register();
        // 0.13.0: boss controllers (state saved on the entity).
        dev.calamidade_awakening.boss.BossServer.register(bus);
    }
}
