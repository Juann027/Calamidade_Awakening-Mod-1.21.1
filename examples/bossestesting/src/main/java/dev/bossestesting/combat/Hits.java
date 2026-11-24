package dev.bossestesting.combat;

import dev.bossestesting.BtConfig;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoMissile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side hits of the boss, independent of terrain edits: area with falloff, cone, capsule (beams, charges)
 * and contact. Knockback respects knockback resistance; players are marked so the push reaches their client.
 * Damage scales with {@link BtConfig#damageScale()}. Creative and spectator players are never touched.
 */
public final class Hits {
    private Hits() {}

    /** Living entities the boss may hit inside a box (not itself, its other form, missiles or untouchable players). */
    public static List<LivingEntity> victims(ServerLevel level, AABB box, Entity boss) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != boss && e.isAlive() && !(e instanceof GuilherminoBoss)
            && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !e.isInvulnerable());
    }

    public static DamageSource melee(Entity boss) { return boss.damageSources().mobAttack(boss instanceof LivingEntity l ? l : null); }

    public static DamageSource blast(Entity boss, Entity direct) { return boss.damageSources().explosion(direct, boss); }

    public static DamageSource energy(Entity boss) { return boss.damageSources().indirectMagic(boss, boss); }

    /** Area hit: full damage at the center, `edge` fraction at `radius`; pushes away and up. Returns the hit count. */
    public static int area(Entity boss, Vec3 center, double radius, float damage, float edge, double push, double lift, DamageSource source) {
        ServerLevel level = (ServerLevel) boss.level();
        int n = 0;
        for (LivingEntity e : victims(level, new AABB(center, center).inflate(radius, radius * .6 + 2, radius), boss)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * .5, 0);
            double d = Math.sqrt(Math.pow(c.x - center.x, 2) + Math.pow(c.z - center.z, 2));
            if (d > radius + e.getBbWidth() * .5 || Math.abs(c.y - center.y) > radius * .6 + 2.5) continue;
            double f = 1 - Math.min(1, d / radius);
            float dmg = (float) (damage * (edge + (1 - edge) * f) * BtConfig.damageScale());
            if (dmg > 0) e.hurt(source, dmg);
            Vec3 away = new Vec3(c.x - center.x, 0, c.z - center.z);
            if (away.lengthSqr() < 1e-4) away = new Vec3(boss.getRandom().nextGaussian(), 0, boss.getRandom().nextGaussian());
            knock(e, away.normalize().scale(push * (.45 + .55 * f)), lift * (.5 + .5 * f));
            n++;
        }
        return n;
    }

    /** Cone hit in front of `origin` along `dir` (horizontal), `halfAngle` degrees. */
    public static int cone(Entity boss, Vec3 origin, Vec3 dir, double reach, double halfAngle, float damage, double push, double lift, Set<UUID> hit) {
        ServerLevel level = (ServerLevel) boss.level();
        Vec3 f = new Vec3(dir.x, 0, dir.z).normalize();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : victims(level, new AABB(origin, origin).inflate(reach, 3, reach), boss)) {
            if (hit != null && hit.contains(e.getUUID())) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() * .5, 0).subtract(origin);
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double d = flat.length();
            if (d > reach + e.getBbWidth() * .5 || Math.abs(to.y) > 3.5) continue;
            if (d > 1 && flat.normalize().dot(f) < cos) continue;
            if (hit == null) e.invulnerableTime = 0; // combos: each blow of a chain lands
            e.hurt(melee(boss), (float) (damage * BtConfig.damageScale()));
            knock(e, f.scale(push), lift);
            if (hit != null) hit.add(e.getUUID());
            n++;
        }
        return n;
    }

    /** Capsule hit from `a` to `b` with `radius` (beams, charges); `hit` keeps each entity to one hit when not null. */
    public static List<LivingEntity> capsule(Entity boss, Vec3 a, Vec3 b, double radius, float damage, double push, double lift,
                                             DamageSource source, Set<UUID> hit) {
        ServerLevel level = (ServerLevel) boss.level();
        List<LivingEntity> out = new ArrayList<>();
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1e-6, ab.lengthSqr());
        Vec3 dir = ab.normalize();
        for (LivingEntity e : victims(level, new AABB(a, b).inflate(radius + 1), boss)) {
            if (hit != null && hit.contains(e.getUUID())) continue;
            Vec3 c = e.position().add(0, e.getBbHeight() * .5, 0);
            double t = Math.clamp(c.subtract(a).dot(ab) / len2, 0, 1);
            if (a.add(ab.scale(t)).distanceTo(c) > radius + e.getBbWidth() * .5 + e.getBbHeight() * .25) continue;
            if (hit == null) e.invulnerableTime = 0; // continuous beams: every pulse lands
            if (damage > 0) e.hurt(source, (float) (damage * BtConfig.damageScale()));
            knock(e, dir.scale(push), lift);
            if (hit != null) hit.add(e.getUUID());
            out.add(e);
        }
        return out;
    }

    /** Pushes an entity, scaled by its knockback resistance. */
    public static void knock(LivingEntity e, Vec3 horizontal, double lift) {
        double resist = e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double k = Math.max(0, 1 - resist);
        if (k <= 0) return;
        e.push(horizontal.x * k, lift * k, horizontal.z * k);
        e.hurtMarked = true;
    }

    /** First block along a ray (blocks only), or null. */
    public static BlockHitResult ground(Entity boss, Vec3 from, Vec3 to) {
        BlockHitResult r = boss.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        return r.getType() == HitResult.Type.MISS ? null : r;
    }

    /** True for entities a missile may collide with. */
    public static boolean missileCanHit(Entity e, Entity owner) {
        return e.isAlive() && e != owner && !(e instanceof GuilherminoMissile) && !(e instanceof GuilherminoBoss) && e.isPickable()
            && !(e instanceof Player p && p.isSpectator());
    }
}
