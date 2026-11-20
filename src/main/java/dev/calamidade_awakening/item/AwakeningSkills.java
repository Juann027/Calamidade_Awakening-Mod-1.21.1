package dev.calamidade_awakening.item;

import cyou.rimuru.rimulib.mc1211.api.SkillApi;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.net.StageNetwork;
import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AwakeningSkills {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CalamidadeAwakening.ID);
    public static final DeferredItem<AwakeningFocus> FOCUS = ITEMS.registerItem("focus", AwakeningFocus::new,
        new Item.Properties().stacksTo(1));
    public static SkillSet SET;
    private static AwakeningSkill[] skills;

    private AwakeningSkills() {}
    public static void register(IEventBus bus) {
        VisualKind.seal();
        skills = java.util.Arrays.stream(VisualKind.values()).map(AwakeningSkill::new).toArray(AwakeningSkill[]::new);
        SET = SkillApi.register(CalamidadeAwakening.ID, "focus", 0x65E8C9, skills,
            (player, skill, tuning) -> StageNetwork.request(player, skill.visual.id, tuning.power()));
        bus.addListener(AwakeningSkills::setup);
        ITEMS.register(bus);
    }

    private static void setup(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (!event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) return;
        dev.calamidade_awakening.api.v1.Addons.finishRegistration();
        var added = dev.calamidade_awakening.api.v1.Addons.entries().stream()
            .map(entry -> new AwakeningSkill(VisualKind.byId(VisualKind.localId(entry.effect().toString()))))
            .toArray(AwakeningSkill[]::new);
        if (added.length == 0) return;
        SkillApi.extend(SET, added, (player, skill, tuning) -> StageNetwork.request(player, skill.visual.id, tuning.power()));
        skills = java.util.stream.Stream.concat(java.util.Arrays.stream(skills),java.util.Arrays.stream(added))
            .toArray(AwakeningSkill[]::new);
    }

    public static void cast(ServerPlayer player, int visual, float requestedPower) {
        AwakeningSkill skill = java.util.Arrays.stream(skills).filter(s -> s.visual.id == visual).findFirst().orElse(null);
        if (skill == null) return;
        var base = SkillApi.tuning(skill);
        float power = skill.powerSliders() ? skill.visual.addon().power().clamp(requestedPower) : base.power();
        if (SkillApi.holds(player, SET)) SET.cast(player, skill, new cyou.rimuru.rimulib.mc1211.skill.Tuning(power,base.damage(),base.speed(),base.width()));
        else StageNetwork.request(player, visual, power); // independent visual demo key
    }

    public static AwakeningSkill[] values() { return skills.clone(); }
}
