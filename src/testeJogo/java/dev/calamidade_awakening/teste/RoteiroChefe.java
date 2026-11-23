package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.api.v1.client.AnchorCapture;
import dev.calamidade_awakening.client.PerfOverlay;
import dev.calamidade_awakening.client.boss.AnchorCache;
import dev.calamidade_awakening.client.boss.BossBarClient;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Teste em jogo dos chefes no cliente (0.13.0), com {@code calamidade.teste=chefe}, numa só inicialização, com o
 * vfx-sample 1.10.0 em {@code mods/} (a camada do golem que captura os olhos e o Sentinela): anchor capturado do modelo
 * × estático, extrapolação e plano B fora da tela, centro preso ao anchor, feixe saindo dos olhos; barra segmentada,
 * troca de fase, forçar vanilla, escala e alto contraste; sair e voltar ao alcance (entrada tardia); o Sentinela do
 * Eclipse de ponta a ponta (comando, fases, fúria com eclipse em área, morte e limpeza); linha do F7. Capturas em
 * {@code screenshots/}; relatório em {@code calamidade-chefe.txt}.
 */
final class RoteiroChefe implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-chefe.txt");
    private static final ResourceLocation SENTINELA = ResourceLocation.fromNamespaceAndPath("vfxsample", "sentinela");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        // A dimensão do vfx-sample (datapack) faz o jogo perguntar sobre "ajustes experimentais" uma vez por mundo:
        // o teste responde "Sei o que estou fazendo!", como no RoteiroCeu.
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
            Thread thread = new Thread(new RoteiroChefe(), "Calamidade teste dos chefes");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste em jogo dos chefes, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            preparar();
            anchors();
            barra();
            sentinela();
            escrever("FIM chefe", true);
        } catch (Throwable failure) {
            check("roteiro dos chefes sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] chefe: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel overworld() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private static final int X = -12000, Z = 0;

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
            jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, 10);   // olhando para o sul (+Z)
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; mc.options.hideGui = false; return null; });
        ticks(20);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(20);
    }

    private IronGolem golem(double x, double z) throws Exception {
        return noServidor(() -> {
            IronGolem g = new IronGolem(EntityType.IRON_GOLEM, overworld());
            g.moveTo(x, 1, z, 180, 0);   // de frente para o jogador (norte)
            g.setYHeadRot(180);
            g.setYBodyRot(180);
            g.setNoAi(true);
            g.setPersistenceRequired();
            overworld().addFreshEntity(g);
            return g;
        });
    }

    // ------------------------------------------------------------------------------------------------ anchors

    private void anchors() throws Exception {
        IronGolem g = golem(X + .5, Z + 7.5);
        Effects.Handle[] h = new Effects.Handle[2];
        noServidor(() -> {
            h[0] = Effects.follow(ChefesTeste.OLHAR, g, Vec3.ZERO).orElseThrow();
            h[1] = Effects.follow(ChefesTeste.BRILHO, g, Vec3.ZERO).orElseThrow();
        });
        ticks(30);
        int[] visto = noCliente(AnchorCache::stats);
        var cliente = noCliente(() -> mc.level.getEntity(g.getId()));
        double[] dif = noCliente(() -> {
            var captured = AnchorCapture.resolve(cliente, "olhos", 1);
            var estatico = RigAnchors.approximate(cliente, "olhos", 1);
            return new double[] {captured.position().distanceTo(estatico.position()), captured.forward().dot(estatico.forward()),
                AnchorCache.stats()[0]};
        });
        check("anchor: a camada do golem captura os olhos no quadro (pose do modelo)", visto[0] > 0, Arrays.toString(visto));
        check("anchor: pose capturada perto do anchor estático (eixos do modelo vanilla corretos)", dif[0] < .6 && dif[1] > .8,
            String.format(java.util.Locale.ROOT, "distância %.3f, frentes %.3f", dif[0], dif[1]));
        captura("chefe_anchor_feixe");
        // Fora da tela: vira o jogador para trás; a captura é carregada por até 10 ticks, depois vale o estático.
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 180, 10));
        ticks(4);
        int[] fora = noCliente(AnchorCache::stats);
        ticks(30);
        int[] depois = noCliente(AnchorCache::stats);
        check("anchor: fora da tela, captura carregada e depois o estático (sem sumir)",
            (fora[1] > 0 || fora[3] > 0) && depois[3] > 0 && depois[0] == 0, Arrays.toString(fora) + " -> " + Arrays.toString(depois));
        noServidor(() -> { h[0].stop(); h[1].stop(); g.discard(); jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, 10); });
        ticks(10);
    }

    // ------------------------------------------------------------------------------------------------ barra

    private void barra() throws Exception {
        IronGolem g = golem(X + 3.5, Z + 9.5);
        var h = noServidor(() -> Bosses.attach(g, ChefesTeste.CHEFE).orElseThrow());
        boolean conhecida = aguardar(() -> noClienteSemErro(() -> BossBarClient.known() == 1), 60);
        ticks(5);
        boolean desenhada = aguardar(() -> noClienteSemErro(() -> BossBarClient.drawn() == 1), 40);
        check("barra: boss_v1 chega e o estilo segmentado substitui a barra vanilla", conhecida && desenhada,
            noCliente(BossBarClient::known) + " conhecidas, " + noCliente(BossBarClient::drawn) + " desenhadas");
        captura("chefe_barra_segmentada");
        noServidor(() -> { h.phase("c"); });
        boolean fase = aguardar(() -> noClienteSemErro(() -> BossBarClient.firstPhase() == 2), 40);
        check("barra: troca de fase chega ao cliente (um pacote)", fase, "fase " + noCliente(BossBarClient::firstPhase));
        ticks(2);
        captura("chefe_barra_fase_c");
        noCliente(() -> { ClientConfig.bossBar(1, false, true, true); return null; });
        ticks(5);
        boolean vanilla = aguardar(() -> noClienteSemErro(() -> BossBarClient.drawn() == 0), 20);
        check("barra: \"forçar barras vanilla\" volta ao desenho do jogo", vanilla, "");
        captura("chefe_barra_vanilla");
        noCliente(() -> { ClientConfig.bossBar(2, true, true, false); return null; });
        ticks(5);
        check("barra: escala 2 e alto contraste desenham o estilo", aguardar(() -> noClienteSemErro(() -> BossBarClient.drawn() == 1), 20), "");
        captura("chefe_barra_escala2_contraste");
        noCliente(() -> { ClientConfig.bossBar(1, false, true, false); return null; });
        Component linha = noCliente(() -> PerfOverlay.bossLine.get());
        check("F7: linha dos chefes", linha != null && linha.getString().contains("1"), linha == null ? "null" : linha.getString());
        // Sai do alcance (64) e volta: a barra some e volta com a fase certa (entrada tardia).
        noServidor(() -> jogador().teleportTo(overworld(), X + 200.5, 2, Z + .5, 0, 10));
        boolean sumiu = aguardar(() -> noClienteSemErro(() -> BossBarClient.known() == 0), 60);
        noServidor(() -> jogador().teleportTo(overworld(), X + .5, 2, Z + .5, 0, 10));
        boolean voltou = aguardar(() -> noClienteSemErro(() -> BossBarClient.known() == 1 && BossBarClient.firstPhase() == 2), 80);
        check("barra: fora do alcance some; de volta, reaparece na fase atual", sumiu && voltou, sumiu + " / " + voltou);
        noServidor(() -> { h.detach(); g.discard(); });
        boolean limpa = aguardar(() -> noClienteSemErro(() -> BossBarClient.known() == 0), 40);
        check("barra: detach remove a barra no cliente", limpa, "");
    }

    // ------------------------------------------------------------------------------------------------ Sentinela

    private void sentinela() throws Exception {
        comando("vfxsample chefe");
        ticks(5);
        var h = noServidor(() -> Bosses.active(server()).stream().filter(b -> b.id().equals(SENTINELA)).findFirst());
        check("Sentinela: /vfxsample chefe cria o golem e anexa o controlador (fase despertar)",
            h.isPresent() && h.get().phase().equals("despertar"), h.map(Object::toString).orElse("nenhum"));
        if (h.isEmpty()) return;
        var golem = h.get().entity();
        boolean combate = aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("combate")), 120);
        check("Sentinela: despertar -> combate em 60 ticks", combate, h.get().phase());
        ticks(30);
        captura("sentinela_combate");
        noServidor(() -> { golem.setHealth(golem.getMaxHealth() * .3f); golem.hurt(overworld().damageSources().generic(), 1); });
        boolean rugido = aguardar(() -> noServidorSemErro(() -> h.get().locked()), 40);
        boolean furia = aguardar(() -> noServidorSemErro(() -> h.get().phase().equals("furia")), 120);
        check("Sentinela: vida abaixo de 40% toca o rugido (trava) e entra na fúria", rugido && furia, rugido + " / " + h.get().phase());
        boolean ceu = aguardar(() -> noClienteSemErro(() -> PerfOverlay.skyLayers() > 0), 200);
        check("Sentinela: o eclipse em área aparece no céu na fúria", ceu, "camadas " + noCliente(PerfOverlay::skyLayers));
        noCliente(() -> { mc.player.setXRot(-30); return null; });
        ticks(5);
        captura("sentinela_furia");
        noServidor(() -> { golem.hurt(overworld().damageSources().genericKill(), Float.MAX_VALUE); });
        boolean fim = aguardar(() -> noServidorSemErro(() -> !Bosses.isBoss(golem) && golem.isRemoved())
            && noClienteSemErro(() -> BossBarClient.known() == 0), 120);
        check("Sentinela: morte -> derrota, barra some e o controlador acaba com o golem", fim, "");
    }

    // ------------------------------------------------------------------------------------------------ apoio

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
        try {
            return noServidor(task);
        } catch (Exception failure) {
            return false;
        }
    }

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

    private static void escrever(String linha, boolean append) {
        try {
            Files.writeString(RELATORIO, linha + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
