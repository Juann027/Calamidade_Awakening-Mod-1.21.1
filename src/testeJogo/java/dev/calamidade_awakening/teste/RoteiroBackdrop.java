package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.FrameStats;
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
 * Teste em jogo do Backdrop (0.14, fase 2), com {@code calamidade.teste=backdrop}, no cliente (renderização por software)
 * com o vfx-sample em {@code mods/}. Usa os fundos registrados pelo mod de teste ({@link CalamidadeTeste}, texturas de
 * {@code scripts/gerar_fundos_teste.py}) pela API real e confere, com sondas de pixel nas capturas: ordem e
 * profundidade; nada sem addon; orientação do cubemap e giro; fundo parcial; gradiente; sol e lua cobertos ou
 * redesenhados; eclipse; Atmosphere sobre o fundo; lente; céu bloqueado (cegueira, escuridão, lava, neve em pó);
 * horizonte com e sem a névoa casada; transição entre fundos de um perfil; opção customSkies; recarga de recursos;
 * estado de renderização; custo; regressão do céu do Overworld. Relatório em {@code calamidade-backdrop.txt};
 * capturas em {@code screenshots/}.
 */
final class RoteiroBackdrop implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-backdrop.txt");
    private static boolean iniciado;
    private static final int X = -9000, Z = 0;
    private static final int TOL = 4;
    /** Cores das faces de cubo.png (scripts/gerar_fundos_teste.py): norte, leste, sul, oeste, cima, baixo. */
    static final int[] FACES = {0xC83232, 0x32B432, 0x3C50E6, 0xDCC828, 0xC832C8, 0x28C8C8};
    static final int GRADE = 0x101010, MEIA = 0x20E090;
    /** Faixas de faixas.png: norte, leste, sul, oeste. */
    static final int[] FAIXAS = {0xE06020, 0x2060E0, 0x60E020, 0xE020A0};

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        SondasDoQuadro.registrar();
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
            Thread thread = new Thread(new RoteiroBackdrop(), "Calamidade teste do Backdrop");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste do Backdrop (0.14, fase 2), " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            int[] overworldAntes = fotoOverworld("backdrop-overworld-antes");
            semAddon();
            ordemEProfundidade();
            orientacao();
            giro();
            parcial();
            gradiente();
            solELua();
            atmosfera();
            lente();
            eclipse();
            bloqueios();
            horizonte();
            estadoDeRenderizacao();
            opcaoDoJogador();
            recarga();
            custo();
            transicao();
            regressaoEntreDimensoes(overworldAntes);
            escrever("FIM backdrop", true);
        } catch (Throwable failure) {
            check("roteiro do Backdrop sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
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
            return null;
        });
        esperarTerreno();
    }

    /** Sem addon pedindo, nada: nenhum fundo, nenhuma textura, nenhum desenho. */
    private void semAddon() throws Exception {
        int desenhos = noCliente(ClientHooks::backdropDraws);
        int[] texturas = noCliente(ClientHooks::backdropTextures);
        ticks(20);
        check("sem addon: nenhum fundo desenhado nem textura carregada", noCliente(ClientHooks::backdrops) == 0
            && noCliente(ClientHooks::backdropDraws) == desenhos && texturas[0] == 0 && texturas[1] == 0,
            "desenhos " + desenhos + ", texturas " + Arrays.toString(texturas));
    }

    private void ordemEProfundidade() throws Exception {
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            olhar(0, -80);
            ticks(10);
            long[] seq = noCliente(() -> new long[] {SondasDoQuadro.seqCeu, SondasDoQuadro.seqNivel, SondasDoQuadro.seqRimu});
            check("ordem no quadro: AFTER_SKY (fundo), depois a camada da RimuLib, depois AFTER_LEVEL",
                seq[0] < seq[2] && seq[2] < seq[1], Arrays.toString(seq));
            check("fundo desenhado no framebuffer principal", SondasDoQuadro.fboCeu == SondasDoQuadro.fboPrincipal
                && SondasDoQuadro.fboCeu != 0, SondasDoQuadro.fboCeu + " / " + SondasDoQuadro.fboPrincipal);
            check("profundidade continua 1 depois do fundo, em AFTER_SKY e em AFTER_LEVEL (os passes o veem como céu)",
                SondasDoQuadro.profundidadeDepois == 1f && SondasDoQuadro.profundidadeNivel == 1f,
                SondasDoQuadro.profundidadeDepois + " / " + SondasDoQuadro.profundidadeNivel);
        }
    }

    /** Cada face do cubemap na direção certa e nenhum passe de pós só por causa do fundo. */
    private void orientacao() throws Exception {
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            String[] nomes = {"norte", "leste", "sul", "oeste", "cima"};
            float[][] vistas = {{180, -10}, {-90, -10}, {0, -10}, {90, -10}, {0, -90}};
            StringBuilder detalhe = new StringBuilder();
            boolean ok = true;
            for (int f = 0; f < 5; f++) {
                olhar(vistas[f][0], vistas[f][1]);
                int c = pixel(foto(f == 2 ? "backdrop-cubo-sul" : null), 640, 300);
                ok &= perto(c, FACES[f], TOL);
                detalhe.append(nomes[f]).append(' ').append(rgb(c)).append(' ');
            }
            check("cubemap: norte, leste, sul, oeste e cima na direção do layout documentado", ok, detalhe.toString().trim());
            check("fundo estático não liga passe de pós (sky e atmosphere desligados)", noCliente(() -> !PerfOverlay.skyPass()
                && !PerfOverlay.atmospherePass() && ClientHooks.backdrops() == 1), "sky " + PerfOverlay.skyPass()
                + ", atmosphere " + PerfOverlay.atmospherePass());
        }
    }

    /** rotation(90): o que era sul aparece no oeste. */
    private void giro() throws Exception {
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_GIRADO)) {
            olhar(90, -10);
            int c = pixel(foto(null), 640, 360);
            check("giro de 90 graus leva o sul para o oeste", perto(c, FACES[2], TOL), rgb(c));
        }
    }

    private void parcial() throws Exception {
        olhar(0, -30);
        int v = pixel(foto(null), 640, 200);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_MEIA)) {
            int m = pixel(foto("backdrop-parcial"), 640, 200), esperado = mistura(v, MEIA, 128 / 255f);
            check("panorama com alfa 0,5 mistura com o céu do jogo", perto(m, esperado, TOL),
                "jogo " + rgb(v) + ", com fundo " + rgb(m) + ", esperado " + rgb(esperado));
        }
    }

    private void gradiente() throws Exception {
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_GRADIENTE)) {
            olhar(0, -90);
            int topo = pixel(foto("backdrop-gradiente"), 640, 360);
            check("gradiente: zênite na cor do topo", perto(topo, CalamidadeTeste.GRAD_TOPO & 0xFFFFFF, TOL), rgb(topo));
        }
    }

    /** Meio-dia e meia-noite olhando o zênite: sol e lua cobertos pelo fundo opaco, ou redesenhados por cima dele. */
    private void solELua() throws Exception {
        for (long hora : new long[] {6000, 18000}) {
            String corpo = hora == 6000 ? "sol" : "lua";
            hora(hora);
            olhar(0, -90);
            int coberto, redesenhado;
            float alfa;
            try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
                coberto = pixel(foto("backdrop-" + corpo + "-coberto"), 640, 360);
            }
            try (var fundo = iniciar(CalamidadeTeste.FUNDO_SOL)) {
                redesenhado = pixel(foto("backdrop-" + corpo + "-redesenhado"), 640, 360);
                alfa = noCliente(ClientHooks::backdropRedrawnBodies)[hora == 6000 ? 0 : 1];
            }
            check(corpo + " do jogo coberto pelo fundo opaco (centro = face de cima)", perto(coberto, FACES[4], TOL), rgb(coberto));
            check(corpo + " do jogo redesenhado sobre o fundo com vanillaSunMoon(true)", brilho(redesenhado) > brilho(FACES[4]) + 30
                && alfa > .9f, rgb(redesenhado) + ", alfa " + String.format(Locale.ROOT, "%.2f", alfa));
        }
        hora(6000);
    }

    /** Atmosphere com gradiente sobre o fundo opaco: mix(fundo, gradiente, 0,7), como sobre o céu do jogo. */
    private void atmosfera() throws Exception {
        olhar(0, -30);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_ATMO)) {
            ticks(20);
            int c = pixel(foto("backdrop-atmosfera"), 640, 360);
            // Centro: direção com altura sin(30°) = 0,5.
            double k = suave(0, .6, .5);
            int topo = CalamidadeTeste.ATMO_TOPO, horiz = CalamidadeTeste.ATMO_HORIZONTE, face = FACES[2];
            int grad = canal(r(horiz) + (r(topo) - r(horiz)) * k) << 16 | canal(g(horiz) + (g(topo) - g(horiz)) * k) << 8
                | canal(b(horiz) + (b(topo) - b(horiz)) * k);
            int esperado = mistura(face, grad, CalamidadeTeste.ATMO_MISTURA);
            check("Atmosphere tinge o fundo opaco como tingiria o céu do jogo", perto(c, esperado, TOL + 2) && noCliente(PerfOverlay::atmospherePass),
                "medido " + rgb(c) + ", esperado " + rgb(esperado) + " (o protótipo da fase 1 dava quase preto)");
        }
    }

    /** Buraco negro (CEU_UM) sobre o cubemap com grade: a lente entorta o fundo. */
    private void lente() throws Exception {
        hora(13500);
        olhar(0, -18);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_GRADE)) {
            int[] semBuraco = foto("backdrop-grade");
            int[] comBuraco;
            try (var buraco = iniciar(CalamidadeTeste.CEU_UM)) {
                ticks(60);
                comBuraco = foto("backdrop-grade-buraco-negro");
            }
            int mudou = 0, total = 0;
            for (int y = 0; y < 400; y += 2) for (int x = 0; x < 1280; x += 2) {
                boolean a = perto(pixel(semBuraco, x, y), GRADE, 12), b = perto(pixel(comBuraco, x, y), GRADE, 12);
                if (a || b) { total++; if (a != b) mudou++; }
            }
            check("lente gravitacional entorta o fundo", mudou > total / 20, mudou + " de " + total + " pixels de grade mudaram");
        }
        hora(6000);
    }

    /** Eclipse global do exemplo com o fundo que redesenha o sol: escurece o fundo e impede o redesenho do sol. */
    private void eclipse() throws Exception {
        hora(2000);
        olhar(-90, -38);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_SOL)) {
            ticks(10);
            int antes = pixel(foto(null), 640 + 400, 360 - 250);
            float solAntes = noCliente(ClientHooks::backdropRedrawnBodies)[0];
            comando("vfxsample evento eclipse start");
            aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
            ticks(430);
            int[] img = foto("backdrop-eclipse");
            float solDurante = noCliente(ClientHooks::backdropRedrawnBodies)[0];
            int depois = pixel(img, 640 + 400, 360 - 250);
            comando("vfxsample evento eclipse stop");
            aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 0), 100);
            check("eclipse: o sol redesenhado some sob a camada que o esconde", solAntes > .9f && solDurante < .05f,
                String.format(Locale.ROOT, "alfa do sol %.2f → %.2f", solAntes, solDurante));
            check("eclipse escurece o fundo longe do disco", brilho(depois) < brilho(antes) * .7 && brilho(depois) > 0,
                rgb(antes) + " → " + rgb(depois));
        }
        ticks(60);
        hora(6000);
    }

    /** Cegueira, escuridão, lava e neve em pó: o jogo não desenha céu, o fundo também não. */
    private void bloqueios() throws Exception {
        olhar(0, -40);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            ticks(5);
            check("controle: fundo visível antes dos bloqueios", perto(pixel(foto(null), 640, 360), FACES[2], TOL), "");
            for (var efeito : java.util.List.of(MobEffects.BLINDNESS, MobEffects.DARKNESS)) {
                noServidor(() -> jogador().addEffect(new MobEffectInstance(efeito, 400, 0, false, false)));
                ticks(20);
                int c = pixel(foto(null), 640, 360);
                boolean bloqueado = noCliente(ClientHooks::backdropBlocked);
                noServidor(() -> jogador().removeEffect(efeito));
                check("sem fundo com " + efeito.getRegisteredName(), bloqueado && !perto(c, FACES[2], 12), rgb(c));
            }
            BlockPos olho = noServidor(() -> BlockPos.containing(jogador().getEyePosition()));
            for (BlockState bloco : new BlockState[] {Blocks.LAVA.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState()}) {
                noServidor(() -> overworld().setBlockAndUpdate(olho, bloco));
                ticks(20);
                int c = pixel(foto(null), 640, 360);
                boolean bloqueado = noCliente(ClientHooks::backdropBlocked);
                noServidor(() -> overworld().setBlockAndUpdate(olho, Blocks.AIR.defaultBlockState()));
                check("sem fundo com a câmera em " + bloco.getBlock().getName().getString(), bloqueado, rgb(c));
            }
            ticks(20);
            check("fundo volta depois dos bloqueios", perto(pixel(foto(null), 640, 360), FACES[2], TOL)
                && !noCliente(ClientHooks::backdropBlocked), "");
        }
    }

    /**
     * Horizonte: no mundo plano, o terreno distante some na névoa numa faixa logo abaixo do horizonte. Com a névoa casada
     * (padrão) a faixa toma a cor do fundo logo acima do horizonte; sem ela (matchFog(false)) a faixa fica na cor de
     * névoa do jogo, mais clara que o fundo: a "linha clara" do experimento da fase 1. Mede-se o quanto a faixa é mais
     * clara que o fundo logo acima dela.
     */
    private void horizonte() throws Exception {
        olhar(0, 0);
        int[] casada, crua;
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) { casada = foto("backdrop-horizonte-casado"); }
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_SEM_NEVOA)) { crua = foto("backdrop-horizonte-sem-nevoa"); }
        int linhaCasada = linhaClara(casada), linhaCrua = linhaClara(crua);
        info("horizonte, coluna 640: casada " + coluna(casada, 640, 340, 380, 2) + " | sem névoa casada " + coluna(crua, 640, 340, 380, 2));
        check("sem linha clara no horizonte com a névoa casada", linhaCasada <= 12 && linhaCrua > 30,
            "faixa do horizonte mais clara que o fundo acima: casada " + linhaCasada + ", sem névoa casada " + linhaCrua + " (/255)");
    }

    /** Terreno igual com e sem fundo (sem névoa casada): profundidade, culling e mistura devolvidos ao jogo. */
    private void estadoDeRenderizacao() throws Exception {
        olhar(0, 30);
        int[] sem = foto(null);
        int[] com;
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_SEM_NEVOA)) { com = foto("backdrop-estado"); }
        long soma = 0;
        int pior = 0, n = 0;
        for (int y = 420; y < 720; y += 3) for (int x = 0; x < 1280; x += 3) {
            int d = difMax(pixel(sem, x, y), pixel(com, x, y));
            soma += d; pior = Math.max(pior, d); n++;
        }
        check("estado de renderização restaurado: terreno igual com e sem fundo", soma / (double) n < 1 && pior <= 8,
            String.format(Locale.ROOT, "diferença média %.2f, pior %d", soma / (double) n, pior));
    }

    private void opcaoDoJogador() throws Exception {
        olhar(0, -40);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            noCliente(() -> { ClientConfig.customSkies(false); return null; });
            ticks(5);
            int c = pixel(foto(null), 640, 360);
            int n = noCliente(ClientHooks::backdrops);
            noCliente(() -> { ClientConfig.customSkies(true); return null; });
            ticks(5);
            check("opção customSkies desligada: nenhum fundo", n == 0 && !perto(c, FACES[2], 12), rgb(c));
            check("opção customSkies ligada de novo: fundo volta", perto(pixel(foto(null), 640, 360), FACES[2], TOL), "");
        }
    }

    /** Reuso (a mesma textura não é carregada de novo) e recarga de recursos (liberada e carregada outra vez). */
    private void recarga() throws Exception {
        olhar(0, -40);
        int[] antes = noCliente(ClientHooks::backdropTextures);
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) { ticks(5); }
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_GIRADO)) { ticks(5); }
        int[] reuso = noCliente(ClientHooks::backdropTextures);
        check("textura reaproveitada entre fundos e instâncias", reuso[0] == antes[0], Arrays.toString(antes) + " → " + Arrays.toString(reuso));
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) {
            ticks(5);
            int[] antesDaRecarga = noCliente(ClientHooks::backdropTextures);
            var recarregar = noCliente(mc::reloadResourcePacks);
            // O servidor integrado pausa durante a recarga: espera pelo relógio, não pelos ticks.
            for (int i = 0; i < 1200 && !recarregar.isDone(); i++) Thread.sleep(50);
            Thread.sleep(500);
            int[] logo = noCliente(ClientHooks::backdropTextures);
            info("recarga: texturas " + Arrays.toString(antesDaRecarga) + " antes, " + Arrays.toString(logo) + " logo depois"
                + (recarregar.isCompletedExceptionally() ? " (recarga terminou com erro)" : ""));
            ticks(20);
            int c = pixel(foto(null), 640, 360);
            int[] depois = noCliente(ClientHooks::backdropTextures);
            // Liberou as texturas guardadas (menos entradas) e carregou de novo a que está em uso (mais uma carga).
            check("recarga de recursos libera as texturas e carrega de novo a que está em uso; o fundo continua certo",
                recarregar.isDone() && logo[1] < antesDaRecarga[1] && depois[0] > antesDaRecarga[0] && depois[1] >= 1
                    && perto(c, FACES[2], TOL),
                Arrays.toString(antesDaRecarga) + " → " + Arrays.toString(logo) + " → " + Arrays.toString(depois) + ", centro " + rgb(c));
        }
    }

    private void custo() throws Exception {
        hora(6000);
        olhar(0, -20);
        ticks(40);
        double[] sem = janela(5000);
        double[] cubo, esfera;
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_CUBO)) { ticks(20); cubo = janela(5000); }
        try (var fundo = iniciar(CalamidadeTeste.FUNDO_FAIXAS)) { ticks(20); esfera = janela(5000); }
        info(String.format(Locale.ROOT, "custo por software (tendência, não GPU física): sem fundo mediana %.2f ms p95 %.2f (%d quadros); "
                + "cubemap %.2f ms p95 %.2f (%d); panorama %.2f ms p95 %.2f (%d)",
            med(sem), p95(sem), sem.length, med(cubo), p95(cubo), cubo.length, med(esfera), p95(esfera), esfera.length));
    }

    /**
     * Transição entre dois fundos do perfil do End: nunca aparece o céu do jogo no meio. Perto da ilha central, a barra
     * de chefe do dragão cria névoa de mundo e o jogo não desenha o céu; o fundo segue a mesma regra (conferido primeiro).
     * A transição é medida longe dela.
     */
    private void transicao() throws Exception {
        comando("execute in minecraft:the_end run tp @s 0.5 110 0.5 0 -30");
        aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().getPath().equals("the_end")), 400);
        esperarTerreno();
        noServidor(() -> SkyProfiles.setState(server(), CalamidadeTeste.END, "fundo_a", 0));
        ticks(40);
        boolean nevoaDoChefe = noCliente(() -> mc.gui.getBossOverlay().shouldCreateWorldFog());
        boolean bloqueadoNaIlha = noCliente(ClientHooks::backdropBlocked);
        check("End, ilha central: com a névoa da barra do dragão, o fundo segue o céu do jogo e não aparece",
            !nevoaDoChefe || (bloqueadoNaIlha && noCliente(ClientHooks::backdrops) == 0),
            "névoa do chefe " + nevoaDoChefe + ", bloqueado " + bloqueadoNaIlha);
        noServidor(() -> SkyProfiles.setState(server(), CalamidadeTeste.END, "a", 0));
        comando("execute in minecraft:the_end run tp @s 3000.5 120 0.5 0 -30");
        esperarTerreno();
        ticks(40);
        olhar(0, -30);
        int vanilla = pixel(foto("backdrop-end-vanilla"), 640, 360);
        noServidor(() -> SkyProfiles.setState(server(), CalamidadeTeste.END, "fundo_a", 0));
        ticks(10);
        int a = pixel(foto("backdrop-end-a"), 640, 360);
        noServidor(() -> SkyProfiles.setState(server(), CalamidadeTeste.END, "fundo_b", 100));
        ticks(50);
        int meio = pixel(foto("backdrop-end-meio"), 640, 360);
        int desenhados = noCliente(ClientHooks::backdrops);
        ticks(80);
        int b = pixel(foto("backdrop-end-b"), 640, 360);
        noServidor(() -> SkyProfiles.setState(server(), CalamidadeTeste.END, "a", 0));
        check("perfil: fundo A no estado fundo_a (sul do cubemap)", perto(a, FACES[2], TOL), rgb(a));
        check("perfil: fundo B no estado fundo_b (faixa do sul do panorama)", perto(b, FAIXAS[2], TOL), rgb(b));
        // No meio, a tela é a mistura de A e B (dois fundos desenhados), sem o céu do End entrando.
        double t = melhorT(a, b, meio);
        int esperado = mistura(a, b, (float) t);
        check("transição: dois fundos no meio, mistura de A e B sem o céu do jogo", desenhados == 2 && t > .1 && t < .9
            && perto(meio, esperado, TOL + 2), "meio " + rgb(meio) + " ≈ A→B em t = " + String.format(Locale.ROOT, "%.2f", t)
            + " (" + rgb(esperado) + "); End sem fundo " + rgb(vanilla) + "; desenhados " + desenhados
            + ", bloqueado " + noClienteSemErro(ClientHooks::backdropBlocked));
        comando("execute in minecraft:overworld run tp @s " + (X + .5) + " 3 " + (Z + .5) + " 0 -25");
        aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().getPath().equals("overworld")), 400);
        esperarTerreno();
    }

    /** O céu do Overworld, com a hora parada, igual no começo e no fim (depois de mexer nos fundos e no End). */
    private void regressaoEntreDimensoes(int[] antes) throws Exception {
        int[] depois = fotoOverworld("backdrop-overworld-depois");
        long soma = 0;
        int pior = 0, n = 0;
        for (int y = 0; y < 720; y += 3) for (int x = 0; x < 1280; x += 3) {
            int d = difMax(pixel(antes, x, y), pixel(depois, x, y));
            soma += d; pior = Math.max(pior, d); n++;
        }
        check("regressão: céu do Overworld igual antes e depois dos fundos e da transição no End",
            soma / (double) n < 1 && noCliente(ClientHooks::backdrops) == 0,
            String.format(Locale.ROOT, "diferença média %.2f, pior %d", soma / (double) n, pior));
    }

    private int[] fotoOverworld(String nome) throws Exception {
        hora(6000);
        olhar(30, -20);
        ticks(20);
        return foto(nome);
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
