package dev.bossestesting;

import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Anims;
import dev.bossestesting.anim.Clip;
import dev.bossestesting.anim.Loop;
import dev.bossestesting.encounter.BossDefs;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Variants;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure checks of the addon (no game): run with {@code ./gradlew checkBoss}.
 * 1. Every Calamidade registration (effects, timelines, beams, lights, terrain edits, skies, boss controllers) is
 *    built and validated by the engine's own builders, as at mod construction.
 * 2. Every clip event used by the code exists in the generated table (a typo would only fail mid-fight).
 * 3. Timing facts the fight depends on: landing impact at 1.5 s, missile launch effective end, chains.
 */
public final class BossChecks {
    static int fails;

    static void check(boolean ok, String what) {
        if (!ok) { fails++; System.out.println("FALHA: " + what); }
    }

    public static void main(String[] args) throws Exception {
        // 1. registrations
        try {
            Fx.register();
            BossDefs.register();
            System.out.println("ok: registros do Calamidade (efeitos, timelines, feixes, luzes, terreno, céu, chefes)");
        } catch (Throwable t) {
            fails++;
            System.out.println("FALHA nos registros: " + t);
            t.printStackTrace(System.out);
        }
        int effects = 0;
        for (Field f : Fx.class.getFields()) {
            Object v = f.get(null);
            if (v instanceof ResourceLocation) effects++;
            if (v instanceof Variants) effects += 4;
        }
        System.out.println("ok: " + effects + " ids de efeito");

        // 2. every on(CLIP, "event") / Fx.t(CLIP, "event") / since(CLIP, "event") in the sources exists
        Pattern use = Pattern.compile("(?:on|t|tickOf|since|at)\\((?:c|f|s|JG_[A-Z_]+|P2_[A-Z_]+|MS_[A-Z_]+), \"([a-z0-9_]+)\"");
        Pattern clipVar = Pattern.compile("(?:Clip (?:c|f|s) =|c ==) ((?:JG|P2|MS)_[A-Z0-9_]+)");
        int uses = 0;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                List<String> clips = new ArrayList<>();
                Matcher cv = clipVar.matcher(src);
                while (cv.find()) clips.add(cv.group(1));
                Matcher tern = Pattern.compile("Clip (?:c|f|s) = [^;]*").matcher(src);
                while (tern.find()) {
                    Matcher tok = Pattern.compile("(?:JG|P2|MS)_[A-Z0-9_]+").matcher(tern.group());
                    while (tok.find()) clips.add(tok.group());
                }
                Matcher m = Pattern.compile("\\b(?:on|Fx\\.t|t|tickOf|since)\\(((?:JG|P2|MS)_[A-Z0-9_]+), \"([a-z0-9_]+)\"").matcher(src);
                while (m.find()) {
                    uses++;
                    Clip c = (Clip) Anims.class.getField(m.group(1)).get(null);
                    check(c.has(m.group(2)), p.getFileName() + ": " + m.group(1) + " não tem o evento " + m.group(2));
                }
                // events through a local Clip variable: must exist in at least one clip assigned in the file
                Matcher m2 = Pattern.compile("\\b(?:on|t|tickOf|since)\\((?:c|f|s), \"([a-z0-9_]+)\"").matcher(src);
                while (m2.find()) {
                    uses++;
                    boolean found = false;
                    for (String name : clips) found |= ((Clip) Anims.class.getField(name).get(null)).has(m2.group(1));
                    check(found, p.getFileName() + ": evento " + m2.group(1) + " não existe nos clipes " + clips);
                }
            }
        }
        System.out.println("ok: " + uses + " usos de eventos conferidos com as animações");

        // 3. timing facts
        check(Anims.JG_SPAWN_LANDING.at("impact") == 30, "spawn_landing: impacto em 1,5 s (30 ticks)");
        check(Anims.JG_SPAWN_LANDING.external().containsAll(List.of("impact", "descent_start", "landing_prep")), "spawn_landing: eventos externos (sem instruções internas)");
        check(Anims.JG_WALK.external().contains("left_heel_strike") && Anims.JG_RUN.external().contains("right_foot_strike"), "walk/run: passos externos");
        check(Anims.MS_LAUNCH.endTick() == 9 && Anims.MS_LAUNCH.ticks() == 8, "míssil: launch declara 8 ticks e termina no 9 (keyframe 0,417 s)");
        check(Anims.P2_OVERLOAD_V2.has("punch_reactor") && Anims.P2_OVERLOAD_V2.at("punch_reactor") == Anims.P2_OVERLOAD_V2.at("reactor_surge"),
            "overload_v2: punch_reactor (só no arquivo de eventos) cai no mesmo tick de reactor_surge");
        check(Anims.JG_RUN.loop() == Loop.LOOP && Anims.P2_OVERLOADED_SHIELD_CHARGE_LOOP.loop() == Loop.LOOP, "laços: run e shield_charge_loop");
        check(Fx.t(Anims.JG_GROUND_SLAM, "impact") == Brain.LEAD + 40, "ground_slam: impacto no tick 40 + LEAD");
        check(Fx.swapTick() > Fx.t(Anims.JG_OVERLOAD, "pause_start"), "a troca acontece depois do reator laranja apagar");
        check(Fx.deathFinalTick() > Fx.t(Anims.P2_DEATH, "pilot_still"), "explosão final depois de pilot_still");
        check(Effects.MAX_TICKS >= 240, "duração dos céus");

        // Landing height profile: starts at the top, lands on the impact tick, never goes up.
        double prev = Double.MAX_VALUE;
        int impact = Fx.t(Anims.JG_SPAWN_LANDING, "impact");
        for (int a = 0; a <= impact; a++) {
            double s = Math.min(1, a / (double) impact);
            double f = s < .55 ? .7 * Math.pow(s / .55, 2) : s < .85 ? .7 + .18 * ((s - .55) / .3) : .88 + .12 * Math.pow((s - .85) / .15, 2);
            double y = 100 + 36 * (1 - f);
            check(y <= prev + 1e-9, "pouso: altura não sobe (tick " + a + ")");
            prev = y;
        }
        check(Math.abs(prev - 100) < 1e-9, "pouso: chega ao chão exatamente no impacto");

        System.out.println(fails == 0 ? "checkBoss: tudo certo" : "checkBoss: " + fails + " falha(s)");
        if (fails > 0) System.exit(1);
    }
}
