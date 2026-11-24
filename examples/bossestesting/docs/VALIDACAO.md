# Validação — BossesTesting 0.1.0 (24/11/2026)

Ambiente: container Linux, Java 21, NeoForge 21.1.267, Calamidade 0.14.0 (`dist/` local), RimuLib 1.3.0,
GeckoLib 4.9.4. Renderização **por software** (Mesa llvmpipe sob Xvfb), sem placa de áudio.

## Executado nesta sessão

| Teste | Resultado |
|---|---|
| `./gradlew -p examples/bossestesting build` (compilação, `checkBoss`, `checkShaders`) | verde |
| `checkBoss`: todos os registros do motor (70 ids de efeito: timelines, feixes, luzes, terreno, céus, 2 controladores) validados pelos builders do Calamidade fora do jogo; 243 usos de eventos de animação conferidos; pouso aos 1,5 s; `launch` do míssil termina no tick 9 (keyframe 0,417 s > 0,4 s declarados) | ok |
| `checkShaders`: 6 shaders `bt_*.fsh` com glslangValidator (common.glsl da RimuLib + `#include`) | ok |
| `runSelfTest`: servidor dedicado sem jogadores, mundo plano; pouso físico, os 6 ataques da fase 1, transformação por dano letal, os 8 ataques da fase 2 (incluindo Overloaded), morte | **43 ok, 0 falhas**: dano em mob em cada ataque, mísseis lançados e explodidos, Overloaded ativado, brecha aberta em parede de pedra pelo Shield Charge, um controlador por vez, nenhum loot prematuro, loot uma vez, limpeza do encontro, destruição real (78 732 → 72 963 blocos sólidos na arena), 0 eventos com erro |
| `runClientTest` (xvfb): mesma luta no cliente integrado, 18 capturas em `run-client/screenshots/` | completou; os 6 shaders compilaram no driver (llvmpipe); renderizadores GeckoLib, glowmasks, barras, céus, partículas e câmeras rodaram sem exceção |

Bugs encontrados e corrigidos por esses testes: NPE no `Brain` quando a troca de entidade remove o chefe durante a
própria ação; shaders não encontrados pela RimuLib (faltava `Addons.resources`); tela de onboarding no primeiro
boot do cliente de teste.

## Não testado (nenhuma afirmação)

- Aparência e desempenho em **GPU física**; as capturas por software só provam que os caminhos executam.
- **Áudio**: não há dispositivo de som no container; streaming, laço, fade e supressão da música vanilla foram
  implementados e compilados, mas não ouvidos.
- **Multiplayer real** (dois ou mais clientes), entrada tardia e troca de dimensão com jogadores reais.
- Mods de proteção (claims) reais; só as regras do motor (mobGriefing, config `terrain`) estiveram ativas.
- Shaderpacks, Sodium/Embeddium.
