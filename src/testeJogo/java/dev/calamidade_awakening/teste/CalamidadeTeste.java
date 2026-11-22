package dev.calamidade_awakening.teste;

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
        NeoForge.EVENT_BUS.register(Protecao.class);
        NeoForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) -> SALVOS.add(event.getChunk().getPos().toLong()));
        NeoForge.EVENT_BUS.addListener(CalamidadeTeste::started);
        if (FMLEnvironment.dist == Dist.CLIENT && "cliente".equals(System.getProperty("calamidade.teste"))) RoteiroCliente.registrar();
        if (FMLEnvironment.dist == Dist.CLIENT && "medicao".equals(System.getProperty("calamidade.teste"))) RoteiroMedicao.registrar();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    private static void registrar(ResourceLocation id, TerrainEdit edit) {
        Effects.register(id, new Effects.Definition(60, 12, 0xffff5a14, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        Effects.timeline(id, TIMELINE);
        Effects.terrain(id, edit);
    }

    private static void started(ServerStartedEvent event) {
        String fase = System.getProperty("calamidade.teste");
        if (fase == null || fase.isBlank() || fase.equals("cliente") || fase.equals("medicao")) return;
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
