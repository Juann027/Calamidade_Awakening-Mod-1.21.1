# Sky API 0.12.0 — prévia visual (aprovada com ajustes, já aplicados)

Renderizadas fora do jogo com `node scripts/previa_passes.cjs ceu` (folha em `build/previa/ceu-0120.png`). Rodam os shaders **reais** do motor (`sky.fsh`, `atmosphere.fsh`, `calamidade_fx.glsl`, `calamidade_sky.glsl`) com o `common.glsl` da RimuLib, no WebGL2 do Chromium headless (SwiftShader), com os mesmos dados que o `SkyFrame` e o `AtmosphereFrame` mandam no jogo. A cena é sintética: montanhas num mapa de altura, céu e névoa no modelo do jogo (`caVanillaSky`) e o **sol do jogo somado como o `LevelRenderer` soma** (mesmo quadrado, mesma textura, lida do JAR de recursos que o build baixa e nunca versionada). Não substitui o teste numa GPU de verdade: vale a forma, a ordem dos passes e as cores; o brilho final depende do bloom e da tela.

![folha](ceu-0120.jpg)

| Linha | Formação | Auge | Dissipação |
|---|---|---|---|
| **BLACK_HOLE** (azimute -25, elevação 16, crepúsculo com estrelas da atmosfera) | 18 graus, disco 30% aceso: a sombra surge sobre as montanhas, o céu em volta escurece | 64 graus: disco largo e quase de lado atravessando a sombra, a metade de trás dobrada pela lente por cima dela, anel de fótons fino e muito brilhante, o lado que se aproxima mais claro, o céu em volta engolido; a lente entorta as estrelas e ondula o relevo distante sem recortá-lo | 12 graus colapsando, disco a 55% |
| **PORTAL** (azimute 15, elevação 28) | abertura 5%: uma fissura que pisca e solta arcos de energia (o piscar e os arcos fortes somem com "ocultar clarões") | aberto: anel com arcos de energia, nebulosa, estrelas densas e uma galáxia espiral girando por dentro | fechando (42%), a galáxia ainda girando |
| **ECLIPSE** (no sol do jogo, de manhã, a leste, 13 graus) | 62%: o sol quadrado do jogo foi apagado e redesenhado redondo; a lua só existe onde cobre o sol (a mordida) | total: coroa com serpentinas, céu e mundo escuros (escurecimento ligado às regras da atmosfera), estrelas | 95,5%: anel de diamante no último ponto de sol (clarão, some com "ocultar clarões") |

Arquivos: [buraco_negro-formacao](buraco_negro-formacao.jpg), [buraco_negro-auge](buraco_negro-auge.jpg), [buraco_negro-dissipacao](buraco_negro-dissipacao.jpg), [portal-formacao](portal-formacao.jpg), [portal-auge](portal-auge.jpg), [portal-dissipacao](portal-dissipacao.jpg), [eclipse-formacao](eclipse-formacao.jpg), [eclipse-auge](eclipse-auge.jpg), [eclipse-dissipacao](eclipse-dissipacao.jpg).

Desde a Etapa 6, a prévia usa framebuffers de 8 bits entre os passes, como o jogo (o teste em jogo mostrou que o ponto flutuante escondia um corte de brilho), e o céu da atmosfera é pintado pelo passe `sky`, debaixo das camadas. Extra: [raios-ao-longe](raios-ao-longe.jpg) (`DISTANT_BOLTS` durante a totalidade, escondidos pelas montanhas).

O que fica de fora da prévia: bloom e clarões da RimuLib (vêm depois dos passes), a névoa por fragmento do jogo, nuvens e as estrelas do próprio jogo (no jogo, à noite, elas também passam pela lente).

**Ajustes pedidos na aprovação (aplicados):** buraco negro bem maior e mais dramático (sombra de 1/5 do tamanho, disco largo e inclinado no estilo do cinema, arco dobrado por cima, anel de fótons fino e muito brilhante, céu em volta engolido); borda da lente sem recorte (o desvio some sem quina, o céu desviado continua céu e o terreno entorta só um terço); semente do portal como fissura que pisca e solta arcos; lua do eclipse só onde cobre o sol.
