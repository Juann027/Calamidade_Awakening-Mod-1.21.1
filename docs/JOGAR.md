# Jogar e instalar

Motor modular de efeitos visuais para Minecraft Java 1.21.1, NeoForge 21.1.x e Java 21. A versão 0.12.0 usa RimuLib 1.3.0. As cinco habilidades internas são cosméticas; consequências no mundo (dano, empurrão) vêm de addons que usam os cues de servidor e, desde a 0.11.0, crateras e estruturas que caem pela Terrain API (que respeita as regras de terreno da config do servidor, desde a 0.11.1 também claims de mods de proteção, a borda do mundo, a proteção de spawn e o modo aventura, e se desfaz com `/calamidade terrain undo`).

## Instalação

Coloque o JAR `dist/calamidade_awakening-0.12.0.jar` e a RimuLib 1.3.0 para NeoForge na pasta mods. Baixe a RimuLib do [CurseForge](https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532) (`rimulib-1.3.0-neoforge.jar`); ela não fica no repositório nem nos releases do GitHub, que trazem só os JARs do Calamidade e do exemplo. Use as mesmas versões no servidor e em todos os clientes (a 0.10.0 mudou o pacote de início de efeito para `event_v2` a 0.11.0 criou o `terrain_v1` e a 0.12.0 o `world_event_v1`, o `world_end_v1` e o `sky_profile_v1`; versões diferentes simplesmente não trocam efeitos ou o terreno). Substitua o JAR da versão anterior; o namespace, o item e as teclas da 0.1.2 em diante continuam compatíveis. Para obter o Focus, use `/give @s calamidade_awakening:focus`. Teclas personalizadas são reatribuídas em Controles.

A interface usa inglês como padrão e tem tradução para português do Brasil pela opção de idioma do Minecraft.

| Tecla padrão | Ação |
|---|---|
| N | Abrir ou fechar o Atlas Estelar |
| B | Trazer o Atlas aberto para a frente da mira |
| Alt esquerdo (segurar) | Apontar no Atlas com o cursor |
| K | Pulso Prismático |
| U | Florescência Cristalina |
| I | Vitrais do Horizonte |
| V | Convergência da Aurora |
| Y | Núcleo do Horizonte |
| O | Interromper a câmera cinematográfica |
| F7 | Painel de diagnóstico |
| F8 | Qualidade: AUTO, LOW, BALANCED, HIGH, ULTRA |

Se a Convergência da Aurora ainda estiver em L depois de atualizar, reatribua para V em Controles. L abre a tela de Progressos do Minecraft.

## Atlas Estelar

Abra o **Atlas Estelar** pelo menu de pausa ou pressione **N** durante o jogo: um astrolábio de placas aparece parado no mundo, à sua frente, e você continua andando e olhando em volta. Mire em um cartão e pressione **Atacar** para conjurar a habilidade pelo servidor; **Usar item** ou a roda passam à próxima página quando há addons. Clicar numa placa nunca ataca nem quebra o que está atrás dela. As abas Qualidade, Acessibilidade, Terreno e Diagnóstico ficam no arco de cima. O Atlas fecha depois da escolha, na troca de dimensão, na morte ou a mais de 24 blocos. Capítulo completo: [ATLAS-0.11.2.md](ATLAS-0.11.2.md).

**Céu e eventos globais (0.12.0):** addons podem pintar o céu (buracos negros, portais, eclipses, luas gigantes, auroras, raios ao longe) e iniciar eventos que todo o servidor vê, em qualquer dimensão — como o eclipse do exemplo (`/vfxsample evento eclipse start`). `/calamidade world list` mostra os eventos; F7 mostra "Céu" e "Eventos globais". Uma dimensão de mod pode ter céu próprio: o jogo pergunta uma vez sobre "ajustes experimentais" ao abrir o mundo (é a dimensão por datapack). Veja CEU-0.12.md.

**Qualidade (0.11.2):** o padrão é **AUTO**, que escolhe entre LOW, BALANCED, HIGH e ULTRA pelo tempo de quadro e nunca troca durante um efeito protagonista. F8 força um perfil; F7 mostra o perfil e o motivo da última troca. Veja PRIORIDADE-0.9.md.

## Habilidades internas

**Núcleo do Horizonte** dura dez segundos. Quatro lâminas curvas se reúnem acima do alvo, estreitam-se numa fenda e liberam uma onda violeta e branca pelas superfícies visíveis próximas. A onda usa distância 3D, então chega mais tarde a blocos elevados. Quatro sons posicionais marcam reunião, tensão, ruptura e eco. A câmera segura o enquadramento durante o impacto; pressione O para voltar à sua visão.

**Pulso Prismático** libera o primeiro pulso cerca de quatro ticks depois da conjuração. Com 38 ticks de apresentação e 48 de recarga, é a alternativa rápida ao Núcleo do Horizonte.

A recarga é contada por habilidade: conjurar o Núcleo do Horizonte não impede o Pulso Prismático.

## Acessibilidade

Em **Mods → Calamidade_Awakening → Configurar** (ou no arquivo `config/calamidade_awakening-client.toml`):

- **Intensidade dos efeitos de tela** (0 a 1): reduz bloom, clarões, contraste e dessaturação deste mod e de todos os addons. 0 desliga.
- **Tremor de tela**: liga ou desliga o tremor.

A opção do Minecraft "Ocultar clarões de relâmpago" também desliga os clarões dos impact frames.

## Focus

Segurando o Foco Awakening, a tecla **H** abre o menu de habilidades da RimuLib. Escolha uma habilidade, feche o menu e use o Focus com o botão direito. Habilidades de addons aparecem no mesmo menu, com arte, descrição e, quando o addon define, controle de potência.

## Ferramentas de teste

**F7** mostra a GPU em uso, FPS, tempo de quadro (mediana e percentil 95), eventos ativos e o custo do quadro. `/calamidade_client record 30` grava 30 segundos em `calamidade_bench/` na pasta do jogo. Com permissão de comandos, `/calamidade bench 6` cria seis Núcleos do Horizonte à sua frente e `/calamidade bench stop` os encerra. O roteiro completo está no capítulo de testes em GPU real e multiplayer.

## Desenvolvimento

O build baixa a RimuLib do CurseForge para `libs/` e confere o SHA-256 (task `rimulib`, veja `libs/README.md`); o JAR nunca é versionado. Com o JDK 21, `./gradlew build` compila e roda os testes de orçamento; `./gradlew dist` copia o JAR para `dist/`; `./gradlew runClient` abre o cliente de desenvolvimento e `./gradlew runServer` o servidor dedicado. No Windows, use `gradlew.bat`.
