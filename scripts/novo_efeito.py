#!/usr/bin/env python3
"""Gera o esqueleto de um efeito de addon do Calamidade_Awakening 0.8.0 que compila sem edição.

Uso:
    python3 scripts/novo_efeito.py <namespace> <nome> [--projeto DIR] [--pacote PACOTE] [--cartao] [--forcar]

Exemplo, dentro deste repositório:
    python3 scripts/novo_efeito.py vfxsample raio_solar --projeto examples/vfx-sample --cartao

Gera, dentro do projeto do addon (padrão: diretório atual):
  - src/main/java/<pacote>/<Classe>.java: definição, timeline, cue, comando de teste e camada post de cliente.
    A classe se registra sozinha: @EventBusSubscriber + FMLConstructModEvent (durante a construção do mod, que é
    quando o motor aceita registros) e FMLClientSetupEvent na classe interna Client. Nada a editar no seu @Mod.
  - src/main/resources/assets/<namespace>/shaders/fx/<nome>.fsh: shader do passe, usando calamidade_fx.glsl.
  - src/main/resources/assets/<namespace>/shaders/fx/calamidade_fx.glsl: cópia da biblioteca do motor (o
    #include da RimuLib só procura no namespace do próprio shader).
  - traduções en_us e pt_br (mescladas aos arquivos existentes).
  - com --cartao: Addons.publish (cartão no Atlas e no Focus), textos do cartão e uma arte 64x32 provisória.

Teste no jogo: /<namespace> efeito <nome> (permissão de comandos). Próximos passos: AGENTS.md e docs/ai/receitas/.
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
    return "".join(parte.capitalize() for parte in nome.split("_") if parte) or "Efeito"


def titulo(nome: str) -> str:
    return " ".join(parte.capitalize() for parte in nome.split("_") if parte)


def cor_do_nome(nome: str) -> tuple[int, int, int]:
    """Cor viva e estável a partir do nome (matiz pelo hash)."""
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
                return pacote.group(1) + ".efeitos"
    return namespace.replace("-", "_").replace(".", "_") + ".efeitos"


def png(largura: int, altura: int, cor: tuple[int, int, int]) -> bytes:
    """PNG RGBA com um gradiente da cor do efeito e um anel claro: arte provisória do cartão."""
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
        // Cartão no Atlas e no Focus (recarga de 100 ticks). A arte fica em
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
 * Efeito {ns}:{nome}, gerado por scripts/novo_efeito.py (Calamidade_Awakening 0.8.0).
 *
 * <p>Registro automático: {{@link #construir}} roda durante a construção do mod (FMLConstructModEvent) e
 * {{@link Client#setup}} no setup do cliente. Teste: /{ns} efeito {nome}. Próximos passos: AGENTS.md e
 * docs/ai/receitas/ do repositório do Calamidade_Awakening.
 */
@EventBusSubscriber(modid = "{ns}")
public final class {classe} {{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("{ns}", "{nome}");

    /** Roteiro em ticks: entrada, impacto no tick 30 e saída. O servidor, o shader e a câmera leem o mesmo objeto. */
    static final Timeline TIMELINE = Timeline.builder(100)
        .phase("entrada", 0, 30, 20, 0)
        .phase("impacto", 30, 50, 0, 15)
        .phase("saida", 50, 100, 0, 40)
        .track("intensidade", keyframe(0, 0f), keyframe(30, 1f, Easing.OVERSHOOT), keyframe(100, 0f, Easing.EASE_OUT))
        .marker("impacto", 30)
        .build();

    private {classe}() {{}}

    /** Construção do mod, nos dois lados. Ordem obrigatória: register, timeline, atmosphere, cues. */
    @SubscribeEvent
    public static void construir(FMLConstructModEvent event) {{
        Addons.resources("{ns}", {classe}.class); // a RimuLib acha os shaders deste JAR (repetir não faz mal)
        // 100 ticks, raio 6 blocos, cor principal, sem forma nem material embutidos (tudo vem do passe).
        Effects.register(ID, new Effects.Definition(100, 6, {argb}, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "impacto", ctx -> {{
            // Servidor, no tick do marcador "impacto": dano, empurrão ou custo (docs/GAMEPLAY-0.5.md).
        }});{publicar}
    }}

    /** /{ns} efeito {nome}: inicia o efeito 6 blocos à frente, na linha do olhar (permissão de comandos). */
    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent event) {{
        event.getDispatcher().register(Commands.literal("{ns}").requires(source -> source.hasPermission(2))
            .then(Commands.literal("efeito").then(Commands.literal("{nome}").executes(ctx -> {{
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(6));
                boolean ok = Effects.start(ID, player.serverLevel(), at).isPresent();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                    ok ? "command.{ns}.{nome}.started" : "command.{ns}.{nome}.failed"), false);
                return ok ? 1 : 0;
            }}))));
    }}

    /** Só no cliente (a classe não é carregada num servidor dedicado). */
    @EventBusSubscriber(modid = "{ns}", value = Dist.CLIENT)
    public static final class Client {{
        private Client() {{}}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {{
            AddonLayers.post(ID, post -> {{
                var t = post.timeline();
                float intensidade = t.value("intensidade");
                if (intensidade <= .001f) return;
                post.pass("{ns}:{nome}", u -> {{
                    u.v3("EfeitoCentro", post.center());
                    u.f4("EfeitoEstado", intensidade, t.weight("impacto"), post.time().opacity(), (float) post.time().radius());
                    u.f1("EfeitoDesde", (float) t.since("impacto"));
                }});
                post.bloom(t.weight("impacto") * .2f);
            }});
        }}
    }}
}}
"""


def shader(ns: str, nome: str, cor: tuple[int, int, int]) -> str:
    r, g, b = (c / 255 for c in cor)
    return f"""// Shader do efeito {ns}:{nome} (gerado por scripts/novo_efeito.py). A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 EfeitoCentro;  // centro relativo à câmera (post.center()), o mesmo espaço de scenePosAt
uniform vec4 EfeitoEstado;  // x: trilha "intensidade", y: peso da fase "impacto", z: opacidade, w: raio (blocos)
uniform float EfeitoDesde;  // ticks desde o marcador "impacto" (negativo antes)

#include "calamidade_fx.glsl"

void main() {{
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 cor = vec3({r:.3f}, {g:.3f}, {b:.3f});
    vec3 rd = viewDir(texCoord);
    float cena = length(scenePosAt(texCoord));
    // Casca de energia: esfera com borda brilhante (fresnel), escondida pelo terreno na frente.
    float raio = EfeitoEstado.w * (0.4 + 0.6 * EfeitoEstado.x);
    vec2 t = caRaySphere(vec3(0.0), rd, EfeitoCentro, raio);
    if (t.x > 0.0 && t.x < cena) {{
        vec3 n = normalize(rd * t.x - EfeitoCentro);
        float borda = pow(1.0 - abs(dot(n, rd)), 2.5);
        color += cor * borda * EfeitoEstado.x * EfeitoEstado.z * 1.5;
    }}
    // Onda no chão a partir do impacto.
    if (!skyAt(texCoord)) {{
        vec3 d = scenePosAt(texCoord) - EfeitoCentro;
        float onda = caDistanceWave(length(d.xz), EfeitoDesde, 0.4, 0.6, 18.0);
        color += cor * onda * (1.0 - smoothstep(2.0, 5.0, abs(d.y))) * EfeitoEstado.z;
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
    p = argparse.ArgumentParser(description="Gera o esqueleto de um efeito de addon do Calamidade_Awakening.")
    p.add_argument("namespace", help="namespace do seu mod (o mesmo do @Mod), ex.: meumod")
    p.add_argument("nome", help="nome do efeito em [a-z0-9_], ex.: raio_solar")
    p.add_argument("--projeto", default=".", help="raiz do projeto Gradle do addon (padrão: diretório atual)")
    p.add_argument("--pacote", help="pacote Java (padrão: pacote da classe @Mod + .efeitos)")
    p.add_argument("--cartao", action="store_true", help="publica um cartão no Atlas e no Focus")
    p.add_argument("--forcar", action="store_true", help="sobrescreve arquivos existentes")
    a = p.parse_args()

    if not re.fullmatch(r"[a-z0-9_.-]+", a.namespace) or a.namespace in ("calamidade_awakening", "minecraft"):
        print("namespace inválido ou reservado: " + a.namespace, file=sys.stderr)
        return 2
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", a.nome):
        print("nome inválido (use [a-z0-9_], começando por letra): " + a.nome, file=sys.stderr)
        return 2
    projeto = Path(a.projeto).resolve()
    if not (projeto / "build.gradle").exists() and not (projeto / "build.gradle.kts").exists():
        print(f"{projeto} não parece um projeto Gradle (falta build.gradle)", file=sys.stderr)
        return 2
    pacote = a.pacote or detectar_pacote(projeto, a.namespace)
    if not re.fullmatch(r"[a-z_][\w]*(\.[a-z_][\w]*)*", pacote):
        print("pacote Java inválido: " + pacote, file=sys.stderr)
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
        print("Já existem (use --forcar para sobrescrever):\n  " + "\n  ".join(existentes), file=sys.stderr)
        return 1
    for arquivo, texto in arquivos.items():
        arquivo.parent.mkdir(parents=True, exist_ok=True)
        arquivo.write_text(texto, encoding="utf-8")
        print("criado   " + str(arquivo.relative_to(projeto)))

    biblioteca = recursos / a.namespace / "shaders/fx/calamidade_fx.glsl"
    nova = BIBLIOTECA.read_text(encoding="utf-8")
    if not biblioteca.exists() or biblioteca.read_text(encoding="utf-8") != nova:
        biblioteca.write_text(nova, encoding="utf-8")
        print("copiado  " + str(biblioteca.relative_to(projeto)) + " (biblioteca GLSL do motor)")

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
            print("criado   " + str(arte.relative_to(projeto)) + " (arte provisória do cartão)")
    for lang, valores in (("en_us", en), ("pt_br", pt)):
        mesclar_json(recursos / a.namespace / "lang" / f"{lang}.json", valores)
        print(f"traduções em assets/{a.namespace}/lang/{lang}.json")

    print(f"""
Pronto: {a.namespace}:{a.nome} ({pacote}.{classe}).
1. Compile o addon (o JAR do Calamidade 0.8.x e a RimuLib no classpath) e confira o manifesto:
   calamidade_awakening com versionRange "[0.8.0,0.9.0)" e rimulib "[1.3.0,2.0)".
2. No jogo: /{a.namespace} efeito {a.nome}{" ou o cartão no Atlas (N)" if a.cartao else ""}.
3. Edite a timeline, o shader e o cue. Receitas prontas: docs/ai/receitas/. Orçamento e regras: AGENTS.md.""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
