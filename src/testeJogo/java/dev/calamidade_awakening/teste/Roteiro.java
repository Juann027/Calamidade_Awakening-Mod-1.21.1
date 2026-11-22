package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.terrain.TerrainWorks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Roteiro do teste em jogo (0.11.1), numa thread própria: o mundo só é lido e escrito dentro de {@link #no}, na thread
 * do servidor. Mundo plano de {@code prepararTesteJogo}: bedrock em y = -64, pedra até -4, terra até -1, grama em y = 0.
 * Cada cenário usa uma região própria, longe das outras (a luz não alcança 64 blocos). Relatório:
 * {@code calamidade-teste.txt} na pasta do jogo, uma linha PASS/FAIL por verificação.
 */
final class Roteiro implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-teste.txt");
    private static final Path REINICIO = Path.of("reinicio.txt");
    private static final int COMMITS = 2, REFUSED = 5;
    /** Os quatro mapas de altura de um chunk FULL (os _WG só existem durante a geração). */
    private static final Heightmap.Types[] MAPAS = {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
        Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES};

    private final MinecraftServer server;
    private final String fase;
    private int passou, falhou;

    Roteiro(MinecraftServer server, String fase) {
        this.server = server;
        this.fase = fase;
    }

    @Override
    public void run() {
        try {
            if (fase.equals("cenarios")) {
                escrever("# Calamidade_Awakening: teste em jogo da Terrain API, " + java.time.LocalDateTime.now(), false);
                cenarios();
                escrever("FIM cenarios", true);
            } else {
                reinicio();
                escrever("FIM reinicio", true);
            }
        } catch (Throwable failure) {
            check("roteiro sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] " + fase + ": " + passou + " passaram, " + falhou + " falharam");
            server.halt(false);
        }
    }

    // ------------------------------------------------------------------------------------------------ fase 1

    private void cenarios() throws Exception {
        aguardar(() -> server.overworld() != null, 200);
        ticks(20);
        protecaoPorListener();
        inicioCancelado();
        eventoDesligado();
        bordaDoMundo();
        protecaoDeSpawn();
        luzMapasContadoresEDesfazer();
        pontosDeInteresse();
        cascaExterna();
        custoDaCratera();
        prepararReinicio();
    }

    /** Etapa 1: um listener que só conhece o ExplosionEvent protege x >= cx; a cratera para exatamente ali. */
    private void protecaoPorListener() throws Exception {
        int cx = 1000, cz = 0;
        carregar(cx, cz, 24);
        BoundingBox caixa = caixa(cx, cz);
        BlockState[] antes = ler(caixa);
        CalamidadeTeste.Protecao.limpar();
        CalamidadeTeste.Protecao.area = new BoundingBox(cx, -64, cz - 64, cx + 64, 320, cz + 64);
        commit(CalamidadeTeste.CRATERA, cx, cz);
        BlockState[] depois = ler(caixa);
        int[] lados = mudancasPorLado(caixa, antes, depois, cx);
        check("proteção: ExplosionEvent.Start e Detonate disparados uma vez",
            CalamidadeTeste.Protecao.inicios == 1 && CalamidadeTeste.Protecao.detonacoes == 1,
            "Start " + CalamidadeTeste.Protecao.inicios + ", Detonate " + CalamidadeTeste.Protecao.detonacoes);
        check("proteção: explosão sintética com as posições do plano",
            CalamidadeTeste.Protecao.oferecidas > 300 && CalamidadeTeste.Protecao.fonteNula && CalamidadeTeste.Protecao.raio > 6,
            CalamidadeTeste.Protecao.oferecidas + " posições, raio " + CalamidadeTeste.Protecao.raio + ", fonte nula (SCRIPT) "
                + CalamidadeTeste.Protecao.fonteNula + ", retiradas pelo listener " + CalamidadeTeste.Protecao.retiradas);
        check("proteção: nada mudou dentro da área protegida (x >= " + cx + ")", lados[1] == 0, lados[1] + " posições mudaram");
        check("proteção: a cratera abriu fora da área", lados[0] > 100, lados[0] + " posições mudaram fora");
        boolean borda = no(() -> server.overworld().getBlockState(new BlockPos(cx - 1, 0, cz)).isAir()
            && server.overworld().getBlockState(new BlockPos(cx, 0, cz)).is(Blocks.GRASS_BLOCK));
        check("proteção: a cratera para na borda (x = " + (cx - 1) + " cavado, x = " + cx + " intacto)", borda, relatorio());
        check("proteção: relatório conta as posições guardadas pelo listener", numero("(\\d+) explosion listeners") > 0, relatorio());
        int itens = no(() -> server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cx - 20, -20, cz - 20, cx + 20, 20, cz + 20)).size());
        check("proteção: a explosão sintética não explode (sem itens soltos)", itens == 0, itens + " itens");
        CalamidadeTeste.Protecao.limpar();
    }

    /** Etapa 1: ExplosionEvent.Start cancelado recusa a edição inteira. */
    private void inicioCancelado() throws Exception {
        int cx = 1200, cz = 0;
        carregar(cx, cz, 24);
        BoundingBox caixa = caixa(cx, cz);
        BlockState[] antes = ler(caixa);
        CalamidadeTeste.Protecao.limpar();
        CalamidadeTeste.Protecao.cancelarInicio = true;
        int recusas = contador(REFUSED + TerrainWorks.Reason.EXPLOSION.ordinal());
        iniciar(CalamidadeTeste.CRATERA, cx, cz);
        boolean recusada = aguardar(() -> contador(REFUSED + TerrainWorks.Reason.EXPLOSION.ordinal()) > recusas, 100);
        ticks(20);
        check("Start cancelado: edição recusada (motivo EXPLOSION)", recusada, "recusas " + recusas);
        check("Start cancelado: nenhum bloco mudou", diferencas(antes, ler(caixa)) == 0, "");
        CalamidadeTeste.Protecao.limpar();
    }

    /** Etapa 1: terrainExplosionEvent = false pula os eventos de explosão (o listener não vê nada). */
    private void eventoDesligado() throws Exception {
        int cx = 1300, cz = 0;
        carregar(cx, cz, 24);
        BoundingBox caixa = caixa(cx, cz);
        BlockState[] antes = ler(caixa);
        CalamidadeTeste.Protecao.limpar();
        CalamidadeTeste.Protecao.area = new BoundingBox(cx, -64, cz - 64, cx + 64, 320, cz + 64);
        configurar("TERRAIN_EXPLOSION_EVENT", false);
        try {
            commit(CalamidadeTeste.CRATERA, cx, cz);
        } finally {
            configurar("TERRAIN_EXPLOSION_EVENT", true);
        }
        int[] lados = mudancasPorLado(caixa, antes, ler(caixa), cx);
        check("evento desligado: listener não chamado", CalamidadeTeste.Protecao.detonacoes == 0 && CalamidadeTeste.Protecao.inicios == 0,
            "Detonate " + CalamidadeTeste.Protecao.detonacoes);
        check("evento desligado: cratera inteira (inclusive x >= " + cx + ")", lados[0] > 100 && lados[1] > 100,
            lados[0] + " / " + lados[1]);
        CalamidadeTeste.Protecao.limpar();
    }

    /** Etapa 1: nada muda fora da borda do mundo. */
    private void bordaDoMundo() throws Exception {
        int cx = 1400, cz = 0;
        carregar(cx, cz, 24);
        BoundingBox caixa = caixa(cx, cz);
        BlockState[] antes = ler(caixa);
        no(() -> { server.overworld().getWorldBorder().setCenter(cx - 50, cz); server.overworld().getWorldBorder().setSize(100); });
        try {
            commit(CalamidadeTeste.CRATERA, cx, cz);
        } finally {
            no(() -> { server.overworld().getWorldBorder().setCenter(0, 0); server.overworld().getWorldBorder().setSize(5.9999968E7); });
        }
        int[] lados = mudancasPorLado(caixa, antes, ler(caixa), cx);
        check("borda do mundo: nada mudou fora (x >= " + cx + ")", lados[1] == 0, lados[1] + " posições mudaram fora");
        check("borda do mundo: a cratera abriu dentro", lados[0] > 100, lados[0] + "");
    }

    /** Etapa 1: proteção de spawn do servidor dedicado (spawn-protection = 16, um operador em ops.json). */
    private void protecaoDeSpawn() throws Exception {
        BlockPos spawn = no(() -> server.overworld().getSharedSpawnPos());
        int raio = no(server::getSpawnProtectionRadius);
        int cx = spawn.getX() + raio, cz = spawn.getZ();
        carregar(cx, cz, 24);
        BoundingBox caixa = caixa(cx, cz);
        BlockState[] antes = ler(caixa);
        commit(CalamidadeTeste.CRATERA, cx, cz);
        int[] lados = mudancasPorLado(caixa, antes, ler(caixa), cx + 1);
        check("spawn: servidor dedicado com proteção " + raio + " em " + spawn.toShortString(), raio == 16 && server.isDedicatedServer(), "");
        check("spawn: nada mudou na área protegida (x <= " + cx + ")", lados[0] == 0, lados[0] + " posições mudaram");
        check("spawn: a cratera abriu fora da área (x > " + cx + ")", lados[1] > 100, lados[1] + "");
    }

    /**
     * Etapas 2 e 3: a mesma cratera (com casca e glowstone) feita pela Terrain API em A e copiada bloco a bloco com
     * {@code setBlock} (o caminho do /fill, sem atualizações de forma) em B, 64 blocos ao sul. Luz do céu e dos blocos,
     * brilho de dia e de noite, mapas de altura e contadores das seções têm de ser iguais; depois o desfazer devolve
     * blocos, luz e mapas de altura de antes.
     */
    private void luzMapasContadoresEDesfazer() throws Exception {
        int cx = 2000, cz = 0, bz = cz + 64;
        carregar(cx, cz, 32);
        carregar(cx, bz, 32);
        BoundingBox caixa = caixa(cx, cz);
        BoundingBox luz = new BoundingBox(cx - 18, -12, cz - 18, cx + 18, 18, cz + 18);
        assentarLuz();
        BlockState[] antes = ler(caixa);
        int[] luzAntes = luz(luz, 0);
        int[] alturaAntes = alturas(luz, 0);
        commit(CalamidadeTeste.LUZ, cx, cz);
        BlockState[] depois = ler(caixa);
        int copiados = no(() -> {
            ServerLevel level = server.overworld();
            int n = 0, i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ())) {
                if (depois[i] != antes[i]) {
                    level.setBlock(pos.offset(0, 0, 64), depois[i], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    n++;
                }
                i++;
            }
            return n;
        });
        assentarLuz();
        int[] luzA = luz(luz, 0), luzB = luz(luz, 64);
        check("luz: cratera de " + copiados + " blocos, luz do céu e dos blocos igual à do caminho do /fill",
            diferencas(luzA, luzB) == 0, diferencas(luzA, luzB) + " valores diferentes; " + primeiraDiferenca(luz, luzA, luzB));
        int glow = 0;
        for (int i = 1; i < luzA.length; i += 2) glow = Math.max(glow, luzA[i]);
        check("luz: glowstone ilumina a cratera", glow >= 14, "luz de bloco máxima " + glow);
        for (long hora : new long[] {6000, 18000}) {
            no(() -> server.overworld().setDayTime(hora));
            ticks(3);
            int[] a = brilho(luz, 0), b = brilho(luz, 64);
            int fundo = no(() -> server.overworld().getMaxLocalRawBrightness(new BlockPos(cx + 4, -1, cz)));
            check("luz: brilho " + (hora == 6000 ? "de dia" : "de noite") + " igual ao do /fill (fundo da cratera " + fundo + ")",
                diferencas(a, b) == 0, diferencas(a, b) + " diferentes");
        }
        no(() -> server.overworld().setDayTime(6000));
        int[] altA = alturas(luz, 0), altB = alturas(luz, 64), altScan = alturasPorVarredura(luz);
        check("mapas de altura: iguais aos do /fill e à varredura das colunas",
            diferencas(altA, altB) == 0 && diferencas(altA, altScan) == 0, diferencas(altA, altB) + " / " + diferencas(altA, altScan));
        check("contadores das seções (não vazios, tick aleatório, fluidos) iguais ao recálculo", contadoresCorretos(caixa), "");

        String desfeito = no(() -> TerrainWorks.undo(server, -1));
        aguardar(() -> !TerrainWorks.busy(), 200);
        assentarLuz();
        check("desfazer: blocos de antes", desfeito != null && diferencas(antes, ler(caixa)) == 0, desfeito + "; " + relatorio());
        int[] luzDepois = luz(luz, 0);
        check("desfazer: luz de antes", diferencas(luzAntes, luzDepois) == 0,
            diferencas(luzAntes, luzDepois) + " diferentes; " + primeiraDiferenca(luz, luzAntes, luzDepois));
        check("desfazer: mapas de altura de antes", diferencas(alturaAntes, alturas(luz, 0)) == 0, "");
        check("desfazer: contadores das seções corretos", contadoresCorretos(caixa), "");
    }

    /** Etapa 3: pontos de interesse removidos e adicionados pela edição e pelo desfazer. */
    private void pontosDeInteresse() throws Exception {
        int cx = 2200, cz = 0;
        carregar(cx, cz, 24);
        BlockPos[] postos = {new BlockPos(cx + 2, 0, cz), new BlockPos(cx - 2, 0, cz), new BlockPos(cx, 0, cz + 2)};
        BlockState[] blocos = {Blocks.COMPOSTER.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState(), Blocks.LODESTONE.defaultBlockState()};
        no(() -> { for (int i = 0; i < postos.length; i++) server.overworld().setBlock(postos[i], blocos[i], Block.UPDATE_ALL); });
        ticks(3);
        check("POI: composteira, mesa de ferraria e magnetita registradas", poi(postos) == 3, poi(postos) + " de 3");
        commit(CalamidadeTeste.POI, cx, cz);
        ticks(3);
        List<BlockPos> composteiras = procurar(caixa(cx, cz), Blocks.COMPOSTER);
        check("POI: os três removidos com os blocos", poi(postos) == 0, poi(postos) + " restantes");
        check("POI: composteiras do preenchimento registradas", !composteiras.isEmpty() && poi(composteiras.toArray(BlockPos[]::new)) == composteiras.size(),
            poi(composteiras.toArray(BlockPos[]::new)) + " de " + composteiras.size());
        no(() -> TerrainWorks.undo(server, -1));
        aguardar(() -> !TerrainWorks.busy(), 200);
        ticks(3);
        check("POI: desfazer devolve os três", poi(postos) == 3, poi(postos) + " de 3");
        check("POI: desfazer tira os do preenchimento", poi(composteiras.toArray(BlockPos[]::new)) == 0, "");
    }

    /**
     * Etapa 3: casca externa. Cerca na borda (a cerca que fica precisa soltar a conexão com a que sumiu) e água ao lado:
     * a água que tocava a cratera vira pedra (vedação), a seguinte ganha tick de fluido, a de mais longe não (sem
     * cascata). O desfazer faz o mesmo ao contrário.
     */
    private void cascaExterna() throws Exception {
        int cx = 2400, cz = 0;
        carregar(cx, cz, 24);
        no(() -> {
            ServerLevel level = server.overworld();
            for (int x = cx - 10; x <= cx + 10; x++) level.setBlock(new BlockPos(x, 1, cz - 3), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = cx + 6; x <= cx + 9; x++) level.setBlock(new BlockPos(x, 0, cz), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        });
        ticks(20);
        BoundingBox caixa = caixa(cx, cz);
        check("casca: cercas e água consistentes antes", inconsistentes(caixa).isEmpty(), inconsistentes(caixa).toString());
        int c0 = contador(COMMITS);
        iniciar(CalamidadeTeste.VEDADA, cx, cz);
        // Lido no tick em que o commit termina, antes que o tick de fluido (5 ticks) rode.
        boolean[] ticksFluido = new boolean[4];
        boolean feito = aguardar(() -> {
            if (contador(COMMITS) <= c0 || TerrainWorks.busy()) return false;
            for (int i = 0; i < 4; i++)
                ticksFluido[i] = server.overworld().getFluidTicks().hasScheduledTick(new BlockPos(cx + 6 + i, 0, cz), Fluids.WATER);
            return true;
        }, 200);
        check("casca: commit", feito, relatorio());
        boolean vedada = no(() -> server.overworld().getBlockState(new BlockPos(cx + 6, 0, cz)).is(Blocks.STONE));
        check("casca: água que tocava a cratera vedada com pedra", vedada, "");
        check("casca: tick de fluido agendado só na água vizinha da casca", ticksFluido[1] && !ticksFluido[2] && !ticksFluido[3],
            "x+7 " + ticksFluido[1] + ", x+8 " + ticksFluido[2] + ", x+9 " + ticksFluido[3]);
        check("casca: atualizações de vizinho contadas no relatório", numero("(\\d+) border updates") > 0, relatorio());
        List<String> ruins = inconsistentes(caixa);
        check("casca: cerca da borda soltou a conexão (formas consistentes)", ruins.isEmpty(), ruins.toString());
        ticks(30);
        int aguaDentro = contar(new BoundingBox(cx - 5, -4, cz - 5, cx + 5, 1, cz + 5), Blocks.WATER);
        check("casca: nenhuma água escorreu para a cratera", aguaDentro == 0, aguaDentro + " blocos de água");

        no(() -> TerrainWorks.undo(server, -1));
        aguardar(() -> !TerrainWorks.busy(), 200);
        ticks(10);
        ruins = inconsistentes(caixa);
        check("casca: desfazer reconecta as cercas (formas consistentes)", ruins.isEmpty(), ruins.toString());
        boolean agua = no(() -> server.overworld().getBlockState(new BlockPos(cx + 6, 0, cz)).is(Blocks.WATER));
        check("casca: desfazer devolve a água", agua, relatorio());
    }

    /**
     * Etapa 4: só chunks FULL. Com um único chunk forçado, o chunk a 2 de distância é FULL (borda) e o a 3 não é. A
     * cratera no limite entre os dois escreve só no primeiro, e o segundo continua não FULL (o commit nunca carrega).
     */
    private void soChunksFull() throws Exception {
        int fx = 160, fz = 0;   // chunk forçado; x de 2560 a 2575
        no(() -> server.overworld().setChunkForced(fx, fz, true));
        aguardar(() -> chunk(fx, fz) != null, 400);
        ticks(20);
        int cx = ((fx + 3) << 4) - 4, cz = 8;   // no chunk fx + 2 (FULL), a 4 blocos do fx + 3: a cratera passa para ele
        boolean full2 = no(() -> chunk(fx + 2, fz) != null), full3 = no(() -> chunk(fx + 3, fz) != null);
        check("FULL: chunk a 2 do forçado é FULL e o a 3 não", full2 && !full3, "fx+2 " + full2 + ", fx+3 " + full3);
        int c0 = contador(COMMITS);
        iniciar(CalamidadeTeste.CRATERA, cx, cz);
        aguardar(() -> contador(COMMITS) > c0 && !TerrainWorks.busy(), 200);
        boolean cavado = no(() -> server.overworld().getBlockState(new BlockPos(cx - 2, 0, cz)).isAir());
        boolean aindaNao = no(() -> chunk(fx + 3, fz) == null);
        check("FULL: escreveu no chunk FULL", cavado, relatorio());
        check("FULL: seções fora de chunk FULL puladas", numero("(\\d+) sections unloaded") > 0, relatorio());
        check("FULL: o commit não carregou o chunk", aindaNao, "");
        no(() -> server.overworld().setChunkForced(fx + 3, fz, true));
        aguardar(() -> chunk(fx + 3, fz) != null, 400);
        int mudados = no(() -> {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed((fx + 3) << 4, -8, cz - 8, ((fx + 3) << 4) + 8, 3, cz + 8))
                if (server.overworld().getBlockState(pos) != plano(pos.getY())) n++;
            return n;
        });
        check("FULL: o chunk que não era FULL ficou intacto", mudados == 0, mudados + " posições mudaram");
    }

    /**
     * Etapa 3: custo da casca externa na cratera grande (esfera r24 com casca de 2): tempo na thread do servidor, ticks
     * e o pior tick (de ServerTickEvent.Pre a Post) durante o commit e durante o desfazer.
     */
    private void custoDaCratera() throws Exception {
        int cx = 3400, cz = 0;
        carregar(cx, cz, 40);
        ticks(40);
        Medidor.ligar();
        commit(CalamidadeTeste.GRANDE, cx, cz);
        double pior = Medidor.desligar();
        check(String.format(java.util.Locale.ROOT, "custo: cratera grande com a casca externa (pior tick %.1f ms)", pior),
            pior < 50 && numero("(\\d+) border updates") > 1000, relatorio());
        no(() -> TerrainWorks.undo(server, -1));
        Medidor.ligar();
        aguardar(() -> !TerrainWorks.busy(), 300);
        ticks(2);
        double piorDesfazer = Medidor.desligar();
        check(String.format(java.util.Locale.ROOT, "custo: desfazer da cratera grande (pior tick %.1f ms)", piorDesfazer),
            piorDesfazer < 50, relatorio());
    }

    /**
     * Etapa 3, primeira metade do reinício: salva tudo, comita e confere que o jogo salva de novo os chunks da cratera
     * (só chunks marcados como modificados são salvos: o servidor ocioso salva até 20 por tick em
     * {@code ChunkMap.processUnloads}); grava o esperado para a fase 2, depois de o servidor fechar e abrir.
     */
    private void prepararReinicio() throws Exception {
        int cx = 3000, cz = 0;
        carregar(cx, cz, 24);
        no(() -> server.overworld().getChunkSource().save(true));
        boolean salvo = no(() -> !chunk(cx >> 4, cz >> 4).isUnsaved());
        ticks(5);
        long centro = net.minecraft.world.level.ChunkPos.asLong(cx >> 4, cz >> 4);
        CalamidadeTeste.SALVOS.clear();
        commit(CalamidadeTeste.POI, cx, cz);
        boolean salvoDeNovo = aguardar(() -> CalamidadeTeste.SALVOS.contains(centro), 400);
        check("reinício: chunk salvo antes; depois do commit, marcado como modificado e salvo de novo pelo jogo",
            salvo && salvoDeNovo, salvo + " / " + salvoDeNovo);
        List<String> linhas = no(() -> {
            List<String> out = new ArrayList<>();
            BoundingBox caixa = caixa(cx, cz);
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ())) {
                BlockState state = server.overworld().getBlockState(pos);
                if (state != plano(pos.getY())) out.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + BlockStateParser.serialize(state));
            }
            return out;
        });
        Files.write(REINICIO, linhas, StandardCharsets.UTF_8);
        check("reinício: " + linhas.size() + " posições gravadas para conferir depois", linhas.size() > 150, "");
    }

    // ------------------------------------------------------------------------------------------------ fase 2

    private void reinicio() throws Exception {
        aguardar(() -> server.overworld() != null, 200);
        int cx = 3000, cz = 0;
        carregar(cx, cz, 24);
        ticks(5);
        List<String> linhas = Files.readAllLines(REINICIO, StandardCharsets.UTF_8);
        List<String> erradas = no(() -> {
            List<String> out = new ArrayList<>();
            for (String linha : linhas) {
                String[] p = linha.split(" ", 4);
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                String agora = BlockStateParser.serialize(server.overworld().getBlockState(pos));
                if (!agora.equals(p[3])) out.add(linha + " -> " + agora);
            }
            return out;
        });
        check("reinício: a cratera sobreviveu (" + linhas.size() + " posições)", !linhas.isEmpty() && erradas.isEmpty(),
            erradas.size() + " diferentes " + erradas.stream().limit(3).toList());
        List<BlockPos> composteiras = procurar(caixa(cx, cz), Blocks.COMPOSTER);
        check("reinício: pontos de interesse das composteiras salvos", !composteiras.isEmpty()
            && poi(composteiras.toArray(BlockPos[]::new)) == composteiras.size(), composteiras.size() + "");
    }

    // ------------------------------------------------------------------------------------------------ apoio

    /** Caixa de conferência de uma cratera de raio 6 com casca, centrada em (cx, cz), superfície em y = 0. */
    private static BoundingBox caixa(int cx, int cz) { return new BoundingBox(cx - 10, -8, cz - 10, cx + 10, 3, cz + 10); }

    /** Estado do mundo plano numa altura. */
    private static BlockState plano(int y) {
        if (y > 0) return Blocks.AIR.defaultBlockState();
        if (y == 0) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (y >= -3) return Blocks.DIRT.defaultBlockState();
        if (y >= -63) return Blocks.STONE.defaultBlockState();
        return Blocks.BEDROCK.defaultBlockState();
    }

    private LevelChunk chunk(int x, int z) {
        LevelChunk chunk = server.overworld().getChunkSource().getChunkNow(x, z);
        return chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL) ? null : chunk;
    }

    /** Força os chunks num raio de blocos em volta de (cx, cz) e espera estarem FULL. */
    private void carregar(int cx, int cz, int raio) throws Exception {
        int x0 = (cx - raio) >> 4, x1 = (cx + raio) >> 4, z0 = (cz - raio) >> 4, z1 = (cz + raio) >> 4;
        no(() -> { for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) server.overworld().setChunkForced(x, z, true); });
        boolean ok = aguardar(() -> {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (chunk(x, z) == null) return false;
            return true;
        }, 600);
        if (!ok) throw new IllegalStateException("chunks em volta de " + cx + " " + cz + " não carregaram");
        ticks(5);
    }

    private boolean iniciar(ResourceLocation id, int cx, int cz) throws Exception {
        boolean ok = no(() -> Effects.start(id, server.overworld(), new Vec3(cx + .5, 1, cz + .5)).isPresent());
        if (!ok) throw new IllegalStateException("efeito " + id + " não começou");
        return ok;
    }

    /** Inicia um efeito e espera o commit (e a casca externa) terminar. */
    private void commit(ResourceLocation id, int cx, int cz) throws Exception {
        int c0 = contador(COMMITS);
        iniciar(id, cx, cz);
        if (!aguardar(() -> contador(COMMITS) > c0 && !TerrainWorks.busy(), 300))
            throw new IllegalStateException("commit de " + id + " em " + cx + " " + cz + " não terminou: " + relatorio());
        ticks(2);
    }

    private int contador(int index) { return TerrainWorks.counters()[index]; }

    private String relatorio() { return TerrainWorks.lastReport(); }

    private int numero(String regex) {
        Matcher m = Pattern.compile(regex).matcher(relatorio());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private BlockState[] ler(BoundingBox caixa) throws Exception {
        return no(() -> {
            List<BlockState> out = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ()))
                out.add(server.overworld().getBlockState(pos));
            return out.toArray(BlockState[]::new);
        });
    }

    /** Posições que mudaram com x < limite e com x >= limite. */
    private static int[] mudancasPorLado(BoundingBox caixa, BlockState[] antes, BlockState[] depois, int limite) {
        int[] out = new int[2];
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ())) {
            if (antes[i] != depois[i]) out[pos.getX() < limite ? 0 : 1]++;
            i++;
        }
        return out;
    }

    private static int diferencas(Object[] a, Object[] b) {
        int n = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++;
        return n;
    }

    private static int diferencas(int[] a, int[] b) {
        int n = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++;
        return n;
    }

    /** Luz do céu e dos blocos (2 valores por posição) na caixa deslocada dz blocos em z. */
    private int[] luz(BoundingBox caixa, int dz) throws Exception {
        return no(() -> {
            ServerLevel level = server.overworld();
            int[] out = new int[caixa.getXSpan() * caixa.getYSpan() * caixa.getZSpan() * 2];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ() + dz, caixa.maxX(), caixa.maxY(), caixa.maxZ() + dz)) {
                out[i++] = level.getBrightness(LightLayer.SKY, pos);
                out[i++] = level.getBrightness(LightLayer.BLOCK, pos);
            }
            return out;
        });
    }

    private String primeiraDiferenca(BoundingBox caixa, int[] a, int[] b) {
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ())) {
            for (int layer = 0; layer < 2; layer++, i++)
                if (a[i] != b[i]) return (layer == 0 ? "céu" : "bloco") + " em " + pos.toShortString() + ": " + a[i] + " x " + b[i];
        }
        return "nenhuma";
    }

    private int[] brilho(BoundingBox caixa, int dz) throws Exception {
        return no(() -> {
            int[] out = new int[caixa.getXSpan() * caixa.getYSpan() * caixa.getZSpan()];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ() + dz, caixa.maxX(), caixa.maxY(), caixa.maxZ() + dz))
                out[i++] = server.overworld().getMaxLocalRawBrightness(pos);
            return out;
        });
    }

    /** Os quatro mapas de altura em cada coluna da caixa deslocada dz. */
    private int[] alturas(BoundingBox caixa, int dz) throws Exception {
        return no(() -> {
            int[] out = new int[caixa.getXSpan() * caixa.getZSpan() * MAPAS.length];
            int i = 0;
            for (int x = caixa.minX(); x <= caixa.maxX(); x++)
                for (int z = caixa.minZ() + dz; z <= caixa.maxZ() + dz; z++)
                    for (Heightmap.Types type : MAPAS) out[i++] = server.overworld().getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15);
            return out;
        });
    }

    /** Os mapas de altura recalculados varrendo cada coluna de cima para baixo (mesma convenção de ChunkAccess.getHeight: y do bloco mais alto que conta). */
    private int[] alturasPorVarredura(BoundingBox caixa) throws Exception {
        return no(() -> {
            ServerLevel level = server.overworld();
            int[] out = new int[caixa.getXSpan() * caixa.getZSpan() * MAPAS.length];
            int i = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = caixa.minX(); x <= caixa.maxX(); x++)
                for (int z = caixa.minZ(); z <= caixa.maxZ(); z++)
                    for (Heightmap.Types type : MAPAS) {
                        int y = level.getMaxBuildHeight() - 1;
                        while (y >= level.getMinBuildHeight() && !type.isOpaque().test(level.getBlockState(pos.set(x, y, z)))) y--;
                        out[i++] = y;
                    }
            return out;
        });
    }

    /** Contadores privados de cada seção da caixa iguais aos de {@code recalcBlockCounts}. */
    private boolean contadoresCorretos(BoundingBox caixa) throws Exception {
        return no(() -> {
            try {
                Field[] campos = new Field[3];
                String[] nomes = {"nonEmptyBlockCount", "tickingBlockCount", "tickingFluidCount"};
                for (int i = 0; i < 3; i++) {
                    campos[i] = LevelChunkSection.class.getDeclaredField(nomes[i]);
                    campos[i].setAccessible(true);
                }
                ServerLevel level = server.overworld();
                boolean ok = true;
                for (int sx = caixa.minX() >> 4; sx <= caixa.maxX() >> 4; sx++)
                    for (int sz = caixa.minZ() >> 4; sz <= caixa.maxZ() >> 4; sz++)
                        for (int sy = caixa.minY() >> 4; sy <= caixa.maxY() >> 4; sy++) {
                            LevelChunkSection section = level.getChunk(sx, sz).getSection(level.getSectionIndexFromSectionY(sy));
                            short[] antes = new short[3], depois = new short[3];
                            for (int i = 0; i < 3; i++) antes[i] = campos[i].getShort(section);
                            section.recalcBlockCounts();
                            for (int i = 0; i < 3; i++) depois[i] = campos[i].getShort(section);
                            if (!java.util.Arrays.equals(antes, depois)) {
                                ok = false;
                                System.out.println("[TESTE] seção " + sx + " " + sy + " " + sz + ": " + java.util.Arrays.toString(antes)
                                    + " x " + java.util.Arrays.toString(depois));
                            }
                        }
                return ok;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    /** Cercas e fluidos da caixa cuja forma não bate com os vizinhos (updateFromNeighbourShapes). */
    private List<String> inconsistentes(BoundingBox caixa) throws Exception {
        return no(() -> {
            List<String> out = new ArrayList<>();
            ServerLevel level = server.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FenceBlock)) continue;
                BlockState expected = Block.updateFromNeighbourShapes(state, level, pos);
                if (expected != state && out.size() < 6) out.add(pos.toShortString() + " " + state + " -> " + expected);
            }
            return out;
        });
    }

    private int poi(BlockPos... postos) throws Exception {
        return no(() -> {
            int n = 0;
            for (BlockPos pos : postos) if (server.overworld().getPoiManager().getType(pos).isPresent()) n++;
            return n;
        });
    }

    private List<BlockPos> procurar(BoundingBox caixa, Block block) throws Exception {
        return no(() -> {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(caixa.minX(), caixa.minY(), caixa.minZ(), caixa.maxX(), caixa.maxY(), caixa.maxZ()))
                if (server.overworld().getBlockState(pos).is(block)) out.add(pos.immutable());
            return out;
        });
    }

    private int contar(BoundingBox caixa, Block block) throws Exception { return procurar(caixa, block).size(); }

    /** Espera o motor de luz do servidor esvaziar a fila. */
    private void assentarLuz() throws Exception {
        ticks(10);
        aguardar(() -> !server.overworld().getChunkSource().getLightEngine().hasLightWork(), 400);
        ticks(10);
    }

    private void configurar(String campo, boolean valor) throws Exception {
        Field field = ServerConfig.class.getDeclaredField(campo);
        field.setAccessible(true);
        var value = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) field.get(null);
        no(() -> value.set(valor));
    }

    // ------------------------------------------------------------------------------------------------ thread

    private <T> T no(Supplier<T> task) throws Exception {
        return server.submit(task).get(60, TimeUnit.SECONDS);
    }

    private void no(Runnable task) throws Exception {
        server.submit(task).get(60, TimeUnit.SECONDS);
    }

    private void ticks(int n) throws InterruptedException {
        int alvo = server.getTickCount() + n;
        while (server.getTickCount() < alvo) Thread.sleep(5);
    }

    /** Espera a condição (avaliada na thread do servidor, uma vez por tick) por até maxTicks. */
    private boolean aguardar(BooleanSupplier condicao, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks; i++) {
            if (no(condicao::getAsBoolean)) return true;
            ticks(1);
        }
        return false;
    }

    private void check(String nome, boolean ok, String detalhe) {
        if (ok) passou++; else falhou++;
        String linha = (ok ? "PASS " : "FAIL ") + nome + (detalhe == null || detalhe.isEmpty() ? "" : " — " + detalhe);
        System.out.println("[TESTE] " + linha);
        escrever(linha, true);
    }

    private static void escrever(String linha, boolean append) {
        try {
            Files.writeString(RELATORIO, linha + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
