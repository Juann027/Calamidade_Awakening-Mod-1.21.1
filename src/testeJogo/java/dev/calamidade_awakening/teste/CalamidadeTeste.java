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
        registrarEstrelas();
        registrarImagens();
        ChefesTeste.registrar();  // 0.13.0
        NeoForge.EVENT_BUS.register(Protecao.class);
        NeoForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) -> SALVOS.add(event.getChunk().getPos().toLong()));
        NeoForge.EVENT_BUS.addListener(CalamidadeTeste::started);
        if (FMLEnvironment.dist == Dist.CLIENT && "cliente".equals(System.getProperty("calamidade.teste"))) RoteiroCliente.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "medicao".equals(System.getProperty("calamidade.teste"))) RoteiroMedicao.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "atlas".equals(System.getProperty("calamidade.teste"))) RoteiroAtlas.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "ceu".equals(System.getProperty("calamidade.teste"))) RoteiroCeu.registrar();
        // 0.14: teste do Backdrop (fase 2: os fundos reais do motor, registrados em registrarFundos).
        if (FMLEnvironment.dist == Dist.CLIENT && "backdrop".equals(System.getProperty("calamidade.teste"))) RoteiroBackdrop.registrar();
        // 0.14 (fase 3): estrelas, constelações e nebulosas (registradas em registrarEstrelas).
        if (FMLEnvironment.dist == Dist.CLIENT && "estrelas".equals(System.getProperty("calamidade.teste"))) RoteiroEstrelas.registrar();
        // 0.14 (fase 4): imagens, flipbooks e impostors (registrados em registrarImagens).
        if (FMLEnvironment.dist == Dist.CLIENT && "imagens".equals(System.getProperty("calamidade.teste"))) RoteiroImagens.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "chefe".equals(System.getProperty("calamidade.teste"))) RoteiroChefe.registrar();
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

    // 0.14 (fase 3): objetos do céu para o testeEstrelas. Celestial desligado nos de sonda (direção fixa na tela).
    static final ResourceLocation EST_CAMPO = id("est_campo"), EST_DENSO = id("est_denso"), EST_CATALOGO = id("est_catalogo"),
        EST_NEBULOSA = id("est_nebulosa"), EST_COBRE = id("est_cobre"), EST_ATMO = id("est_atmo"), EST_VITRINE = id("est_vitrine"),
        EST_FAIXA = id("est_faixa");
    /** Estrela vermelha NORMAL do catálogo, no sul a 30 graus de altura. */
    static final int EST_VERMELHA = 0xff2010;
    /** Cor única das nebulosas de sonda. */
    static final int EST_NEB = 0xff40a0;

    // 0.14 (fase 4): imagens, flipbooks e impostors para o testeImagens. Recursos de scripts/gerar_sprites_teste.py; todos
    // no sul a 30 graus (centro da tela olhando para lá), fixos no céu (não celestes), sem horizonte nem luz do dia.
    static final ResourceLocation IMG_DISCO = id("img_disco"), IMG_COBRE = id("img_cobre"), IMG_LOOP = id("img_loop"),
        IMG_ONCE = id("img_once"), IMG_PING = id("img_ping"), IMG_TRACK = id("img_track"), IMG_PISCA = id("img_pisca"),
        IMG_PISCA_SEM = id("img_pisca_sem"), IMP_NAVE_0 = id("imp_nave_0"), IMP_NAVE_90 = id("imp_nave_90"),
        IMP_NAVE_180 = id("imp_nave_180"), IMP_PEQUENA = id("imp_pequena"), IMG_FRENTE = id("img_frente"),
        IMG_ATRAS = id("img_atras"), IMG_INVALIDOS = id("img_invalidos"), IMG_DUPLA = id("img_dupla"),
        IMG_MUITAS = id("img_muitas"), IMG_VITRINE = id("img_vitrine");
    static final String TEX_DISCO = ID + ":textures/sky/img_disco.png", TEX_SUAVE = ID + ":textures/sky/img_suave.png",
        TEX_QUADROS = ID + ":textures/sky/img_quadros.png", TEX_PISCA = ID + ":textures/sky/img_pisca.png";
    /** Cores das texturas de teste (scripts/gerar_sprites_teste.py). */
    static final int COR_DISCO = 0xf030a0, COR_SUAVE = 0x30c0f0;
    static final int[] COR_QUADROS = {0xe04040, 0x40e040, 0x4040e0, 0xe0e040};
    static final int[] COR_VISTAS = {0xf04040, 0xf0a030, 0xe0e030, 0x50e050, 0x30d0d0, 0x4060f0, 0xa040f0, 0xf050c0};

    private static dev.calamidade_awakening.api.v1.SkyImage.Builder sul(dev.calamidade_awakening.api.v1.SkyImage.Builder b) {
        return b.direction(0, 30).daylight(0).horizon(0).atmosphere(1);
    }

    private static void registrarImagens() {
        var def = new Effects.Definition(12000, 8, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(1024);
        Effects.register(IMG_DISCO, def);
        Effects.images(IMG_DISCO, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_DISCO)).size(12).build());
        Effects.register(IMG_COBRE, def);
        Effects.images(IMG_COBRE, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_SUAVE)).size(12)
            .blend(dev.calamidade_awakening.api.v1.SkyBlend.COVER).build());
        // Flipbooks de 4 quadros de cor sentinela, a 1 quadro por segundo (as fotos caem no meio de cada quadro).
        Effects.register(IMG_LOOP, def);
        Effects.images(IMG_LOOP, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_QUADROS, 4, 1)).size(8).fps(1).build());
        Effects.register(IMG_ONCE, def);
        Effects.images(IMG_ONCE, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_QUADROS, 4, 1)).size(8).fps(1)
            .play(dev.calamidade_awakening.api.v1.SkyImage.Play.ONCE).build());
        Effects.register(IMG_PING, def);
        Effects.images(IMG_PING, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_QUADROS, 4, 1)).size(8).fps(1)
            .play(dev.calamidade_awakening.api.v1.SkyImage.Play.PING_PONG).build());
        Effects.register(IMG_TRACK, def);
        Effects.timeline(IMG_TRACK, dev.calamidade_awakening.api.v1.Timeline.builder(12000)
            .track("quadro", dev.calamidade_awakening.api.v1.Timeline.keyframe(0, 2)).build());
        Effects.images(IMG_TRACK, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_QUADROS, 4, 1)).size(8)
            .play(dev.calamidade_awakening.api.v1.SkyImage.Play.TRACK).track(dev.calamidade_awakening.api.v1.SkyImage.Param.FRAME, "quadro").build());
        // Folha que pisca (preto/branco a 12 por segundo): marcada pelo addon e sem marca (a rede de segurança).
        Effects.register(IMG_PISCA, def);
        Effects.images(IMG_PISCA, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_PISCA, 2, 1)).size(8).fps(12).flashing(true).build());
        Effects.register(IMG_PISCA_SEM, def);
        Effects.images(IMG_PISCA_SEM, sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_PISCA, 2, 1)).size(8).fps(12).build());
        // Impostor de 8 vistas, uma cor por vista, sem mistura (NEAREST: cor exata), virado para 0, 90 e 180 graus.
        for (var e : new Object[][] {{IMP_NAVE_0, 0f}, {IMP_NAVE_90, 90f}, {IMP_NAVE_180, 180f}}) {
            Effects.register((ResourceLocation) e[0], def);
            Effects.impostors((ResourceLocation) e[0], dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":nave").direction(0, 30).size(10)
                .orientation((float) e[1], 0, 0).viewBlend(dev.calamidade_awakening.api.v1.SkyImpostor.ViewBlend.NEAREST).build());
        }
        Effects.register(IMP_PEQUENA, def);
        Effects.impostors(IMP_PEQUENA, dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":nave").direction(0, 30).size(.4f).build());
        // FRONT e BEHIND diante de um portal do motor no mesmo lugar.
        for (var e : new Object[][] {{IMG_FRENTE, dev.calamidade_awakening.api.v1.SkyImage.Layer.FRONT},
                {IMG_ATRAS, dev.calamidade_awakening.api.v1.SkyImage.Layer.BEHIND}}) {
            ResourceLocation rid = (ResourceLocation) e[0];
            Effects.register(rid, def);
            Effects.sky(rid, dev.calamidade_awakening.api.v1.Sky.portal().direction(0, 30).size(30).build());
            Effects.images(rid, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_DISCO)).size(8)
                .blend(dev.calamidade_awakening.api.v1.SkyBlend.COVER).layer((dev.calamidade_awakening.api.v1.SkyImage.Layer) e[1]).build());
        }
        // Recursos inválidos junto de uma imagem válida: PNG ausente, PNG corrompido, grade que não divide a imagem,
        // descritor inválido e descritor com página ausente.
        Effects.register(IMG_INVALIDOS, def);
        Effects.images(IMG_INVALIDOS, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_DISCO)).size(12).build(),
            sul(dev.calamidade_awakening.api.v1.SkyImage.of(ID + ":textures/sky/nao_existe.png")).direction(30, 30).build(),
            sul(dev.calamidade_awakening.api.v1.SkyImage.of(ID + ":textures/sky/img_corrompido.png")).direction(-30, 30).build(),
            sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_DISCO, 5, 1)).direction(20, 45).build());
        Effects.impostors(IMG_INVALIDOS, dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":quebrado").direction(0, 60).build(),
            dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":sem_pagina").direction(40, 60).build(),
            dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":nao_existe").direction(-40, 60).build());
        // Duas imagens da mesma textura lado a lado: uma chamada de desenho para as duas.
        Effects.register(IMG_DUPLA, def);
        Effects.images(IMG_DUPLA, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_DISCO)).direction(-6, 30).size(6).build(),
            sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_DISCO)).direction(6, 30).size(6).build());
        // Carga: 8 imagens de 4 texturas (custo, INFO).
        Effects.register(IMG_MUITAS, def);
        var muitas = new dev.calamidade_awakening.api.v1.SkyImage[8];
        String[] tex = {TEX_DISCO, TEX_SUAVE, TEX_QUADROS, TEX_DISCO};
        for (int i = 0; i < 8; i++) muitas[i] = dev.calamidade_awakening.api.v1.SkyImage.of(tex[i % 4]).direction(-35 + i * 10, 25 + (i % 2) * 12)
            .size(9).daylight(0).build();
        Effects.images(IMG_MUITAS, muitas);
        // Vitrine para a captura: disco, folha animada e o impostor girando (trilha de yaw).
        Effects.register(IMG_VITRINE, def);
        Effects.timeline(IMG_VITRINE, dev.calamidade_awakening.api.v1.Timeline.builder(12000)
            .track("giro", dev.calamidade_awakening.api.v1.Timeline.keyframe(0, 0), dev.calamidade_awakening.api.v1.Timeline.keyframe(12000, 36000)).build());
        Effects.images(IMG_VITRINE, sul(dev.calamidade_awakening.api.v1.SkyImage.of(TEX_SUAVE)).direction(-14, 34).size(14).build(),
            sul(dev.calamidade_awakening.api.v1.SkyImage.sheet(TEX_QUADROS, 4, 1)).direction(14, 26).size(7).fps(2).blendFrames(true).build());
        Effects.impostors(IMG_VITRINE, dev.calamidade_awakening.api.v1.SkyImpostor.of(ID + ":nave").direction(0, 34).size(10)
            .track(dev.calamidade_awakening.api.v1.SkyImpostor.Param.YAW, "giro").build());
    }

    private static void registrarEstrelas() {
        var def = new Effects.Definition(12000, 8, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(1024);
        Effects.register(EST_CAMPO, def);
        Effects.stars(EST_CAMPO, dev.calamidade_awakening.api.v1.StarField.random(42, 6000).magnitudes(-1, 6).band(90, 30, 14, .5f).build());
        Effects.register(EST_DENSO, def);
        Effects.stars(EST_DENSO, dev.calamidade_awakening.api.v1.StarField.random(7, 60000).build());
        Effects.register(EST_CATALOGO, def);
        Effects.stars(EST_CATALOGO, dev.calamidade_awakening.api.v1.StarField.catalog()
            .star("a", 0, 30, -1.5f, 0xff000000 | EST_VERMELHA).star("b", 12, 38, 1, 0xffffffff).star("c", -12, 40, 1.2f, 0xffffffff)
            .constellation(0xc0a0b8ff, "c", "a", "b").size(8).blend(dev.calamidade_awakening.api.v1.SkyBlend.NORMAL)
            .daylight(0).celestial(false).horizon(0).twinkle(0, 0).build());
        Effects.register(EST_NEBULOSA, def);
        Effects.nebulae(EST_NEBULOSA, dev.calamidade_awakening.api.v1.Nebula.cloud(0, 30, 16).colors(0xff000000 | EST_NEB, 0xff000000 | EST_NEB)
            .density(.5f).daylight(0).celestial(false).horizon(0).build());
        Effects.register(EST_COBRE, def);
        Effects.nebulae(EST_COBRE, dev.calamidade_awakening.api.v1.Nebula.cloud(0, 30, 16).colors(0xff000000 | EST_NEB, 0xff000000 | EST_NEB)
            .density(1).blend(dev.calamidade_awakening.api.v1.SkyBlend.COVER).daylight(0).celestial(false).horizon(0).build());
        Effects.register(EST_ATMO, def);
        Effects.atmosphere(EST_ATMO, dev.calamidade_awakening.api.v1.Atmosphere.builder().sky(ATMO_TOPO, ATMO_HORIZONTE, ATMO_MISTURA).build());
        Effects.register(EST_FAIXA, def);
        Effects.nebulae(EST_FAIXA, dev.calamidade_awakening.api.v1.Nebula.band(90, 30, 9).colors(0xb0fff0d8, 0x50607090).density(.55f).build());
        Effects.register(EST_VITRINE, def);
        Effects.stars(EST_VITRINE, dev.calamidade_awakening.api.v1.StarField.random(42, 8000).magnitudes(-1, 6).band(90, 30, 14, .5f).build(),
            dev.calamidade_awakening.api.v1.StarField.catalog()
                .star("a", -20, 40, .5f, 0xffcfe0ff).star("b", -8, 46, 1, 0xffffffff).star("c", 4, 43, .8f, 0xffffe0b0)
                .star("d", 14, 36, 1.2f, 0xffffc080).star("e", -12, 30, 1.5f, 0xffa0c0ff)
                .constellation(0x90a0b8ff, "a", "b", "c", "d").constellation(0x90a0b8ff, "b", "e").celestial(false).build());
        Effects.nebulae(EST_VITRINE, dev.calamidade_awakening.api.v1.Nebula.band(70, 40, 8).colors(0xb0fff0d8, 0x50607090).density(.55f).detail(4).celestial(false).build(),
            dev.calamidade_awakening.api.v1.Nebula.cloud(24, 28, 12).colors(0xffff70c0, 0x903060ff).seed(321).celestial(false).build());
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
            || fase.equals("ceu") || fase.equals("chefe") || fase.equals("backdrop") || fase.equals("estrelas") || fase.equals("imagens")) return;
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
