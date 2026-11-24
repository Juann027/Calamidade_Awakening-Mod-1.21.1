package dev.bossestesting.entity;

import dev.bossestesting.BtConfig;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.ai.Phase2Brain;
import dev.bossestesting.anim.Clip;
import dev.bossestesting.fx.Fx;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import static dev.bossestesting.anim.Anims.*;

/** Second form: El Guilhermino Finale (cyan reactor exposed, missile pods, Overloaded state). Drops the loot. */
public class GuilherminoFinale extends GuilherminoBoss {
    public GuilherminoFinale(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 800).add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 8)
            .add(Attributes.MOVEMENT_SPEED, .21).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ATTACK_DAMAGE, 16)
            .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.SAFE_FALL_DISTANCE, 256);
    }

    @Override protected Brain createBrain() { return new Phase2Brain(this); }
    @Override public Clip idleClip() { return P2_IDLE; }
    @Override public Clip walkClip() { return P2_WALK; }
    @Override public Clip runClip() { return null; }
    @Override protected int deathTicks() { return Fx.deathFinalTick() + 30; }
    @Override protected boolean dropsLoot() { return true; }

    public Phase2Brain phase2() { return (Phase2Brain) brain; }

    /** Applies the configured health (keeps the fraction given by the transformation). */
    public void applyConfiguredHealth(float fraction) {
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(BtConfig.healthPhase2());
        setHealth(getMaxHealth() * fraction);
    }
}
