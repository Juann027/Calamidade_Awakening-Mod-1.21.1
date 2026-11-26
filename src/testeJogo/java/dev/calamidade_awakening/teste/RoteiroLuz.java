package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.QualityMode;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 0.15.0: laboratório de luz em jogo e benchmark comparável com a 0.14.0 (xvfb-run -a ./gradlew testeLuz). A parte de
 * medição usa só o que existe nas duas versões (bench do motor, receitas do vfx-sample, janela de quadros do F7, perfil
 * forçado), por isso o mesmo arquivo roda numa cópia da 0.14.0 para a linha de base; os extras da 0.15 (luzes com
 * cookie, detalhes do passe, memória do atlas) entram por reflexão quando existem. Tabela em
 * {@code calamidade-luz.txt} (linhas MEDIDA) e capturas em {@code screenshots/}. Por software (Mesa llvmpipe), vale a
 * tendência e a comparação entre versões no mesmo ambiente, não o valor absoluto.
 */
final class RoteiroLuz implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-luz.txt");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private final boolean v015 = existe("dev.calamidade_awakening.api.CookieAtlas");
    private int passou, falhou;
    private Thread render;
    private boolean rapido;

    static void registrar() {
        // A dimensão do vfx-sample (datapack) faz o jogo perguntar sobre "ajustes experimentais": "Sei o que estou fazendo!".
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
            Thread thread = new Thread(new RoteiroLuz(), "Calamidade teste de luz");
            thread.setDaemon(true);
            thread.start();
        });
    }

    private static boolean existe(String classe) {
        try {
            Class.forName(classe);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: laboratório de luz e benchmark, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> com.mojang.blaze3d.systems.RenderSystem.getBackendDescription() + " · "
                + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER) + " · "
                + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()), true);
            escrever("# motor " + net.neoforged.fml.ModList.get().getModContainerById("calamidade_awakening")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"), true);
            render = noCliente(Thread::currentThread);
            preparar();
            if ("fase2".equals(System.getProperty("calamidade.luz.modo"))) {
                fase2();
                escrever("FIM luz", true);
                return;
            }
            if ("bench".equals(System.getProperty("calamidade.luz.modo"))) {
                // Metodologia de benchmark (0.15 auditoria): sem funcional nem capturas; veja benchmarkRigoroso().
                benchmarkRigoroso();
                escrever("FIM luz", true);
                return;
            }
            if (Files.exists(Path.of("rapido.txt"))) {
                // A/B rápido: só cenários comuns às duas versões, BALANCED e HIGH (sem funcional nem capturas).
                rapido = true;
                benchmark();
                escrever("FIM luz", true);
                return;
            }
            if (v015) funcional();
            benchmark();
            if (v015) capturas();
            escrever("FIM luz", true);
        } catch (Throwable failure) {
            check("roteiro de luz sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] luz: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }
    private ServerLevel overworld() { return server().overworld(); }
    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private static final int X = -16000, Z = 0;

    private void preparar() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server());
            overworld().setDayTime(18000);
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = false;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, 25);
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; return null; });
        ticks(20);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(40);
    }

    // ------------------------------------------------------------------------------------------------ funcional

    @SuppressWarnings("unchecked")
    private int[] detalhe() throws Exception {
        return noCliente(() -> {
            try {
                return (int[]) PerfOverlay.class.getMethod("lightDetail").invoke(null);
            } catch (ReflectiveOperationException e) {
                return new int[10];
            }
        });
    }

    private int[] detalheSE() {
        try { return detalhe(); } catch (Exception e) { return new int[10]; }
    }

    private long atlasBytes() throws Exception {
        return noCliente(() -> {
            try {
                return (long) PerfOverlay.class.getMethod("cookieBytes").invoke(null);
            } catch (ReflectiveOperationException e) {
                return 0L;
            }
        });
    }

    private void funcional() throws Exception {
        perfil(QualityMode.HIGH);
        ticks(20);
        check("cookies: nenhum atlas antes do primeiro cookie (sem custo de memória)", atlasBytes() == 0, atlasBytes() + " bytes");
        comando("calamidade bench 1 vfxsample:luz_runas");
        boolean pronto = aguardar(() -> detalheSE()[6] >= 1 && detalheSE()[3] >= 1, 200);
        int[] d = detalhe();
        check("cookies: a imagem carrega uma vez, o atlas existe e a luz é desenhada com textura", pronto && atlasBytes() > 0 && d[8] == 0,
            Arrays.toString(d) + " · " + atlasBytes() + " bytes");
        int uploads = d[9];
        ticks(100);
        check("cookies: nenhum upload repetido por quadro (o mesmo PNG continua no atlas)", detalhe()[9] == uploads,
            uploads + " -> " + detalhe()[9]);
        comando("calamidade bench stop");
        // Imagem que não existe: registrada, falha uma vez no log, sem travar; a luz usa o fallback.
        comando("calamidade bench 1 calamidade_teste:cookie_ausente");
        boolean falhou = aguardar(() -> detalheSE()[8] >= 1, 200);
        ticks(20);
        check("cookies: PNG ausente conta como falha, sem crash, e a luz segue com o fallback", falhou && detalhe()[4] >= 1,
            Arrays.toString(detalhe()));
        comando("calamidade bench stop");
        // Orçamento: no LOW só 2 luzes com cookie por quadro amostram a imagem; as outras usam o fallback.
        perfil(QualityMode.LOW);
        comando("calamidade bench 2 vfxsample:luz_estresse");
        aguardar(() -> detalheSE()[0] > 0, 100);
        ticks(60);
        int[] low = detalhe();
        check("orçamento LOW: no máximo 2 cookies amostrados, excedentes em fallback, luzes até o teto de 8",
            low[3] <= 2 && low[5] == 2 && (low[4] > 0 || low[2] > 0), Arrays.toString(low));
        comando("calamidade bench stop");
        perfil(QualityMode.HIGH);
        // Culling: um projetor atrás da câmera não ocupa vaga.
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 180, 25));
        comando("calamidade bench 1 vfxsample:luz_runas");
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, 25));
        ticks(30);
        int[] atras = detalhe();
        check("culling: luz fora do frustum é descartada antes do teto", atras[1] >= 1 && atras[3] == 0, Arrays.toString(atras));
        comando("calamidade bench stop");
        // Recarregar recursos solta o atlas; ele volta no próximo uso.
        noCliente(() -> { mc.reloadResourcePacks(); return null; });
        aguardar(() -> noClienteSemErro(() -> mc.getOverlay() == null), 2400);
        ticks(20);
        check("recarga de recursos: atlas liberado", atlasBytes() == 0, atlasBytes() + " bytes");
    }

    // ------------------------------------------------------------------------------------------------ benchmark

    record Cenario(String nome, List<String> comandos, boolean novo) {}

    // ------------------------------------------------------------------------------------------------ fase 2

    /**
     * Fase 2 (composição e nitidez): o atlas grande só existe com um cookie de alta resolução, respeita o perfil (LOW
     * nenhum, BALANCED 1024, HIGH 2048) e cai para a versão de 512; nada é reenviado por quadro; recarga libera tudo;
     * capturas comparativas das demos novas de noite e de dia.
     */
    private void fase2() throws Exception {
        long padrao = 22369536L;
        perfil(QualityMode.HIGH);
        comando("vfxsample luz runas");
        aguardar(() -> detalheSE()[3] >= 1, 300);
        ticks(20);
        check("fase 2: cookie padrão não cria o atlas grande", atlasBytes() == padrao, atlasBytes() + " bytes");
        comando("vfxsample luz parar");
        comando("vfxsample luz resolucoes");
        boolean grande = aguardar(() -> detalheSE()[10] == 1 && detalheSE()[12] == 1 && detalheSE()[3] == 2, 600);
        int[] d = detalhe();
        long bytesAlta = atlasBytes();
        check("fase 2 HIGH: a luz ULTRA usa a versão de 2048 do atlas grande e a STANDARD a de 512; memória = padrão + grande", grande && bytesAlta > padrao * 2 - 1000,
            Arrays.toString(d) + " · " + bytesAlta + " bytes");
        int uploads = detalhe()[9];
        ticks(100);
        check("fase 2: nenhum upload repetido com as imagens grandes prontas", detalhe()[9] == uploads, uploads + " -> " + detalhe()[9]);
        captura("f2_resolucoes_high");
        perfil(QualityMode.BALANCED);
        boolean balanced = aguardar(() -> detalheSE()[10] == 1 && detalheSE()[12] == 1, 600);
        check("fase 2 BALANCED: o ULTRA é refeito em 1024 e amostrado", balanced, Arrays.toString(detalhe()) + " · " + atlasBytes() + " bytes");
        perfil(QualityMode.LOW);
        ticks(30);
        int[] low = detalhe();
        check("fase 2 LOW: atlas grande solto, cookies na versão de 512", atlasBytes() == padrao && low[12] == 0 && low[3] >= 1,
            Arrays.toString(low) + " · " + atlasBytes() + " bytes");
        perfil(QualityMode.HIGH);
        comando("vfxsample luz parar");
        // Capturas: noite e dia.
        String[][] noite = {{"modos", "0"}, {"neon", "0"}, {"filtros", "0"}, {"resolucoes", "0"}, {"transicao", "0"},
            {"superficies", "0"}, {"sobrepostas", "0"}, {"ataque", "0"}};
        for (String[] demo : noite) demo(demo[0], "f2_" + demo[0] + "_noite", 18000, demo[0].equals("resolucoes"));
        String[] dia = {"modos", "forcas", "dia", "ataque", "sobrepostas"};
        for (String demo : dia) demo(demo, "f2_" + demo + "_dia", 6000, false);
        noServidor(() -> overworld().setDayTime(18000));
        noCliente(() -> { mc.reloadResourcePacks(); return null; });
        aguardar(() -> noClienteSemErro(() -> mc.getOverlay() == null), 2400);
        ticks(20);
        check("fase 2: recarga de recursos solta os dois atlas", atlasBytes() == 0, atlasBytes() + " bytes");
    }

    private void demo(String nome, String captura, int hora, boolean perto) throws Exception {
        noServidor(() -> {
            overworld().setDayTime(hora);
            jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, perto ? 50 : 32);
        });
        ticks(5);
        comando("vfxsample luz " + nome);
        // Vista de cima, para os dois lados da comparação caberem e o detalhe aparecer.
        if (perto) noServidor(() -> jogador().teleportTo(overworld(), X + .5, 10, Z + 7.5, 0, 89));
        ticks(nome.equals("resolucoes") ? 160 : 60);
        captura(captura);
        int[] d = detalhe();
        check("fase 2 demo " + nome + (hora == 6000 ? " (dia)" : " (noite)") + ": desenhada, sem cookie com falha",
            campo("lastLights") > 0 && d[8] == 0, Arrays.toString(d));
        comando("vfxsample luz parar");
        if (nome.equals("superficies")) {
            for (String b : new String[] {"stone_bricks", "stone_brick_stairs", "moss_block"})
                comando("fill ~-8 ~ ~-8 ~8 ~6 ~16 air replace minecraft:" + b);
            comando("kill @e[type=minecraft:armor_stand]");
        }
        ticks(10);
    }

    // ------------------------------------------------------------------------------------------------ benchmark rigoroso

    /**
     * Um quadro gravado: tempo (ms, de um RenderFrameEvent.Pre ao seguinte, tempo de parede do quadro inteiro) e a carga
     * real daquele quadro: efeitos desenhados, luzes desenhadas, passes, cookies amostrados e fallbacks.
     */
    record Quadro(double ms, int efeitos, int luzes, int passes, int cookies, int fallback) {}

    private static final List<Quadro> GRAVANDO = java.util.Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean gravar;
    private static long ultimoQuadro;
    private static java.lang.reflect.Field fEfeitos, fLuzes, fPasses;
    private static java.lang.reflect.Method mDetalhe;

    /** Ouvinte por quadro (registrado só no modo bench): grava tempo e carga do quadro anterior. */
    static void ouvirQuadros() {
        try {
            fEfeitos = PerfOverlay.class.getDeclaredField("lastEffects");
            fLuzes = PerfOverlay.class.getDeclaredField("lastLights");
            fPasses = PerfOverlay.class.getDeclaredField("lastPasses");
            for (var f : new java.lang.reflect.Field[] {fEfeitos, fLuzes, fPasses}) f.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try { mDetalhe = PerfOverlay.class.getMethod("lightDetail"); } catch (NoSuchMethodException e) { mDetalhe = null; }
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) -> {
            long agora = System.nanoTime();
            if (gravar && ultimoQuadro != 0) {
                try {
                    // Os campos "last" do PerfOverlay mudam uma vez por quadro, no início dele; conforme a ordem dos
                    // ouvintes, a carga lida é a do quadro medido ou a do anterior (defasagem de 1 quadro, irrelevante
                    // com carga estável; a faixa min-max da janela mostra se ela não foi estável).
                    int[] d = mDetalhe == null ? new int[10] : (int[]) mDetalhe.invoke(null);
                    GRAVANDO.add(new Quadro((agora - ultimoQuadro) / 1e6, fEfeitos.getInt(null), fLuzes.getInt(null),
                        fPasses.getInt(null), d[3], d[4]));
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            ultimoQuadro = agora;
        });
    }

    private static int prop(String nome, int padrao) {
        try { return Integer.parseInt(System.getProperty(nome, "" + padrao)); } catch (NumberFormatException e) { return padrao; }
    }

    /**
     * Benchmark para GPU real (e também roda em llvmpipe): cada cenário é montado uma vez por repetição e medido nos
     * três perfis sobre a MESMA carga, em ordem girada a cada repetição (LOW-BAL-HIGH, BAL-HIGH-LOW, HIGH-LOW-BAL...);
     * a ordem dos cenários é sorteada por repetição (semente fixa). Aquecimento global (todos os cenários, sem medir),
     * aquecimento depois de montar o cenário e depois de cada troca de perfil, amostra longa, tempo e carga gravados
     * quadro a quadro. Saída: linhas JANELA (por repetição) e RESUMO (mediana das medianas, IQR, p95 e p99 de todos os
     * quadros, e a carga mínima e máxima vista). Parâmetros: -Dcalamidade.luz.reps (5), .aquecer (8 s), .trocar (3 s),
     * .amostra (15 s).
     */
    private void benchmarkRigoroso() throws Exception {
        ouvirQuadros();
        int reps = prop("calamidade.luz.reps", 5), aquecer = prop("calamidade.luz.aquecer", 8),
            trocar = prop("calamidade.luz.trocar", 3), amostra = prop("calamidade.luz.amostra", 15);
        List<Cenario> cenarios = new ArrayList<>(List.of(
            new Cenario("vazio", List.of(), false),
            new Cenario("convencionais_8", List.of("calamidade bench 8 vfxsample:receita_fogueira"), false),
            new Cenario("convencionais_24", List.of("calamidade bench 24 vfxsample:receita_fogueira"), false),
            new Cenario("mistura_luz_feixe_shader", List.of("calamidade bench 4 vfxsample:receita_pilar",
                "calamidade bench 4 vfxsample:receita_teia", "calamidade bench 2 vfxsample:receita_cubo",
                "calamidade bench 4 vfxsample:receita_fogueira"), false),
            new Cenario("cookie_1", List.of("calamidade bench 1 vfxsample:luz_runas"), true),
            new Cenario("cookie_8_estaveis", List.of("calamidade bench 1 vfxsample:luz_estresse"), true),
            new Cenario("cookie_estresse_32", List.of("calamidade bench 4 vfxsample:luz_estresse"), true),
            // Fase 2: os mesmos 3 projetores (mesmo cookie) em ADD, EMISSIVE e OVERRIDE; 5 OVERRIDE; 3 resoluções.
            new Cenario("modos_add_emissive_override", List.of("calamidade bench 1 vfxsample:luz_modos"), true),
            new Cenario("override_5", List.of("calamidade bench 1 vfxsample:luz_forcas"), true),
            new Cenario("alta_resolucao_3", List.of("calamidade bench 1 vfxsample:luz_resolucoes"), true),
            new Cenario("sobrepostas_5_modos", List.of("calamidade bench 1 vfxsample:luz_sobrepostas"), true)));
        cenarios.removeIf(c -> c.novo() && !v015);
        String so = System.getProperty("calamidade.luz.cenarios", "");
        if (!so.isBlank()) {
            var nomes = java.util.Set.of(so.split(","));
            cenarios.removeIf(c -> !nomes.contains(c.nome()));
        }
        QualityMode[] perfis = {QualityMode.LOW, QualityMode.BALANCED, QualityMode.HIGH};
        escrever(String.format(Locale.ROOT, "# bench: %d repetições, aquecer %d s, trocar %d s, amostra %d s, %s", reps, aquecer,
            trocar, amostra, System.getProperty("java.vm.name") + " " + Runtime.version()), true);
        escrever("# JANELA versão;rep;perfil;cenário;quadros;mediana ms;p95 ms;p99 ms;média ms;efeitos min-max;luzes min-max;passes min-max;cookies min-max;fallback min-max", true);
        // Aquecimento global: JIT, shaders, atlas e chunks; nada é medido.
        perfil(QualityMode.HIGH);
        for (Cenario c : cenarios) {
            montar(c);
            Thread.sleep(aquecer * 1000L);
            desmontar();
        }
        java.util.Map<String, List<double[]>> porChave = new java.util.LinkedHashMap<>();
        java.util.Map<String, List<Double>> todos = new java.util.LinkedHashMap<>();
        for (int r = 0; r < reps; r++) {
            List<Cenario> ordem = new ArrayList<>(cenarios);
            java.util.Collections.shuffle(ordem, new java.util.Random(1000 + r));
            for (Cenario c : ordem) {
                montar(c);
                Thread.sleep(aquecer * 1000L);
                for (int k = 0; k < perfis.length; k++) {
                    QualityMode modo = perfis[(k + r) % perfis.length];
                    perfil(modo);
                    Thread.sleep(trocar * 1000L);
                    System.gc();   // coleta fora da janela, não dentro dela
                    Thread.sleep(500);
                    GRAVANDO.clear();
                    gravar = true;
                    Thread.sleep(amostra * 1000L);
                    gravar = false;
                    List<Quadro> q;
                    synchronized (GRAVANDO) { q = new ArrayList<>(GRAVANDO); }
                    double[] t = q.stream().mapToDouble(Quadro::ms).sorted().toArray();
                    String chave = modo.name() + ";" + c.nome();
                    porChave.computeIfAbsent(chave, x -> new ArrayList<>()).add(new double[] {pct(t, .5), pct(t, .95)});
                    var lista = todos.computeIfAbsent(chave, x -> new ArrayList<>());
                    for (double v : t) lista.add(v);
                    escrever(String.format(Locale.ROOT, "JANELA %s;%d;%s;%s;%d;%.2f;%.2f;%.2f;%.2f;%s;%s;%s;%s;%s", v015 ? "0.15.0" : "0.14.0", r,
                        modo.name(), c.nome(), t.length, pct(t, .5), pct(t, .95), pct(t, .99), java.util.Arrays.stream(t).average().orElse(0),
                        faixa(q, Quadro::efeitos), faixa(q, Quadro::luzes), faixa(q, Quadro::passes), faixa(q, Quadro::cookies),
                        faixa(q, Quadro::fallback)), true);
                }
                desmontar();
            }
        }
        escrever("# RESUMO versão;perfil;cenário;mediana das medianas ms;IQR das medianas ms;mediana p95 ms;p95 de todos os quadros ms;p99 de todos ms;quadros", true);
        for (var e : porChave.entrySet()) {
            double[] med = e.getValue().stream().mapToDouble(x -> x[0]).sorted().toArray();
            double[] p95 = e.getValue().stream().mapToDouble(x -> x[1]).sorted().toArray();
            double[] all = todos.get(e.getKey()).stream().mapToDouble(Double::doubleValue).sorted().toArray();
            escrever(String.format(Locale.ROOT, "RESUMO %s;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%d", v015 ? "0.15.0" : "0.14.0", e.getKey(),
                pct(med, .5), pct(med, .75) - pct(med, .25), pct(p95, .5), pct(all, .95), pct(all, .99), all.length), true);
        }
        perfil(QualityMode.AUTO);
    }

    private void montar(Cenario c) throws Exception {
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, 25));
        ticks(5);
        for (String linha : c.comandos()) comando(linha);
    }

    private void desmontar() throws Exception {
        comando("calamidade bench stop");
        ticks(20);
    }

    private static double pct(double[] ordenado, double p) {
        if (ordenado.length == 0) return 0;
        double i = p * (ordenado.length - 1);
        int a = (int) Math.floor(i), b = (int) Math.ceil(i);
        return ordenado[a] + (ordenado[b] - ordenado[a]) * (i - a);
    }

    private static String faixa(List<Quadro> q, java.util.function.ToIntFunction<Quadro> f) {
        int min = q.stream().mapToInt(f).min().orElse(0), max = q.stream().mapToInt(f).max().orElse(0);
        return min == max ? "" + min : min + "-" + max;
    }

    private void benchmark() throws Exception {
        List<Cenario> cenarios = new ArrayList<>(List.of(
            new Cenario("vazio", List.of(), false),
            new Cenario("convencionais_8", List.of("calamidade bench 8 vfxsample:receita_fogueira"), false),
            new Cenario("convencionais_24", List.of("calamidade bench 24 vfxsample:receita_fogueira"), false),
            new Cenario("mistura_luz_feixe_shader", List.of("calamidade bench 4 vfxsample:receita_pilar",
                "calamidade bench 4 vfxsample:receita_teia", "calamidade bench 2 vfxsample:receita_cubo",
                "calamidade bench 4 vfxsample:receita_fogueira"), false),
            new Cenario("chefe_furia", List.of("vfxsample chefe"), false),
            new Cenario("cookie_1", List.of("calamidade bench 1 vfxsample:luz_runas"), true),
            new Cenario("cookie_varios", List.of("calamidade bench 2 vfxsample:luz_agua", "calamidade bench 1 vfxsample:luz_tecnologia",
                "calamidade bench 1 vfxsample:luz_vitral", "calamidade bench 1 vfxsample:luz_selo", "calamidade bench 1 vfxsample:luz_combinada"), true),
            new Cenario("cookie_estresse_32", List.of("calamidade bench 4 vfxsample:luz_estresse"), true),
            new Cenario("mistura_com_cookies", List.of("calamidade bench 4 vfxsample:receita_pilar",
                "calamidade bench 4 vfxsample:receita_teia", "calamidade bench 2 vfxsample:receita_cubo",
                "calamidade bench 2 vfxsample:luz_agua", "calamidade bench 2 vfxsample:luz_runas"), true)));
        escrever("# MEDIDA versão;perfil;cenário;quadros;média ms;p95 ms;luzes;passes;KB alocados/quadro;pedidas;descartadas;cookies;fallback;MB atlas", true);
        QualityMode[] modos = rapido ? new QualityMode[] {QualityMode.BALANCED, QualityMode.HIGH}
            : new QualityMode[] {QualityMode.LOW, QualityMode.BALANCED, QualityMode.HIGH};
        for (QualityMode modo : modos) {
            perfil(modo);
            for (Cenario c : cenarios) {
                if (c.novo() && (!v015 || rapido)) continue;
                if (rapido && c.nome().equals("chefe_furia")) continue;
                noServidor(() -> jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, 25));
                for (String linha : c.comandos()) comando(linha);
                if (c.nome().equals("chefe_furia")) furia();
                Thread.sleep(2500);
                medir(modo, c.nome());
                comando("calamidade bench stop");
                if (c.nome().equals("chefe_furia")) {
                    comando("kill @e[type=minecraft:iron_golem]");
                    aguardar(() -> noServidorSemErro(() -> dev.calamidade_awakening.net.StageNetwork.liveCount() == 0), 600);
                }
                ticks(10);
            }
        }
        perfil(QualityMode.AUTO);
    }

    private void furia() throws Exception {
        ticks(5);
        var h = noServidor(() -> dev.calamidade_awakening.api.v1.Bosses.active(server()).stream()
            .filter(b -> b.id().toString().equals("vfxsample:sentinela") && b.alive()).findFirst());
        if (h.isEmpty()) return;
        aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("combate")), 120);
        var golem = h.get().entity();
        noServidor(() -> { golem.setHealth(golem.getMaxHealth() * .3f); golem.hurt(overworld().damageSources().generic(), 1); });
        aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("furia")), 160);
        ticks(200);   // eclipse na entrada, céu e atmosfera já fortes
    }

    private void medir(QualityMode modo, String nome) throws Exception {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long antes = bean.getThreadAllocatedBytes(render.threadId());
        int quadrosAntes = noCliente(() -> PerfOverlay.recentFrames().length);
        double[] janela = janela(5000);
        long depois = bean.getThreadAllocatedBytes(render.threadId());
        double soma = 0;
        for (double v : janela) soma += v;
        double media = janela.length == 0 ? 0 : soma / janela.length;
        double[] ord = janela.clone();
        Arrays.sort(ord);
        double p95 = ord.length == 0 ? 0 : ord[Math.min(ord.length - 1, (int) Math.ceil(ord.length * .95) - 1)];
        int luzes = campo("lastLights"), passes = campo("lastPasses");
        int[] d = v015 ? detalhe() : new int[10];
        double kb = janela.length == 0 ? 0 : (depois - antes) / 1024.0 / janela.length;
        String linha = String.format(Locale.ROOT, "MEDIDA %s;%s;%s;%d;%.2f;%.2f;%d;%d;%.1f;%d;%d;%d;%d;%.1f", v015 ? "0.15.0" : "0.14.0",
            modo.name(), nome, janela.length, media, p95, luzes, passes, kb, d[0], d[1], d[3], d[4], atlasBytes() / 1048576.0);
        escrever(linha, true);
        System.out.println("[TESTE] " + linha);
    }

    private int campo(String nome) throws Exception {
        return noCliente(() -> {
            try {
                var f = PerfOverlay.class.getDeclaredField(nome);
                f.setAccessible(true);
                return f.getInt(null);
            } catch (ReflectiveOperationException e) {
                return -1;
            }
        });
    }

    /** Tempos de quadro dos últimos {@code ms} milissegundos (a janela do F7 guarda 240 quadros). */
    private double[] janela(int ms) throws Exception {
        Thread.sleep(ms);
        double[] todos = PerfOverlay.recentFrames();
        double soma = 0;
        int n = 0;
        for (int i = todos.length - 1; i >= 0 && soma < ms; i--, n++) soma += todos[i];
        return Arrays.copyOfRange(todos, todos.length - n, todos.length);
    }

    // ------------------------------------------------------------------------------------------------ capturas

    private void capturas() throws Exception {
        perfil(QualityMode.HIGH);
        String[] demos = {"referencia", "runas", "tecnologia", "agua", "vitral", "holofote", "selo", "seguir", "combinada", "estresse"};
        for (String demo : demos) {
            noServidor(() -> jogador().teleportTo(overworld(), X + .5, 1, Z + .5, 0, 30));
            ticks(5);
            comando("vfxsample luz " + demo);
            ticks(demo.equals("selo") ? 110 : 60);
            if (demo.equals("seguir")) noServidor(() -> jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, 35));
            ticks(10);
            captura("luz_" + demo);
            int[] d = detalhe();
            check("demo " + demo + ": luzes desenhadas e nenhum cookie com falha", campo("lastLights") > 0 && d[8] <= 1,
                Arrays.toString(d));
            comando("vfxsample luz parar");
            ticks(10);
        }
        comando("vfxsample luz comparar referencia runas");
        ticks(60);
        captura("luz_comparar_referencia_runas");
        comando("vfxsample luz parar");
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private void perfil(QualityMode modo) throws Exception {
        noCliente(() -> { ClientHooks.coordinator().select(modo); return null; });
        ticks(10);
    }

    private void comando(String linha) throws Exception {
        noServidor(() -> server().getCommands().performPrefixedCommand(jogador().createCommandSourceStack().withPermission(4), linha));
    }

    private void captura(String nome) throws Exception {
        noCliente(() -> { Screenshot.grab(mc.gameDirectory, nome + ".png", mc.getMainRenderTarget(), (Component c) -> {}); return null; });
        Thread.sleep(300);
    }

    private <T> T noServidor(Supplier<T> task) throws Exception { return server().submit(task).get(60, TimeUnit.SECONDS); }
    private void noServidor(Runnable task) throws Exception { server().submit(task).get(60, TimeUnit.SECONDS); }

    private boolean noServidorSemErro(Supplier<Boolean> task) {
        try { return noServidor(task); } catch (Exception failure) { return false; }
    }

    private <T> T noCliente(Supplier<T> task) throws Exception { return mc.submit(task).get(120, TimeUnit.SECONDS); }

    private boolean noClienteSemErro(Supplier<Boolean> task) {
        try { return noCliente(task); } catch (Exception failure) { return false; }
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

    private static void escrever(String linha, boolean append) {
        try {
            Files.writeString(RELATORIO, linha + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
