package dev.bossestesting.selftest;

import dev.bossestesting.BtConfig;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.command.Spawner;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.encounter.Encounters;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.GuilherminoMissile;
import dev.bossestesting.entity.JarGolem;
import dev.calamidade_awakening.api.v1.Bosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Automated fight on a dedicated server without players, only with {@code -Dbossestesting.selftest=true}
 * (./gradlew selfTest). Spawns the boss with the landing, forces every attack of both forms, transforms, kills it,
 * and checks: no failed events, damage on mobs, real terrain changes, one controller at a time, loot once, cleanup.
 * Report: run/bossestesting-selftest.txt. The server stops at the end. Inert in normal games.
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("bossestesting.selftest");

    private record Step(String name, Runnable start, BooleanSupplier done, int timeout) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int index = -1, waited, fails, passes;
    private static boolean started;
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Vec3 center;
    private static GuilherminoBoss boss;
    private static Zombie dummy;
    private static int solidBefore;
    private static int missilesSeen;

    private SelfTest() {}

    public static void register() {
        if (!ENABLED) return;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> { server = e.getServer(); started = true; });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick());
    }

    static void check(boolean ok, String what) {
        if (ok) passes++; else fails++;
        REPORT.add((ok ? "PASS " : "FAIL ") + what);
    }

    static void note(String s) { REPORT.add("     " + s); }

    private static void tick() {
        if (!started) return;
        if (index < 0) { setup(); index = 0; waited = 0; STEPS.get(0).start().run(); return; }
        if (index >= STEPS.size()) return;
        if (boss != null) for (var m : level.getEntitiesOfClass(GuilherminoMissile.class, new AABB(center, center).inflate(96))) missilesSeen = Math.max(missilesSeen, m.getId());
        Step s = STEPS.get(index);
        waited++;
        boolean done;
        try { done = s.done().getAsBoolean(); } catch (RuntimeException e) { done = true; check(false, s.name() + " lançou " + e); }
        if (!done && waited < s.timeout()) return;
        if (!done) check(false, s.name() + ": tempo esgotado (" + s.timeout() + " ticks)");
        else REPORT.add("ok   " + s.name() + " (" + waited + " ticks)");
        System.out.println("[selftest] " + REPORT.get(REPORT.size() - 1));
        index++;
        waited = 0;
        if (index < STEPS.size()) {
            try { STEPS.get(index).start().run(); } catch (RuntimeException e) { check(false, STEPS.get(index).name() + " (início) lançou " + e); }
        } else finish();
    }

    private static boolean idle() { return boss != null && (boss.brain() == null || boss.brain().action() == null); }

    private static GuilherminoFinale finale() {
        return level.getEntitiesOfClass(GuilherminoFinale.class, new AABB(center, center).inflate(96)).stream().findFirst().orElse(null);
    }

    private static void force(String move, double dummyDistance) {
        placeDummy(dummyDistance);
        boss.setTarget(dummy);
        check(boss.brain().force(move), "ataque disponível: " + move);
    }

    private static void placeDummy(double distance) {
        Vec3 f = boss.getLookAngle().multiply(1, 0, 1);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        Vec3 p = Spawner.ground(level, boss.position().add(f.normalize().scale(distance)));
        if (dummy == null || !dummy.isAlive()) spawnDummy(p); else dummy.teleportTo(p.x, p.y, p.z);
        dummy.setHealth(dummy.getMaxHealth());
    }

    private static void spawnDummy(Vec3 p) {
        dummy = EntityType.ZOMBIE.create(level);
        dummy.moveTo(p.x, p.y, p.z, 0, 0);
        dummy.setNoAi(true);
        dummy.setPersistenceRequired();
        var hp = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(1000);
        dummy.setHealth(1000);
        level.addFreshEntity(dummy);
    }

    private static int solid() {
        int n = 0;
        BlockPos c = BlockPos.containing(center);
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) for (int y = -12; y <= 4; y++)
            if (!level.getBlockState(c.offset(x, y, z)).isAir()) n++;
        return n;
    }

    private static void setup() {
        level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        center = Spawner.ground(level, Vec3.atCenterOf(spawn));
        for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) level.setChunkForced((spawn.getX() >> 4) + cx, (spawn.getZ() >> 4) + cz, true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        BtConfig.DESTRUCTION.set(BtConfig.Destruction.NORMAL);
        REPORT.add("BossesTesting 0.1.0 — autoteste em servidor dedicado (sem jogadores), centro " + BlockPos.containing(center).toShortString());
        solidBefore = solid();

        STEPS.add(new Step("pouso (spawn_landing, queda física, impacto em 1,5 s)", () -> {
            spawnDummy(center.add(5, 0, 0));
            var s = Spawner.phase1(level, center, 0);
            boss = s.boss();
            note("altura da queda: " + s.height() + " blocos");
        }, () -> idle() && boss.onGround(), 200));
        STEPS.add(new Step("pós-pouso", () -> {
            check(boss.onGround() && boss.getY() <= center.y + 1 && boss.getY() >= center.y - 6,
                "pouso: o chefe está no chão, dentro da cratera (y " + String.format("%.1f", boss.getY()) + ", chão original " + center.y + ")");
            check(dummy.getHealth() < dummy.getMaxHealth(), "pouso: dano no mob a 5 blocos (" + dummy.getHealth() + ")");
            check(Bosses.get(boss).isPresent(), "controlador da Boss API anexado à primeira forma");
        }, () -> true, 1));
        for (String m : List.of("slam", "reactor", "punch_right", "dash", "stagger", "fury")) {
            double d = m.equals("reactor") ? 12 : m.equals("dash") ? 16 : 3.5;
            STEPS.add(new Step("fase 1: " + m, () -> force(m, d), SelfTest::idle, 400));
            STEPS.add(new Step("dano de " + m, () -> {
                if (!m.equals("stagger") && !m.equals("fury") && !m.equals("dash"))
                    check(dummy.getHealth() < dummy.getMaxHealth(), m + ": acertou o mob (" + dummy.getHealth() + "/1000)");
            }, () -> true, 1));
        }
        STEPS.add(new Step("transformação (dano letal → overload → troca de entidade)", () -> {
            boss.hurt(boss.damageSources().generic(), 100000);
            check(boss.isAlive() && boss.getHealth() >= 1, "dano letal não matou a primeira forma (vida " + boss.getHealth() + ")");
        }, () -> finale() != null && !(boss.isAlive() && boss instanceof JarGolem), 260));
        STEPS.add(new Step("segunda forma revelada", () -> {
            GuilherminoFinale f = finale();
            check(f != null, "a segunda forma existe");
            check(boss.isRemoved(), "a primeira forma foi removida (sem loot)");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(40)).isEmpty(), "nenhum loot prematuro");
            if (f != null) {
                check(Bosses.get(f).isPresent(), "novo controlador anexado à segunda forma");
                check(Bosses.active(server).size() == 1, "um controlador ativo (uma BossBar): " + Bosses.active(server).size());
                Encounter e = Encounters.of(f, level);
                check(e.stage() == Encounter.Stage.PHASE2, "encontro na fase 2");
                check(Math.abs(f.getHealth() - f.getMaxHealth()) < 1, "vida da segunda forma conforme o balanceamento (" + f.getHealth() + ")");
                boss = f;
            }
        }, SelfTest::idle, 120));
        STEPS.add(new Step("música na revelação", () -> check(Encounters.of(boss, level).music(), "música da fase 2 ligada na revelação"), () -> true, 1));
        for (String m : List.of("slam_v2", "missile_barrage", "punch_stream", "exposed_reactor", "overload_v2", "charged_punch", "shield_charge", "leap_smash")) {
            double d = switch (m) { case "missile_barrage" -> 20; case "exposed_reactor" -> 14; case "shield_charge", "leap_smash" -> 16; default -> 3.5; };
            STEPS.add(new Step("fase 2: " + m, () -> {
                if (m.equals("shield_charge")) wall();
                force(m, d);
            }, () -> idle() && (!m.equals("missile_barrage") || level.getEntitiesOfClass(GuilherminoMissile.class, new AABB(center, center).inflate(96)).isEmpty()), 500));
            STEPS.add(new Step("resultado de " + m, () -> {
                if (m.equals("shield_charge")) check(wallStone() < 35, "shield_charge: brecha aberta na parede pela Terrain API (pedras restantes " + wallStone() + "/35)");
                if (m.equals("overload_v2")) check(boss.flag(GuilherminoBoss.F_OVERLOADED), "overload_v2 ativou o estado Sobrecarregado");
                if (m.equals("missile_barrage")) check(missilesSeen > 0, "a barragem lançou mísseis");
                if (!m.equals("overload_v2")) check(dummy.getHealth() < dummy.getMaxHealth(), m + ": acertou o mob (" + dummy.getHealth() + "/1000)");
            }, () -> true, 1));
        }
        STEPS.add(new Step("morte cinematográfica", () -> boss.hurt(boss.damageSources().genericKill(), 1_000_000),
            () -> boss.isRemoved(), 400));
        STEPS.add(new Step("pós-morte", () -> {
            long stars = level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(64), i -> i.getItem().is(Items.NETHER_STAR)).size();
            check(stars == 1, "loot entregue uma vez (nether star: " + stars + ")");
            check(Bosses.active(server).isEmpty(), "nenhum controlador ativo depois da derrota");
        }, () -> true, 1));
        STEPS.add(new Step("limpeza do encontro", () -> {}, () -> Encounters.all().isEmpty(), 60));
        STEPS.add(new Step("terreno", () -> {}, () -> waited > 80, 120));
    }

    private static BlockPos wallBase;
    private static Vec3 wallRight;

    private static int wallStone() {
        int n = 0;
        for (int i = -3; i <= 3; i++) for (int y = 0; y < 5; y++)
            if (level.getBlockState(BlockPos.containing(Vec3.atBottomCenterOf(wallBase).add(wallRight.scale(i)).add(0, y, 0))).is(Blocks.STONE)) n++;
        return n;
    }

    /** A stone wall in the shield charge path (the breach must open it through the Terrain API). */
    private static void wall() {
        Vec3 f = boss.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 base = boss.position().add(f.scale(8));
        wallBase = BlockPos.containing(base);
        wallRight = r;
        for (int i = -3; i <= 3; i++) for (int y = 0; y < 5; y++)
            level.setBlockAndUpdate(BlockPos.containing(base.add(r.scale(i)).add(0, y, 0)), Blocks.STONE.defaultBlockState());
    }

    private static void finish() {
        int after = solid();
        check(after != solidBefore, "destruição real pela Terrain API: blocos sólidos na arena " + solidBefore + " → " + after);
        check(Brain.ERRORS.get() == 0, "nenhum evento de ação falhou (" + Brain.ERRORS.get() + ")");
        REPORT.add("");
        REPORT.add("Resultado: " + passes + " ok, " + fails + " falha(s)");
        try {
            Path out = server.getServerDirectory().resolve("bossestesting-selftest.txt");
            Files.write(out, REPORT);
        } catch (IOException e) {
            REPORT.forEach(System.out::println);
        }
        REPORT.forEach(l -> System.out.println("[selftest] " + l));
        server.halt(false);
    }
}
