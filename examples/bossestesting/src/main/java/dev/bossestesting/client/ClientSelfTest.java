package dev.bossestesting.client;

import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Client run of the whole fight with screenshots, only with {@code -Dbossestesting.clienttest=true}
 * (./gradlew -p examples/bossestesting runClientTest, under xvfb-run). It drives the integrated server with the
 * addon's own commands, captures frames at the key moments (landing, beam, transformation, reveal, barrage,
 * Overloaded, leap smash, death) into screenshots/, checks that renderers, shaders, bars and particles ran without
 * errors and closes the game. Software rendering (llvmpipe) only proves that the paths run, not the look on a GPU.
 */
public final class ClientSelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("bossestesting.clienttest");
    private static final TreeMap<Integer, Runnable> SCRIPT = new TreeMap<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int ticks = -1;
    private static int bossFrames;

    private ClientSelfTest() {}

    static void cmd(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        var player = Minecraft.getInstance().player;
        if (server == null || player == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp == null) return;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(sp).withPosition(sp.position())
                .withRotation(sp.getRotationVector()).withLevel(sp.serverLevel()), command);
            REPORT.add("cmd  /" + command);
        });
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "bt_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
        REPORT.add("shot " + name);
    }

    static void at(int tick, Runnable r) { SCRIPT.merge(tick, r, (a, b) -> () -> { a.run(); b.run(); }); }

    static {
        int t = 140;
        at(t, () -> { cmd("tp @s ~ ~ ~ 0 -8"); cmd("effect give @s minecraft:resistance infinite 4 true"); cmd("effect give @s minecraft:regeneration infinite 4 true"); cmd("time set 18000"); });
        at(t + 5, () -> cmd("bossestesting spawn"));
        at(t + 20, () -> shot("01_descida"));
        at(t + 36, () -> shot("02_impacto"));
        at(t + 70, () -> shot("03_cratera_barra"));
        at(t + 110, () -> cmd("bossestesting attack reactor"));
        at(t + 110 + 56, () -> shot("04_reator_carga"));
        at(t + 110 + 64, () -> shot("05_feixe"));
        at(t + 220, () -> cmd("bossestesting test sky p1"));
        at(t + 235, () -> cmd("tp @s ~ ~ ~ 0 -60"));
        at(t + 290, () -> shot("06_ceu_p1"));
        at(t + 292, () -> cmd("bossestesting test sky off"));
        at(t + 295, () -> cmd("tp @s ~ ~ ~ 0 -8"));
        at(t + 300, () -> cmd("bossestesting transform"));
        at(t + 300 + 60, () -> shot("07_pulsos"));
        at(t + 300 + 110, () -> shot("08_escuro"));
        at(t + 300 + 131, () -> shot("09_liberacao"));
        at(t + 300 + 150, () -> shot("10_revelacao_ciano"));
        at(t + 520, () -> cmd("bossestesting attack missile_barrage"));
        at(t + 520 + 60, () -> shot("11_barragem"));
        at(t + 640, () -> cmd("bossestesting overloaded on"));
        at(t + 640 + 24, () -> shot("12_overload_v2"));
        at(t + 760, () -> cmd("tp @s ~ ~ ~ 0 -50"));
        at(t + 800, () -> shot("13_ceu_sobrecarregado"));
        at(t + 805, () -> cmd("tp @s ~ ~ ~ 0 -8"));
        at(t + 810, () -> cmd("bossestesting attack leap_smash"));
        at(t + 810 + 20, () -> shot("14_salto"));
        at(t + 810 + 36, () -> shot("15_impacto_salto"));
        at(t + 960, () -> cmd("bossestesting damage 100000"));
        at(t + 960 + 25, () -> shot("16_morte_pods"));
        at(t + 960 + 140, () -> shot("17_explosao_final"));
        at(t + 1180, () -> shot("18_depois"));
        at(t + 1200, ClientSelfTest::finish);
    }

    /** Client tick. */
    public static void tick() {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ticks++;
        if (!mc.level.getEntitiesOfClass(GuilherminoBoss.class, new AABB(mc.player.blockPosition()).inflate(96)).isEmpty()) bossFrames++;
        Runnable r = SCRIPT.remove(ticks);
        if (r != null) {
            try { r.run(); } catch (RuntimeException e) { REPORT.add("FAIL " + e); }
        }
    }

    static void finish() {
        Minecraft mc = Minecraft.getInstance();
        boolean finaleSeen = REPORT.stream().anyMatch(s -> s.contains("revelacao"));
        REPORT.add("ticks com chefe visível no cliente: " + bossFrames);
        REPORT.add("finale alcançado: " + finaleSeen);
        REPORT.add("GuilherminoFinale no fim: " + !mc.level.getEntitiesOfClass(GuilherminoFinale.class, new AABB(mc.player.blockPosition()).inflate(96)).isEmpty());
        try { Files.write(new File(mc.gameDirectory, "bossestesting-clienttest.txt").toPath(), REPORT); } catch (Exception ignored) {}
        REPORT.forEach(s -> System.out.println("[clienttest] " + s));
        mc.stop();
    }
}
