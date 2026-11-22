package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Teste em jogo da Sky API, dos eventos globais e do céu por dimensão (0.12.0), com {@code calamidade.teste=ceu}, no
 * cliente (renderização por software) com o vfx-sample 1.9.0 em {@code mods/}: custo do passe de céu com 0, 1, 4 e 8
 * camadas; o eclipse global do exemplo visto no Overworld, no Nether e na dimensão de teste; respawn e relogin (que
 * fecha e reabre o servidor integrado: o evento persistente volta na mesma fase); os três estados da dimensão de teste;
 * F7. Capturas em {@code screenshots/}; relatório em {@code calamidade-ceu.txt}.
 */
final class RoteiroCeu implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-ceu.txt");
    private static final ResourceLocation ECLIPSE = ResourceLocation.fromNamespaceAndPath("vfxsample", "evento_eclipse");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        // A dimensão do vfx-sample (datapack) faz o jogo perguntar sobre "ajustes experimentais" uma vez por mundo:
        // o teste responde "Sei o que estou fazendo!", como o jogador faria.
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
            Thread thread = new Thread(new RoteiroCeu(), "Calamidade teste do céu");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste em jogo do céu e dos eventos globais, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            medirCusto();
            eclipseGlobal();
            receitas();
            respawnERelogin();
            dimensaoDeTeste();
            escrever("FIM ceu", true);
        } catch (Throwable failure) {
            check("roteiro do céu sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] ceu: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel overworld() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private static final int X = -9000, Z = 0;

    private void preparar() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            overworld().setDayTime(2000);   // manhã: sol a ~42 graus, a leste
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 3, Z + .5, 0, -25);
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; mc.options.hideGui = false; return null; });
        esperarTerreno();
    }

    private void esperarTerreno() throws Exception {
        ticks(20);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(20);
    }

    // ------------------------------------------------------------------------------------------------ custo

    /** Passe de céu com 0, 1, 4 e 8 camadas (perfil de teste UNLIMITED: 8 camadas por quadro). */
    private void medirCusto() throws Exception {
        noCliente(() -> { ClientHooks.coordinator().test(FrameLimits.unlimited()); return null; });
        List<String> tabela = new ArrayList<>();
        tabela.add("| Camadas | Quadros | Mediana (ms) | p95 (ms) | Passe |");
        tabela.add("|---|---|---|---|---|");
        double base = 0;
        int[][] passos = {{0, 0}, {1, 0}, {0, 1}, {0, 2}};
        for (int[] passo : passos) {
            List<Effects.Handle> handles = new ArrayList<>();
            noServidor(() -> {
                Vec3 at = jogador().position();
                for (int i = 0; i < passo[0]; i++) Effects.start(CalamidadeTeste.CEU_UM, overworld(), at).ifPresent(handles::add);
                for (int i = 0; i < passo[1]; i++) Effects.start(CalamidadeTeste.CEU_QUATRO, overworld(), at).ifPresent(handles::add);
            });
            int esperadas = passo[0] + 4 * passo[1];
            ticks(60);
            double[] ms = janela(6000);
            int camadas = PerfOverlay.skyLayers();
            boolean passe = PerfOverlay.skyPass();
            Arrays.sort(ms);
            double mediana = FrameStats.percentile(ms, .5), p95 = FrameStats.percentile(ms, .95);
            if (esperadas == 0) base = mediana;
            tabela.add(String.format(Locale.ROOT, "| %d | %d | %.2f | %.2f | %s |", camadas, ms.length, mediana, p95, passe ? "sim" : "não"));
            check("custo: " + esperadas + " camadas pintadas e o passe " + (esperadas > 0 ? "presente" : "ausente"),
                camadas == esperadas && passe == (esperadas > 0), camadas + " camadas, passe " + passe);
            if (esperadas == 4) captura("ceu-quatro-camadas-0120");
            noServidor(() -> handles.forEach(Effects.Handle::stop));
            ticks(20);
        }
        tabela.forEach(linha -> escrever(linha, true));
        noCliente(() -> { ClientHooks.coordinator().test(null); return null; });
        check("custo medido (base " + String.format(Locale.ROOT, "%.2f", base) + " ms)", base > 0, "");
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

    // ------------------------------------------------------------------------------------------------ eclipse

    private void comando(String linha) throws Exception {
        noServidor(() -> server().getCommands().performPrefixedCommand(jogador().createCommandSourceStack().withPermission(4), linha));
    }

    private void olharSol() throws Exception {
        noServidor(() -> jogador().teleportTo(jogador().serverLevel(), jogador().getX(), jogador().getY(), jogador().getZ(), -90, -38));
        ticks(10);
    }

    private void eclipseGlobal() throws Exception {
        olharSol();
        comando("vfxsample evento eclipse start");
        boolean ativo = aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
        check("eclipse global: o cliente recebe o evento (F7 \"Eventos globais: 1\")", ativo, "");
        ticks(430);   // entrada de 400 ticks: totalidade
        int camadas = PerfOverlay.skyLayers();
        check("eclipse na totalidade: sol e raios pintados (2 camadas, passe sim)", camadas == 2 && PerfOverlay.skyPass(),
            camadas + " camadas");
        PerfOverlay.show();
        Thread.sleep(500);
        List<String> f7 = noCliente(() -> PerfOverlay.lines().stream().map(Component::getString).toList());
        check("F7 mostra a linha do céu e a dos eventos globais", f7.stream().anyMatch(l -> l.startsWith("Céu: 2 camadas"))
            && f7.stream().anyMatch(l -> l.equals("Eventos globais: 1")), f7.stream().filter(l -> l.startsWith("Céu") || l.startsWith("Eventos")).toList().toString());
        captura("ceu-f7-0120");
        noCliente(() -> { PerfOverlay.hide(); return null; });
        Thread.sleep(300);
        captura("ceu-eclipse-overworld-0120");
        // Nether: o mesmo evento, no próprio céu (sem sol do jogo, no ponto fixo ao sul), acima do teto.
        comando("execute in minecraft:the_nether run tp @s 0.5 132 0.5 0 -40");
        boolean nether = aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().getPath().equals("the_nether")), 400);
        esperarTerreno();
        boolean noNether = aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
        ticks(10);
        check("troca de dimensão: o evento continua no Nether (SERVER)", nether && noNether && PerfOverlay.skyLayers() >= 1,
            PerfOverlay.skyLayers() + " camadas");
        captura("ceu-eclipse-nether-0120");
        comando("execute in minecraft:overworld run tp @s " + (X + .5) + " 3 " + (Z + .5) + " -90 -38");
        aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().getPath().equals("overworld")), 400);
        esperarTerreno();
    }

    // ------------------------------------------------------------------------------------------------ receitas

    private void receitas() throws Exception {
        comando("vfxsample evento eclipse stop");
        aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 0), 100);
        noServidor(() -> overworld().setDayTime(13500));   // começo da noite
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 3, Z + .5, 0, -18));
        ticks(20);
        String[][] cenas = {{"buraco_negro", "240", "ceu-buraco-negro-0120"}, {"portal_galaxia", "200", "ceu-portal-0120"},
            {"lua_gigante", "500", "ceu-lua-gigante-0120"}};
        for (String[] cena : cenas) {
            comando("vfxsample receita " + cena[0]);
            ticks(Integer.parseInt(cena[1]));
            int camadas = PerfOverlay.skyLayers();
            check("receita " + cena[0] + ": camada pintada", camadas >= 1 && PerfOverlay.skyPass(), camadas + " camadas");
            captura(cena[2]);
            comando("vfxsample receita stop");
            ticks(20);
        }
        noServidor(() -> overworld().setDayTime(2000));
    }

    // ------------------------------------------------------------------------------------------------ entrada tardia

    private void respawnERelogin() throws Exception {
        comando("vfxsample evento eclipse start");
        aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 100);
        ticks(200);
        // Respawn: o jogador morre e renasce; o servidor reenvia o evento ao jogador novo.
        noServidor(() -> jogador().setGameMode(GameType.SURVIVAL));
        comando("kill @s");
        aguardar(() -> noClienteSemErro(() -> mc.player != null && mc.player.isDeadOrDying()), 100);
        noCliente(() -> { mc.player.respawn(); return null; });
        boolean renasceu = aguardar(() -> noClienteSemErro(() -> mc.player != null && mc.player.isAlive() && mc.screen == null), 200);
        ticks(20);
        check("respawn durante o evento: continua visível", renasceu && noCliente(ClientHooks::worldEvents) == 1, "");
        noServidor(() -> {
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
        });
        // Relogin: sair do mundo fecha o servidor integrado (salva o evento persistente); reabrir retoma na mesma fase.
        long antes = noServidor(() -> dev.calamidade_awakening.world.WorldEventServer.ageOf(ECLIPSE));
        noCliente(() -> {
            mc.level.disconnect();
            mc.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("Salvando")));
            return null;
        });
        aguardar(() -> noClienteSemErro(() -> mc.level == null && mc.getSingleplayerServer() == null), 600);
        Thread.sleep(2000);
        check("relogin: saiu do mundo e o evento sumiu do cliente", noCliente(ClientHooks::worldEvents) == 0, "");
        noCliente(() -> { mc.createWorldOpenFlows().openWorld("teste", () -> {}); return null; });
        boolean voltou = aguardarTempo(() -> noClienteSemErro(() -> mc.player != null && mc.level != null && mc.getSingleplayerServer() != null
            && mc.getSingleplayerServer().isReady()), 180);
        check("relogin: mundo reaberto", voltou, "");
        if (!voltou) return;
        esperarTerreno();
        long depois = noServidor(() -> dev.calamidade_awakening.world.WorldEventServer.ageOf(ECLIPSE));
        boolean visivel = aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 1), 200);
        check("relogin e reinício do servidor: o eclipse persistente voltou na mesma fase e o cliente o recebeu",
            visivel && depois >= antes && depois < antes + 600, "idade " + antes + " → " + depois);
        olharSol();
        ticks(20);
        captura("ceu-eclipse-relogin-0120");
    }

    // ------------------------------------------------------------------------------------------------ dimensão

    private void dimensaoDeTeste() throws Exception {
        comando("vfxsample dimensao");
        boolean la = aguardar(() -> noClienteSemErro(() -> mc.level.dimension().location().toString().equals("vfxsample:dimensao_teste")), 400);
        esperarTerreno();
        check("dimensão de teste: chegou, eclipse global também lá, céu no estado inicial", la
            && noCliente(ClientHooks::worldEvents) == 1 && "calmo".equals(noCliente(ClientHooks::skyState)), noCliente(ClientHooks::skyState));
        olharSol();
        captura("ceu-dimensao-eclipse-0120");
        comando("vfxsample evento eclipse stop");
        aguardar(() -> noClienteSemErro(() -> ClientHooks.worldEvents() == 0), 100);
        noServidor(() -> jogador().teleportTo(jogador().serverLevel(), .5, 8, .5, 0, -20));
        ticks(40);
        captura("ceu-dimensao-calmo-0120");
        for (String estado : new String[] {"ameaca", "caos"}) {
            comando("vfxsample ceu " + estado);
            // O estado dominante troca na metade da transição (50 ticks); o cliente por software atrasa alguns ticks.
            boolean meio = aguardar(() -> noClienteSemErro(() -> estado.equals(ClientHooks.skyState())), 100);
            ticks(70);
            int camadas = PerfOverlay.skyLayers();
            check("dimensão de teste: transição para " + estado + " (5 s) e camadas do estado", meio && camadas >= 2,
                camadas + " camadas");
            captura("ceu-dimensao-" + estado + "-0120");
        }
        comando("vfxsample ceu calmo");
        ticks(120);
        check("dimensão de teste: volta ao calmo", "calmo".equals(noCliente(ClientHooks::skyState)), "");
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private void captura(String nome) throws Exception {
        noCliente(() -> { Screenshot.grab(mc.gameDirectory, nome + ".png", mc.getMainRenderTarget(), (Component c) -> {}); return null; });
        Thread.sleep(300);
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

    private void ticks(int n) throws InterruptedException {
        MinecraftServer s = server();
        if (s == null) { Thread.sleep(n * 50L); return; }
        int alvo = s.getTickCount() + n;
        while (s.getTickCount() < alvo && !s.isStopped()) Thread.sleep(5);
    }

    private boolean aguardar(BooleanSupplier condicao, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks; i++) {
            if (condicao.getAsBoolean()) return true;
            if (server() == null) Thread.sleep(50); else ticks(1);
        }
        return false;
    }

    private boolean aguardarTempo(BooleanSupplier condicao, int segundos) throws Exception {
        long fim = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < fim) {
            if (condicao.getAsBoolean()) return true;
            Thread.sleep(200);
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
