#!/usr/bin/env python3
"""Gera docs/MANUAL-GERAL.md a partir dos capítulos em docs/.

Edite sempre os arquivos de capítulo e rode:  python3 scripts/gerar_manual.py
O manual não deve ser editado à mão: ele é sobrescrito a cada geração.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
VERSION = re.search(r"^version = '([^']+)'", (ROOT / "build.gradle").read_text(encoding="utf-8"), re.M).group(1)

CHAPTERS = [
    ("JOGAR.md", "Jogar e instalar"),
    ("ATLAS-0.11.2.md", "Atlas Estelar: o painel holográfico no mundo"),
    ("API-0.3.md", "API de efeitos e impact frames"),
    ("GAMEPLAY-0.5.md", "Gameplay sincronizado no servidor"),
    ("ADDONS-0.4.md", "Criar addons e publicar cartões"),
    ("AVANCADO-0.6.md", "Ganchos avançados: pós-processamento, câmera e início"),
    ("TIMELINE-0.7.md", "Timeline: fases, trilhas, marcadores e sustain"),
    ("ATMOSFERA-0.8.md", "Atmosphere: céu, névoa, exposição e tinta"),
    ("FEIXES-0.9.md", "Beam: feixes de luz"),
    ("LUZ-0.9.md", "Light: luz visual sobre o mundo"),
    ("PRIORIDADE-0.9.md", "Prioridade protagonista e orçamento por quadro"),
    ("INTEGRACAO-0.10.md", "Integração com outros mods: orientação, fontes, limites e gatilhos"),
    ("TERRENO-0.11.md", "Terrain API: destruição, crateras e objetos que viram blocos reais"),
    ("ARQUITETURA.md", "Arquitetura do motor"),
    ("HABILIDADES.md", "Criar habilidades dentro do motor"),
    ("TESTE-REAL.md", "Testes em GPU real e multiplayer"),
    ("VALIDACAO.md", "Validação, medições e limites"),
    ("ROADMAP.md", "Histórico e evolução prevista"),
]

INTRO = f"""# Manual geral do Calamidade_Awakening {VERSION}

Minecraft Java 1.21.1 · NeoForge 21.1.267 · Java 21 · RimuLib 1.3.0

> Arquivo gerado por `scripts/gerar_manual.py` a partir dos capítulos em `docs/`. Edite os capítulos, não este arquivo.

## Por onde começar

**Para jogar**, instale a RimuLib e o JAR do Calamidade na pasta mods do cliente e do servidor. Pressione N para abrir o Atlas Estelar, um painel holográfico parado no mundo à sua frente (capítulo 2): mire num cartão e ataque para conjurar; as outras abas trazem qualidade, acessibilidade, terreno e diagnóstico. Para o segundo menu, obtenha o Focus com `/give @s calamidade_awakening:focus`, segure-o e pressione H. Veja o capítulo 1.

**Para criar seu primeiro efeito**, comece pelo capítulo 3. Use uma posição fixa para um portal ou uma âncora de entidade para acompanhar um personagem. Para que o efeito cause dano ou outra consequência no mundo, registre um cue (capítulo 4). Para publicar um cartão, adicionar uma câmera ou fornecer um shader pelo JAR do seu mod, siga o capítulo 5. O Sinal de Maré é o exemplo executável de todo esse fluxo.

**Para efeitos no nível de chefe** (uniforms próprios, vários passes por fase, zoom blur, câmera com cortes e travellings), use os ganchos do capítulo 6. Para escrever o roteiro do efeito uma vez só (fases, trilhas e marcadores que servidor, shader e câmera compartilham) e para efeitos ambientes em loop, use a Timeline do capítulo 7. Para mudar o clima da cena (céu, névoa, exposição, tinta e estrelas) sem escrever shader, use a Atmosphere do capítulo 8. Para feixes (lasers, pilares, teias) e para luz que clareia o terreno em volta do efeito, também sem shader, use o Beam e a Light dos capítulos 9 e 10. Para que o efeito principal de uma cena apareça inteiro com dezenas de outros na tela, marque-o como protagonista (HERO, capítulo 11, que também explica para leigos a diferença entre quantos efeitos são desenhados e quanto cada um custa). Para virar efeitos, dispará-los de itens, blocos e mobs e usar o Calamidade a partir de outro mod, veja o capítulo 12. Para crateras, trincheiras, um meteoro que vira bloco real ou uma construção que cai e fica no mundo, use a Terrain API do capítulo 13.

**Para IAs e agentes**, comece por `AGENTS.md` na raiz do repositório e pelo material de `docs/ai/`: referência da API gerada do JAR, guia de GLSL com a biblioteca `calamidade_fx.glsl`, vinte e uma receitas completas (`/vfxsample receita <nome>`, item, blocos e mob do exemplo) e o modelo para usar o Calamidade como dependência (`docs/ai/usar-como-dependencia.md`). `python3 scripts/novo_efeito.py <namespace> <nome>` gera o esqueleto de um efeito novo.

**Para modificar o motor**, consulte os capítulos 14 e 15. O build baixa a RimuLib do CurseForge para `libs/` e confere o SHA-256; ela não é versionada. Na raiz, use `./gradlew build` para compilar e testar, `./gradlew dist` para copiar o JAR para `dist/` e `./gradlew runClient` para abrir o cliente de desenvolvimento. No Windows, use `gradlew.bat`.

**Para medir e validar**, use o capítulo 16 (painel F7, gravação de quadros e `/calamidade bench`) e registre os resultados no capítulo 17.

**Escopo.** As cinco habilidades internas são cosméticas. O motor oferece iluminação visual por materiais e shaders, desde a 0.8 tingimento do céu e névoa por efeito (Atmosphere) e, desde a 0.9, feixes e luz visual sobre o que a câmera vê (Beam e Light) , desde a 0.10, efeitos orientados e gatilhos de item, bloco e mob e, desde a 0.11, edições de terreno (Terrain API) com regras de segurança do servidor e desfazer; a luz da 0.9 não muda o nível de luz da simulação (spawn de mobs, plantas), e substituir o skybox por texturas próprias exige implementação adicional. Consequências de gameplay pertencem aos addons, pelos cues de servidor e pela Terrain API.
"""


def demote(markdown: str) -> str:
    """Remove o título H1 do capítulo e desce um nível os demais títulos, fora de blocos de código."""
    out, fenced, dropped_title = [], False, False
    for line in markdown.splitlines():
        if line.lstrip().startswith("```"):
            fenced = not fenced
        elif not fenced and re.match(r"^#{1,5} ", line):
            if line.startswith("# ") and not dropped_title:
                dropped_title = True
                continue
            line = "#" + line
        out.append(line)
    return "\n".join(out).strip("\n")


def main() -> int:
    parts = [INTRO]
    parts.append("\n".join(f"{i}. [{title}](#cap{i})" for i, (_, title) in enumerate(CHAPTERS, 1)))
    parts.append("Leitura complementar, fora do manual: [estudo técnico da integração da SA Series](ESTUDO-SA-SERIES.md).")
    for i, (name, title) in enumerate(CHAPTERS, 1):
        body = demote((DOCS / name).read_text(encoding="utf-8"))
        parts.append(f'<a id="cap{i}"></a>\n\n## {i}. {title}\n\n{body}')
    (DOCS / "MANUAL-GERAL.md").write_text("\n\n".join(parts) + "\n", encoding="utf-8")
    print(f"docs/MANUAL-GERAL.md gerado com {len(CHAPTERS)} capítulos (versão {VERSION}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
