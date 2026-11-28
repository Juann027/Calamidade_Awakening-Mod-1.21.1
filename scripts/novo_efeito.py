#!/usr/bin/env python3
"""Scaffolds a Calamidade_Awakening addon effect that compiles without edits (0.15.0).

Usage:
    python3 scripts/novo_efeito.py <namespace> <name> [--project DIR] [--package PACKAGE] [--card] [--force]

(The Portuguese flags of earlier versions, --projeto, --pacote, --cartao and --forcar, still work.)

Example, inside this repository:
    python3 scripts/novo_efeito.py vfxsample solar_ray --project examples/vfx-sample --card

Creates, inside the addon project (default: the current directory):
  - src/main/java/<package>/<Class>.java: definition, timeline, cue, test command and client post layer.
    The class registers itself: @EventBusSubscriber + FMLConstructModEvent (during mod construction, which is when
    the engine accepts registrations) and FMLClientSetupEvent in the inner Client class. Nothing to edit in your @Mod.
  - src/main/resources/assets/<namespace>/shaders/fx/<name>.fsh: the pass shader, using calamidade_fx.glsl.
  - src/main/resources/assets/<namespace>/shaders/fx/calamidade_fx.glsl: a copy of the engine library (the RimuLib
    #include only looks in the shader's own namespace).
  - en_us and pt_br translations (merged into existing files).
  - with --card: Addons.publish (a card in the Atlas and the Focus), card texts and a provisional 64x32 artwork.

Test in game: /<namespace> effect <name> (command permission). Next steps: AGENTS.md and docs/ai/receitas/.
"""
from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zlib
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
BIBLIOTECA = RAIZ / "src/main/resources/assets/calamidade_awakening/shaders/fx/calamidade_fx.glsl"


def camel(nome: str) -> str:
    return "".join(parte.capitalize() for parte in nome.split("_") if parte) or "Effect"


def titulo(nome: str) -> str:
    return " ".join(parte.capitalize() for parte in nome.split("_") if parte)


def cor_do_nome(nome: str) -> tuple[int, int, int]:
    """A vivid, stable color from the name (hue from the hash)."""
    h = (zlib.crc32(nome.encode()) % 360) / 360.0
    i, f = int(h * 6), h * 6 - int(h * 6)
    q, t = 1 - f, f
    r, g, b = [(1, t, 0), (q, 1, 0), (0, 1, t), (0, q, 1), (t, 0, 1), (1, 0, q)][i % 6]
    return tuple(int(80 + 175 * c) for c in (r, g, b))


def detectar_pacote(projeto: Path, namespace: str) -> str:
    java = projeto / "src/main/java"
    for arquivo in sorted(java.rglob("*.java")) if java.is_dir() else []:
        texto = arquivo.read_text(encoding="utf-8", errors="ignore")
        if re.search(r'@Mod\(\s*"%s"\s*\)' % re.escape(namespace), texto):
            pacote = re.search(r"^package\s+([\w.]+);", texto, re.M)
            if pacote:
                return pacote.group(1) + ".effects"
    return namespace.replace("-", "_").replace(".", "_") + ".effects"


def png(largura: int, altura: int, cor: tuple[int, int, int]) -> bytes:
    """RGBA PNG with a gradient of the effect color and a light ring: provisional card artwork."""
    linhas = bytearray()
    cx, cy = largura / 2, altura / 2
    for y in range(altura):
        linhas.append(0)
        for x in range(largura):
            k = 0.35 + 0.65 * (1 - y / altura)
            d = abs(((x - cx) ** 2 + ((y - cy) * 2) ** 2) ** 0.5 - altura * 0.6)
            anel = max(0.0, 1 - d / 2.5)
            px = [min(255, int(c * k + 255 * anel * 0.8)) for c in cor]
            linhas += bytes(px + [255])
    def bloco(tipo: bytes, dados: bytes) -> bytes:
        return struct.pack(">I", len(dados)) + tipo + dados + struct.pack(">I", zlib.crc32(tipo + dados) & 0xFFFFFFFF)
    cabecalho = struct.pack(">IIBBBBB", largura, altura, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + bloco(b"IHDR", cabecalho) + bloco(b"IDAT", zlib.compress(bytes(linhas), 9)) + bloco(b"IEND", b"")


def java(pacote: str, classe: str, ns: str, nome: str, cor: tuple[int, int, int], cartao: bool) -> str:
    argb = "0xff%02x%02x%02x" % cor
    publicar = f"""
        // Card in the Atlas and the Focus (100-tick cooldown). The artwork lives in
        // assets/calamidade_awakening/textures/gui/sprites/skill/focus/addon/{ns}/{nome}.png.
        Addons.publish(ID, new Addons.Card("ability.{ns}.{nome}", "{titulo(nome)}", "CUSTOM", 100, null, List.of()));""" if cartao else ""
    import_list = "import java.util.List;\n" if cartao else ""
    return f"""package {pacote};

import dev.calamidade_awakening.api.v1.Addons;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
{import_list}import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Effect {ns}:{nome}, generated by scripts/novo_efeito.py (Calamidade_Awakening 0.15.0).
 *
 * <p>Automatic registration: {{@link #construct}} runs during mod construction (FMLConstructModEvent) and
 * {{@link Client#setup}} in the client setup. Test: /{ns} effect {nome}. Next steps: AGENTS.md and docs/ai/receitas/
 * in the Calamidade_Awakening repository.
 */
@EventBusSubscriber(modid = "{ns}")
public final class {classe} {{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("{ns}", "{nome}");

    /** Script in ticks: intro, impact at tick 30 and outro. The server, the shader and the camera read the same object. */
    static final Timeline TIMELINE = Timeline.builder(100)
        .phase("intro", 0, 30, 20, 0)
        .phase("impact", 30, 50, 0, 15)
        .phase("outro", 50, 100, 0, 40)
        .track("intensity", keyframe(0, 0f), keyframe(30, 1f, Easing.OVERSHOOT), keyframe(100, 0f, Easing.EASE_OUT))
        .marker("impact", 30)
        .build();

    private {classe}() {{}}

    /** Mod construction, on both sides. Required order: register, timeline, atmosphere, cues. */
    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {{
        Addons.resources("{ns}", {classe}.class); // RimuLib finds the shaders of this JAR (repeating it is harmless)
        // 100 ticks, radius 6 blocks, main color, no built-in shape or material (everything comes from the pass).
        Effects.register(ID, new Effects.Definition(100, 6, {argb}, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "impact", ctx -> {{
            // Server, on the tick of the "impact" marker: damage, knockback or cost (docs/GAMEPLAY-0.5.md).
        }});{publicar}
    }}

    /** /{ns} effect {nome}: starts the effect 6 blocks ahead, along the line of sight (command permission). */
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {{
        event.getDispatcher().register(Commands.literal("{ns}").requires(source -> source.hasPermission(2))
            .then(Commands.literal("effect").then(Commands.literal("{nome}").executes(ctx -> {{
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(6));
                boolean ok = Effects.start(ID, player.serverLevel(), at).isPresent();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                    ok ? "command.{ns}.{nome}.started" : "command.{ns}.{nome}.failed"), false);
                return ok ? 1 : 0;
            }}))));
    }}

    /** Client only (the class is not loaded on a dedicated server). */
    @EventBusSubscriber(modid = "{ns}", value = Dist.CLIENT)
    public static final class Client {{
        private Client() {{}}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {{
            AddonLayers.post(ID, post -> {{
                var t = post.timeline();
                float intensity = t.value("intensity");
                if (intensity <= .001f) return;
                post.pass("{ns}:{nome}", u -> {{
                    u.v3("EffectCenter", post.center());
                    u.f4("EffectState", intensity, t.weight("impact"), post.time().opacity(), (float) post.time().radius());
                    u.f1("EffectSince", (float) t.since("impact"));
                }});
                post.bloom(t.weight("impact") * .2f);
            }});
        }}
    }}
}}
"""


def shader(ns: str, nome: str, cor: tuple[int, int, int]) -> str:
    r, g, b = (c / 255 for c in cor)
    return f"""// Shader of the effect {ns}:{nome} (generated by scripts/novo_efeito.py). RimuLib prepends #version 150 and common.glsl.
uniform vec3 EffectCenter;  // center relative to the camera (post.center()), the same space as scenePosAt
uniform vec4 EffectState;   // x: "intensity" track, y: weight of the "impact" phase, z: opacity, w: radius (blocks)
uniform float EffectSince;  // ticks since the "impact" marker (negative before)

#include "calamidade_fx.glsl"

void main() {{
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 tint = vec3({r:.3f}, {g:.3f}, {b:.3f});
    vec3 rd = viewDir(texCoord);
    float scene = length(scenePosAt(texCoord));
    // Energy shell: a sphere with a bright rim (fresnel), hidden by terrain in front of it.
    float radius = EffectState.w * (0.4 + 0.6 * EffectState.x);
    vec2 t = caRaySphere(vec3(0.0), rd, EffectCenter, radius);
    if (t.x > 0.0 && t.x < scene) {{
        vec3 n = normalize(rd * t.x - EffectCenter);
        float rim = pow(1.0 - abs(dot(n, rd)), 2.5);
        color += tint * rim * EffectState.x * EffectState.z * 1.5;
    }}
    // Ground wave from the impact.
    if (!skyAt(texCoord)) {{
        vec3 d = scenePosAt(texCoord) - EffectCenter;
        float wave = caDistanceWave(length(d.xz), EffectSince, 0.4, 0.6, 18.0);
        color += tint * wave * (1.0 - smoothstep(2.0, 5.0, abs(d.y))) * EffectState.z;
    }}
    fragColor = vec4(color, 1.0);
}}
"""


def mesclar_json(arquivo: Path, valores: dict[str, str]) -> None:
    dados = json.loads(arquivo.read_text(encoding="utf-8")) if arquivo.exists() else {}
    dados.update(valores)
    arquivo.parent.mkdir(parents=True, exist_ok=True)
    arquivo.write_text(json.dumps(dados, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="Scaffolds a Calamidade_Awakening addon effect.")
    p.add_argument("namespace", help="your mod's namespace (the same as in @Mod), e.g. mymod")
    p.add_argument("nome", metavar="name", help="effect name in [a-z0-9_], e.g. solar_ray")
    p.add_argument("--project", "--projeto", dest="projeto", default=".", help="root of the addon Gradle project (default: current directory)")
    p.add_argument("--package", "--pacote", dest="pacote", help="Java package (default: package of the @Mod class + .effects)")
    p.add_argument("--card", "--cartao", dest="cartao", action="store_true", help="publish a card in the Atlas and the Focus")
    p.add_argument("--force", "--forcar", dest="forcar", action="store_true", help="overwrite existing files")
    a = p.parse_args()

    if not re.fullmatch(r"[a-z0-9_.-]+", a.namespace) or a.namespace in ("calamidade_awakening", "minecraft"):
        print("invalid or reserved namespace: " + a.namespace, file=sys.stderr)
        return 2
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", a.nome):
        print("invalid name (use [a-z0-9_], starting with a letter): " + a.nome, file=sys.stderr)
        return 2
    projeto = Path(a.projeto).resolve()
    if not (projeto / "build.gradle").exists() and not (projeto / "build.gradle.kts").exists():
        print(f"{projeto} does not look like a Gradle project (no build.gradle)", file=sys.stderr)
        return 2
    pacote = a.pacote or detectar_pacote(projeto, a.namespace)
    if not re.fullmatch(r"[a-z_][\w]*(\.[a-z_][\w]*)*", pacote):
        print("invalid Java package: " + pacote, file=sys.stderr)
        return 2
    classe = camel(a.nome)
    cor = cor_do_nome(a.nome)
    recursos = projeto / "src/main/resources/assets"
    arquivos = {
        projeto / "src/main/java" / pacote.replace(".", "/") / f"{classe}.java": java(pacote, classe, a.namespace, a.nome, cor, a.cartao),
        recursos / a.namespace / "shaders/fx" / f"{a.nome}.fsh": shader(a.namespace, a.nome, cor),
    }
    existentes = [str(f) for f in arquivos if f.exists()]
    if existentes and not a.forcar:
        print("Already exist (use --force to overwrite):\n  " + "\n  ".join(existentes), file=sys.stderr)
        return 1
    for arquivo, texto in arquivos.items():
        arquivo.parent.mkdir(parents=True, exist_ok=True)
        arquivo.write_text(texto, encoding="utf-8")
        print("created  " + str(arquivo.relative_to(projeto)))

    biblioteca = recursos / a.namespace / "shaders/fx/calamidade_fx.glsl"
    nova = BIBLIOTECA.read_text(encoding="utf-8")
    if not biblioteca.exists() or biblioteca.read_text(encoding="utf-8") != nova:
        biblioteca.write_text(nova, encoding="utf-8")
        print("copied   " + str(biblioteca.relative_to(projeto)) + " (engine GLSL library)")

    t = titulo(a.nome)
    en = {f"effect.{a.namespace}.{a.nome}": t,
          f"command.{a.namespace}.{a.nome}.started": f"{t} started.",
          f"command.{a.namespace}.{a.nome}.failed": f"Could not start {t} here."}
    pt = {f"effect.{a.namespace}.{a.nome}": t,
          f"command.{a.namespace}.{a.nome}.started": f"{t} iniciado.",
          f"command.{a.namespace}.{a.nome}.failed": f"Não foi possível iniciar {t} aqui."}
    if a.cartao:
        chave = f"addon/{a.namespace}/{a.nome}"
        en |= {f"ability.{a.namespace}.{a.nome}": t, f"key.calamidade_awakening.focus.{chave}": t,
               f"skill.calamidade_awakening.focus.{chave}.desc": f"{t}: an energy shell and a ground wave on impact."}
        pt |= {f"ability.{a.namespace}.{a.nome}": t, f"key.calamidade_awakening.focus.{chave}": t,
               f"skill.calamidade_awakening.focus.{chave}.desc": f"{t}: uma casca de energia e uma onda no chão no impacto."}
        arte = recursos / "calamidade_awakening/textures/gui/sprites/skill/focus/addon" / a.namespace / f"{a.nome}.png"
        if not arte.exists() or a.forcar:
            arte.parent.mkdir(parents=True, exist_ok=True)
            arte.write_bytes(png(64, 32, cor))
            print("created  " + str(arte.relative_to(projeto)) + " (provisional card artwork)")
    for lang, valores in (("en_us", en), ("pt_br", pt)):
        mesclar_json(recursos / a.namespace / "lang" / f"{lang}.json", valores)
        print(f"translations in assets/{a.namespace}/lang/{lang}.json")

    print(f"""
Done: {a.namespace}:{a.nome} ({pacote}.{classe}).
1. Build the addon (the Calamidade JAR and RimuLib on the classpath; see docs/ai/usar-como-dependencia.md) and check the
   manifest: calamidade_awakening with the versionRange of the engine you build against (e.g. "[0.15.0,0.16.0)") and
   rimulib "[1.3.0,2.0)".
2. In game: /{a.namespace} effect {a.nome}{" or the card in the Atlas (N)" if a.cartao else ""}.
3. Edit the timeline, the shader and the cue. Ready-made recipes: docs/ai/receitas/. Budgets and rules: AGENTS.md.""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
