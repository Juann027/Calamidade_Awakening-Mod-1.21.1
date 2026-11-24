package dev.bossestesting.registry;

import dev.bossestesting.BossesTesting;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.GuilherminoMissile;
import dev.bossestesting.entity.JarGolem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types. None of them spawns naturally: only the commands create the boss. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, BossesTesting.MODID);

    /** First form: hitbox of the 5-block robot (model 80 px tall, 46 px wide at the arms). */
    public static final DeferredHolder<EntityType<?>, EntityType<JarGolem>> JAR_GOLEM = REGISTER.register("jar_golem",
        () -> EntityType.Builder.of(JarGolem::new, MobCategory.MONSTER).sized(3.2f, 5.0f).eyeHeight(4.1f)
            .clientTrackingRange(16).fireImmune().build("jar_golem"));

    /** Second form: same frame, damaged. */
    public static final DeferredHolder<EntityType<?>, EntityType<GuilherminoFinale>> FINALE = REGISTER.register("guilhermino_phase2",
        () -> EntityType.Builder.of(GuilherminoFinale::new, MobCategory.MONSTER).sized(3.4f, 5.0f).eyeHeight(4.1f)
            .clientTrackingRange(16).fireImmune().build("guilhermino_phase2"));

    /** Missile of the barrage (model 1.5 × 0.6 blocks). */
    public static final DeferredHolder<EntityType<?>, EntityType<GuilherminoMissile>> MISSILE = REGISTER.register("guilhermino_missile",
        () -> EntityType.Builder.<GuilherminoMissile>of(GuilherminoMissile::new, MobCategory.MISC).sized(.6f, .6f)
            .clientTrackingRange(10).updateInterval(1).fireImmune().build("guilhermino_missile"));

    private ModEntities() {}
}
