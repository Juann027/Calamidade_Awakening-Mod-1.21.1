package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Experimento em jogo do Backdrop (0.14, fase 1), com {@code calamidade.teste=backdrop}, no cliente (renderização por
 * software) com o vfx-sample em {@code mods/}. Usa o protótipo {@link BackdropExperimento} (só no mod de teste) para
 * comprovar: ordem de AFTER_SKY, AFTER_LEVEL e da camada da RimuLib; profundidade; cobertura do céu do jogo (sol, lua,
 * estrelas); transparência; nuvens; terreno; eclipse; Atmosphere; lente; cegueira; Nether; custo. Linhas PASS e FAIL
 * são afirmações; linhas INFO são medições do comportamento atual que a arquitetura precisa levar em conta.
 * Relatório em {@code calamidade-backdrop.txt}; capturas em {@code screenshots/}.
 */
final class RoteiroBackdrop implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-backdrop.txt");
    private static boolean iniciado;
    private static final int X = -9000, Z = 0;
    private static final int TOL = 4;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        BackdropExperimento.registrar();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) -> {
            String pular = Component.translatable("selectWorld.backupJoinSkipButton").getString();
            for (var item : event.getListenersList())
                if (item instanceof net.minecraft.client.gui.components.Button botao && botao.getMessage().getString().equals(pular)) {
                    Minecraft.getInstance().execute(botao::onPress);
                    return;
                }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (iniciado || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            iniciado = true;
            Thread thread = new Thread(new RoteiroBackdrop(), "Calamidade experimento do Backdrop");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: experimento do Backdrop (0.14, fase 1), " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            ordemEProfundidade();
            coberturaDeDia();
            transparencia();
            noite();
            nuvens();
            terreno();
            atmosfera();
            lente();
            eclipse();
            cegueira();
            custo();
            nether();
            escrever("FIM backdrop", true);
        } catch (Throwable failure) {
            check("roteiro do Backdrop sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            BackdropExperimento.alfa = 0;
            System.out.println("[TESTE] backdrop: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    // ------------------------------------------------------------------------------------------------ casos

    private void preparar() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            overworld().setDayTime(6000);
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 3, Z + .5, 0, -25);
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; return null; });
        esperarTerreno();
    }

    private void ordemEProfundidade() throws Exception {
        olhar(0, -80);
        BackdropExperimento.alfa = 1;
        ticks(10);
        long[] seq = noCliente(() -> new long[] {BackdropExperimento.seqCeu, BackdropExperimento.seqNivel, BackdropExperimento.seqRimu});
        check("ordem no quadro: AFTER_SKY, depois AFTER_LEVEL, depois a camada da RimuLib", seq[0] < seq[1] && seq[1] < seq[2],
            Arrays.toString(seq));
        check("AFTER_SKY desenha no framebuffer principal", BackdropExperimento.fboCeu == BackdropExperimento.fboPrincipal
            && BackdropExperimento.fboCeu != 0, BackdropExperimento.fboCeu + " / " + BackdropExperimento.fboPrincipal);
        check("profundidade em AFTER_SKY é 1 (céu do jogo não grava) e continua 1 depois do fundo (depthMask false)",
            BackdropExperimento.profundidadeAntes == 1f && BackdropExperimento.profundidadeDepois == 1f,
            BackdropExperimento.profundidadeAntes + " → " + BackdropExperimento.profundidadeDepois);
        check("profundidade de um pixel de céu em AFTER_LEVEL continua 1 (o passe sky o reconhece como céu)",
            BackdropExperimento.profundidadeNivel == 1f, String.valueOf(BackdropExperimento.profundidadeNivel));
        olhar(0, 70);
        ticks(10);
        check("profundidade de um pixel de chão em AFTER_LEVEL é menor que 1", BackdropExperimento.profundidadeNivel < 1f,
            String.valueOf(BackdropExperimento.profundidadeNivel));
        BackdropExperimento.alfa = 0;
    }

    /** Meio-dia olhando o sol no zênite: o fundo opaco cobre sol e céu inteiro. */
    private void coberturaDeDia() throws Exception {
        noServidor(() -> overworld().setDayTime(6000));
        olhar(0, -90);
        int[] vanilla = foto("backdrop-sol-vanilla");
        int sol = pixel(vanilla, 640, 360);
        info("sol do jogo no centro sem fundo: " + rgb(sol));
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] opaco = foto("backdrop-sol-opaco");
        check("fundo opaco cobre o sol do jogo (centro = face +Y)", perto(pixel(opaco, 640, 360), BackdropExperimento.FACES[2], TOL),
            rgb(pixel(opaco, 640, 360)));
        olhar(0, -30);
        int[] ceu = foto("backdrop-ceu-opaco");
        int fora = 0;
        for (int y = 20; y < 260; y += 40) for (int x = 40; x < 1280; x += 120) if (!algumaFace(pixel(ceu, x, y))) fora++;
        check("fundo opaco cobre todo o céu visível (amostras da metade de cima = cor de uma face)", fora == 0, fora + " amostras fora");
        BackdropExperimento.alfa = 0;
    }

    private void transparencia() throws Exception {
        olhar(0, -30);
        int[] vanilla = foto(null);
        BackdropExperimento.alfa = .5f;
        ticks(5);
        int[] meio = foto("backdrop-ceu-meio");
        BackdropExperimento.alfa = 0;
        int px = 640, py = 120, v = pixel(vanilla, px, py), m = pixel(meio, px, py);
        int fundo = melhorFace(m, v);
        int esperado = mistura(v, fundo, .5f);
        check("fundo com alfa 0,5 mistura com o céu do jogo (substituição parcial)", perto(m, esperado, TOL),
            "jogo " + rgb(v) + ", meio " + rgb(m) + ", esperado " + rgb(esperado) + " (face " + rgb(fundo) + ")");
    }

    /** Meia-noite olhando a lua no zênite: lua e estrelas do jogo somem sob o fundo opaco. */
    private void noite() throws Exception {
        noServidor(() -> overworld().setDayTime(18000));
        olhar(0, -90);
        int[] vanilla = foto("backdrop-lua-vanilla");
        int brilhantesJogo = brilhantes(vanilla, 0, 0, 1280, 720, 140);
        info("noite sem fundo: " + brilhantesJogo + " pixels brilhantes (lua e estrelas); centro " + rgb(pixel(vanilla, 640, 360)));
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] opaco = foto("backdrop-lua-opaco");
        BackdropExperimento.alfa = 0;
        check("fundo opaco cobre a lua e as estrelas do jogo", perto(pixel(opaco, 640, 360), BackdropExperimento.FACES[2], TOL)
            && brilhantes(opaco, 0, 0, 1280, 720, 140) == 0 && brilhantesJogo > 0,
            "centro " + rgb(pixel(opaco, 640, 360)) + ", brilhantes " + brilhantes(opaco, 0, 0, 1280, 720, 140));
        noServidor(() -> overworld().setDayTime(6000));
    }

    /** As nuvens do jogo vêm depois de AFTER_SKY: ficam na frente do fundo. */
    private void nuvens() throws Exception {
        noCliente(() -> { mc.options.cloudStatus().set(CloudStatus.FANCY); return null; });
        olhar(0, -20);
        ticks(40);
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] img = foto("backdrop-nuvens");
        BackdropExperimento.alfa = 0;
        int total = 0, fora = 0;
        for (int y = 0; y < 300; y += 4) for (int x = 0; x < 1280; x += 4) { total++; if (!algumaFace(pixel(img, x, y))) fora++; }
        check("nuvens do jogo desenhadas na frente do fundo (parte do céu não é cor de face)", fora > total / 100,
            String.format(Locale.ROOT, "%.1f%% fora das faces", 100.0 * fora / total));
        noCliente(() -> { mc.options.cloudStatus().set(CloudStatus.OFF); return null; });
    }

    private void terreno() throws Exception {
        olhar(0, 15);
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] img = foto("backdrop-terreno");
        BackdropExperimento.alfa = 0;
        check("o terreno cobre o fundo (pixel de chão não é cor de face) e o céu acima do horizonte é fundo",
            !algumaFace(pixel(img, 640, 700)) && algumaFace(pixel(img, 640, 10)),
            "chão " + rgb(pixel(img, 640, 700)) + ", céu " + rgb(pixel(img, 640, 10)));
        // Faixa do horizonte: a névoa do jogo pinta o terreno distante com a cor de névoa, não com o fundo.
        info("horizonte (linha 330..390, coluna 640): " + coluna(img, 640, 330, 390, 15));
    }

    /** Receita "ceu" (Atmosphere com gradiente) por cima do fundo opaco: o que o passe faz hoje com os pixels de céu. */
    private void atmosfera() throws Exception {
        olhar(0, -30);
        comando("vfxsample receita ceu");
        ticks(60);
        int[] soAtmo = foto("backdrop-atmosfera-sem-fundo");
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] comFundo = foto("backdrop-atmosfera-com-fundo");
        BackdropExperimento.alfa = 0;
        comando("vfxsample receita stop");
        ticks(40);
        int px = 640, py = 120, f = pixel(comFundo, px, py);
        info("atmosfera sem fundo " + rgb(pixel(soAtmo, px, py)) + "; com fundo " + rgb(f) + "; face mais próxima "
            + rgb(melhorFace(f, 0)) + " (diferença máx. " + difMax(f, melhorFace(f, 0)) + ")");
    }

    /** Buraco negro do exemplo sobre o fundo com grade: o passe sky lê a cena (com o fundo) pela coordenada desviada. */
    private void lente() throws Exception {
        noServidor(() -> overworld().setDayTime(13500));
        olhar(0, -18);
        BackdropExperimento.grade = true;
        BackdropExperimento.alfa = 1;
        ticks(10);
        int[] semBuraco = foto("backdrop-grade");
        comando("vfxsample receita buraco_negro");
        ticks(240);
        int[] comBuraco = foto("backdrop-grade-buraco-negro");
        int camadas = PerfOverlay.skyLayers();
        comando("vfxsample receita stop");
        ticks(40);
        BackdropExperimento.alfa = 0;
        BackdropExperimento.grade = false;
        // Linhas da grade (cinza muito escuro) deslocadas: pixels que eram grade e deixaram de ser, e vice-versa.
        int mudou = 0, total = 0;
        for (int y = 0; y < 400; y += 2) for (int x = 0; x < 1280; x += 2) {
            boolean a = perto(pixel(semBuraco, x, y), BackdropExperimento.GRADE, 12), b = perto(pixel(comBuraco, x, y), BackdropExperimento.GRADE, 12);
            if (a || b) { total++; if (a != b) mudou++; }
        }
        check("lente entorta o fundo desenhado em AFTER_SKY (linhas da grade deslocadas com o buraco negro)",
            camadas >= 1 && mudou > total / 20, camadas + " camadas; " + mudou + " de " + total + " pixels de grade mudaram");
        noServidor(() -> overworld().setDayTime(6000));
    }

    /** Eclipse global do exemplo, na totalidade, com e sem fundo opaco: escurecimento e o apagamento do sol. */
    private void eclipse() throws Exception {
        noServidor(() -> overworld().setDayTime(2000));
        olhar(-90, -38);
        comando("vfxsample evento eclipse start");
        aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
        ticks(430);
        int[] semFundo = foto("backdrop-eclipse-sem-fundo");
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] comFundo = foto("backdrop-eclipse-com-fundo");
        BackdropExperimento.alfa = 0;
        comando("vfxsample evento eclipse stop");
        aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 0), 100);
        ticks(60);
        // Sol no centro. ~16 px por grau (FOV 70 em 1280): 160 px ≈ 10° (dentro do quadrado do sol, fora do disco e da
        // coroa mais forte) e 400 px ≈ 25° (fora do quadrado).
        int dentro = pixel(comFundo, 640 + 160, 360 + 160), fora = pixel(comFundo, 640 + 400, 360 - 250);
        info("eclipse com fundo: céu dentro do quadrado do sol " + rgb(dentro) + ", fora " + rgb(fora)
            + "; sem fundo: dentro " + rgb(pixel(semFundo, 800, 520)) + ", fora " + rgb(pixel(semFundo, 1040, 110)));
        info("eclipse com fundo: razão de brilho dentro/fora do quadrado do sol "
            + String.format(Locale.ROOT, "%.2f", (brilho(dentro) + 1.0) / (brilho(fora) + 1.0))
            + " (1 = sem marca do apagamento; < 1 = o apagamento do sol subtraiu do fundo)");
    }

    /** Cegueira: o jogo não desenha o céu; o protótipo desenha assim mesmo (o Backdrop real precisa seguir a regra). */
    private void cegueira() throws Exception {
        olhar(0, -60);
        noServidor(() -> jogador().addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false)));
        ticks(20);
        int[] vanilla = foto("backdrop-cegueira-vanilla");
        BackdropExperimento.alfa = 1;
        ticks(5);
        int[] opaco = foto("backdrop-cegueira-opaco");
        BackdropExperimento.alfa = 0;
        noServidor(() -> jogador().removeEffect(MobEffects.BLINDNESS));
        info("cegueira: centro sem fundo " + rgb(pixel(vanilla, 640, 360)) + ", com o protótipo " + rgb(pixel(opaco, 640, 360))
            + (algumaFace(pixel(opaco, 640, 360)) ? " (o protótipo aparece: o Backdrop precisa seguir as condições do céu do jogo)" : ""));
    }

    private void custo() throws Exception {
        noServidor(() -> overworld().setDayTime(6000));
        olhar(0, -20);
        ticks(40);
        double[] sem = janela(5000);
        BackdropExperimento.alfa = 1;
        ticks(20);
        double[] com = janela(5000);
        BackdropExperimento.alfa = 0;
        info(String.format(Locale.ROOT, "custo por software (tendência): sem fundo mediana %.2f ms p95 %.2f (%d quadros); com fundo %.2f ms p95 %.2f (%d quadros)",
            FrameStats.percentile(sorted(sem), .5), FrameStats.percentile(sorted(sem), .95), sem.length,
            FrameStats.percentile(sorted(com), .5), FrameStats.percentile(sorted(com), .95), com.length));
    }

    /** Nether: skyType NONE (o jogo não desenha céu), mas AFTER_SKY acontece; acima do teto o fundo aparece. */
    private void nether() throws Exception {
        comando("execute in minecraft:the_nether run tp @s 0.5 132 0.5 0 -80");
        aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().getPath().equals("the_nether")), 400);
        esperarTerreno();
        int antes = BackdropExperimento.desenhos;
        BackdropExperimento.alfa = 1;
        ticks(10);
        int[] img = foto("backdrop-nether");
        BackdropExperimento.alfa = 0;
        check("Nether: AFTER_SKY acontece e o fundo aparece acima do teto", BackdropExperimento.desenhos > antes
            && algumaFace(pixel(img, 640, 360)), rgb(pixel(img, 640, 360)));
    }

    // ------------------------------------------------------------------------------------------------ imagens

    /** Captura do último quadro (ARGB por pixel, 1280 × 720) e, com nome, o PNG em screenshots/. */
    private int[] foto(String nome) throws Exception {
        ticks(3);
        return noCliente(() -> {
            if (nome != null) Screenshot.grab(mc.gameDirectory, nome + ".png", mc.getMainRenderTarget(), (Component c) -> {});
            try (var img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int w = img.getWidth(), h = img.getHeight();
                int[] out = new int[1280 * 720];
                for (int y = 0; y < 720; y++) for (int x = 0; x < 1280; x++) {
                    int v = img.getPixelRGBA(Math.min(x, w - 1), Math.min(y, h - 1));   // ABGR
                    out[y * 1280 + x] = (v & 0xFF) << 16 | (v & 0xFF00) | (v >> 16 & 0xFF);
                }
                return out;
            }
        });
    }

    private static int pixel(int[] img, int x, int y) { return img[Math.clamp(y, 0, 719) * 1280 + Math.clamp(x, 0, 1279)]; }

    private static int r(int c) { return c >> 16 & 255; }
    private static int g(int c) { return c >> 8 & 255; }
    private static int b(int c) { return c & 255; }
    private static int brilho(int c) { return (r(c) + g(c) + b(c)) / 3; }

    private static int difMax(int a, int c) { return Math.max(Math.abs(r(a) - r(c)), Math.max(Math.abs(g(a) - g(c)), Math.abs(b(a) - b(c)))); }

    private static boolean perto(int a, int c, int tol) { return difMax(a, c) <= tol; }

    private static boolean algumaFace(int c) {
        for (int f : BackdropExperimento.FACES) if (perto(c, f, TOL)) return true;
        return false;
    }

    /** Face cuja mistura 50% com {@code vanilla} fica mais perto de {@code c} (com vanilla 0: a face mais perto). */
    private static int melhorFace(int c, int vanilla) {
        int melhor = BackdropExperimento.FACES[0], dif = Integer.MAX_VALUE;
        for (int f : BackdropExperimento.FACES) {
            int alvo = vanilla == 0 ? f : mistura(vanilla, f, .5f), d = difMax(c, alvo);
            if (d < dif) { dif = d; melhor = f; }
        }
        return melhor;
    }

    private static int mistura(int a, int c, float t) {
        return Math.round(r(a) + (r(c) - r(a)) * t) << 16 | Math.round(g(a) + (g(c) - g(a)) * t) << 8 | Math.round(b(a) + (b(c) - b(a)) * t);
    }

    private static int brilhantes(int[] img, int x0, int y0, int x1, int y1, int limite) {
        int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (brilho(pixel(img, x, y)) > limite) n++;
        return n;
    }

    private static String coluna(int[] img, int x, int y0, int y1, int passo) {
        StringBuilder sb = new StringBuilder();
        for (int y = y0; y <= y1; y += passo) sb.append(y).append(':').append(rgb(pixel(img, x, y))).append(' ');
        return sb.toString().trim();
    }

    private static String rgb(int c) { return String.format(Locale.ROOT, "(%d,%d,%d)", r(c), g(c), b(c)); }

    // ------------------------------------------------------------------------------------------------ apoio

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel overworld() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private void olhar(float yaw, float pitch) throws Exception {
        noServidor(() -> jogador().teleportTo(jogador().serverLevel(), jogador().getX(), jogador().getY(), jogador().getZ(), yaw, pitch));
        ticks(10);
    }

    private void comando(String linha) throws Exception {
        noServidor(() -> server().getCommands().performPrefixedCommand(jogador().createCommandSourceStack().withPermission(4), linha));
    }

    private void esperarTerreno() throws Exception {
        ticks(20);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(20);
    }

    private double[] janela(int ms) throws Exception {
        Thread.sleep(ms);
        double[] todos = PerfOverlay.recentFrames();
        double soma = 0;
        int n = 0;
        for (int i = todos.length - 1; i >= 0 && soma < ms; i--, n++) soma += todos[i];
        return Arrays.copyOfRange(todos, todos.length - n, todos.length);
    }

    private static double[] sorted(double[] v) { double[] c = v.clone(); Arrays.sort(c); return c; }

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

    private void ticks(int n) throws InterruptedException {
        MinecraftServer s = server();
        if (s == null) { Thread.sleep(n * 50L); return; }
        int alvo = s.getTickCount() + n;
        while (s.getTickCount() < alvo && !s.isStopped()) Thread.sleep(5);
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

    private void info(String texto) {
        System.out.println("[TESTE] INFO " + texto);
        escrever("INFO " + texto, true);
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
