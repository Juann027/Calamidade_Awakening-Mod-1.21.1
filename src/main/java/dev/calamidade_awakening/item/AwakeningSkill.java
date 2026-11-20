package dev.calamidade_awakening.item;

import cyou.rimuru.rimulib.mc1211.skill.Skill;
import dev.calamidade_awakening.api.VisualKind;

public final class AwakeningSkill implements Skill {
    public final VisualKind visual;
    AwakeningSkill(VisualKind visual) { this.visual = visual; }
    @Override public String id() { return visual.addon() == null ? visual.key : visual.addon().skillKey(); }
    @Override public String title() { return visual.localizedTitle(); }
    @Override public String tier() { return visual.addon() == null ? "AWAKENING" : visual.addon().card().tier(); }
    @Override public int color() { return visual.color; }
    @Override public int cooldown() { return visual.addon() == null ? visual.duration + 10 : visual.addon().card().cooldown(); }
    @Override public int duration() { return visual.duration; }
    @Override public int defaultKey() { return 0; }
    @Override public boolean powerSliders() { return visual.addon() != null && visual.addon().power() != null; }
    @Override public cyou.rimuru.rimulib.mc1211.skill.Power power() {
        if (!powerSliders()) return Skill.super.power();
        var range = visual.addon().power();
        return new cyou.rimuru.rimulib.mc1211.skill.Power(range.min(),range.max(),range.initial(),true,1f);
    }
    @Override public boolean speedSlider() { return false; }

}
