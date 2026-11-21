---
name: calamidade-efeitos
description: Cria ou altera efeitos visuais do Calamidade_Awakening (motor de VFX para Minecraft 1.21.1, NeoForge, RimuLib) e de seus addons — definição, Timeline, Atmosphere (céu, névoa, exposição), passes de pós-processamento com shader GLSL, malha de mundo, câmera, cues de gameplay no servidor e testes com F7 e bench. Use quando pedirem um efeito, habilidade, ataque, shader, feixe, anel, bolha, impact frame, céu ou névoa para o Calamidade, ou um addon que use a API dev.calamidade_awakening.api.v1.
---

# Criar efeitos no Calamidade_Awakening

Antes de tudo, leia `AGENTS.md` (regras invioláveis: API pública, rede, licenças, javap, git status). Esta skill é o fluxo de trabalho; as assinaturas exatas estão em `docs/ai/API-REFERENCIA.md` (gerada do JAR) e os exemplos completos em `docs/ai/receitas/`.

## Quando usar

- Um efeito novo num addon (ou no `examples/vfx-sample`).
- Mudar o roteiro, o visual, a câmera ou o gameplay de um efeito existente.
- Dar clima a uma luta ou área (céu, névoa, escuridão) com a Atmosphere API.

Não use para mudar o próprio motor (renderização interna, rede, registro): aí siga `docs/ARQUITETURA.md` e `docs/HABILIDADES.md`.

## Fluxo

Siga na ordem. Cada passo aponta para o capítulo e a receita que mostram o código.

### 1. Definição

Comece do esqueleto: `python3 scripts/novo_efeito.py <namespace> <nome> --projeto <addon> [--cartao]` (compila sem edição, registra-se sozinho em `FMLConstructModEvent`, comando `/<namespace> efeito <nome>`). Ou copie a receita mais parecida.

- `Effects.Definition(ticks, raio, argb, Shape, Material, partícula, som[, Impact])`, `.withRange(blocos)`.
- Protagonista: `.withPriority(Effects.Priority.HERO)` só no efeito principal da cena (ataque de chefe, golpe final). Entra antes dos NORMAL, nunca é cortado pelo limite de desenhados (no máximo 2 HERO por quadro, os mais próximos) e tem orçamento de passes próprio (1/2/3). Não marque tudo como HERO: o terceiro HERO mais distante simplesmente não é desenhado.
- Raio: o teste de frustum usa 2,5 × raio em volta do centro. Cubra tudo o que o shader desenha (feixe alto, cubo grande), senão o efeito some quando o centro sai da tela.
- Capítulos: `docs/API-0.3.md`, `docs/ADDONS-0.4.md` (cartão). Receitas: todas.

### 2. Timeline

- `Timeline.builder(duração).phase(nome, início, fim, subida, queda).track(nome, keyframe(...)...).marker(nome, tick).build()`; `Effects.timeline(id, timeline)` logo depois do `register`.
- Fases para "quando" (pesos suaves 0..1), trilhas para "quanto" (valores com easing), marcadores para gameplay e tempo relativo (`t.since("impacto")`).
- Ambiente: `sustain(início, fim)` + `Handle.release()`. Trilhas iguais nas bordas do loop e no início da saída; movimento com período igual ao loop.
- Capítulo: `docs/TIMELINE-0.7.md`. Receitas: 1 (marcador), 2 e 3 (trilhas com OVERSHOOT), 6 (sustain).

### 3. Atmosfera (opcional)

- `Atmosphere.builder().sky(topo, horizonte, mistura).fog(cor, densidade, início, fim).exposure(x).ambient(cor, força).stars(x)`; `.track(Param.FOG_DENSITY, "trilha")`, `.weightPhase("fase")`; `Effects.atmosphere(id, atmosfera)` depois da timeline.
- Não precisa de shader nem de camada de cliente. Peso = envelope × fase × trilha WEIGHT × fade no fim do alcance; o motor mistura as duas mais fortes.
- Capítulo: `docs/ATMOSFERA-0.8.md`. Receita: 7. Exemplo: Sinal de Maré (céu no impacto) e Farol (névoa no sustain).

### 4. Pós-processamento e shader

- Cliente (`FMLClientSetupEvent`): `AddonLayers.post(id, post -> { var t = post.timeline(); post.pass("ns:programa", u -> {...}); post.bloom(...); })`.
- Shader em `assets/<ns>/shaders/fx/<programa>.fsh`. A RimuLib antepõe `#version 150` e `common.glsl`. Leia `DiffuseSampler`, some o efeito, escreva `fragColor`. Centro e posições são relativos à câmera (`post.center()`, `scenePosAt`).
- Biblioteca: `#include "calamidade_fx.glsl"` (copie o arquivo para o seu namespace): `caRayBox`, `caRaySphere`, `caSegmentGlow`, `caWorldGrid`, `caRainbow`, `caEnvelope`, `caDistanceWave`, `caStars`. Documentação: `docs/ai/glsl.md`.
- Trilhas como uniforms: `post.uploadTracks(u, "Track_")`.
- Capítulo: `docs/AVANCADO-0.6.md`. Receitas: 1 (profundidade), 2 (raio-caixa), 3 (raio-esfera), 4 (tela inteira), 5 (segmento).
- Malha de mundo em vez de shader: `AddonLayers.world` (`triangle`, `ribbon`; confira `remainingVertices()`). Receita: 6.

### 5. Câmera

- `AddonLayers.film(id, film -> List.of(FilmApi.cut(...), FilmApi.sweep(...)))`, com cortes em `film.timeline().start("fase")`. Só toca para o conjurador (`Effects.start(id, jogador, posição)` ou cartão).
- Capítulo: `docs/AVANCADO-0.6.md`. Exemplo: `SampleClient` (Sinal de Maré).

### 6. Cues (gameplay no servidor)

- `Effects.cue(id, "marcador", ctx -> ...)`: dano (`ctx.living(raio)`), empurrão, som, custo. Roda no mesmo tick do visual, uma vez por instância (a cada volta dentro de um loop).
- Valide permissões e PvP no cue. O motor é cosmético.
- Capítulo: `docs/GAMEPLAY-0.5.md`. Receitas: 1, 3, 4, 6.

### 7. Teste

1. `./gradlew build` (inclui `checkShaders`, `checkApiDocs`, `checkRecipes`), `./gradlew dist`, build do addon.
2. No jogo: comando do efeito; **F7** (passes de pós, vértices, "Atmosfera: N ativas · passe sim/não"); **F8** em LOW, BALANCED e HIGH; intensidade dos efeitos de tela em 0; "ocultar clarões" ligado; "Efeitos de movimento" desligado.
3. Carga: `/calamidade bench <n> <efeito>` e `/calamidade_client record 30` (CSV em `calamidade_bench/`).
4. Roteiro: `docs/TESTE-REAL.md`. Registre o que mediu em `docs/VALIDACAO.md`.

## Orçamento e limites

| Recurso | LOW | BALANCED | HIGH |
|---|---|---|---|
| Efeitos desenhados por quadro | 3 | 6 | 12 |
| Passes de addon (cartões + `post.pass`) | 1 | 2 | 4 |
| Passes de efeitos HERO (orçamento próprio) | 1 | 2 | 3 |
| Efeitos HERO desenhados (os mais próximos) | 2 | 2 | 2 |
| Vértices de malha | 12 000 | 48 000 | 96 000 |
| Passos de raymarch do motor | 8 | 14 | 22 |

- `post.pass` devolve `false` sem vaga; o callback roda mesmo assim, então bloom, tremor e clarão continuam valendo. Um shader de cartão já gasta uma vaga.
- Passe de atmosfera: 1 por quadro, do motor, fora do orçamento.
- Duração até 12000 ticks; raio até 512; alcance 16..1024; 64 instâncias por servidor.
- Acessibilidade obrigatória: passes próprios de tela inteira × `post.screenEffects()`; nada de piscar com `!post.flashAllowed()`; nada de zoom ou borrão com `!post.motionEffects()`.

## Erros comuns

- Registrar fora da construção do mod (cartões são fechados no `RegisterEvent` de itens). Use o construtor do `@Mod` ou `FMLConstructModEvent`.
- Ordem errada: `cue` antes de `timeline`, ou atmosfera que lê trilha antes da timeline.
- Nome de fase ou trilha com maiúscula ou hífen (`[a-z0-9_]`).
- Grade ou padrão que "nada" com a câmera: use coordenadas relativas a um ponto fixo (o centro do efeito), não `scenePosAt` direto.
- `#include` de um arquivo que não está no namespace do shader (a RimuLib não procura no namespace do motor).
- Usar tipos da RimuLib ou do Minecraft sem conferir com `javap` (veja `AGENTS.md`).
