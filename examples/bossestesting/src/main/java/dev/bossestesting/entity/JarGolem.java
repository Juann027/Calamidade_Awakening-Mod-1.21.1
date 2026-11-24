package dev.bossestesting.entity;

import dev.bossestesting.BtConfig;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.ai.Phase1Brain;
import dev.bossestesting.anim.Clip;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import static dev.bossestesting.anim.Anims.*;

/**
 * First form: El Guilhermino Bananino. Lethal damage never kills it: the health stops at 1 and the overload
 * transformation starts (only the void or /kill-type damage that bypasses everything can kill it, with its own death).
 */
public class JarGolem extends GuilherminoBoss {
    public JarGolem(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 600).add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 6)
            .add(Attributes.MOVEMENT_SPEED, .23).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ATTACK_DAMAGE, 14)
            .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.SAFE_FALL_DISTANCE, 256);
    }

    @Override protected Brain createBrain() { return new Phase1Brain(this); }
    @Override public Clip idleClip() { return JG_IDLE; }
    @Override public Clip walkClip() { return JG_WALK; }
    @Override public Clip runClip() { return JG_RUN; }
    @Override protected int deathTicks() { return Brain.LEAD + JG_DEATH.ticks() + 10; }
    @Override protected boolean dropsLoot() { return false; }

    /** Applies the configured health (on spawn, keeps the current fraction). */
    public void applyConfiguredHealth() {
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(BtConfig.healthPhase1());
        setHealth(getMaxHealth());
    }

    @Override
    public void setHealth(float health) {
        if (!level().isClientSide && health <= 0 && !isRemoved() && brain != null && !bypass) {
            super.setHealth(1);
            ((Phase1Brain) brain).requestTransform();
            return;
        }
        super.setHealth(health);
    }

    private boolean bypass;

    @Override
    public boolean hurt(DamageSource source, float amount) {
        bypass = source.is(DamageTypes.FELL_OUT_OF_WORLD);
        try {
            return super.hurt(source, amount);
        } finally {
            bypass = false;
        }
    }
}
