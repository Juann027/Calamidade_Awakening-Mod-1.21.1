# BossesTesting 0.1.0 — El Guilhermino

Addon independente do **Calamidade Awakening 0.14.0** (mod ID `bossestesting`, projeto Gradle e JAR próprios).
Chefe em duas formas — **El Guilhermino Bananino** (JarGolem) e **El Guilhermino Finale** — e um míssil, com os
modelos, texturas, glowmasks e animações fornecidos (nomes normalizados, conteúdo intacto).

## Instalação

Minecraft 1.21.1, NeoForge 21.1.267+, Java 21. Na pasta `mods/`:

| JAR | Origem |
|---|---|
| `bossestesting-0.1.0.jar` | este addon (release do Calamidade) |
| `calamidade_awakening-0.14.0.jar` | release do Calamidade |
| `rimulib-1.3.0-neoforge.jar` | CurseForge (RimuLib) |
| `geckolib-neoforge-1.21.1-4.9.4.jar` | CurseForge/Modrinth (GeckoLib ≥ 4.9) |

Servidor e clientes precisam do addon (entidades, partículas e o canal `bossestesting:music_v1`).

Compilar: na raiz, `./gradlew dist`; depois `./gradlew -p examples/bossestesting build` (gera
`build/libs/bossestesting-0.1.0.jar`, roda `checkBoss` e `checkShaders`). Autoteste em servidor dedicado sem tela:
`./gradlew -p examples/bossestesting runSelfTest` (relatório em `run/bossestesting-selftest.txt`).

## Comandos (permissão 2)

O chefe **nunca nasce sozinho**: só por comando.

| Comando | Efeito |
|---|---|
| `/bossestesting spawn` ou `spawn phase1` | Primeira forma 16 blocos à frente, caindo do céu (`spawn_landing`) com câmera para os participantes |
| `/bossestesting spawn phase2` | Segunda forma direto (revelação + música) |
| `/bossestesting transform` | Força a transformação (`overload`) da primeira forma mais próxima |
| `/bossestesting overloaded on\|off` | `on`: toca `overload_v2` e ativa o estado Sobrecarregado; `off`: encerra o estado |
| `/bossestesting attack <ataque>` | Força um ataque: `slam`, `reactor`, `punch_right`, `punch_left`, `dash`, `stagger`, `fury`, `transform` (fase 1); `slam_v2`, `missile_barrage`, `punch_stream`, `exposed_reactor`, `overload_v2`, `charged_punch`, `shield_charge`, `leap_smash` (fase 2; os três últimos ligam o Sobrecarregado se preciso) |
| `/bossestesting freeze on\|off` | Congela/retoma a IA do chefe mais próximo |
| `/bossestesting damage <n>` | Causa `n` de dano no chefe mais próximo (barra, fases, transformação) |
| `/bossestesting test vfx <efeito>` | Toca qualquer efeito do addon (sugestões no Tab); presos ao chefe quando há um perto |
| `/bossestesting test sky p1\|p1_fury\|transform\|p2\|overload\|off` | Céu de teste em área na sua posição; `off` solta |
| `/bossestesting test music play\|stop` | Trilha da fase 2 só para você |
| `/bossestesting test bar` | Congela o chefe e põe a vida em 62% (use `damage`) |
| `/bossestesting destruction off\|low\|normal\|epic` | Intensidade da destruição (salva no config do servidor) |
| `/bossestesting destruction demo on\|off` | Perfil de demonstração: força EPIC para mundos de teste |
| `/bossestesting status` | Encontros, vida, último ataque, estado, música, participantes |
| `/bossestesting clear` | Remove chefes e mísseis, encerra encontros, música e céus |

Config do servidor (`serverconfig/bossestesting-server.toml`): `destruction.level`, `destruction.demoProfile`,
`balance.healthPhase1` (600), `healthPhase2` (800), `damageScale`, `arenaRadius` (64).

## Como funciona (para outras IAs)

- `anim/Anims.java` é **gerado** por `tools/eventos.py` a partir das animações e dos arquivos de eventos; a
  comparação está em `docs/EVENTOS.md`. Nenhum tempo é inventado: ações (`ai/Phase1Brain`, `ai/Phase2Brain`) e
  timelines (`fx/FxP1`, `fx/FxP2`) usam `Anims.X.at("evento")` + `Brain.LEAD` (transição do GeckoLib + 1 tick de rede).
- Gameplay (dano, knockback, movimento, mísseis, troca de entidade) roda no servidor; o GeckoLib no cliente só
  dispara cosméticos que o servidor ignora (passos, espasmos), sem duplicar eventos.
- Destruição: só Terrain API (`fx/Variants`: cada efeito destrutivo em 4 ids — sem terreno, LOW, NORMAL, EPIC),
  sempre sujeita a `mobGriefing`, config `terrain` do motor, blocos protegidos e claims. Dano e terreno são
  independentes e caem no mesmo tick.
- Céus: eventos globais AREA por fase do controlador (Boss API), soltos pelo motor ao mudar de fase ou acabar.
- Música: `sounds.json` com `stream: true`; troque `assets/bossestesting/sounds/music/guilhermino_phase2.ogg` por
  resource pack, sem mexer no código.
- Assets originais (painéis da barra, panoramas, flipbook, partículas, máscaras derivadas): `tools/gerar_assets.py`.

## Limitações reais

Veja `docs/LIMITACOES.md` (o que faltou na engine para a futura Combat VFX Engine) e `docs/VALIDACAO.md` (o que
foi e o que **não** foi testado: nenhum teste visual em GPU física nem multiplayer real foi executado).
