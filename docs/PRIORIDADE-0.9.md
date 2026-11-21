# Prioridade protagonista (HERO) e orçamento por quadro (0.9)

Num servidor cheio, dezenas de efeitos disputam a mesma tela. O motor sempre limitou quanto desenha por quadro, e isso protege o FPS. Mas o limite não sabia qual efeito era o principal da cena: o golpe final de um chefe podia perder o lugar para seis faíscas mais perto da câmera. A 0.9 deixa o addon dizer **"este efeito é o protagonista"**.

## Para quem não é programador: quantidade e complexidade

Dois números diferentes decidem o custo dos efeitos na tela:

- **Limite de efeitos desenhados ao mesmo tempo:** quantos efeitos o motor desenha num quadro. Os outros continuam existindo (estão sincronizados, tocam som, causam dano), só não são desenhados naquele quadro. O limite é 3, 6 ou 12 (qualidade LOW, BALANCED ou HIGH).
- **Complexidade de um efeito:** quanto trabalho a placa de vídeo tem para desenhar **um** efeito. O que mais pesa são os **passes**: cada passe é uma passada de shader pela tela inteira. Malhas (vértices), feixes e luzes também contam.

Pense numa cozinha de restaurante: o limite de desenhados é quantas mesas são servidas ao mesmo tempo; a complexidade é quanto trabalho dá cada prato. O orçamento de passes é o número de fogões: por mais mesas que cheguem, só cabem 1, 2 ou 4 panelas ao fogo (o orçamento comum dos addons).

O teste do autor na 0.8.0, numa RTX 4060 em BALANCED, mostra os dois limites funcionando:

| Carga | F7 | Resultado |
|---|---|---|
| 60 × receita de quadro de duas cores | 60 residentes · 6 desenhados · 2 passes de pós | FPS praticamente igual ao do mundo vazio: 60 efeitos chegaram, mas só 6 foram desenhados e só 2 passes rodaram. |
| 6 × céu tingido | 6 atmosferas · 1 passe de atmosfera | Mediana 8,37 ms, p95 11,29 ms: seis céus misturados num único passe custam o mesmo que seis Núcleos do Horizonte da 0.6.2 (8,40 ms e 11,56 ms). |

Ou seja: **mais efeitos não deixam o jogo mais lento depois do limite**; o que pesa é a complexidade dos que são desenhados. A prioridade HERO não muda esses números para os efeitos comuns; ela só garante que o protagonista esteja entre os desenhados e tenha os seus próprios fogões.

## O que HERO faz

```java
Effects.register(ID, new Effects.Definition(240, 80, 0xffffb030, Effects.Shape.NONE, Effects.Material.NONE,
    null, null).withRange(512).withPriority(Effects.Priority.HERO));
```

| Regra | NORMAL (padrão) | HERO |
|---|---|---|
| Seleção | Disputa as vagas por intensidade sobre distância. | Entra **antes** de todos os NORMAL, os mais próximos primeiro. |
| Limite de desenhados (3/6/12) | Pode ser cortado. | **Nunca é cortado**: ocupa vaga, mas o limite mínimo (3) é maior que o teto de HERO. |
| Teto | — | **No máximo 2 HERO desenhados por quadro**, os mais próximos. Um terceiro HERO, mais longe, não é desenhado naquele quadro. Assim, um servidor cheio de HERO não derruba o FPS de novo. |
| Passes (cartão, `post.pass`, feixes, luzes) | Orçamento **comum**: 1/2/4 por quadro, dividido entre os NORMAL. | Orçamento **HERO próprio**: 1/2/3 por quadro (LOW/BALANCED/HIGH), dividido entre os HERO desenhados. |
| Sem nenhum HERO | Exatamente como na 0.8. | — |

- Os dois orçamentos são separados: os HERO nunca gastam do comum, e os NORMAL nunca gastam do HERO.
- `post.passesLeft()` informa o orçamento da classe do efeito.
- Use HERO com moderação: um ou dois efeitos por addon, os que definem a cena.

## Ordem e pagamento dos passes num quadro

| Ordem na tela | Passe | Quem paga |
|---|---|---|
| 1 | `lights` (0.9) | HERO se algum HERO desenhado tem luzes; senão comum, se algum NORMAL tem. |
| 2 | `awakening_field` (volumes do motor) | Fora dos orçamentos de addon. |
| 3 | Shaders de cartão e `post.pass`, na ordem do plano (HERO primeiro) | O orçamento da classe do efeito. |
| 4 | `beams` (0.9) | HERO se algum HERO desenhado tem feixes; senão comum, se algum NORMAL tem. |
| 5 | `atmosphere` (0.8) | Fora dos orçamentos. |

Os passes de feixes e de luzes **são pagos primeiro** (feixes, depois luzes), antes dos cartões e de `post.pass`, porque um único passe serve todos os efeitos do quadro. Um orçamento nunca paga pelo outro; uma vez pago, o passe desenha os feixes ou as luzes das duas classes (HERO primeiro), até o teto do quadro (16/48/128 feixes, 8/24/64 luzes).

Exemplos em BALANCED (2 comuns, 2 HERO):

- Um HERO com feixes, luzes e um `post.pass`: feixes e luzes gastam os 2 HERO; o `post.pass` é recusado (`pass` devolve false; os ajustes de tela do callback continuam valendo). Em HIGH (3 HERO), os três cabem.
- Um NORMAL com feixes e luzes e outro NORMAL com shader de cartão: feixes e luzes gastam os 2 comuns; o cartão fica sem vaga naquele quadro.
- Um HERO com feixes e um NORMAL com feixes: o passe é pago pelo HERO e desenha os dois; o comum fica livre para cartões e `post.pass`.

## Diagnóstico

O F7 ganha a linha **"Protagonistas (HERO): N desenhadas · X/Y passes"**: quantos HERO foram desenhados e quantos passes do orçamento HERO (de Y = 1/2/3) foram usados, incluindo feixes e luzes pagos por ele. As linhas "Feixes" e "Luzes" dizem qual orçamento pagou cada passe. O CSV de `/calamidade_client record` ganha `heroes`, `heroPasses`, `beams`, `beamPass`, `lights` e `lightPass`.

## Como testar

1. `/calamidade bench 60` (60 Núcleos do Horizonte num arco à frente) e, logo depois, `/vfxsample receita protagonista`.
2. F7 em BALANCED: "6 desenhados", "Protagonistas (HERO): 1 desenhadas · 2/2 passes", "Feixes: 7 … passe HERO", "Luzes: 3 … passe HERO".
3. F8 para LOW: "1/1 passes", os feixes ficam e as luzes somem. F8 para HIGH: "2/3 passes".
4. `/calamidade_client record 8` com e sem o protagonista, para comparar o custo (TESTE-REAL.md, parte 4).

Coberto por testes puros no `checkBudget` (`PriorityCheck`: HERO nunca cortado, teto de 2, orçamento separado e seleção NORMAL idêntica à da 0.8 quando não há HERO; `BeamCheck`: pagamento dos passes compartilhados).
