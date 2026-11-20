package dev.calamidade_awakening.item;

import cyou.rimuru.rimulib.mc1211.skill.SkillHolder;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import net.minecraft.world.item.Item;

public final class AwakeningFocus extends Item implements SkillHolder {
    public AwakeningFocus(Properties properties) { super(properties); }
    @Override public SkillSet skillSet() { return AwakeningSkills.SET; }
}
