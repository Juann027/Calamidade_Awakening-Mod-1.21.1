# Receita 28 — Laboratório de luz (Light Cookies, holofotes, projetores)

**Objetivo.** Mostrar a Light API da 0.15.0 em dez demonstrações visualmente distintas, cada uma com imagens próprias
(`textures/lights/`, geradas por `scripts/gerar_cookies_exemplo.py`). **São exemplos técnicos, não um catálogo de
estilos**: um addon deve trazer as próprias imagens e cores; o motor só sabe projetar luz.

| | |
|---|---|
| Teste no jogo | `/vfxsample luz lista`; `/vfxsample luz <demo>` (7 blocos à frente, virada para onde você olha), `/vfxsample luz <demo> soltar` (saída) ou `parar` (corte), `/vfxsample luz comparar referencia runas` (lado a lado), `/vfxsample luz estresse 4` (4 × 8 luzes), `/vfxsample luz parar`. De noite, F7 aberto. |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/LaboratorioLuz.java`; PNGs em `examples/vfx-sample/src/main/resources/assets/vfxsample/textures/lights/` (sem shader) |
| Usa | `Light.projector`, `Light.spot`, `direction`, `toward(Point.anchorAim(...))`, `roll`, `Light.Cookie` (`MASK`, `colored`, `repeat`, `scale`, `rotation`, `offset`, `sheet`, `fps`, `blendFrames`), trilhas `ROLL`, `YAW`, `FRAME`, `COLOR`, `COOKIE_U/V`, `INTENSITY`, `WEIGHT`; `colorRamp`, `distanceGradient`, `Attenuation.SMOOTH`, `Blend.SOFT`, `Blend.SUBTRACT`; `Effects.follow` |
| Custo | 1 a 8 luzes por demo num único passe; 0 a 4 cookies; atlas de ~21 MB criado no primeiro cookie; nenhum passe novo |

| Demo | O que mostra |
|---|---|
| `referencia` | luz convencional (ponto + disco), o caminho rápido do shader, para comparar |
| `runas` | projetor de cima com um círculo arcano (MASK) que gira (`ROLL`) e respira (`INTENSITY`) |
| `tecnologia` | grade hexagonal em ladrilho (`repeat`, `scale 3`) com varredura (`COOKIE_V`), cor ciano→verde (`colorRamp` + `COLOR`) e uma retícula girando |
| `agua` | cáusticas: duas camadas da mesma folha 4 × 4 com escalas, giros e velocidades diferentes, quadros misturados, composição `SOFT` |
| `vitral` | holofote com textura **RGB** (`colored()`): a luz branca leva as cores do vitral |
| `holofote` | holofote sem textura varrendo (`YAW`), gradiente de distância quente→frio, curva `SMOOTH` |
| `selo` | folha 4 × 2 dirigida pela Timeline: `FRAME` enche o selo, `ROLL` acelera, `COLOR` vai de violeta a branco a dourado |
| `seguir` | presa ao jogador: retícula projetada em volta e uma lanterna dos olhos até a mira (`toward(Point.anchorAim(24))`) |
| `combinada` | fogo convencional + anel + disco com runas + persiana `SUBTRACT` que escurece em faixas |
| `estresse` | 8 luzes por instância (4 projetores com cookie, 4 pontos) para medir o teto e o fallback |
| **Fase 2** | |
| `modos` | o mesmo cookie neon, mesma cor e intensidade, lado a lado: `ADD` \| `EMISSIVE` \| `OVERRIDE` |
| `forcas` | `OVERRIDE` nas forças 0 \| 0,25 \| 0,5 \| 0,75 \| 1 (retícula vermelha, contraste 3) |
| `filtros` | pixel art 32 × 32 ampliado: `SMOOTH` \| `SHARP` (`sharpness 1`) \| `PIXELATED` |
| `resolucoes` | mandala de 2048: `STANDARD` (512) \| `ULTRA` (2048 no HIGH, 1024 no BALANCED, 512 no LOW; troque com F8) |
| `neon` | `EMISSIVE` intenso com contraste 2,5, núcleo que estoura em branco, giro e pulso |
| `dia` | vitral RGB que domina a grama de dia: `OVERRIDE` 0,9 com 40% de detalhe + `EMISSIVE` fraco |
| `transicao` | a mesma luz vai de convencional a emissiva e volta (trilha `BLEND_STRENGTH`) |
| `superficies` | monta escada, muro, relevo e um suporte de armadura (só no comando) e projeta grade `OVERRIDE` + retícula `EMISSIVE` por cima |
| `sobrepostas` | `ADD`, `SOFT`, `EMISSIVE`, `OVERRIDE` e `SUBTRACT` parcialmente sobrepostos (resultado independe da ordem) |
| `ataque` | retícula de ataque legível de dia e de noite: `OVERRIDE` total (contraste 4, nítida) + núcleo `EMISSIVE` pulsando |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/LaboratorioLuz.java -->
```java
package example.vfxsample.receitas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 28 — laboratório de iluminação do motor 0.15.0 (docs/ai/receitas/28-laboratorio-luz.md): dez demonstrações
 * da Light API com Light Cookies, holofotes, projetores, curvas, composição e gradientes. São exemplos técnicos, não um
 * catálogo de estilos: cada demo usa uma imagem própria (textures/lights/, gerada por scripts/gerar_cookies_exemplo.py)
 * para mostrar que a forma da luz vem do addon, não do motor.
 *
 * <p>Todas as demos ficam em laço (sustain) até {@code parar} ou {@code soltar}; começam 7 blocos à frente do jogador,
 * viradas para onde ele olha. Comandos: {@code /vfxsample luz <demo>}, {@code /vfxsample luz <demo> soltar|parar},
 * {@code /vfxsample luz comparar <a> <b>} (lado a lado), {@code /vfxsample luz estresse <n>}, {@code /vfxsample luz
 * parar} e {@code /vfxsample luz lista}.
 */
public final class LaboratorioLuz {
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("vfxsample", "luz_" + path); }

    static final String RUNAS = "vfxsample:textures/lights/runas.png";
    static final String HEX = "vfxsample:textures/lights/hexagonos.png";
    static final String CAUSTICAS = "vfxsample:textures/lights/causticas.png";
    static final String VITRAL = "vfxsample:textures/lights/vitral.png";
    static final String SELO = "vfxsample:textures/lights/selo.png";
    static final String ALVO = "vfxsample:textures/lights/alvo.png";
    static final String PERSIANA = "vfxsample:textures/lights/persiana.png";
    // Fase 2 (composição e nitidez).
    static final String NEON = "vfxsample:textures/lights/neon.png";
    static final String ATAQUE = "vfxsample:textures/lights/ataque.png";
    static final String PIXEL = "vfxsample:textures/lights/pixel.png";
    static final String MANDALA = "vfxsample:textures/lights/mandala.png";

    /**
     * Laço de 160 ticks (8 s) entre 20 e 180: trilhas com o mesmo valor nas bordas do laço, entrada e saída de 1 s.
     * "giro" vai de 0 a 360 graus por volta (sem salto), "varrer" é um vaivém, "pulso" respira, "cor" anda na rampa.
     */
    static final Timeline LACO = Timeline.builder(200)
        .track("entrada", keyframe(0, 0f), keyframe(20, 1f, Easing.EASE_OUT), keyframe(180, 1f), keyframe(200, 0f, Easing.EASE_IN))
        .track("giro", keyframe(20, 0f), keyframe(180, 360f, Easing.LINEAR))
        .track("varrer", keyframe(20, -35f), keyframe(100, 35f, Easing.SMOOTH), keyframe(180, -35f, Easing.SMOOTH))
        .track("pulso", keyframe(20, 2.4f), keyframe(60, 3.4f, Easing.SMOOTH), keyframe(100, 2.4f, Easing.SMOOTH),
            keyframe(140, 3.4f, Easing.SMOOTH), keyframe(180, 2.4f, Easing.SMOOTH))
        .track("cor", keyframe(20, 0f), keyframe(100, 1f, Easing.SMOOTH), keyframe(180, 0f, Easing.SMOOTH))
        .track("rolar", keyframe(20, 0f), keyframe(180, 2f, Easing.LINEAR))
        .sustain(20, 180)
        .build();

    /**
     * Selo que carrega: 80 ticks para encher os 8 quadros da folha (trilha FRAME), giro acelerado e cor de violeta a
     * branco a dourado (trilha COLOR na rampa); em laço do 100 ao 180 com o selo cheio.
     */
    static final Timeline CARGA = Timeline.builder(200)
        .track("quadro", keyframe(0, 0f), keyframe(80, 7f, Easing.EASE_IN), keyframe(200, 7f))
        .track("giro", keyframe(0, 0f), keyframe(80, 90f, Easing.EASE_IN), keyframe(100, 180f), keyframe(180, 540f, Easing.LINEAR))
        .track("cor", keyframe(0, 0f), keyframe(80, .5f), keyframe(100, 1f, Easing.EASE_OUT), keyframe(180, 1f))
        .track("forca", keyframe(0, .6f), keyframe(80, 2.5f, Easing.EASE_IN), keyframe(90, 5f, Easing.EASE_OUT),
            keyframe(100, 3.2f), keyframe(180, 3.2f), keyframe(200, 0f))
        .sustain(100, 180)
        .build();

    /** Uma demo: nome do comando, efeito e descrição curta. */
    record Demo(String nome, ResourceLocation id, String descricao) {}

    static final Map<String, Demo> DEMOS = new LinkedHashMap<>();

    private static Demo demo(String nome, String descricao) {
        Demo d = new Demo(nome, id(nome), descricao);
        DEMOS.put(nome, d);
        return d;
    }

    // 1. Referência: uma luz convencional (ponto) e um disco, sem textura, no caminho rápido do shader.
    static final Demo REFERENCIA = demo("referencia", "luz convencional (ponto + disco), o caminho da 0.9");
    static final Light REF_PONTO = Light.point(Point.center(0, 1.2, 0)).color(0xffffb070).intensity(2.6f).radius(10).falloff(1.4f)
        .flicker(.12f, 3).build();
    static final Light REF_DISCO = Light.disc(Point.center(), 6).color(0xffff9050).intensity(1.1f).build();

    // 2. Runas: projetor planar de cima, o círculo arcano gira (ROLL) e respira (INTENSITY).
    static final Demo RUNAS_DEMO = demo("runas", "projeção de runas girando no chão (cookie MASK)");
    static final Light RUNAS_LUZ = Light.projector(Point.center(0, 6, 0), 11, 11).cookie(RUNAS)
        .color(0xff8ab4ff).radius(9).falloff(.6f).softness(.05f)
        .track(Light.Param.ROLL, "giro").track(Light.Param.INTENSITY, "pulso").track(Light.Param.WEIGHT, "entrada").build();

    // 3. Tecnologia: grade hexagonal que repete, varredura (COOKIE_V), cor ciano a verde (rampa) e uma retícula.
    static final Demo TECNO = demo("tecnologia", "grade hexagonal em ladrilho com varredura e cor animada");
    static final Light TECNO_GRADE = Light.projector(Point.center(0, 5, 0), 14, 12)
        .cookie(Light.Cookie.of(HEX).repeat().scale(3))
        .colorRamp(0xff30f0ff, 0xff40ff90).intensity(2.2f).radius(8).falloff(.3f).softness(.2f)
        .track(Light.Param.COOKIE_V, "rolar").track(Light.Param.COLOR, "cor").track(Light.Param.WEIGHT, "entrada").build();
    static final Light TECNO_ALVO = Light.projector(Point.center(0, 5, 0), 4, 4).cookie(ALVO)
        .color(0xffb0fff0).intensity(3.5f).radius(8).falloff(.3f).softness(.02f)
        .track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build();

    // 4. Água: duas camadas de cáusticas animadas (folha 4 x 4, quadros misturados), escalas e giros diferentes,
    //    composição SOFT para a soma não estourar.
    static final Demo AGUA = demo("agua", "cáusticas animadas em duas camadas (folha 4x4, SOFT)");
    static final Light AGUA_A = Light.projector(Point.center(0, 7, 0), 16, 16)
        .cookie(Light.Cookie.of(CAUSTICAS).sheet(4, 4).fps(9).blendFrames(true).repeat().scale(2))
        .color(0xff60d0ff).intensity(2.6f).radius(10).falloff(.3f).softness(.35f).blend(Light.Blend.SOFT)
        .track(Light.Param.WEIGHT, "entrada").build();
    static final Light AGUA_B = Light.projector(Point.center(0, 7, 0), 16, 16)
        .cookie(Light.Cookie.of(CAUSTICAS).sheet(4, 4).fps(6).blendFrames(true).repeat().scale(3.2f).rotation(37).offset(.3f, .1f))
        .color(0xff40a0ff).intensity(1.6f).radius(10).falloff(.3f).softness(.35f).blend(Light.Blend.SOFT)
        .track(Light.Param.COOKIE_U, "rolar").track(Light.Param.WEIGHT, "entrada").build();
    static final Light AGUA_FUNDO = Light.disc(Point.center(), 9).color(0xff1060a0).intensity(.6f)
        .track(Light.Param.WEIGHT, "entrada").build();

    // 5. Vitral: holofote com cookie COLOR; a luz branca leva as cores da imagem para o chão e as paredes.
    static final Demo VITRAL_DEMO = demo("vitral", "holofote com textura RGB (cookie COLOR)");
    static final Light VITRAL_LUZ = Light.spot(Point.center(-2.5, 7, -1)).direction(-20, 62).angle(48).cookie(Light.Cookie.of(VITRAL).colored())
        .color(0xffffffff).intensity(4.2f).radius(16).falloff(1).softness(.04f).track(Light.Param.WEIGHT, "entrada").build();

    // 6. Holofote orientável: busca de um lado a outro (YAW), gradiente de distância quente -> frio, curva SMOOTH.
    static final Demo HOLOFOTE = demo("holofote", "holofote orientável varrendo, gradiente de distância");
    static final Light HOLOFOTE_LUZ = Light.spot(Point.center(0, 6, -3)).direction(0, 38).angle(26).softness(.3f)
        .distanceGradient(0xfffff4d0, 0xffffa040, 0xff3060ff).intensity(4.5f).radius(22).attenuation(Light.Attenuation.SMOOTH)
        .track(Light.Param.YAW, "varrer").track(Light.Param.WEIGHT, "entrada").build();

    // 7. Selo animado pela timeline: quadro da folha (FRAME), giro (ROLL), cor na rampa (COLOR) e força (INTENSITY).
    static final Demo SELO_DEMO = demo("selo", "folha de quadros dirigida pela timeline (FRAME, ROLL, COLOR)");
    static final Light SELO_LUZ = Light.projector(Point.center(0, 5, 0), 9, 9).cookie(Light.Cookie.of(SELO).sheet(4, 2))
        .colorRamp(0xff9040ff, 0xffffffff, 0xffffc040).radius(7).falloff(.4f).softness(.03f)
        .track(Light.Param.FRAME, "quadro").track(Light.Param.ROLL, "giro").track(Light.Param.COLOR, "cor")
        .track(Light.Param.INTENSITY, "forca").build();

    // 8. Seguir: presa ao jogador (Effects.follow): retícula projetada em volta dele e uma lanterna dos olhos até a
    //    mira. Com um chefe, troque por Point.socket("olhos", ...) e Light.spot(...).toward(Point.socketAim(...)).
    static final Demo SEGUIR = demo("seguir", "retícula e lanterna presas à entidade (anchor, mira)");
    static final Light SEGUIR_ALVO = Light.projector(Point.anchor(0, 4, 0), 5, 5).cookie(ALVO).color(0xffff6060)
        .intensity(3).radius(7).falloff(.25f).softness(.02f).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build();
    static final Light SEGUIR_LANTERNA = Light.spot(Point.anchorEye(0, -.2, 0)).toward(Point.anchorAim(24)).angle(30)
        .softness(.45f).color(0xfffff0d0).intensity(3.2f).radius(24).falloff(1.2f).track(Light.Param.WEIGHT, "entrada").build();

    // 9. Combinação: fogo convencional, anel de choque, runas por cima e uma persiana SUBTRACT que escurece em faixas.
    static final Demo COMBINADA = demo("combinada", "convencional + anel + cookie + sombra SUBTRACT");
    static final Light COMB_FOGO = Light.point(Point.center(1.5, .8, 1.5)).color(0xffff8a30).intensity(2.6f).radius(9)
        .flicker(.3f, 6).build();
    static final Light COMB_ANEL = Light.ring(Point.center(), 5, 1.5f).color(0xff80c0ff).intensity(2).track(Light.Param.INTENSITY, "pulso").build();
    static final Light COMB_RUNAS = Light.disc(Point.center(), 5).cookie(RUNAS).color(0xffc080ff).intensity(2.2f).roll(15)
        .track(Light.Param.ROLL, "giro").build();
    static final Light COMB_SOMBRA = Light.projector(Point.center(0, 6, 0), 12, 12).cookie(Light.Cookie.of(PERSIANA).repeat().scale(2))
        .color(0xffffffff).intensity(.7f).radius(10).falloff(.25f).softness(.3f).blend(Light.Blend.SUBTRACT)
        .track(Light.Param.COOKIE_V, "rolar").track(Light.Param.WEIGHT, "entrada").build();

    // 10. Estresse: 8 luzes por instância (4 projetores com cookie e 4 pontos); /vfxsample luz estresse <n>.
    static final Demo ESTRESSE = demo("estresse", "8 luzes por instância (4 com cookie)");
    static final List<Light> ESTRESSE_LUZES = estresse();

    // ---------------------------------------------------------------- fase 2: composição, sobrescrita e nitidez
    // As comparações lado a lado usam Point.center(x, ...): x positivo é a esquerda de quem olha (o efeito começa virado
    // como o jogador), então a ordem na tela é da esquerda para a direita. Atenuação CONSTANT para a distância não
    // mudar a comparação.

    /** Projetor de comparação: mesmo cookie, mesma intensidade e tamanho, só o modo muda. */
    private static Light.Builder lado(double x, String cookie, float tamanho) {
        return Light.projector(Point.center(x, 5, 0), tamanho, tamanho).cookie(Light.Cookie.of(cookie).sharpness(.6f))
            .radius(8).attenuation(Light.Attenuation.CONSTANT).softness(.04f).track(Light.Param.WEIGHT, "entrada");
    }

    // 11. ADD × EMISSIVE × OVERRIDE com o mesmo cookie, cor e intensidade.
    static final Demo MODOS = demo("modos", "ADD | EMISSIVE | OVERRIDE com o mesmo cookie e a mesma intensidade");
    static final Light MODO_ADD = lado(6, NEON, 5).color(0xff40e0ff).intensity(2.5f).contrast(2).build();
    static final Light MODO_EMISSIVE = lado(0, NEON, 5).color(0xff40e0ff).intensity(2.5f).contrast(2).blend(Light.Blend.EMISSIVE).build();
    static final Light MODO_OVERRIDE = lado(-6, NEON, 5).color(0xff40e0ff).intensity(2.5f).contrast(2).blend(Light.Blend.OVERRIDE).build();

    // 12. OVERRIDE nas forças 0, 0,25, 0,5, 0,75 e 1.
    static final Demo FORCAS = demo("forcas", "OVERRIDE nas forças 0 | 0,25 | 0,5 | 0,75 | 1");
    static final Light[] FORCAS_LUZES = forcas();

    private static Light[] forcas() {
        float[] f = {0, .25f, .5f, .75f, 1};
        Light[] out = new Light[f.length];
        for (int i = 0; i < f.length; i++)
            out[i] = lado(8 - i * 4, ATAQUE, 3.5f).color(0xffff3050).intensity(1.2f).contrast(3).blend(Light.Blend.OVERRIDE)
                .blendStrength(f[i]).build();
        return out;
    }

    // 13. Filtros: o mesmo pixel art 32 x 32 ampliado em SMOOTH, SHARP e PIXELATED.
    static final Demo FILTROS = demo("filtros", "pixel art 32x32: SMOOTH | SHARP | PIXELATED");
    static final Light FILTRO_SUAVE = filtro(5, Light.Cookie.of(PIXEL));
    static final Light FILTRO_NITIDO = filtro(0, Light.Cookie.of(PIXEL).filter(Light.Cookie.Filter.SHARP).sharpness(1));
    static final Light FILTRO_PIXEL = filtro(-5, Light.Cookie.of(PIXEL).pixelated());

    private static Light filtro(double x, Light.Cookie cookie) {
        return Light.projector(Point.center(x, 5, 0), 4, 4).cookie(cookie).color(0xffffd060).intensity(2.6f).radius(8)
            .attenuation(Light.Attenuation.CONSTANT).softness(.02f).blend(Light.Blend.EMISSIVE).track(Light.Param.WEIGHT, "entrada").build();
    }

    // 14. A mesma mandala de 2048 em 512 (STANDARD) e em alta resolução (ULTRA: 2048 em HIGH/ULTRA, 1024 em BALANCED,
    //     512 em LOW; troque o perfil com F8). Uma imagem guarda uma versão grande só, a do maior pedido.
    static final Demo RESOLUCOES = demo("resolucoes", "mandala 2048: STANDARD 512 | ULTRA (2048 no HIGH, 1024 no BALANCED)");
    static final Light RES_512 = resolucao(4, Light.Cookie.Resolution.STANDARD);
    static final Light RES_ALTA = resolucao(-4, Light.Cookie.Resolution.ULTRA);

    private static Light resolucao(double x, Light.Cookie.Resolution res) {
        return Light.projector(Point.center(x, 5, 0), 7.5f, 7.5f).cookie(Light.Cookie.of(MANDALA).resolution(res).sharpness(.5f))
            .color(0xffffffff).intensity(2.8f).radius(8).attenuation(Light.Attenuation.CONSTANT).softness(.02f)
            .blend(Light.Blend.EMISSIVE).track(Light.Param.WEIGHT, "entrada").build();
    }

    // 15. Neon intenso: EMISSIVE com contraste alto, núcleo que estoura em branco e um disco emissivo fraco embaixo.
    static final Demo NEON_DEMO = demo("neon", "projeção neon intensa (EMISSIVE, contraste, núcleo branco)");
    static final Light NEON_LUZ = Light.projector(Point.center(0, 6, 0), 9, 9).cookie(Light.Cookie.of(NEON).sharpness(.8f))
        .color(0xffff30d0).intensity(4.5f).radius(9).attenuation(Light.Attenuation.CONSTANT).softness(.03f).contrast(2.5f)
        .blend(Light.Blend.EMISSIVE).track(Light.Param.ROLL, "giro").track(Light.Param.INTENSITY, "pulso")
        .track(Light.Param.WEIGHT, "entrada").build();
    static final Light NEON_BRILHO = Light.disc(Point.center(), 5).color(0xff6010a0).intensity(.8f).blend(Light.Blend.EMISSIVE)
        .blendStrength(.6f).track(Light.Param.WEIGHT, "entrada").build();

    // 16. Multicolorido de dia: vitral COLOR em OVERRIDE (domina a grama, guarda 40% da textura) + um pouco de EMISSIVE.
    static final Demo DIA = demo("dia", "vitral RGB forte de dia (OVERRIDE com detalhe + EMISSIVE)");
    static final Light DIA_OVERRIDE = Light.projector(Point.center(0, 6, 0), 9, 9).cookie(Light.Cookie.of(VITRAL).colored())
        .color(0xffffffff).intensity(1.1f).radius(9).attenuation(Light.Attenuation.CONSTANT).softness(.03f)
        .blend(Light.Blend.OVERRIDE).blendStrength(.9f).surfaceDetail(.4f).track(Light.Param.WEIGHT, "entrada").build();
    static final Light DIA_BRILHO = Light.projector(Point.center(0, 6, 0), 9, 9).cookie(Light.Cookie.of(VITRAL).colored())
        .color(0xffffffff).intensity(.5f).radius(9).attenuation(Light.Attenuation.CONSTANT).softness(.03f)
        .blend(Light.Blend.EMISSIVE).track(Light.Param.WEIGHT, "entrada").build();

    // 17. Transição: a mesma luz vai de convencional (BLEND_STRENGTH 0 = ADD) a emissiva (1) e volta, pela Timeline.
    static final Demo TRANSICAO = demo("transicao", "convencional -> emissiva -> convencional (trilha BLEND_STRENGTH)");
    static final Light TRANSICAO_LUZ = Light.projector(Point.center(0, 6, 0), 9, 9).cookie(NEON).color(0xff60ff90).intensity(3)
        .radius(9).attenuation(Light.Attenuation.CONSTANT).softness(.04f).contrast(2).blend(Light.Blend.EMISSIVE)
        .track(Light.Param.BLEND_STRENGTH, "cor").track(Light.Param.WEIGHT, "entrada").build();

    // 18. Superfícies: o comando monta uma escada, um muro, chão irregular e um suporte de armadura; a projeção cobre
    //     tudo (grade OVERRIDE parcial + retícula EMISSIVE) e segue o relevo e a entidade.
    static final Demo SUPERFICIES = demo("superficies", "paredes, escada, relevo e entidade (monta a pista de teste)");
    static final Light SUP_GRADE = Light.projector(Point.center(0, 8, 0), 11, 11).cookie(Light.Cookie.of(HEX).repeat().scale(3))
        .color(0xff40ffd0).intensity(1).radius(12).attenuation(Light.Attenuation.CONSTANT).softness(.15f)
        .blend(Light.Blend.OVERRIDE).blendStrength(.55f).surfaceDetail(.5f).track(Light.Param.WEIGHT, "entrada").build();
    static final Light SUP_ALVO = Light.spot(Point.center(0, 8, 4)).direction(0, 62).angle(44).cookie(Light.Cookie.of(ATAQUE).sharpness(.8f))
        .color(0xffff4040).intensity(3).radius(16).attenuation(Light.Attenuation.CONSTANT).softness(.02f).contrast(3)
        .blend(Light.Blend.EMISSIVE).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build();

    // 19. Sobrepostas: ADD, SOFT, EMISSIVE, OVERRIDE e SUBTRACT parcialmente sobrepostos (resultado não depende da ordem).
    static final Demo SOBREPOSTAS = demo("sobrepostas", "ADD + SOFT + EMISSIVE + OVERRIDE + SUBTRACT sobrepostos");
    static final Light SOB_ADD = Light.projector(Point.center(2.5, 6, 1), 7, 7).cookie(RUNAS).color(0xffffa040).intensity(2.5f)
        .radius(9).attenuation(Light.Attenuation.CONSTANT).track(Light.Param.WEIGHT, "entrada").build();
    static final Light SOB_SOFT = Light.projector(Point.center(-2.5, 6, 1), 7, 7)
        .cookie(Light.Cookie.of(CAUSTICAS).sheet(4, 4).fps(9).blendFrames(true).repeat().scale(2)).color(0xff40a0ff).intensity(2.5f)
        .radius(9).attenuation(Light.Attenuation.CONSTANT).blend(Light.Blend.SOFT).track(Light.Param.WEIGHT, "entrada").build();
    static final Light SOB_EMISSIVE = Light.projector(Point.center(0, 6, -1.5), 6, 6).cookie(NEON).color(0xffff40ff).intensity(3)
        .radius(9).attenuation(Light.Attenuation.CONSTANT).contrast(2).blend(Light.Blend.EMISSIVE).track(Light.Param.ROLL, "giro")
        .track(Light.Param.WEIGHT, "entrada").build();
    static final Light SOB_OVERRIDE = Light.projector(Point.center(0, 6, 2.5), 8, 4).cookie(Light.Cookie.of(HEX).repeat().scale(2))
        .color(0xff30ff80).intensity(1).radius(9).attenuation(Light.Attenuation.CONSTANT).blend(Light.Blend.OVERRIDE)
        .blendStrength(.6f).track(Light.Param.WEIGHT, "entrada").build();
    static final Light SOB_SUBTRACT = Light.projector(Point.center(0, 6, 0), 10, 10).cookie(Light.Cookie.of(PERSIANA).repeat().scale(2))
        .color(0xffffffff).intensity(.6f).radius(9).attenuation(Light.Attenuation.CONSTANT).softness(.3f).blend(Light.Blend.SUBTRACT)
        .track(Light.Param.COOKIE_V, "rolar").track(Light.Param.WEIGHT, "entrada").build();

    // 20. Retícula de ataque extremamente legível: OVERRIDE total vermelho (contraste 4, nítida) + EMISSIVE fraco (sem estourar).
    static final Demo ATAQUE_DEMO = demo("ataque", "retícula de ataque legível (OVERRIDE total + EMISSIVE)");
    static final Light ATAQUE_BASE = Light.projector(Point.center(0, 7, 0), 7, 7).cookie(Light.Cookie.of(ATAQUE).sharpness(1))
        .color(0xffff1020).intensity(1.1f).radius(10).attenuation(Light.Attenuation.CONSTANT).softness(.01f).contrast(4)
        .blend(Light.Blend.OVERRIDE).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build();
    static final Light ATAQUE_BRILHO = Light.projector(Point.center(0, 7, 0), 7, 7).cookie(Light.Cookie.of(ATAQUE).sharpness(1))
        .color(0xffff3020).intensity(.35f).radius(10).attenuation(Light.Attenuation.CONSTANT).softness(.01f).contrast(4)
        .blend(Light.Blend.EMISSIVE).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada")
        .build();

    private static List<Light> estresse() {
        List<Light> out = new ArrayList<>();
        String[] cookies = {RUNAS, HEX, ALVO, VITRAL};
        int[] cores = {0xff80b0ff, 0xff40ffa0, 0xffff6060, 0xffffffff};
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            var cookie = i == 3 ? Light.Cookie.of(cookies[i]).colored() : Light.Cookie.of(cookies[i]);
            out.add(Light.projector(Point.center(Math.cos(a) * 3, 4, Math.sin(a) * 3), 5, 5).cookie(cookie).color(cores[i])
                .intensity(2.5f).radius(6).falloff(.4f).track(Light.Param.ROLL, "giro").build());
            out.add(Light.point(Point.center(Math.cos(a + .8) * 4, 1, Math.sin(a + .8) * 4)).color(cores[(i + 1) % 4])
                .intensity(2).radius(6).build());
        }
        return out;
    }

    /** Instâncias por jogador e demo, para soltar, parar e comparar. */
    private static final Map<UUID, Map<String, List<Effects.Handle>>> VIVAS = new HashMap<>();

    private LaboratorioLuz() {}

    /** Construtor comum do mod: as dez demos (register, timeline, luzes). */
    public static void register() {
        registrar(REFERENCIA, 6, LACO, REF_PONTO, REF_DISCO);
        registrar(RUNAS_DEMO, 7, LACO, RUNAS_LUZ);
        registrar(TECNO, 9, LACO, TECNO_GRADE, TECNO_ALVO);
        registrar(AGUA, 10, LACO, AGUA_A, AGUA_B, AGUA_FUNDO);
        registrar(VITRAL_DEMO, 9, LACO, VITRAL_LUZ);
        registrar(HOLOFOTE, 12, LACO, HOLOFOTE_LUZ);
        registrar(SELO_DEMO, 6, CARGA, SELO_LUZ);
        registrar(SEGUIR, 10, LACO, SEGUIR_ALVO, SEGUIR_LANTERNA);
        registrar(COMBINADA, 7, LACO, COMB_FOGO, COMB_ANEL, COMB_RUNAS, COMB_SOMBRA);
        registrar(ESTRESSE, 6, LACO, ESTRESSE_LUZES.toArray(Light[]::new));
        // Fase 2.
        registrar(MODOS, 10, LACO, MODO_ADD, MODO_EMISSIVE, MODO_OVERRIDE);
        registrar(FORCAS, 11, LACO, FORCAS_LUZES);
        registrar(FILTROS, 8, LACO, FILTRO_SUAVE, FILTRO_NITIDO, FILTRO_PIXEL);
        registrar(RESOLUCOES, 9, LACO, RES_512, RES_ALTA);
        registrar(NEON_DEMO, 7, LACO, NEON_LUZ, NEON_BRILHO);
        registrar(DIA, 7, LACO, DIA_OVERRIDE, DIA_BRILHO);
        registrar(TRANSICAO, 7, LACO, TRANSICAO_LUZ);
        registrar(SUPERFICIES, 9, LACO, SUP_GRADE, SUP_ALVO);
        registrar(SOBREPOSTAS, 8, LACO, SOB_ADD, SOB_SOFT, SOB_EMISSIVE, SOB_OVERRIDE, SOB_SUBTRACT);
        registrar(ATAQUE_DEMO, 7, LACO, ATAQUE_BASE, ATAQUE_BRILHO);
    }

    private static void registrar(Demo demo, double raio, Timeline timeline, Light... luzes) {
        // Raio da definição: o teste de frustum usa 2,5 x raio; cobre o alcance das luzes.
        Effects.register(demo.id(), new Effects.Definition(timeline.duration(), raio, 0xffffffff, Effects.Shape.NONE,
            Effects.Material.NONE, null, null).withRange(96));
        Effects.timeline(demo.id(), timeline);
        Effects.lights(demo.id(), luzes);
    }

    /** /vfxsample luz ... */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        var raiz = Commands.literal("luz");
        for (Demo demo : DEMOS.values()) {
            raiz.then(Commands.literal(demo.nome()).executes(ctx -> iniciar(ctx.getSource(), demo, 0, 1))
                .then(Commands.literal("soltar").executes(ctx -> encerrar(ctx.getSource(), demo.nome(), false)))
                .then(Commands.literal("parar").executes(ctx -> encerrar(ctx.getSource(), demo.nome(), true))));
        }
        var nomes = Commands.argument("a", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(DEMOS.keySet(), b));
        raiz.then(Commands.literal("comparar").then(nomes.then(Commands.argument("b", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(DEMOS.keySet(), b))
            .executes(ctx -> comparar(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "a"),
                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "b"))))));
        raiz.then(Commands.literal("estresse").then(Commands.argument("n", IntegerArgumentType.integer(1, 16))
            .executes(ctx -> estresse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))));
        raiz.then(Commands.literal("parar").executes(ctx -> pararTudo(ctx.getSource())));
        raiz.then(Commands.literal("lista").executes(ctx -> lista(ctx.getSource())));
        return raiz;
    }

    private static int iniciar(CommandSourceStack source, Demo demo, double lado, int vezes) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var lista = VIVAS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).computeIfAbsent(demo.nome(), k -> new ArrayList<>());
        int ok = 0;
        for (int i = 0; i < vezes; i++) {
            if (demo == SUPERFICIES && i == 0) pista(player, posicao(player, lado, 0, 1).position());
            var handle = demo == SEGUIR
                ? Effects.follow(demo.id(), player, Vec3.ZERO)
                : Effects.start(demo.id(), player.serverLevel(), posicao(player, lado, i, vezes));
            if (handle.isPresent()) { lista.add(handle.get()); ok++; }
        }
        int n = ok;
        source.sendSuccess(() -> Component.translatable(n > 0 ? "command.vfxsample.luz.started" : "command.vfxsample.luz.failed",
            demo.nome(), demo.descricao()), false);
        return ok;
    }

    /** 7 blocos à frente, virado como o jogador; {@code lado} desloca para a direita (comparar); grade no estresse. */
    private static Placement posicao(ServerPlayer player, double lado, int i, int vezes) {
        Vec3 frente = player.getLookAngle().multiply(1, 0, 1);
        if (frente.lengthSqr() < 1e-4) frente = new Vec3(0, 0, 1);
        frente = frente.normalize();
        Vec3 direita = new Vec3(-frente.z, 0, frente.x);
        int lado2 = (int) Math.ceil(Math.sqrt(vezes));
        double gx = vezes > 1 ? (i % lado2 - (lado2 - 1) / 2.0) * 10 : 0, gz = vezes > 1 ? (i / lado2) * 10 : 0;
        Vec3 pos = player.position().add(frente.scale(7 + gz)).add(direita.scale(lado + gx));
        return Placement.at(pos, player.getYRot(), 0).withCaster(player);
    }

    private static int comparar(CommandSourceStack source, String a, String b) throws CommandSyntaxException {
        Demo da = DEMOS.get(a), db = DEMOS.get(b);
        if (da == null || db == null) {
            source.sendFailure(Component.translatable("command.vfxsample.luz.unknown", da == null ? a : b));
            return 0;
        }
        return iniciar(source, da, -7, 1) + iniciar(source, db, 7, 1);
    }

    private static int estresse(CommandSourceStack source, int n) throws CommandSyntaxException {
        return iniciar(source, ESTRESSE, 0, n);
    }

    private static int encerrar(CommandSourceStack source, String nome, boolean parar) throws CommandSyntaxException {
        var lista = VIVAS.getOrDefault(source.getPlayerOrException().getUUID(), Map.of()).get(nome);
        int n = 0;
        if (lista != null) {
            for (var h : lista) if (parar ? h.stop() : h.release()) n++;
            if (parar) lista.clear(); else lista.removeIf(h -> !h.alive());
        }
        int count = n;
        source.sendSuccess(() -> Component.translatable(parar ? "command.vfxsample.luz.stopped" : "command.vfxsample.luz.released",
            nome, count), false);
        return n;
    }

    private static int pararTudo(CommandSourceStack source) throws CommandSyntaxException {
        int n = parar(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.translatable("command.vfxsample.luz.stopped", "*", n), false);
        return n;
    }

    private static int lista(CommandSourceStack source) {
        for (Demo d : DEMOS.values())
            source.sendSuccess(() -> Component.literal("/vfxsample luz " + d.nome() + " — " + d.descricao()), false);
        return DEMOS.size();
    }

    /**
     * Pista de superfícies da demo "superficies" (só no comando, nunca num cue): escada, muro, chão irregular e um
     * suporte de armadura em volta do centro, para ver a projeção seguir o relevo e a entidade. Blocos do vanilla; a
     * pista fica no mundo (é um cenário de teste).
     */
    private static void pista(ServerPlayer player, Vec3 centro) {
        var level = player.serverLevel();
        var base = net.minecraft.core.BlockPos.containing(centro);
        var pedra = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
        var escada = net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        for (int i = 0; i < 4; i++) for (int z = -2; z <= 2; z++) {
            // Escada subindo para leste, a 2 blocos do centro.
            for (int y = 0; y < i; y++) level.setBlockAndUpdate(base.offset(2 + i, y, z), pedra);
            level.setBlockAndUpdate(base.offset(2 + i, i, z), escada.setValue(
                net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        for (int y = 0; y < 4; y++) for (int z = -3; z <= 3; z++) level.setBlockAndUpdate(base.offset(-4, y, z), pedra);
        int[][] relevo = {{-1, -2}, {0, -2}, {-2, 1}, {-1, 1}, {0, 2}, {1, -1}};
        for (int[] r : relevo) level.setBlockAndUpdate(base.offset(r[0], 0, r[1]), net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState());
        var suporte = new net.minecraft.world.entity.decoration.ArmorStand(level, centro.x + .5, base.getY(), centro.z - 1.5);
        level.addFreshEntity(suporte);
    }

    /** Encerra todas as demos de um jogador (também ao sair do jogo). */
    public static int parar(UUID player) {
        int n = 0;
        var mapa = VIVAS.remove(player);
        if (mapa != null) for (var lista : mapa.values()) for (var h : lista) if (h.stop()) n++;
        return n;
    }

    /** Servidor parou: esquece os handles. */
    public static void limpar() { VIVAS.clear(); }
}
```
