package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.client.PerfOverlay;
import dev.calamidade_awakening.net.StageNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 0.15.0: regressão do eclipse órfão observado na 0.14.0 (xvfb-run ./gradlew testeOrfaos). O Sentinela do Eclipse do
 * vfx-sample entra na fúria (eclipse em área, {@code Scope.AREA}); o mundo é fechado e reaberto com o chefe carregado;
 * depois o golem morre (kill), e o céu precisa voltar ao normal depois da dissipação. Também: outra instância do mesmo
 * efeito sobreposta de propósito continua viva quando o chefe morre, e um /kill @e (que mata também o jogador) com
 * respawn não deixa instâncias no cliente que o servidor já encerrou. Relatório em {@code calamidade-orfaos.txt}.
 */
final class RoteiroOrfaos implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-orfaos.txt");
    private static final ResourceLocation SENTINELA = ResourceLocation.fromNamespaceAndPath("vfxsample", "sentinela");
    private static boolean iniciado;

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
            Thread thread = new Thread(new RoteiroOrfaos(), "Calamidade teste dos órfãos");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: regressão de efeitos órfãos, " + java.time.LocalDateTime.now(), false);
            preparar();
            fluxoDoUsuario();
            sobreposicaoIntencional();
            killTudoComRespawn();
            donoEncerrado();
            reconciliacao();
            comandos();
            escrever("FIM orfaos", true);
        } catch (Throwable failure) {
            check("roteiro dos órfãos sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] orfaos: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }
    private ServerLevel overworld() { return server().overworld(); }
    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private static final int X = -14000, Z = 0;

    private void preparar() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            server().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server());
            overworld().setDayTime(6000);
            server().getPlayerList().op(jogador().getGameProfile());
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, -30);
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; return null; });
        ticks(20);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(20);
    }

    /** Estado dos dois lados, numa linha (INFO no relatório). */
    private String estado(String rotulo) throws Exception {
        String servidor = noServidor(() -> "servidor: instâncias por fonte " + Arrays.toString(StageNetwork.liveBySource())
            + ", chefes " + Bosses.active(server()).size());
        String cliente = noCliente(() -> "cliente: camadas de céu " + PerfOverlay.skyLayers() + ", atmosfera "
            + PerfOverlay.atmospherePass() + ", eventos globais " + PerfOverlay.worldEvents());
        String linha = "INFO " + rotulo + " — " + servidor + "; " + cliente;
        escrever(linha, true);
        System.out.println("[TESTE] " + linha);
        return linha;
    }

    private int ativasDeEntidade() throws Exception { return noServidor(() -> StageNetwork.liveBySource()[1]); }

    private java.util.Optional<Bosses.Handle> sentinela() throws Exception {
        return noServidor(() -> Bosses.active(server()).stream().filter(b -> b.id().equals(SENTINELA) && b.alive()).findFirst());
    }

    private boolean furia() throws Exception {
        comando("vfxsample chefe");
        ticks(5);
        var h = sentinela();
        if (h.isEmpty()) return false;
        aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("combate")), 120);
        var golem = h.get().entity();
        noServidor(() -> { golem.setHealth(golem.getMaxHealth() * .3f); golem.hurt(overworld().damageSources().generic(), 1); });
        boolean ok = aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("furia")), 160);
        return ok && aguardar(() -> noClienteSemErro(() -> PerfOverlay.skyLayers() > 0), 200);
    }

    /** O roteiro do relato: fúria, fechar e reabrir o mundo, kill do golem, esperar a dissipação. */
    private void fluxoDoUsuario() throws Exception {
        boolean f = furia();
        estado("fúria");
        check("fluxo: o Sentinela entra na fúria e o eclipse em área aparece", f, "");
        sairEReabrir();
        ticks(60);
        String reaberto = estado("mundo reaberto");
        var h = sentinela();
        check("fluxo: depois de reabrir, o chefe volta na fúria com o eclipse", h.isPresent() && h.get().phase().equals("furia")
            && noCliente(PerfOverlay::skyLayers) > 0, reaberto);
        comando("kill @e[type=minecraft:iron_golem]");
        // Dissipação do eclipse: 1100 - 780 = 320 ticks depois da soltura; mais folga.
        boolean limpo = aguardar(() -> noServidorSemErro(() -> StageNetwork.liveBySource()[1] == 0)
            && noClienteSemErro(() -> PerfOverlay.skyLayers() == 0), 520);
        String depois = estado("depois do kill e da dissipação");
        check("fluxo: depois do kill, nenhuma instância de mob fica viva e o céu volta ao normal", limpo, depois);
    }

    /** Uma segunda instância do mesmo efeito, iniciada de propósito por script, sobrevive à morte do chefe. */
    private void sobreposicaoIntencional() throws Exception {
        boolean f = furia();
        var extra = noServidor(() -> dev.calamidade_awakening.api.v1.Effects.start(
            ResourceLocation.fromNamespaceAndPath("vfxsample", "evento_eclipse"), overworld(),
            dev.calamidade_awakening.api.v1.Placement.at(new net.minecraft.world.phys.Vec3(X + .5, 2, Z + 8.5))));
        ticks(10);
        estado("fúria + eclipse extra de script");
        comando("kill @e[type=minecraft:iron_golem]");
        boolean chefeLimpo = aguardar(() -> noServidorSemErro(() -> StageNetwork.liveBySource()[1] == 0), 520);
        boolean extraVivo = extra.isPresent() && noServidor(() -> extra.get().alive());
        String linha = estado("depois do kill, com o extra");
        check("sobreposição: o eclipse do chefe some, o de script continua", f && chefeLimpo && extraVivo
            && noCliente(PerfOverlay::skyLayers) > 0, linha);
        noServidor(() -> extra.ifPresent(dev.calamidade_awakening.api.v1.Effects.Handle::stop));
        boolean apagado = aguardar(() -> noClienteSemErro(() -> PerfOverlay.skyLayers() == 0), 60);
        check("sobreposição: stop do extra apaga o céu na hora", apagado, estado("depois do stop do extra"));
    }

    /** /kill @e mata o jogador junto: respawn com o chefe morto não deixa nada no cliente. */
    private void killTudoComRespawn() throws Exception {
        boolean f = furia();
        noServidor(() -> jogador().setGameMode(GameType.SURVIVAL));
        comando("kill @e");
        aguardar(() -> noClienteSemErro(() -> mc.player != null && mc.player.isDeadOrDying()), 100);
        noCliente(() -> { mc.player.respawn(); return null; });
        aguardar(() -> noClienteSemErro(() -> mc.player != null && mc.player.isAlive() && mc.screen == null), 200);
        noServidor(() -> {
            jogador().setGameMode(GameType.CREATIVE);
            jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, -30);
        });
        boolean limpo = aguardar(() -> noServidorSemErro(() -> StageNetwork.liveBySource()[1] == 0)
            && noClienteSemErro(() -> PerfOverlay.skyLayers() == 0), 520);
        check("kill @e com respawn: nada fica no servidor nem no cliente", f && limpo, estado("depois de kill @e e respawn"));
    }

    /**
     * 0.15.0: uma instância sustentada iniciada com a fonte do chefe por fora do controlador (handle perdido de
     * propósito) é solta quando o controlador acaba; e uma com fonte de chefe sem controlador nenhum é solta pelo
     * vigia. Efêmeras do chefe continuam até o fim.
     */
    private void donoEncerrado() throws Exception {
        boolean f = furia();
        var h = sentinela();
        if (!f || h.isEmpty()) { check("dono: Sentinela na fúria", false, ""); return; }
        var golem = h.get().entity();
        var eclipse = ResourceLocation.fromNamespaceAndPath("vfxsample", "evento_eclipse");
        var perdido = noServidor(() -> dev.calamidade_awakening.api.v1.Effects.start(eclipse, overworld(),
            dev.calamidade_awakening.api.v1.Placement.at(new net.minecraft.world.phys.Vec3(X + .5, 2, Z + 12.5))
                .withSource(dev.calamidade_awakening.api.v1.EffectSource.boss(golem))));
        long order = perdido.map(StageNetwork::orderOf).orElse(-1L);
        estado("fúria + eclipse com a fonte do chefe e handle perdido (" + order + ")");
        comando("kill @e[type=minecraft:iron_golem]");
        boolean soltoNaMorte = aguardar(() -> noServidorSemErro(() -> StageNetwork.describe().stream()
            .anyMatch(i -> i.order() == order && i.state().startsWith("exiting"))), 60);
        boolean acabou = aguardar(() -> noServidorSemErro(() -> StageNetwork.describe().stream().noneMatch(i -> i.order() == order)), 520);
        check("dono: sustentada da fonte do chefe, sem handle, é solta quando o controlador acaba e termina",
            perdido.isPresent() && soltoNaMorte && acabou, estado("depois da morte"));
        // Sem controlador nenhum: o vigia solta (fonte de chefe numa entidade que nunca foi chefe).
        var vaca = noServidor(() -> {
            var c = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, overworld());
            c.moveTo(X + 3.5, 1, Z + 6.5, 0, 0);
            c.setNoAi(true);
            overworld().addFreshEntity(c);
            return c;
        });
        int antes = noServidor(StageNetwork::orphansReleased);
        var orfa = noServidor(() -> dev.calamidade_awakening.api.v1.Effects.start(eclipse, overworld(),
            dev.calamidade_awakening.api.v1.Placement.at(new net.minecraft.world.phys.Vec3(X + .5, 2, Z + 12.5))
                .withSource(dev.calamidade_awakening.api.v1.EffectSource.boss(vaca))));
        boolean vigiada = aguardar(() -> noServidorSemErro(() -> StageNetwork.orphansReleased() > antes), 120);
        boolean fim = aguardar(() -> noServidorSemErro(() -> StageNetwork.liveBySource()[1] == 0)
            && noClienteSemErro(() -> PerfOverlay.skyLayers() == 0), 520);
        check("vigia: sustentada com fonte de chefe sem controlador é solta (com saída) e o céu volta", orfa.isPresent() && vigiada && fim,
            estado("depois do vigia"));
        noServidor(vaca::discard);
    }

    /** 0.15.0: o servidor esquece uma instância sem avisar (fim perdido); a lista periódica limpa o cliente. */
    private void reconciliacao() throws Exception {
        var eclipse = ResourceLocation.fromNamespaceAndPath("vfxsample", "evento_eclipse");
        var h = noServidor(() -> dev.calamidade_awakening.api.v1.Effects.start(eclipse, overworld(),
            dev.calamidade_awakening.api.v1.Placement.at(new net.minecraft.world.phys.Vec3(X + .5, 2, Z + 12.5))));
        boolean apareceu = aguardar(() -> noClienteSemErro(() -> PerfOverlay.skyLayers() > 0), 200);
        long order = h.map(StageNetwork::orderOf).orElse(-1L);
        noServidor(() -> StageNetwork.forgetSilently(order));
        ticks(10);
        boolean presoAntes = noCliente(() -> PerfOverlay.skyLayers() > 0);
        boolean limpo = aguardar(() -> noClienteSemErro(() -> PerfOverlay.skyLayers() == 0), 140);
        check("reconciliação: fim perdido fica preso só até a próxima lista (5 s) e some do cliente",
            apareceu && presoAntes && limpo, estado("depois da lista"));
    }

    /** 0.15.0: /calamidade fx list mostra escopo, fonte e dono; world list mostra o evento AREA; release e stop. */
    private void comandos() throws Exception {
        boolean f = furia();
        java.util.List<String> saida = new java.util.concurrent.CopyOnWriteArrayList<>();
        noServidor(() -> server().getCommands().performPrefixedCommand(saidaDe(saida), "calamidade fx list"));
        noServidor(() -> server().getCommands().performPrefixedCommand(saidaDe(saida), "calamidade world list"));
        boolean area = saida.stream().anyMatch(l -> l.contains("vfxsample:evento_eclipse") && l.contains("AREA") && l.contains("owner boss"));
        long ordem = noServidor(() -> StageNetwork.describe().stream().filter(i -> i.scope().equals("AREA")).mapToLong(StageNetwork.Info::order)
            .findFirst().orElse(-1));
        noServidor(() -> server().getCommands().performPrefixedCommand(saidaDe(saida), "calamidade fx release " + ordem));
        boolean saindo = noServidor(() -> StageNetwork.describe().stream().anyMatch(i -> i.order() == ordem && i.state().startsWith("exiting")));
        noServidor(() -> server().getCommands().performPrefixedCommand(saidaDe(saida), "calamidade fx stop " + ordem));
        boolean parado = noServidor(() -> StageNetwork.describe().stream().noneMatch(i -> i.order() == ordem));
        check("comandos: fx list e world list mostram o eclipse AREA com dono; fx release solta, fx stop encerra",
            f && area && saindo && parado, saida.stream().filter(l -> l.contains("evento_eclipse")).findFirst().orElse(saida.toString()));
        comando("kill @e[type=minecraft:iron_golem]");
        aguardar(() -> noServidorSemErro(() -> StageNetwork.liveBySource()[1] == 0), 520);
    }

    private net.minecraft.commands.CommandSourceStack saidaDe(java.util.List<String> saida) {
        return server().createCommandSourceStack().withPermission(4).withSource(new net.minecraft.commands.CommandSource() {
            public void sendSystemMessage(Component c) { saida.add(c.getString()); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        });
    }

    private void sairEReabrir() throws Exception {
        noCliente(() -> {
            mc.level.disconnect();
            mc.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("Salvando")));
            return null;
        });
        aguardar(() -> noClienteSemErro(() -> mc.level == null && mc.getSingleplayerServer() == null), 600);
        Thread.sleep(2000);
        noCliente(() -> { mc.createWorldOpenFlows().openWorld("teste", () -> {}); return null; });
        long limite = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < limite && !noClienteSemErro(() -> mc.player != null && mc.level != null
            && mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isReady())) Thread.sleep(200);
        ticks(40);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private void comando(String linha) throws Exception {
        noServidor(() -> server().getCommands().performPrefixedCommand(jogador().createCommandSourceStack().withPermission(4), linha));
    }

    private <T> T noServidor(Supplier<T> task) throws Exception { return server().submit(task).get(60, TimeUnit.SECONDS); }
    private void noServidor(Runnable task) throws Exception { server().submit(task).get(60, TimeUnit.SECONDS); }

    private boolean noServidorSemErro(Supplier<Boolean> task) {
        try { return noServidor(task); } catch (Exception failure) { return false; }
    }

    private <T> T noCliente(Supplier<T> task) throws Exception { return mc.submit(task).get(60, TimeUnit.SECONDS); }

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
