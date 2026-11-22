package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.terrain.TerrainWorks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Roteiro do teste em jogo no cliente (0.11.1), com {@code calamidade.teste=cliente} e o mundo aberto por
 * {@code --quickPlaySingleplayer teste}. A cratera da Terrain API (A) e a mesma troca de blocos feita por
 * {@code setBlock} no servidor (B, o caminho do /fill) chegam ao cliente por caminhos diferentes (pacote de seção
 * direto x {@code ChunkHolder.broadcastChanges}); a luz que o cliente recalcula, e a imagem de cada cratera vista do
 * mesmo ângulo, de dia e de noite, têm de ser iguais. Também: modo aventura não edita. Relatório em
 * {@code calamidade-teste-cliente.txt} e capturas em {@code screenshots/}.
 */
final class RoteiroCliente implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-teste-cliente.txt");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (iniciado || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            iniciado = true;
            Thread thread = new Thread(new RoteiroCliente(), "Calamidade teste em jogo (cliente)");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste em jogo no cliente, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> com.mojang.blaze3d.systems.RenderSystem.getBackendDescription() + " · "
                + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            cenarios();
            escrever("FIM cliente", true);
        } catch (Throwable failure) {
            check("roteiro do cliente sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] cliente: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel level() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private void cenarios() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            level().setDayTime(6000);
            jogador().setGameMode(GameType.SPECTATOR);   // sem gravidade: as duas capturas do mesmo ponto
        });
        noCliente(() -> { mc.options.hideGui = true; mc.options.pauseOnLostFocus = false; return null; });
        luzNoCliente();
        modoAventura();
        dadosDeBlockEntity();
    }

    /**
     * Etapa 3: o texto de uma placa trocada pela edição volta no desfazer, e o cliente o vê (o pacote de seção não
     * leva dados de block entity; o caminho do jogo manda {@code getUpdatePacket()} de cada block entity mudada).
     */
    private void dadosDeBlockEntity() throws Exception {
        int cx = 5400, cz = 0;
        forcar(cx, cz, 24);
        olhar(cx, cz);
        renderizado();
        BlockPos placa = new BlockPos(cx + 1, 1, cz);
        noServidor(() -> {
            level().setBlock(placa, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
            if (level().getBlockEntity(placa) instanceof SignBlockEntity sign)
                sign.setText(new SignText().setMessage(0, Component.literal("Calamidade")), true);
        });
        ticks(20);
        check("block entity: o cliente vê o texto da placa antes", "Calamidade".equals(textoNoCliente(placa)), textoNoCliente(placa));
        Field campo = ServerConfig.class.getDeclaredField("TERRAIN_BLOCK_ENTITIES");
        campo.setAccessible(true);
        var opcao = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) campo.get(null);
        noServidor(() -> opcao.set(true));
        try {
            int c0 = TerrainWorks.counters()[2];
            noServidor(() -> Effects.start(CalamidadeTeste.ENTIDADES, level(), new Vec3(cx + .5, 1, cz + .5)).isPresent());
            aguardar(() -> TerrainWorks.counters()[2] > c0 && !TerrainWorks.busy(), 200);
        } finally {
            noServidor(() -> opcao.set(false));
        }
        ticks(20);
        boolean sumiu = noCliente(() -> mc.level.getBlockState(placa).isAir());
        check("block entity: a placa saiu com a cratera (no cliente)", sumiu, TerrainWorks.lastReport());
        noServidor(() -> TerrainWorks.undo(server(), -1));
        aguardar(() -> !TerrainWorks.busy(), 200);
        ticks(20);
        String servidor = noServidor(() -> level().getBlockEntity(placa) instanceof SignBlockEntity sign
            ? sign.getFrontText().getMessage(0, false).getString() : "(sem placa)");
        check("block entity: desfazer devolve a placa com o texto (servidor)", "Calamidade".equals(servidor), servidor);
        check("block entity: o cliente recebe o texto da placa devolvida", "Calamidade".equals(textoNoCliente(placa)),
            "cliente vê '" + textoNoCliente(placa) + "'");
    }

    private String textoNoCliente(BlockPos pos) throws Exception {
        return noCliente(() -> mc.level.getBlockEntity(pos) instanceof SignBlockEntity sign
            ? sign.getFrontText().getMessage(0, false).getString() : "(sem placa)");
    }

    /** Etapa 2: luz recalculada pelo cliente e imagem de dia e de noite, Terrain API x setBlock. */
    private void luzNoCliente() throws Exception {
        int cx = 5000, cz = 0, dz = 48;
        forcar(cx, cz, 32);
        forcar(cx, cz + dz, 32);
        olhar(cx, cz);
        renderizado();
        int c0 = TerrainWorks.counters()[2];
        noServidor(() -> Effects.start(CalamidadeTeste.LUZ, level(), new Vec3(cx + .5, 1, cz + .5)).isPresent());
        aguardar(() -> TerrainWorks.counters()[2] > c0 && !TerrainWorks.busy(), 400);
        int copiados = noServidor(() -> {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(cx - 10, -8, cz - 10, cx + 10, 3, cz + 10)) {
                BlockPos b = pos.offset(0, 0, dz);
                BlockState a = level().getBlockState(pos);
                if (level().getBlockState(b) != a) {
                    level().setBlock(b, a, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    n++;
                }
            }
            return n;
        });
        ticks(60);
        aguardarLuzDoCliente();
        int entreCrateras = noCliente(() -> {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(cx - 18, -12, cz - 18, cx + 18, 18, cz + 18))
                for (LightLayer layer : LightLayer.values())
                    if (mc.level.getBrightness(layer, pos) != mc.level.getBrightness(layer, pos.offset(0, 0, dz))) n++;
            return n;
        });
        int clienteServidor = 0;
        int[] servidor = noServidor(() -> {
            int[] out = new int[37 * 31 * 37 * 2];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(cx - 18, -12, cz - 18, cx + 18, 18, cz + 18)) {
                out[i++] = level().getBrightness(LightLayer.SKY, pos);
                out[i++] = level().getBrightness(LightLayer.BLOCK, pos);
            }
            return out;
        });
        int[] cliente = noCliente(() -> {
            int[] out = new int[37 * 31 * 37 * 2];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(cx - 18, -12, cz - 18, cx + 18, 18, cz + 18)) {
                out[i++] = mc.level.getBrightness(LightLayer.SKY, pos);
                out[i++] = mc.level.getBrightness(LightLayer.BLOCK, pos);
            }
            return out;
        });
        for (int i = 0; i < servidor.length; i++) if (servidor[i] != cliente[i]) clienteServidor++;
        check("luz no cliente: cratera da Terrain API igual à do setBlock (" + copiados + " blocos)", entreCrateras == 0,
            entreCrateras + " valores diferentes");
        check("luz no cliente: igual à do servidor", clienteServidor == 0, clienteServidor + " valores diferentes");
        for (long hora : new long[] {6000, 18000}) {
            String nome = hora == 6000 ? "dia" : "noite";
            noServidor(() -> level().setDayTime(hora));
            olhar(cx, cz);
            renderizado();
            File a = captura("cratera-terrain-" + nome);
            olhar(cx, cz + dz);
            renderizado();
            File b = captura("cratera-fill-" + nome);
            double[] d = diferenca(a, b);
            check(String.format(Locale.ROOT, "imagem de %s sem manchas: Terrain API igual ao setBlock em blocos de 40 px (diferença média %.2f, máxima %.1f de 255)",
                nome, d[0], d[1]), d[0] < 3 && d[1] < 16, a.getName() + " x " + b.getName());
        }
        noServidor(() -> level().setDayTime(6000));
    }

    /** Etapa 1: jogador em modo aventura não edita; em sobrevivência, edita. */
    private void modoAventura() throws Exception {
        int cx = 5200, cz = 0;
        forcar(cx, cz, 24);
        ticks(20);
        int recusas0 = TerrainWorks.counters()[5 + TerrainWorks.Reason.PERMISSION.ordinal()];
        noServidor(() -> jogador().setGameMode(GameType.ADVENTURE));
        boolean comecou = noServidor(() -> Effects.start(CalamidadeTeste.CRATERA, jogador(), new Vec3(cx + .5, 1, cz + .5)).isPresent());
        aguardar(() -> TerrainWorks.counters()[5 + TerrainWorks.Reason.PERMISSION.ordinal()] > recusas0, 100);
        ticks(20);
        boolean intacto = noServidor(() -> !level().getBlockState(new BlockPos(cx, 0, cz)).isAir());
        int recusas = TerrainWorks.counters()[5 + TerrainWorks.Reason.PERMISSION.ordinal()] - recusas0;
        check("modo aventura: efeito toca, edição recusada (PERMISSION), nada muda", comecou && recusas == 1 && intacto,
            "começou " + comecou + ", recusas " + recusas + ", intacto " + intacto);
        noServidor(() -> jogador().setGameMode(GameType.SURVIVAL));
        int c0 = TerrainWorks.counters()[2];
        noServidor(() -> Effects.start(CalamidadeTeste.CRATERA, jogador(), new Vec3(cx + .5, 1, cz + .5)).isPresent());
        boolean feito = aguardar(() -> TerrainWorks.counters()[2] > c0 && !TerrainWorks.busy(), 200);
        boolean cavado = noServidor(() -> level().getBlockState(new BlockPos(cx, 0, cz)).isAir());
        check("modo sobrevivência: edição comitada", feito && cavado, TerrainWorks.lastReport());
        noServidor(() -> jogador().setGameMode(GameType.SPECTATOR));
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private void forcar(int cx, int cz, int raio) throws Exception {
        noServidor(() -> {
            for (int x = (cx - raio) >> 4; x <= (cx + raio) >> 4; x++)
                for (int z = (cz - raio) >> 4; z <= (cz + raio) >> 4; z++) level().setChunkForced(x, z, true);
        });
    }

    /** Põe a câmera 9 blocos ao norte e 14 acima do centro, olhando para o sul e para baixo, para o centro da cratera. */
    private void olhar(int cx, int cz) throws Exception {
        noServidor(() -> jogador().teleportTo(level(), cx + .5, 14, cz - 8.5, 0, 55));
        ticks(10);
        String onde = noCliente(() -> String.format(Locale.ROOT, "%.1f %.1f %.1f yaw %.0f pitch %.0f", mc.player.getX(),
            mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()));
        System.out.println("[TESTE] câmera em " + onde);
    }

    /** Espera as seções em volta serem montadas e alguns quadros a mais. */
    private void renderizado() throws Exception {
        ticks(40);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(40);
    }

    private void aguardarLuzDoCliente() throws Exception {
        aguardar(() -> noClienteSemErro(() -> !mc.level.getLightEngine().hasLightWork()), 1200);
        ticks(20);
    }

    private File captura(String nome) throws Exception {
        File pasta = new File(mc.gameDirectory, "screenshots");
        File arquivo = new File(pasta, nome + ".png");
        Files.deleteIfExists(arquivo.toPath());
        noCliente(() -> { Screenshot.grab(mc.gameDirectory, nome + ".png", mc.getMainRenderTarget(), (Component c) -> {}); return null; });
        for (int i = 0; i < 400 && !arquivo.exists(); i++) Thread.sleep(25);
        Thread.sleep(500);
        return arquivo;
    }

    /**
     * Diferença entre as duas imagens reduzidas a blocos de 40 × 40 px (média de cada canal): média e máxima, 0..255.
     * A textura de grama, terra e pedra gira conforme a posição do bloco, então as crateras A e B nunca são iguais
     * pixel a pixel; a média de cada bloco de pixels ignora a rotação e acusa manchas de luz (claras ou escuras).
     */
    private static double[] diferenca(File a, File b) throws IOException {
        BufferedImage x = ImageIO.read(a), y = ImageIO.read(b);
        int w = Math.min(x.getWidth(), y.getWidth()), h = Math.min(x.getHeight(), y.getHeight()), lado = 40;
        double soma = 0, maxima = 0;
        int blocos = 0;
        for (int bj = 0; bj + lado <= h; bj += lado)
            for (int bi = 0; bi + lado <= w; bi += lado) {
                double[] mx = new double[3], my = new double[3];
                for (int j = bj; j < bj + lado; j++)
                    for (int i = bi; i < bi + lado; i++) {
                        int p = x.getRGB(i, j), q = y.getRGB(i, j);
                        for (int c = 0; c < 3; c++) {
                            mx[c] += (p >> (16 - 8 * c)) & 255;
                            my[c] += (q >> (16 - 8 * c)) & 255;
                        }
                    }
                for (int c = 0; c < 3; c++) {
                    double d = Math.abs(mx[c] - my[c]) / (lado * lado);
                    soma += d;
                    maxima = Math.max(maxima, d);
                }
                blocos++;
            }
        return new double[] {soma / (3.0 * blocos), maxima};
    }

    private <T> T noServidor(Supplier<T> task) throws Exception { return server().submit(task).get(60, TimeUnit.SECONDS); }

    private void noServidor(Runnable task) throws Exception { server().submit(task).get(60, TimeUnit.SECONDS); }

    private <T> T noCliente(Supplier<T> task) throws Exception { return mc.submit(task).get(60, TimeUnit.SECONDS); }

    private boolean noClienteSemErro(Supplier<Boolean> task) {
        try {
            return noCliente(task);
        } catch (Exception failure) {
            return false;
        }
    }

    /** Ticks do servidor integrado. */
    private void ticks(int n) throws InterruptedException {
        int alvo = server().getTickCount() + n;
        while (server().getTickCount() < alvo) Thread.sleep(5);
    }

    private boolean aguardar(BooleanSupplier condicao, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks; i++) {
            if (condicao.getAsBoolean()) return true;
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
