# Criando habilidades no Calamidade_Awakening

Para publicar uma habilidade em um JAR separado, use o [tutorial de criação de addons 0.4](ADDONS-0.4.md).

## Nova definição

Abra `src/main/java/dev/calamidade_awakening/api/VisualKind.java`. Cada definição associa uma habilidade a materiais e geometria. Exemplo de uma habilidade original adicional:

```java
public static final VisualKind HALO = register(
    5, "halo", "Ascending Halo", 0xFFFFCB85,
    120, 8,
    Geometry.ORBIT, Volume.VEIL,
    false, false
);
```

Registre definições antes de AwakeningSkills selar o catálogo. IDs e chaves são únicos; após remover uma habilidade, evite reutilizar seu ID. O registro é gerado no cliente e servidor a partir das mesmas definições. A nova habilidade terá um atalho configurável e aparecerá no menu ao segurar Foco Awakening.

Adicione traduções em assets/calamidade_awakening/lang e card PNG 64×32 em assets/calamidade_awakening/textures/gui/sprites/skill/focus/halo.png.

## Estilos reutilizáveis

| Geometria | Resultado |
|---|---|
| NONE | Sem lote de geometria para aquela habilidade |
| PRISM | Anéis, partículas, núcleo e feixe |
| RINGS | Anéis e partículas |
| ORBIT | Anéis inclinados, trilhas, feixe, ondas |
| NEXUS | Quatro lâminas curvas, fenda e frente de ruptura no Horizon Core |

| Material | Resultado |
|---|---|
| NONE | Sem passe volumétrico para aquela habilidade |
| CRYSTAL | Volume de cristal e malha |
| VEIL | Aro deformado com deslocamento tangencial |
| LENS | Lente radial, núcleo escuro e aberração |
| NEBULA | Campo comprimido com lente, luz na superfície e rastro da onda |

## Criar geometria própria

Adicione um estilo ao enum Geometry e sua composição em GeometryEffects. Use Mesh para emitir as primitivas, o centro e progresso de StageScene.Sample, e o perfil de qualidade. Não acesse diretamente o framebuffer nessa camada.

Exemplo de três anéis horizontais:

```java
for (int i = 0; i < 3 && frame.mesh().remaining() >= 12; i++) {
    double radius = 1 + i * 1.5 + sample.progress() * 5;
    frame.mesh().ring(sample.event().center(), new Vec3(1, 0, 0),
        new Vec3(0, 0, 1), radius, .2, quality.segments,
        new Color(.4f, .8f, 1f, sample.opacity()));
}
```

## Material volumétrico próprio

Adicione um modo de shader estável ao enum Volume e implemente uma função de densidade em awakening_field.fsh. A função recebe a posição normalizada dentro do volume, progressão da habilidade e seed. O passe já cuida da interseção, orçamento de passos, oclusão por profundidade e composição. Mantenha a densidade não negativa.

Exemplo conceitual de concha luminosa:

```glsl
float shell = exp(-abs(length(q) - 0.6) * 20.0);
float density = shell * (0.55 + 0.45 * sin(phase * 12.0 + q.y * 8.0));
```

## Sequência cinematográfica

Marque film=true para criar uma tomada local. orbital=true ativa a transição procedural por envelope durante uma parte da sequência. Use StageFilms para personalizar os planos futuros, mantendo um único diretor e cancelamento.

## Testes

O task Gradle checkBudget valida a seleção, a estabilidade em empates, as quantidades máximas, a integridade do catálogo, a recarga por habilidade e o agendamento de cues. Para cada nova habilidade, confirme o descarte após duração, render em outra dimensão, qualidade baixa e efeitos simultâneos.

## Publicar um efeito em outro mod

Para consumidores da 0.3.x, comece pelo [guia da API pública](API-0.3.md) e pelo projeto Gradle em examples/vfx-sample. As instruções anteriores desta página descrevem alterações internas do motor. Um consumidor pode manter a definição, a partícula e o som em seu próprio namespace.

Ao documentar uma nova camada, inclua o registro completo, o evento que a inicia, as unidades e os limites dos parâmetros, o comportamento para observadores e um comando para reproduzir o resultado. Para eventos luminosos, descreva também o resultado com a redução de clarões ligada. Faça o exemplo compilar contra o JAR que será distribuído.
