package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.api.QualityMode;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.SkyProfiles;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Teste em jogo dos objetos do céu (0.14, fase 3), com {@code calamidade.teste=estrelas}, no cliente (renderização por
 * software) com o vfx-sample em {@code mods/}. Usa os objetos registrados pelo mod de teste ({@link CalamidadeTeste},
 * registrarEstrelas) pela API real e confere, com sondas de pixel nas capturas: nada sem addon (nenhuma malha, nenhum
 * alvo, nenhum desenho); estrela NORMAL com cor exata sobre o fundo; estrelas LUMINOUS que não apagam o fundo e somem de
 * dia; nebulosa semitransparente e silhueta COVER; Atmosphere sobre a nebulosa (camada de cobertura); estrelas que
 * aparecem no eclipse; céu bloqueado; customSkies; recarga; perfis de qualidade (estrelas por perfil, prefixo do buffer,
 * uma chamada por campo); custo; regressão do céu do Overworld. Relatório em {@code calamidade-estrelas.txt}; capturas
 * em {@code screenshots/}.
 */
final class RoteiroEstrelas implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-estrelas.txt");
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
            Thread thread = new Thread(new RoteiroEstrelas(), "Calamidade teste das estrelas");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste das estrelas e nebulosas (0.14, fase 3), " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            int[] overworldAntes = fotoOverworld("estrelas-overworld-antes");
            semAddon();
            corExata();
            luminosas();
            nebulosas();
            atmosfera();
            eclipse();
            bloqueios();
            opcaoDoJogador();
            perfis();
            recarga();
            custo();
            vitrine();
            regressao(overworldAntes);
            escrever("FIM estrelas", true);
        } catch (Throwable failure) {
            check("roteiro das estrelas sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] estrelas: " + passou + " passaram, " + falhou + " falharam");
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
            overworld().setWeatherParameters(12000, 0, false, false);
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 3, Z + .5, 0, -25);
        });
        noCliente(() -> {
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.cloudStatus().set(CloudStatus.OFF);
            ClientHooks.coordinator().select(QualityMode.HIGH);
            return null;
        });
        esperarTerreno();
    }

    /** Sem addon pedindo: nenhuma malha, nenhum alvo de cobertura, nenhum desenho, nenhum objeto no quadro. */
    private void semAddon() throws Exception {
        int desenhos = noCliente(ClientHooks::skyObjectDraws);
        ticks(40);
        long[] malhas = noCliente(ClientHooks::skyObjectMeshes);
        int[] objetos = noCliente(ClientHooks::skyObjects);
        check("sem addon: nenhum objeto, nenhuma malha, nenhum desenho, nenhuma camada de cobertura alocada",
            objetos[3] == 0 && malhas[0] == 0 && noCliente(ClientHooks::skyObjectDraws) == desenhos && desenhos == 0
                && !noCliente(ClientHooks::skyCoverageAllocated),
            "candidatos " + objetos[3] + ", malhas " + Arrays.toString(malhas) + ", desenhos " + desenhos);
    }

    /** Estrela NORMAL vermelha sobre o cubemap (face sul): miolo com a cor exata, o fundo intacto ao lado. */
    private void corExata() throws Exception {
        hora(18000);
        olhar(0, -30);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO); var est = iniciar(CalamidadeTeste.EST_CATALOGO)) {
            int[] img = foto("estrelas-cor-exata");
            int miolo = maisVermelho(img, 640, 360, 4);
            int lado = pixel(img, 640 - 120, 360 + 60);
            check("estrela NORMAL: cor exata no miolo sobre o fundo", perto(miolo, CalamidadeTeste.EST_VERMELHA, 6), rgb(miolo));
            check("estrela NORMAL: o fundo continua intacto fora dela", perto(lado, FACES[2], TOL), rgb(lado));
            int[] n = noCliente(ClientHooks::skyObjects);
            long[] m = noCliente(ClientHooks::skyObjectMeshes);
            check("catálogo com constelação: um campo, três estrelas, uma malha estática", n[0] == 1 && n[1] == 3 && m[1] == 1,
                "objetos " + Arrays.toString(n) + ", malhas " + Arrays.toString(m));
        }
    }

    /** Campo LUMINOUS à noite sobre o fundo: nenhum pixel mais escuro que o fundo, várias estrelas acesas; de dia, nada. */
    private void luminosas() throws Exception {
        hora(18000);
        olhar(0, -45);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            int[] sem = foto(null);
            try (var est = iniciar(CalamidadeTeste.EST_CAMPO)) {
                int[] noite = foto("estrelas-luminosas-noite");
                int escuro = 0, acesos = 0;
                for (int y = 0; y < 300; y++) for (int x = 0; x < 1280; x++) {
                    int a = pixel(sem, x, y), c = pixel(noite, x, y);
                    if (!perto(a, FACES[2], TOL)) continue;
                    escuro = Math.max(escuro, Math.max(r(a) - r(c), Math.max(g(a) - g(c), b(a) - b(c))));
                    if (difMax(a, c) > 12) acesos++;
                }
                check("estrelas LUMINOUS não apagam o fundo (nenhum pixel mais escuro)", escuro <= 2 && acesos > 30,
                    "pior escurecimento " + escuro + "/255, " + acesos + " pixels acesos");
                hora(6000);
                int[] dia = foto("estrelas-luminosas-dia");
                int sobra = 0;
                for (int y = 0; y < 300; y += 2) for (int x = 0; x < 1280; x += 2)
                    if (perto(pixel(sem, x, y), FACES[2], TOL) && difMax(pixel(sem, x, y), pixel(dia, x, y)) > 6) sobra++;
                check("de dia, estrelas com daylight 1 somem (como as do jogo)", sobra < 5, sobra + " pixels diferentes");
            }
        }
        hora(6000);
    }

    /** Nebulosa NORMAL semitransparente e COVER, centradas no sul a 30 graus (centro da tela). */
    private void nebulosas() throws Exception {
        olhar(0, -30);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            int[] img;
            try (var neb = iniciar(CalamidadeTeste.EST_NEBULOSA)) { img = foto("estrelas-nebulosa-normal"); }
            double amax = 0, pior = 0;
            int dentro = 0;
            int bg = FACES[2], n = CalamidadeTeste.EST_NEB;
            for (int y = 260; y < 460; y += 2) for (int x = 540; x < 740; x += 2) {
                int c = pixel(img, x, y);
                double t = (r(c) - r(bg)) / (double) (r(n) - r(bg));
                if (t < .05) continue;
                dentro++;
                amax = Math.max(amax, t);
                pior = Math.max(pior, difMax(c, mistura(bg, n, (float) t)));
            }
            int longe = pixel(img, 100, 200);
            check("nebulosa NORMAL: mistura por alfa coerente nos três canais", dentro > 200 && pior <= 6,
                dentro + " pixels, pior " + String.format(Locale.ROOT, "%.1f", pior));
            check("nebulosa NORMAL densidade 0,5: o fundo continua visível no miolo", amax > .2 && amax < .65,
                String.format(Locale.ROOT, "alfa máximo %.2f", amax));
            check("nebulosa NORMAL não substitui a skybox fora dela", perto(longe, pixel(foto(null), 100, 200), 2), rgb(longe));
            try (var cob = iniciar(CalamidadeTeste.EST_COBRE)) {
                int[] c = foto("estrelas-nebulosa-cover");
                int cheio = 0, intacto = 0, borda = 0;
                for (int y = 200; y < 520; y += 2) for (int x = 480; x < 800; x += 2) {
                    int v = pixel(c, x, y);
                    if (perto(v, n, 3)) cheio++; else if (perto(v, bg, 3)) intacto++; else borda++;
                }
                check("nebulosa COVER: só a silhueta esconde o fundo", cheio > 300 && intacto > 300 && borda < (cheio + intacto) / 5,
                    cheio + " com a cor da nebulosa, " + intacto + " com o fundo, " + borda + " outros");
            }
        }
    }

    /** Atmosphere (FUNDO_ATMO: cubemap + gradiente 0,7) com a nebulosa: tinge o que aparece, camada de cobertura usada. */
    private void atmosfera() throws Exception {
        olhar(0, -30);
        int semAtmo;
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO); var neb = iniciar(CalamidadeTeste.EST_NEBULOSA)) {
            semAtmo = pixel(foto(null), 640, 360);
        }
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_ATMO); var neb = iniciar(CalamidadeTeste.EST_NEBULOSA)) {
            ticks(20);
            int c = pixel(foto("estrelas-atmosfera"), 640, 360);
            double k = suave(0, .6, .5);
            int topo = CalamidadeTeste.ATMO_TOPO, horiz = CalamidadeTeste.ATMO_HORIZONTE;
            int grad = canal(r(horiz) + (r(topo) - r(horiz)) * k) << 16 | canal(g(horiz) + (g(topo) - g(horiz)) * k) << 8
                | canal(b(horiz) + (b(topo) - b(horiz)) * k);
            int esperado = mistura(semAtmo, grad, CalamidadeTeste.ATMO_MISTURA);
            boolean camada = noCliente(ClientHooks::skyCoverageDrawn);
            int[] tam = noCliente(ClientHooks::skyCoverageSize);
            check("Atmosphere sobre a nebulosa: mix(tela sem atmosfera, gradiente, 0,7) no miolo", perto(c, esperado, TOL + 2),
                "sem atmosfera " + rgb(semAtmo) + ", medido " + rgb(c) + ", esperado " + rgb(esperado));
            check("camada de cobertura desenhada no quadro com passe de atmosfera, do tamanho da tela", camada && tam[0] > 0
                && noCliente(PerfOverlay::atmospherePass), "camada " + camada + ", " + Arrays.toString(tam));
        }
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO); var neb = iniciar(CalamidadeTeste.EST_NEBULOSA)) {
            ticks(5);
            check("sem passe no quadro, a camada de cobertura não é desenhada", !noCliente(ClientHooks::skyCoverageDrawn)
                && !noCliente(PerfOverlay::atmospherePass) && !noCliente(PerfOverlay::skyPass), "");
        }
    }

    /** Eclipse global do exemplo de dia: as estrelas do campo (REVEAL) aparecem na totalidade. */
    private void eclipse() throws Exception {
        hora(2000);
        olhar(-60, -40);
        try (var est = iniciar(CalamidadeTeste.EST_CAMPO)) {
            int antes = picos(foto("estrelas-eclipse-antes"));
            comando("vfxsample evento eclipse start");
            aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
            ticks(430);
            int durante = picos(foto("estrelas-eclipse"));
            comando("vfxsample evento eclipse stop");
            aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 0), 100);
            check("eclipse de dia: estrelas REVEAL aparecem na totalidade", durante > antes + 20,
                "pontos de estrela " + antes + " antes, " + durante + " na totalidade");
        }
        ticks(60);
        hora(6000);
    }

    private void bloqueios() throws Exception {
        hora(18000);
        olhar(0, -45);
        try (var est = iniciar(CalamidadeTeste.EST_CAMPO)) {
            ticks(5);
            int antes = noCliente(ClientHooks::skyObjectDraws);
            noServidor(() -> jogador().addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0, false, false)));
            ticks(20);
            boolean bloqueado = noCliente(ClientHooks::skyObjectsBlocked);
            int a = noCliente(ClientHooks::skyObjectDraws);
            ticks(10);
            int b = noCliente(ClientHooks::skyObjectDraws);
            noServidor(() -> jogador().removeEffect(MobEffects.BLINDNESS));
            check("cegueira: o jogo não desenha céu, os objetos também não", bloqueado && a == b, "bloqueado " + bloqueado + ", desenhos " + antes + " → " + a + " → " + b);
        }
        hora(6000);
    }

    private void opcaoDoJogador() throws Exception {
        hora(18000);
        try (var est = iniciar(CalamidadeTeste.EST_CAMPO)) {
            noCliente(() -> { ClientConfig.customSkies(false); return null; });
            ticks(5);
            int[] n = noCliente(ClientHooks::skyObjects);
            noCliente(() -> { ClientConfig.customSkies(true); return null; });
            ticks(5);
            int[] m = noCliente(ClientHooks::skyObjects);
            check("customSkies desligado: nenhum objeto; ligado de novo: voltam", n[0] == 0 && n[3] == 0 && m[0] == 1,
                Arrays.toString(n) + " → " + Arrays.toString(m));
        }
        hora(6000);
    }

    /** Campo de 60 000 estrelas em cada perfil: estrelas desenhadas pelo teto e uma chamada de desenho por campo. */
    private void perfis() throws Exception {
        hora(18000);
        olhar(0, -45);
        try (var est = iniciar(CalamidadeTeste.EST_DENSO)) {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            int anterior = 0;
            for (QualityMode modo : new QualityMode[] {QualityMode.LOW, QualityMode.BALANCED, QualityMode.HIGH, QualityMode.ULTRA}) {
                noCliente(() -> { ClientHooks.coordinator().select(modo); return null; });
                ticks(10);
                int[] n = noCliente(ClientHooks::skyObjects);
                int esperado = Math.min(60000, dev.calamidade_awakening.api.SkyObjectsMath.caps(modo.profile.limits()).stars());
                int d0 = noCliente(ClientHooks::skyObjectDraws);
                ticks(20);
                int d1 = noCliente(ClientHooks::skyObjectDraws);
                int quadros = noCliente(() -> PerfOverlay.recentFrames().length);
                ok &= n[1] == esperado && n[1] >= anterior;
                anterior = n[1];
                sb.append(modo).append(' ').append(n[1]).append(" estrelas ");
                info(modo + ": " + n[1] + " estrelas desenhadas (teto " + esperado + "), " + (d1 - d0) + " chamadas em 20 ticks");
            }
            check("perfis: estrelas desenhadas pelo teto de cada perfil, crescendo de LOW a ULTRA", ok, sb.toString().trim());
            noCliente(() -> { ClientHooks.coordinator().select(QualityMode.HIGH); return null; });
            // Uma chamada por campo por quadro sem passe (sem camada de cobertura): conta quadros pelo frame time.
            ticks(10);
            int d0 = noCliente(ClientHooks::skyObjectDraws);
            double[] janela = janela(3000);
            int d1 = noCliente(ClientHooks::skyObjectDraws);
            double porQuadro = (d1 - d0) / (double) Math.max(1, janela.length);
            check("lote: uma chamada de desenho por campo por quadro (não uma por estrela)", porQuadro > .5 && porQuadro < 1.5,
                String.format(Locale.ROOT, "%.2f chamadas por quadro com 40 000 estrelas", porQuadro));
        }
        hora(6000);
    }

    private void recarga() throws Exception {
        hora(18000);
        try (var est = iniciar(CalamidadeTeste.EST_CAMPO)) {
            ticks(5);
            long[] antes = noCliente(ClientHooks::skyObjectMeshes);
            var recarregar = noCliente(mc::reloadResourcePacks);
            for (int i = 0; i < 1200 && !recarregar.isDone(); i++) Thread.sleep(50);
            Thread.sleep(500);
            ticks(20);
            long[] depois = noCliente(ClientHooks::skyObjectMeshes);
            int[] n = noCliente(ClientHooks::skyObjects);
            check("recarga de recursos: malhas liberadas e montadas de novo; o campo continua", recarregar.isDone()
                && depois[0] > antes[0] && depois[1] == 1 && n[0] == 1, Arrays.toString(antes) + " → " + Arrays.toString(depois));
        }
        hora(6000);
    }

    private void custo() throws Exception {
        hora(18000);
        olhar(0, -45);
        ticks(40);
        StringBuilder sb = new StringBuilder("custo por software (tendência, não GPU física), mediana/p95 ms:");
        double[] sem = janela(5000);
        sb.append(String.format(Locale.ROOT, " sem objetos %.2f/%.2f;", med(sem), p95(sem)));
        Object[][] casos = {{"6 000 estrelas", CalamidadeTeste.EST_CAMPO, null}, {"60 000 estrelas (HIGH: 40 000)", CalamidadeTeste.EST_DENSO, null},
            {"nuvem", CalamidadeTeste.EST_NEBULOSA, null}, {"faixa", CalamidadeTeste.EST_FAIXA, null},
            {"6 000 estrelas + atmosfera (camada de cobertura)", CalamidadeTeste.EST_CAMPO, CalamidadeTeste.EST_ATMO}};
        for (Object[] caso : casos) {
            try (var a = iniciar((ResourceLocation) caso[1]); var b = caso[2] == null ? (Fundo) () -> {} : iniciar((ResourceLocation) caso[2])) {
                ticks(20);
                double[] j = janela(5000);
                sb.append(String.format(Locale.ROOT, " %s %.2f/%.2f;", caso[0], med(j), p95(j)));
            }
        }
        info(sb.toString());
        hora(6000);
    }

    private void vitrine() throws Exception {
        hora(18000);
        olhar(0, -32);
        try (var v = iniciar(CalamidadeTeste.EST_VITRINE)) {
            ticks(10);
            foto("estrelas-vitrine");
            int[] n = noCliente(ClientHooks::skyObjects);
            info("vitrine: " + n[0] + " campos, " + n[1] + " estrelas, " + n[2] + " nebulosas");
        }
        olhar(0, -32);
        try (var f = iniciar(CalamidadeTeste.FUNDO_GRADE); var v = iniciar(CalamidadeTeste.EST_VITRINE)) {
            foto("estrelas-vitrine-fundo");
        }
        hora(6000);
    }

    private void regressao(int[] antes) throws Exception {
        int[] depois = fotoOverworld("estrelas-overworld-depois");
        long soma = 0;
        int pior = 0, n = 0;
        for (int y = 0; y < 720; y += 3) for (int x = 0; x < 1280; x += 3) {
            int d = difMax(pixel(antes, x, y), pixel(depois, x, y));
            soma += d; pior = Math.max(pior, d); n++;
        }
        check("regressão: céu do Overworld igual antes e depois dos objetos do céu",
            soma / (double) n < 1 && noCliente(ClientHooks::skyObjects)[3] == 0,
            String.format(Locale.ROOT, "diferença média %.2f, pior %d", soma / (double) n, pior));
    }

    private int[] fotoOverworld(String nome) throws Exception {
        hora(6000);
        olhar(30, -20);
        ticks(20);
        return foto(nome);
    }

    /** O pixel mais vermelho (r − g) num quadrado de lado 2·raio em volta de (x, y). */
    private static int maisVermelho(int[] img, int x, int y, int raio) {
        int melhor = pixel(img, x, y);
        for (int dy = -raio * 4; dy <= raio * 4; dy++) for (int dx = -raio * 4; dx <= raio * 4; dx++) {
            int c = pixel(img, x + dx, y + dy);
            if (r(c) - g(c) > r(melhor) - g(melhor)) melhor = c;
        }
        return melhor;
    }

    /** Pontos de estrela: pixels da metade de cima mais claros que os 8 vizinhos por 10/255. */
    private static int picos(int[] img) {
        int n = 0;
        for (int y = 2; y < 340; y++) for (int x = 2; x < 1278; x++) {
            int c = brilho(pixel(img, x, y));
            boolean pico = true;
            for (int dy = -2; dy <= 2 && pico; dy += 2) for (int dx = -2; dx <= 2 && pico; dx += 2)
                if ((dx != 0 || dy != 0) && brilho(pixel(img, x + dx, y + dy)) > c - 10) pico = false;
            if (pico) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------ fundos

    /** Instância de um efeito de teste na posição do jogador; fechar para o efeito (try-with-resources). */
    private AutoCloseable iniciarRaw(ResourceLocation id) throws Exception {
        Optional<Effects.Handle> handle = noServidor(() -> Effects.start(id, jogador().serverLevel(), jogador().position()));
        if (handle.isEmpty()) throw new IllegalStateException("efeito não começou: " + id);
        // Efeitos de mais de 1200 ticks entram em 168 ticks (FrameBudget.envelope): espera a opacidade inteira.
        ticks(180);
        return () -> {
            noServidor(() -> { handle.get().stop(); return null; });
            ticks(10);
        };
    }

    private interface Fundo extends AutoCloseable { @Override void close() throws Exception; }

    private Fundo iniciar(ResourceLocation id) throws Exception {
        AutoCloseable c = iniciarRaw(id);
        return c::close;
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
    private static int canal(double v) { return (int) Math.round(Math.clamp(v, 0, 255)); }
    private static double suave(double a, double b, double x) { double t = Math.clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }

    private static int difMax(int a, int c) { return Math.max(Math.abs(r(a) - r(c)), Math.max(Math.abs(g(a) - g(c)), Math.abs(b(a) - b(c)))); }

    private static boolean perto(int a, int c, int tol) { return difMax(a, c) <= tol; }

    private static int mistura(int a, int c, float t) {
        return Math.round(r(a) + (r(c) - r(a)) * t) << 16 | Math.round(g(a) + (g(c) - g(a)) * t) << 8 | Math.round(b(a) + (b(c) - b(a)) * t);
    }

    /** t em [0, 1] que põe mistura(a, b, t) mais perto de c (mínimos quadrados por canal). */
    private static double melhorT(int a, int b, int c) {
        double num = 0, den = 0;
        for (int s : new int[] {16, 8, 0}) {
            double da = (b >> s & 255) - (a >> s & 255), dc = (c >> s & 255) - (a >> s & 255);
            num += da * dc; den += da * da;
        }
        return den == 0 ? 0 : Math.clamp(num / den, 0, 1);
    }

    /**
     * Quanto a faixa de névoa do horizonte (colunas 400 a 880) é mais clara que o fundo 8 linhas acima dela: para cada
     * coluna, acha o horizonte (primeira linha, de cima para baixo a partir da 300, que difere do céu da linha 300) e
     * compara as 3 linhas a partir dele com o céu; devolve a mediana entre as colunas (o maior excesso de brilho).
     */
    private static int linhaClara(int[] img) {
        int[] excessos = new int[481];
        for (int x = 400; x <= 880; x++) {
            int ceu = pixel(img, x, 300), h = -1;
            for (int y = 301; y < 460 && h < 0; y++) if (difMax(pixel(img, x, y), ceu) > 6) h = y;
            if (h < 0) continue;
            int acima = pixel(img, x, Math.max(300, h - 8)), pior = 0;
            for (int y = h; y < h + 3; y++) pior = Math.max(pior, brilho(pixel(img, x, y)) - brilho(acima));
            excessos[x - 400] = pior;
        }
        Arrays.sort(excessos);
        return excessos[excessos.length / 2];
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

    /** Muda a hora no servidor e espera o cliente recebê-la. */
    private void hora(long tempo) throws Exception {
        noServidor(() -> overworld().setDayTime(tempo));
        boolean chegou = aguardar(() -> noClienteSemErro(() -> mc.level != null && mc.level.getDayTime() % 24000 == tempo % 24000), 200);
        if (!chegou) info("a hora " + tempo + " não chegou ao cliente em 200 ticks");
        ticks(5);
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
