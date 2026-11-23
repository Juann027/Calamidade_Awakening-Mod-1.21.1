package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.api.QualityMode;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Teste em jogo das imagens, flipbooks e impostors do céu (0.14, fase 4), com {@code calamidade.teste=imagens}, no
 * cliente (renderização por software), numa só inicialização. Usa os objetos registrados pelo mod de teste
 * ({@link CalamidadeTeste}, registrarImagens) pela API real, com os recursos de {@code scripts/gerar_sprites_teste.py}, e
 * confere por sondas de pixel nas capturas: nada sem addon; cor exata e transparência do PNG sobre o fundo; silhueta
 * COVER; flipbooks LOOP, ONCE, PING_PONG e TRACK pelas cores sentinela; proteção de flashes (marcada e a rede de
 * segurança); vistas do impostor e LOD; FRONT na frente de um portal do motor e BEHIND atrás; recursos ausentes e
 * inválidos sem quebrar o resto; uma chamada para duas imagens da mesma textura; memória e envios contabilizados;
 * cegueira; customSkies; recarga; custo; regressão do céu do Overworld. Relatório em {@code calamidade-imagens.txt}.
 */
final class RoteiroImagens implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-imagens.txt");
    private static boolean iniciado;
    private static final int X = -9000, Z = 0;
    private static final int TOL = 4;
    private static final int[] FACES = RoteiroBackdrop.FACES;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
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
            Thread thread = new Thread(new RoteiroImagens(), "Calamidade teste das imagens");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste das imagens, flipbooks e impostors (0.14, fase 4), " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            int[] overworldAntes = fotoOverworld("imagens-overworld-antes");
            semAddon();
            corExata();
            silhueta();
            flipbooks();
            flashes();
            impostor();
            frente();
            invalidos();
            lote();
            bloqueios();
            opcaoDoJogador();
            recarga();
            custo();
            vitrine();
            regressao(overworldAntes);
            escrever("FIM imagens", true);
        } catch (Throwable failure) {
            check("roteiro das imagens sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] imagens: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    // ------------------------------------------------------------------------------------------------ casos

    private void preparar() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            overworld().setDayTime(18000);
            overworld().setWeatherParameters(12000, 0, false, false);
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 3, Z + .5, 0, -30);
        });
        noCliente(() -> {
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.options.hideLightningFlash().set(false);
            ClientHooks.coordinator().select(QualityMode.HIGH);
            return null;
        });
        esperarTerreno();
    }

    /** Sem addon pedindo: nenhuma imagem, nenhuma textura de sprite, nenhuma camada de frente. */
    private void semAddon() throws Exception {
        ticks(40);
        int[] s = noCliente(ClientHooks::skySprites);
        check("sem addon: nenhuma imagem, nenhum impostor, nenhuma textura de sprite, nenhuma camada de frente",
            s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && !noCliente(ClientHooks::skyFrontAllocated), Arrays.toString(s));
    }

    /** Disco NORMAL sobre o cubemap (face sul): cor exata no miolo, a parte transparente do PNG não toca o fundo. */
    private void corExata() throws Exception {
        olhar(0, -30);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            int[] sem = foto(null);
            try (var img = iniciar(CalamidadeTeste.IMG_DISCO)) {
                int[] c = foto("imagens-cor-exata");
                int miolo = pixel(c, 640, 360);
                check("imagem NORMAL: cor exata no miolo", perto(miolo, CalamidadeTeste.COR_DISCO, TOL), rgb(miolo));
                // Quadrado de meia largura 6 graus (~54 px na tela) e disco de raio 28/32 disso (~47 px): a (±48, ±48) px do
                // centro o ponto está dentro do quadrado e fora do disco, na parte transparente do PNG.
                int pior = 0;
                for (int[] p : new int[][] {{640 - 48, 360 - 48}, {640 + 48, 360 - 48}, {640 - 48, 360 + 48}, {640 + 48, 360 + 48}})
                    pior = Math.max(pior, difMax(pixel(c, p[0], p[1]), pixel(sem, p[0], p[1])));
                check("imagem NORMAL: os cantos transparentes do PNG não tocam o fundo", pior <= 2, "pior " + pior + "/255");
                int[] s = noCliente(ClientHooks::skySprites);
                long[] mem = noCliente(ClientHooks::skyTextureMemory);
                check("uma imagem desenhada, uma textura residente, memória contada dentro do orçamento", s[0] == 1 && s[2] >= 1
                    && mem[0] > 0 && mem[0] <= mem[1] && s[7] >= 1, "sprites " + Arrays.toString(s) + ", memória " + Arrays.toString(mem));
            }
        }
    }

    /** Disco de alfa suave em COVER: só a silhueta (alfa ≥ 0,5) esconde o fundo. */
    private void silhueta() throws Exception {
        olhar(0, -30);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO); var img = iniciar(CalamidadeTeste.IMG_COBRE)) {
            int[] c = foto("imagens-cover");
            int cheio = 0, intacto = 0, borda = 0;
            for (int y = 260; y < 460; y += 2) for (int x = 540; x < 740; x += 2) {
                int v = pixel(c, x, y);
                if (perto(v, CalamidadeTeste.COR_SUAVE, 3)) cheio++; else if (perto(v, FACES[2], 3)) intacto++; else borda++;
            }
            check("imagem COVER: só a silhueta esconde o fundo", cheio > 300 && intacto > 300 && borda < (cheio + intacto) / 5,
                cheio + " com a cor da imagem, " + intacto + " com o fundo, " + borda + " outros");
        }
    }

    /** Flipbooks de cores sentinela a 1 quadro por segundo: sequência por fotos no meio de cada quadro. */
    private void flipbooks() throws Exception {
        olhar(0, -30);
        int[] cores = CalamidadeTeste.COR_QUADROS;
        try (var img = iniciar(CalamidadeTeste.IMG_LOOP)) {
            List<Integer> seq = sequencia(6, cores);
            boolean ok = !seq.contains(-1);
            for (int i = 1; i < seq.size() && ok; i++) ok = seq.get(i) == (seq.get(i - 1) + 1) % 4;
            check("flipbook LOOP: um quadro por segundo, em ordem, voltando ao 0", ok, "quadros " + seq);
        }
        try (var img = iniciar(CalamidadeTeste.IMG_ONCE)) {
            // Começa depois de 9 s de envelope: um ONCE de 4 quadros a 1 por segundo já parou no último.
            List<Integer> seq = sequencia(3, cores);
            check("flipbook ONCE: parado no último quadro", seq.stream().allMatch(q -> q == 3), "quadros " + seq);
        }
        try (var img = iniciar(CalamidadeTeste.IMG_PING)) {
            List<Integer> seq = sequencia(8, cores);
            boolean ok = !seq.contains(-1);
            int voltas = 0;
            for (int i = 1; i < seq.size() && ok; i++) ok = Math.abs(seq.get(i) - seq.get(i - 1)) == 1;
            for (int i = 1; i + 1 < seq.size(); i++) if ((seq.get(i) == 3 || seq.get(i) == 0) && seq.get(i - 1).equals(seq.get(i + 1))) voltas++;
            check("flipbook PING_PONG: passo de um quadro, ida e volta sem repetir as pontas", ok && voltas >= 1, "quadros " + seq);
        }
        try (var img = iniciar(CalamidadeTeste.IMG_TRACK)) {
            List<Integer> seq = sequencia(2, cores);
            check("flipbook TRACK: o quadro da trilha (2), não o relógio", seq.stream().allMatch(q -> q == 2), "quadros " + seq);
        }
    }

    /** Fotos a cada 20 ticks, alinhadas ao meio de um quadro de 1 s pelo relógio do jogo, e o quadro de cada uma. */
    private List<Integer> sequencia(int n, int[] cores) throws Exception {
        // O quadro troca a cada 20 ticks de idade; espera o meio (10 ticks depois de uma troca) pela cor do miolo.
        int inicio = quadro(pixel(foto(null), 640, 360), cores);
        for (int i = 0; i < 25 && quadro(pixel(foto(null), 640, 360), cores) == inicio; i++) ticks(1);
        ticks(8);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(quadro(pixel(foto(i == 0 ? "imagens-flipbook" : null), 640, 360), cores));
            ticks(17);
        }
        return out;
    }

    private static int quadro(int cor, int[] cores) {
        for (int i = 0; i < cores.length; i++) if (perto(cor, cores[i], 8)) return i;
        return -1;
    }

    /** Folha preto/branco a 12 quadros por segundo: marcada congela com "ocultar clarões"; sem marca, a rede pega. */
    private void flashes() throws Exception {
        olhar(0, -30);
        try (var img = iniciar(CalamidadeTeste.IMG_PISCA)) {
            int muda = variacao();
            noCliente(() -> { mc.options.hideLightningFlash().set(true); return null; });
            ticks(5);
            int congelado = variacao();
            int quadro = brilho(pixel(foto(null), 640, 360));
            int frozen = noCliente(ClientHooks::skySprites)[8];
            noCliente(() -> { mc.options.hideLightningFlash().set(false); return null; });
            check("flashing(true): anima normalmente; com \"ocultar clarões\" congela no quadro seguro (0, preto)",
                muda > 100 && congelado <= 2 && quadro < 30 && frozen == 1, "variação " + muda + " → " + congelado + ", brilho " + quadro + ", congeladas " + frozen);
        }
        try (var img = iniciar(CalamidadeTeste.IMG_PISCA_SEM)) {
            noCliente(() -> { mc.options.hideLightningFlash().set(true); return null; });
            ticks(5);
            int congelado = variacao();
            noCliente(() -> { mc.options.hideLightningFlash().set(false); return null; });
            ticks(5);
            int livre = variacao();
            check("rede de segurança: folha que pisca sem marca também congela com \"ocultar clarões\" (e anima sem)",
                congelado <= 2 && livre > 100, "variação " + congelado + " / " + livre);
        }
        try (var img = iniciar(CalamidadeTeste.IMG_LOOP)) {
            noCliente(() -> { mc.options.hideLightningFlash().set(true); return null; });
            ticks(5);
            List<Integer> seq = sequencia(3, CalamidadeTeste.COR_QUADROS);
            noCliente(() -> { mc.options.hideLightningFlash().set(false); return null; });
            check("flipbook normal continua animando com \"ocultar clarões\" (só os que piscam congelam)",
                seq.size() == 3 && !seq.contains(-1) && seq.get(1) != seq.get(0).intValue(), "quadros " + seq);
        }
    }

    /** Maior diferença de brilho no miolo entre 6 fotos seguidas. */
    private int variacao() throws Exception {
        int min = 255, max = 0;
        for (int i = 0; i < 6; i++) {
            int b = brilho(pixel(foto(null), 640, 360));
            min = Math.min(min, b);
            max = Math.max(max, b);
            ticks(1);
        }
        return max - min;
    }

    /** Impostor de 8 vistas de cores sentinela: a vista certa para cada orientação; LOD pequeno troca de nível. */
    private void impostor() throws Exception {
        olhar(0, -30);
        int[] vistas = CalamidadeTeste.COR_VISTAS;
        // Objeto ao sul a 30 graus virado para o yaw ψ: a câmera fica na vista (180 − ψ) mod 360 do objeto.
        for (Object[] caso : new Object[][] {{CalamidadeTeste.IMP_NAVE_0, 4}, {CalamidadeTeste.IMP_NAVE_90, 2}, {CalamidadeTeste.IMP_NAVE_180, 0}}) {
            try (var imp = iniciar((ResourceLocation) caso[0])) {
                int c = pixel(foto(caso[1].equals(4) ? "imagens-impostor" : null), 640, 360);
                int esperado = vistas[(Integer) caso[1]];
                int[] s = noCliente(ClientHooks::skySprites);
                check("impostor virado para " + caso[0].toString().replaceAll(".*_", "") + " graus: vista " + caso[1] + " (cor exata)",
                    perto(c, esperado, TOL) && s[1] == 1, rgb(c) + " contra " + rgb(esperado) + ", impostors " + s[1]);
            }
        }
        try (var imp = iniciar(CalamidadeTeste.IMP_NAVE_0)) {
            int grande = noCliente(ClientHooks::skySprites)[9];
            try (var peq = iniciar(CalamidadeTeste.IMP_PEQUENA)) {
                ticks(20);
                int[] s = noCliente(ClientHooks::skySprites);
                check("LOD: o impostor grande usa o nível 0, o pequeno (menos de 0,85 × 12 px) o nível 1",
                    grande == 0 && s[9] == 1 && s[1] == 2, "nível grande " + grande + ", maior nível com os dois " + s[9] + ", impostors " + s[1]);
            }
        }
    }

    /** Disco COVER FRONT e BEHIND sobre um portal do motor no mesmo lugar. */
    private void frente() throws Exception {
        olhar(0, -30);
        try (var f = iniciar(CalamidadeTeste.IMG_FRENTE)) {
            ticks(10);
            int c = pixel(foto("imagens-frente"), 640, 360);
            boolean camada = noCliente(ClientHooks::skyFrontDrawn);
            check("FRONT: a imagem fica na frente do portal com a cor exata (camada de frente no quadro)",
                perto(c, CalamidadeTeste.COR_DISCO, TOL) && camada && noCliente(PerfOverlay::skyPass), rgb(c) + ", camada " + camada);
        }
        try (var b = iniciar(CalamidadeTeste.IMG_ATRAS)) {
            ticks(10);
            int c = pixel(foto("imagens-atras"), 640, 360);
            check("BEHIND: a mesma imagem fica atrás do portal (as camadas pintam por cima)", difMax(c, CalamidadeTeste.COR_DISCO) > 40
                && !noCliente(ClientHooks::skyFrontDrawn), rgb(c));
        }
    }

    /** PNG ausente, corrompido, grade errada, descritor inválido e sem página: nada quebra, a imagem válida continua. */
    private void invalidos() throws Exception {
        olhar(0, -30);
        try (var img = iniciar(CalamidadeTeste.IMG_INVALIDOS)) {
            ticks(40);
            int c = pixel(foto("imagens-invalidos"), 640, 360);
            int[] s = noCliente(ClientHooks::skySprites);
            String problema = noCliente(ClientHooks::skyTextureProblem);
            check("recursos inválidos: ignorados com aviso, contados no F7; a imagem válida continua desenhada",
                perto(c, CalamidadeTeste.COR_DISCO, TOL) && s[0] == 1 && s[1] == 0 && s[4] >= 6 && !problema.isEmpty(),
                "miolo " + rgb(c) + ", sprites " + Arrays.toString(s) + ", último problema: " + problema);
            int falhas = s[4];
            ticks(60);
            check("recursos inválidos: o aviso não se repete a cada quadro (contagem estável)", noCliente(ClientHooks::skySprites)[4] == falhas, "");
        }
    }

    /** Duas imagens da mesma textura: uma chamada de desenho por quadro para as duas. */
    private void lote() throws Exception {
        olhar(0, -30);
        try (var img = iniciar(CalamidadeTeste.IMG_DUPLA)) {
            ticks(10);
            int d0 = noCliente(ClientHooks::skyObjectDraws);
            double[] janela = janela(3000);
            int d1 = noCliente(ClientHooks::skyObjectDraws);
            double porQuadro = (d1 - d0) / (double) Math.max(1, janela.length);
            int[] s = noCliente(ClientHooks::skySprites);
            check("lote: duas imagens da mesma textura, uma chamada de desenho por quadro", s[0] == 2 && porQuadro > .5 && porQuadro < 1.5,
                String.format(Locale.ROOT, "%.2f chamadas por quadro, %d imagens", porQuadro, s[0]));
        }
    }

    private void bloqueios() throws Exception {
        try (var img = iniciar(CalamidadeTeste.IMG_DISCO)) {
            noServidor(() -> jogador().addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0, false, false)));
            ticks(20);
            boolean bloqueado = noCliente(ClientHooks::skyObjectsBlocked);
            int[] s = noCliente(ClientHooks::skySprites);
            noServidor(() -> jogador().removeEffect(MobEffects.BLINDNESS));
            check("cegueira: o jogo não desenha céu, as imagens também não", bloqueado && s[0] == 0, "bloqueado " + bloqueado + ", " + Arrays.toString(s));
        }
    }

    private void opcaoDoJogador() throws Exception {
        try (var img = iniciar(CalamidadeTeste.IMG_DISCO)) {
            noCliente(() -> { ClientConfig.customSkies(false); return null; });
            ticks(5);
            int[] n = noCliente(ClientHooks::skySprites);
            noCliente(() -> { ClientConfig.customSkies(true); return null; });
            ticks(5);
            int[] m = noCliente(ClientHooks::skySprites);
            check("customSkies desligado: nenhuma imagem; ligado de novo: volta", n[0] == 0 && m[0] == 1, n[0] + " → " + m[0]);
        }
    }

    private void recarga() throws Exception {
        olhar(0, -30);
        try (var img = iniciar(CalamidadeTeste.IMG_DISCO)) {
            int antes = noCliente(ClientHooks::skySprites)[7];
            var recarregar = noCliente(mc::reloadResourcePacks);
            for (int i = 0; i < 1200 && !recarregar.isDone(); i++) Thread.sleep(50);
            Thread.sleep(500);
            ticks(30);
            int[] s = noCliente(ClientHooks::skySprites);
            int c = pixel(foto(null), 640, 360);
            check("recarga de recursos: texturas liberadas e enviadas de novo; a imagem continua", recarregar.isDone() && s[7] > antes
                && s[0] == 1 && perto(c, CalamidadeTeste.COR_DISCO, TOL), "envios " + antes + " → " + s[7] + ", miolo " + rgb(c));
        }
    }

    private void custo() throws Exception {
        olhar(0, -30);
        ticks(40);
        StringBuilder sb = new StringBuilder("custo por software (tendência, não GPU física), mediana/p95 ms:");
        double[] sem = janela(5000);
        sb.append(String.format(Locale.ROOT, " sem objetos %.2f/%.2f;", med(sem), p95(sem)));
        Object[][] casos = {{"1 imagem", CalamidadeTeste.IMG_DISCO}, {"8 imagens de 3 texturas", CalamidadeTeste.IMG_MUITAS},
            {"flipbook", CalamidadeTeste.IMG_LOOP}, {"impostor", CalamidadeTeste.IMP_NAVE_0},
            {"FRONT com portal", CalamidadeTeste.IMG_FRENTE}, {"BEHIND com portal", CalamidadeTeste.IMG_ATRAS}};
        for (Object[] caso : casos) {
            try (var a = iniciar((ResourceLocation) caso[1])) {
                ticks(20);
                double[] j = janela(5000);
                sb.append(String.format(Locale.ROOT, " %s %.2f/%.2f;", caso[0], med(j), p95(j)));
            }
        }
        info(sb.toString());
        long[] mem = noCliente(ClientHooks::skyTextureMemory);
        int[] s = noCliente(ClientHooks::skySprites);
        info("memória de texturas do céu " + (mem[0] >> 10) + " KiB de " + (mem[1] >> 20) + " MiB; " + s[7] + " envios desde o início");
    }

    private void vitrine() throws Exception {
        olhar(0, -32);
        try (var f = iniciar(CalamidadeTeste.FUNDO_GRADE); var v = iniciar(CalamidadeTeste.IMG_VITRINE)) {
            ticks(10);
            foto("imagens-vitrine");
        }
    }

    private void regressao(int[] antes) throws Exception {
        int[] depois = fotoOverworld("imagens-overworld-depois");
        long soma = 0;
        int pior = 0, n = 0;
        for (int y = 0; y < 720; y += 3) for (int x = 0; x < 1280; x += 3) {
            int d = difMax(pixel(antes, x, y), pixel(depois, x, y));
            soma += d; pior = Math.max(pior, d); n++;
        }
        check("regressão: céu do Overworld igual antes e depois das imagens", soma / (double) n < 1 && noCliente(ClientHooks::skySprites)[0] == 0,
            String.format(Locale.ROOT, "diferença média %.2f, pior %d", soma / (double) n, pior));
    }

    private int[] fotoOverworld(String nome) throws Exception {
        hora(6000);
        olhar(30, -20);
        ticks(20);
        int[] f = foto(nome);
        hora(18000);
        return f;
    }

    // ------------------------------------------------------------------------------------------------ efeitos

    private interface Fundo extends AutoCloseable { @Override void close() throws Exception; }

    /** Instância de um efeito de teste na posição do jogador, com o envelope inteiro; fechar para o efeito. */
    private Fundo iniciar(ResourceLocation id) throws Exception {
        Optional<Effects.Handle> handle = noServidor(() -> Effects.start(id, jogador().serverLevel(), jogador().position()));
        if (handle.isEmpty()) throw new IllegalStateException("efeito não começou: " + id);
        // Efeitos de mais de 1200 ticks entram em 168 ticks (FrameBudget.envelope): espera a opacidade inteira.
        ticks(180);
        return () -> {
            noServidor(() -> { handle.get().stop(); return null; });
            ticks(10);
        };
    }

    // ------------------------------------------------------------------------------------------------ imagens

    /** Captura do último quadro (RGB por pixel, 1280 × 720) e, com nome, o PNG em screenshots/. */
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
    private static String rgb(int c) { return String.format(Locale.ROOT, "(%d,%d,%d)", r(c), g(c), b(c)); }

    // ------------------------------------------------------------------------------------------------ apoio

    private MinecraftServer server() { return mc.getSingleplayerServer(); }
    private ServerLevel overworld() { return server().overworld(); }
    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private void olhar(float yaw, float pitch) throws Exception {
        noServidor(() -> jogador().teleportTo(jogador().serverLevel(), jogador().getX(), jogador().getY(), jogador().getZ(), yaw, pitch));
        ticks(10);
    }

    private void hora(long tempo) throws Exception {
        noServidor(() -> overworld().setDayTime(tempo));
        boolean chegou = aguardar(() -> noClienteSemErro(() -> mc.level != null && mc.level.getDayTime() % 24000 == tempo % 24000), 200);
        if (!chegou) info("a hora " + tempo + " não chegou ao cliente em 200 ticks");
        ticks(5);
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

    private static double med(double[] v) { return FrameStats.percentile(sorted(v), .5); }
    private static double p95(double[] v) { return FrameStats.percentile(sorted(v), .95); }
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
