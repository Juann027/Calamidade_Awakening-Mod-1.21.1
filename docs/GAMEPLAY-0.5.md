# Gameplay sincronizado no servidor (cues)

O motor continua cosmético: ele não altera vida, blocos ou IA por conta própria. A partir da 0.5.0, um addon pode agendar **cues**: funções que rodam no servidor quando o relógio de uma instância chega a um tick. É o lugar certo para dano, empurrão, efeitos de poção, quebra de blocos ou custo de mana, porque o cue usa o mesmo relógio que o cliente usa para desenhar. O golpe acontece no quadro em que o jogador vê o contato.

## Registro

Registre o efeito e depois os cues, no construtor comum do mod:

```java
var golpe = ResourceLocation.fromNamespaceAndPath("meumod", "golpe");
Effects.register(golpe, new Effects.Definition(80, 4, 0xffffb050,
    Effects.Shape.RINGS, Effects.Material.LENS, null, null,
    new Effects.Impact(30, 3, .8f, Effects.ImpactStyle.TINTED_FLASH)));

// Mesmo tick do impact frame (30).
Effects.onImpact(golpe, ctx -> {
    var fonte = ctx.caster() == null ? ctx.level().damageSources().magic()
        : ctx.level().damageSources().indirectMagic(ctx.caster(), ctx.caster());
    for (var alvo : ctx.living(4)) alvo.hurt(fonte, 6);
});

// Qualquer outro tick, de 0 até a duração menos 1.
Effects.cue(golpe, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1f, 1f));
```

| Chamada | Resultado |
|---|---|
| Effects.cue(id, tick, cue) | Agenda o cue no tick indicado de toda instância desse efeito. Vários cues por efeito e por tick são permitidos e rodam na ordem de registro. |
| Effects.onImpact(id, cue) | Atalho para o tick do Impact da definição. Gera erro se a definição não tiver Impact. |
| ctx.level(), ctx.center() | Mundo e centro atual. Para instâncias presas a uma entidade, o centro já inclui o offset. |
| ctx.caster() | Jogador que conjurou, ou null quando o efeito foi iniciado sem jogador ou o jogador saiu. |
| ctx.anchor() | Entidade âncora de Effects.follow, ou null. |
| ctx.living(raio) | Entidades vivas cujo centro está dentro do raio, sem o conjurador e sem a âncora. Raio até 128. |
| ctx.tick(), ctx.seed() | Tick agendado e seed da instância (a mesma que o cliente usa no visual). |
| ctx.handle() | Handle da própria instância. `ctx.handle().stop()` encerra o efeito, por exemplo quando o alvo morre. `ctx.handle().release()` libera um efeito com sustain (0.7.0). |
| Effects.cue(id, "marcador", cue) | 0.7.0: cue no tick de um marcador da Timeline do efeito. Registre a timeline antes (capítulo da Timeline). |
| ctx.timeline(), ctx.localAge(), ctx.age() | 0.7.0: timeline do efeito (null sem timeline), idade local (igual ao tick) e idade real da instância. |

## Regras

- Registre somente efeitos do seu namespace. Os cinco efeitos internos não aceitam cues.
- O cue roda na thread do servidor, uma vez por instância e por tick agendado, mesmo se o servidor atrasar alguns ticks.
- Instâncias encerradas por `stop()`, perda da âncora ou descarregamento do chunk não executam os cues seguintes, nem os que estavam agendados para o mesmo tick depois do cue que chamou `stop()`.
- Uma exceção no cue é registrada no log e não derruba o efeito nem os outros cues.
- Valide permissões, PvP, proteção de terreno e custos dentro do cue ou no callback de conjuração. O motor não sabe quem pode ferir quem.
- Para chefes, combine `Effects.follow(id, chefe, offset)` com cues: o centro acompanha o chefe e o golpe acontece onde ele está naquele tick.

O Sinal de Maré do exemplo agenda o cue no marcador `impacto` da timeline (tick 40, o mesmo do impact frame) para causar 2 corações de dano e empurrar as criaturas a até três blocos. Com timeline, os ticks dos cues são locais: dentro de um loop de sustain, um cue roda a cada volta (veja TIMELINE-0.7.md). O código está em `examples/vfx-sample/src/main/java/example/vfxsample/Sample.java`.
