package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Backdrop;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Mod de teste em jogo da Terrain API (0.11.1). Registra efeitos de cratera sem borda irregular (a forma não depende da
 * seed, então dá para comparar regiões), um listener de proteção que imita um mod de claims e, com a propriedade
 * {@code calamidade.teste} ({@code cenarios} ou {@code reinicio}), inicia o {@link Roteiro} quando o servidor sobe.
 */
@Mod(CalamidadeTeste.ID)
public final class CalamidadeTeste {
    public static final String ID = "calamidade_teste";
    /** Chunks serializados pelo jogo (ChunkDataEvent.Save), para conferir que a cratera foi salva. */
    static final java.util.Set<Long> SALVOS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final Timeline TIMELINE = Timeline.builder(60).marker("impacto", 5).build();

    /** Cratera com casca queimada de 1. */
    static final ResourceLocation CRATERA = id("cratera");
    /** Cratera sem casca (só vedação de fluidos e queima do que não é sólido): para a casca externa. */
    static final ResourceLocation VEDADA = id("vedada");
    /** Cratera com casca e uma esfera de glowstone no fundo: luz do céu e dos blocos. */
    static final ResourceLocation LUZ = id("luz");
    /** Cratera com composteiras no fundo: pontos de interesse adicionados. */
    static final ResourceLocation POI = id("poi");
    /** Cratera que troca blocos com block entity (com terrainBlockEntities ligado): dados de volta no desfazer. */
    static final ResourceLocation ENTIDADES = id("entidades");
    /** Esfera de raio 24 com casca de 2 (~73 000 blocos), a cratera grande da medição da 0.11.0: custo da casca externa. */
    static final ResourceLocation GRANDE = id("grande");

    public CalamidadeTeste() {
        registrar(CRATERA, TerrainEdit.carve(TerrainShape.crater(6, 4)).commitAt("impacto").burn(TerrainPalette.burnt(), 1).build());
        registrar(VEDADA, TerrainEdit.carve(TerrainShape.crater(6, 4)).commitAt("impacto").build());
        // Sem calor: no cliente, a cratera da Terrain API é comparada pixel a pixel com a mesma troca feita por setBlock.
        registrar(LUZ, TerrainEdit.carve(TerrainShape.crater(6, 4)).commitAt("impacto").burn(TerrainPalette.burnt(), 1)
            .fill(TerrainEdit.Fill.solid("minecraft:glowstone", 1.5f).at(0, -3, 0)).noHeat().build());
        registrar(POI, TerrainEdit.carve(TerrainShape.crater(6, 4)).commitAt("impacto")
            .fill(TerrainEdit.Fill.solid("minecraft:composter", 1f).at(0, -3, 0)).build());
        registrar(GRANDE, TerrainEdit.carve(TerrainShape.sphere(24)).commitAt("impacto").burn(TerrainPalette.burnt(), 2).noHeat().build());
        registrar(ENTIDADES, TerrainEdit.carve(TerrainShape.crater(6, 4)).commitAt("impacto").replacingBlockEntities().noHeat().build());
        registrarCeu();
        registrarFundos();
        NeoForge.EVENT_BUS.register(Protecao.class);
        NeoForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) -> SALVOS.add(event.getChunk().getPos().toLong()));
        NeoForge.EVENT_BUS.addListener(CalamidadeTeste::started);
        if (FMLEnvironment.dist == Dist.CLIENT && "cliente".equals(System.getProperty("calamidade.teste"))) RoteiroCliente.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "medicao".equals(System.getProperty("calamidade.teste"))) RoteiroMedicao.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "atlas".equals(System.getProperty("calamidade.teste"))) RoteiroAtlas.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "ceu".equals(System.getProperty("calamidade.teste"))) RoteiroCeu.registrar();
        // 0.14: teste do Backdrop (fase 2: os fundos reais do motor, registrados em registrarFundos).
        if (FMLEnvironment.dist == Dist.CLIENT && "backdrop".equals(System.getProperty("calamidade.teste"))) RoteiroBackdrop.registrar();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    // 0.12.0: eventos globais, céu por dimensão e a carga do passe de céu.
    /** Evento global em loop (entrada de 50 ticks, laço de 50 a 200, saída até 300) com eclipse e atmosfera. */
    static final ResourceLocation GLOBAL = id("global");
    /** Eventos globais curtos (10 min, sem laço) para o limite e as prioridades. */
    static final ResourceLocation[] CURTOS = {id("curto_a"), id("curto_b"), id("curto_c"), id("curto_d"), id("curto_e")};
    /** Uma camada (buraco negro) e quatro camadas (portal, astro, fenda, aurora): medição do passe de céu. */
    static final ResourceLocation CEU_UM = id("ceu_um"), CEU_QUATRO = id("ceu_quatro");
    /** Perfil de céu do End (dimensão vanilla presente no servidor dedicado): estados "a" e "b". */
    static final ResourceLocation END = ResourceLocation.withDefaultNamespace("the_end");

    private static void registrarCeu() {
        var def = new Effects.Definition(300, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(1024);
        Effects.register(GLOBAL, def);
        Effects.timeline(GLOBAL, Timeline.builder(300).track("cobrir", Timeline.keyframe(0, 0f), Timeline.keyframe(50, 1f),
            Timeline.keyframe(200, 1f), Timeline.keyframe(300, 0f)).marker("laco", 100).sustain(50, 200).build());
        Effects.sky(GLOBAL, dev.calamidade_awakening.api.v1.Sky.eclipse().track(dev.calamidade_awakening.api.v1.Sky.Param.PROGRESS, "cobrir").build(),
            dev.calamidade_awakening.api.v1.Sky.bolts().build());
        Effects.atmosphere(GLOBAL, dev.calamidade_awakening.api.v1.Atmosphere.builder().sky(0xff020308, 0xff302040, .8f).stars(1).build());
        Effects.cue(GLOBAL, "laco", ctx -> CUES_GLOBAIS.incrementAndGet());
        for (ResourceLocation curto : CURTOS)
            Effects.register(curto, new Effects.Definition(12000, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        var longo = new Effects.Definition(12000, 8, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(1024);
        Effects.register(CEU_UM, longo);
        Effects.sky(CEU_UM, dev.calamidade_awakening.api.v1.Sky.blackHole().direction(0, 30).size(50).build());
        Effects.register(CEU_QUATRO, longo);
        Effects.sky(CEU_QUATRO, dev.calamidade_awakening.api.v1.Sky.portal().direction(-40, 35).size(36).build(),
            dev.calamidade_awakening.api.v1.Sky.body().direction(40, 30).size(30).tilt(20).build(),
            dev.calamidade_awakening.api.v1.Sky.rift().direction(0, 60).size(80).build(),
            dev.calamidade_awakening.api.v1.Sky.aurora().direction(0, 10).size(170).build());
        dev.calamidade_awakening.api.v1.SkyProfiles.define(END, dev.calamidade_awakening.api.v1.SkyProfile.builder()
            .state("a", dev.calamidade_awakening.api.v1.SkyProfile.State.builder().build())
            .state("b", dev.calamidade_awakening.api.v1.SkyProfile.State.builder()
                .layers(dev.calamidade_awakening.api.v1.Sky.body().build()).vanillaSun(false).build())
            // 0.14 (fase 2): transição entre dois fundos (só o testeBackdrop usa estes estados).
            .state("fundo_a", dev.calamidade_awakening.api.v1.SkyProfile.State.builder().backdrop(Backdrop.cubemap(CUBO).build()).build())
            .state("fundo_b", dev.calamidade_awakening.api.v1.SkyProfile.State.builder().backdrop(Backdrop.panorama(FAIXAS).build()).build())
            .build());
    }

    // 0.14 (fase 2): fundos de céu para o testeBackdrop. Texturas de scripts/gerar_fundos_teste.py.
    static final String CUBO = ID + ":textures/sky/cubo.png", GRADE = ID + ":textures/sky/grade.png",
        MEIA = ID + ":textures/sky/meia.png", FAIXAS = ID + ":textures/sky/faixas.png";
    static final ResourceLocation FUNDO_CUBO = id("fundo_cubo"), FUNDO_GRADE = id("fundo_grade"), FUNDO_GIRADO = id("fundo_girado"),
        FUNDO_MEIA = id("fundo_meia"), FUNDO_SOL = id("fundo_sol"), FUNDO_GRADIENTE = id("fundo_gradiente"),
        FUNDO_SEM_NEVOA = id("fundo_sem_nevoa"), FUNDO_ATMO = id("fundo_atmo"), FUNDO_FAIXAS = id("fundo_faixas");
    /** Gradiente do teste (ARGB): zênite, horizonte, nadir. */
    static final int GRAD_TOPO = 0xff3010a0, GRAD_HORIZONTE = 0xffe07020, GRAD_BAIXO = 0xff102010;
    /** Atmosfera de FUNDO_ATMO: sem estrelas nem névoa, para comparar com mix(fundo, gradiente, 0,7). */
    static final int ATMO_TOPO = 0xff0c0520, ATMO_HORIZONTE = 0xffd85a33;
    static final float ATMO_MISTURA = .7f;

    private static void registrarFundos() {
        var def = new Effects.Definition(12000, 8, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(1024);
        Effects.register(FUNDO_CUBO, def);
        Effects.backdrop(FUNDO_CUBO, Backdrop.cubemap(CUBO).build());
        Effects.register(FUNDO_GRADE, def);
        Effects.backdrop(FUNDO_GRADE, Backdrop.cubemap(GRADE).build());
        Effects.register(FUNDO_GIRADO, def);
        Effects.backdrop(FUNDO_GIRADO, Backdrop.cubemap(CUBO).rotation(90).build());
        Effects.register(FUNDO_MEIA, def);
        // Sem névoa casada: com fundo parcial, a névoa casada muda também o horizonte do céu do jogo que aparece por trás;
        // aqui o teste mede só a mistura por alfa.
        Effects.backdrop(FUNDO_MEIA, Backdrop.panorama(MEIA).matchFog(false).build());
        Effects.register(FUNDO_SOL, def);
        Effects.backdrop(FUNDO_SOL, Backdrop.cubemap(CUBO).vanillaSunMoon(true).build());
        Effects.register(FUNDO_GRADIENTE, def);
        Effects.backdrop(FUNDO_GRADIENTE, Backdrop.gradient(GRAD_TOPO, GRAD_HORIZONTE, GRAD_BAIXO).build());
        Effects.register(FUNDO_SEM_NEVOA, def);
        Effects.backdrop(FUNDO_SEM_NEVOA, Backdrop.cubemap(CUBO).matchFog(false).build());
        Effects.register(FUNDO_FAIXAS, def);
        Effects.backdrop(FUNDO_FAIXAS, Backdrop.panorama(FAIXAS).build());
        Effects.register(FUNDO_ATMO, def);
        Effects.atmosphere(FUNDO_ATMO, dev.calamidade_awakening.api.v1.Atmosphere.builder().sky(ATMO_TOPO, ATMO_HORIZONTE, ATMO_MISTURA).build());
        Effects.backdrop(FUNDO_ATMO, Backdrop.cubemap(CUBO).build());
    }

    /** Voltas do laço do evento global em que o cue rodou (no servidor). */
    static final java.util.concurrent.atomic.AtomicInteger CUES_GLOBAIS = new java.util.concurrent.atomic.AtomicInteger();

    private static void registrar(ResourceLocation id, TerrainEdit edit) {
        Effects.register(id, new Effects.Definition(60, 12, 0xffff5a14, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.timeline(id, TIMELINE);
        Effects.terrain(id, edit);
    }

    private static void started(ServerStartedEvent event) {
        String fase = System.getProperty("calamidade.teste");
        if (fase == null || fase.isBlank() || fase.equals("cliente") || fase.equals("medicao") || fase.equals("atlas")
            || fase.equals("ceu") || fase.equals("backdrop")) return;
        Thread thread = new Thread(new Roteiro(event.getServer(), fase), "Calamidade teste em jogo");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Imita um mod de proteção territorial que só conhece os eventos de explosão do NeoForge: tira de
     * {@code ExplosionEvent.Detonate} as posições dentro da área protegida e, se pedido, cancela o
     * {@code ExplosionEvent.Start}.
     */
    public static final class Protecao {
        static volatile BoundingBox area;
        static volatile boolean cancelarInicio;
        static volatile int inicios, detonacoes, oferecidas, retiradas;
        static volatile float raio;
        static volatile boolean fonteNula;

        private Protecao() {}

        static void limpar() {
            area = null; cancelarInicio = false; inicios = detonacoes = oferecidas = retiradas = 0; raio = 0; fonteNula = false;
        }

        @SubscribeEvent
        public static void inicio(ExplosionEvent.Start event) {
            inicios++;
            if (cancelarInicio) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void detonacao(ExplosionEvent.Detonate event) {
            detonacoes++;
            raio = event.getExplosion().radius();
            fonteNula = event.getExplosion().getDirectSourceEntity() == null;
            var blocks = event.getAffectedBlocks();
            oferecidas = blocks.size();
            BoundingBox box = area;
            if (box == null) return;
            int before = blocks.size();
            blocks.removeIf(pos -> box.isInside((BlockPos) pos));
            retiradas = before - blocks.size();
        }
    }
}
