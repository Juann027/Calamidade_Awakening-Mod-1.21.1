package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.client.CostProbe;
import dev.calamidade_awakening.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Medição de custo em jogo (0.11.2), com {@code calamidade.teste=medicao}: roda {@code /calamidade_client debug measure
 * all} (o {@link CostProbe}, o mesmo código que o dono roda na própria placa) num mundo plano, com o vfx-sample em
 * {@code mods/}, e confere que cada passo teve a carga pedida (desenhados e passes). Grava a tabela, as inclinações
 * (ms por efeito desenhado e por passe) e capturas da carga de 60 efeitos e de 12 passes. A reserva de jogadores vai a 0
 * durante a medição, para o bench chegar a 60 instâncias.
 */
final class RoteiroMedicao implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-medicao.txt");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (iniciado || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            iniciado = true;
            Thread thread = new Thread(new RoteiroMedicao(), "Calamidade medição em jogo");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: medição de custo em jogo, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> com.mojang.blaze3d.systems.RenderSystem.getBackendDescription() + " · "
                + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            medir();
            autoQualidade();
            escrever("FIM medicao", true);
        } catch (Throwable failure) {
            check("medição sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] medição: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel level() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private void medir() throws Exception {
        ticks(40);
        int x = -3000, z = 0;
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            level().setDayTime(6000);
            server().getPlayerList().op(jogador().getGameProfile());
            for (int cx = (x - 48) >> 4; cx <= (x + 48) >> 4; cx++)
                for (int cz = (z - 16) >> 4; cz <= (z + 48) >> 4; cz++) level().setChunkForced(cx, cz, true);
            jogador().setGameMode(GameType.SPECTATOR);
            // Olhando para o sul, um pouco para baixo: os anéis correm pelo chão, cubos, teias e céus à altura dos olhos.
            jogador().teleportTo(level(), x + .5, 1, z + .5, 0, 8);
        });
        noCliente(() -> { mc.options.hideGui = true; mc.options.pauseOnLostFocus = false; return null; });
        ticks(40);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(40);
        Field campo = ServerConfig.class.getDeclaredField("PLAYER_RESERVE");
        campo.setAccessible(true);
        var reserva = (ModConfigSpec.IntValue) campo.get(null);
        int antes = noServidor(reserva::get);
        noServidor(() -> reserva.set(0));
        boolean viu60 = false, viu12 = false;
        try {
            boolean comecou = noCliente(() -> CostProbe.start("all"));
            check("medição começou (/calamidade_client debug measure all)", comecou, "");
            long limite = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
            while (noCliente(CostProbe::running) && System.currentTimeMillis() < limite) {
                String status = noCliente(CostProbe::status);
                if (!viu60 && status.contains("60 drawn") && status.contains("recording")) {
                    viu60 = true;
                    captura("medicao-60-efeitos");
                }
                if (!viu12 && status.contains("12 passes") && status.contains("recording")) {
                    viu12 = true;
                    captura("medicao-12-passes");
                }
                Thread.sleep(500);
            }
        } finally {
            noServidor(() -> reserva.set(antes));
        }
        List<CostProbe.Result> r = noCliente(CostProbe::results);
        check("medição terminou com 12 passos", r.size() == 12, r.size() + " passos");
        if (r.size() != 12) return;
        List<String> tabela = noCliente(() -> CostProbe.report(mc));
        for (String linha : tabela) escrever(linha, true);
        for (CostProbe.Result x0 : r) {
            check("passo " + x0.group() + " · " + x0.label() + " gravou quadros", x0.frames() >= 10, x0.frames() + " quadros");
            if (x0.label().startsWith("base")) continue;
            int n = Integer.parseInt(x0.label().split(" ")[0]);
            if (x0.group().equals("effects"))
                check(String.format(Locale.ROOT, "efeitos: %d pedidos, %.1f desenhados em média", n, x0.drawn()),
                    x0.drawn() >= n * .8, String.format(Locale.ROOT, "%.1f residentes", x0.resident()));
            else
                check(String.format(Locale.ROOT, "passes: teto %d, %.1f passes em média com %.1f desenhados", n, x0.passes(),
                    x0.drawn()), x0.passes() >= n * .8 && x0.passes() <= n + .01, "");
        }
        // Inclinação por mínimos quadrados: ms de mediana e de p95 por efeito desenhado e por passe.
        escrever(String.format(Locale.ROOT, "# inclinação efeitos: %+.3f ms de mediana e %+.3f ms de p95 por efeito desenhado",
            inclinacao(r, "effects", true, false), inclinacao(r, "effects", true, true)), true);
        escrever(String.format(Locale.ROOT, "# inclinação passes: %+.3f ms de mediana e %+.3f ms de p95 por passe de addon",
            inclinacao(r, "passes", false, false), inclinacao(r, "passes", false, true)), true);
        check("capturas da carga de 60 efeitos e de 12 passes", viu60 && viu12, "");
    }

    /**
     * Etapa 3: o modo AUTO desce sob carga (com o motivo no F7), não sobe nem desce sem efeitos na tela e não troca
     * durante um efeito HERO (a receita protagonista, 12 s). A subida depende de folga de quadro que o llvmpipe não tem:
     * coberta pelo AutoQualityCheck e pelo TESTE-REAL.
     */
    private void autoQualidade() throws Exception {
        var coord = dev.calamidade_awakening.client.ClientHooks.coordinator();
        var reserva = (ModConfigSpec.IntValue) campoReserva().get(null);
        int antes = noServidor(reserva::get);
        noServidor(() -> reserva.set(0));
        try {
            noCliente(() -> {
                coord.select(dev.calamidade_awakening.api.QualityMode.HIGH);
                coord.select(dev.calamidade_awakening.api.QualityMode.AUTO);
                mc.getConnection().sendCommand("calamidade bench 24 mix");
                return null;
            });
            long t0 = System.currentTimeMillis();
            boolean desceu = aguardarMs(() -> noClienteSemErro(() -> coord.profile() != dev.calamidade_awakening.api.QualityProfile.HIGH), 60_000);
            var r = noCliente(() -> coord.auto().reason());
            check(String.format(Locale.ROOT, "AUTO: desce de HIGH sob carga (%s em %.0f s, p95 %.1f ms > %.1f ms)", r.to(),
                (System.currentTimeMillis() - t0) / 1000.0, r.p95(), r.thresholdMs()),
                desceu && r.change() == dev.calamidade_awakening.api.AutoQuality.Change.DOWN && r.from() == dev.calamidade_awakening.api.QualityProfile.HIGH
                    && r.p95() > r.thresholdMs(), "");
            // F7 com a linha do AUTO, para a documentação.
            noCliente(() -> { mc.options.hideGui = false; return null; });
            noCliente(() -> { dev.calamidade_awakening.client.PerfOverlay.show(); return null; });
            Thread.sleep(1500);
            captura("auto-f7");
            String linha = noCliente(() -> dev.calamidade_awakening.client.ClientHooks.qualityLine().getString());
            check("AUTO: F7 mostra o perfil e o motivo da troca", linha.contains("AUTO") && linha.contains(r.to().name())
                && linha.contains("p95"), linha);
            noCliente(() -> { mc.options.hideGui = true; return null; });
            aguardarMs(() -> noClienteSemErro(() -> coord.profile() == dev.calamidade_awakening.api.QualityProfile.LOW), 60_000);
            noCliente(() -> { mc.getConnection().sendCommand("calamidade bench stop"); return null; });
            Thread.sleep(3000);
            var parado = noCliente(coord::profile);
            Thread.sleep(20_000);
            check("AUTO: sem efeitos na tela, o perfil fica (" + parado + ")", noCliente(coord::profile) == parado, "");
            // HERO: carga pesada com a protagonista; nenhuma troca enquanto ela existe.
            noCliente(() -> {
                coord.select(dev.calamidade_awakening.api.QualityMode.HIGH);
                coord.select(dev.calamidade_awakening.api.QualityMode.AUTO);
                mc.getConnection().sendCommand("calamidade bench 24 mix");
                return null;
            });
            Thread.sleep(500);
            noServidor(() -> dev.calamidade_awakening.api.v1.Effects.start(
                net.minecraft.resources.ResourceLocation.parse("vfxsample:receita_protagonista"), level(),
                jogador().position().add(0, 0, 20)).isPresent());
            boolean trocou = false, viuHero = false;
            long fim = System.currentTimeMillis() + 11_000;
            while (System.currentTimeMillis() < fim) {
                boolean hero = noCliente(() -> dev.calamidade_awakening.client.ClientHooks.heroActive());
                viuHero |= hero;
                if (hero && noCliente(coord::profile) != dev.calamidade_awakening.api.QualityProfile.HIGH) trocou = true;
                Thread.sleep(250);
            }
            check("AUTO: não troca durante o efeito HERO (12 s de carga pesada)", viuHero && !trocou,
                "viu HERO " + viuHero + ", trocou " + trocou);
            boolean depois = aguardarMs(() -> noClienteSemErro(() -> coord.profile() != dev.calamidade_awakening.api.QualityProfile.HIGH), 40_000);
            check("AUTO: depois do HERO, desce", depois, noCliente(() -> coord.auto().reason().toString()));
        } finally {
            noCliente(() -> {
                mc.getConnection().sendCommand("calamidade bench stop");
                coord.select(dev.calamidade_awakening.api.QualityMode.AUTO);
                return null;
            });
            noServidor(() -> reserva.set(antes));
        }
    }

    private static Field campoReserva() throws NoSuchFieldException {
        Field campo = ServerConfig.class.getDeclaredField("PLAYER_RESERVE");
        campo.setAccessible(true);
        return campo;
    }

    private boolean aguardarMs(java.util.function.BooleanSupplier condicao, long ms) throws InterruptedException {
        long fim = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fim) {
            if (condicao.getAsBoolean()) return true;
            Thread.sleep(200);
        }
        return false;
    }

    /** Mínimos quadrados de mediana (ou p95) contra desenhados (ou passes) dos passos de um grupo, base incluída. */
    private static double inclinacao(List<CostProbe.Result> r, String grupo, boolean porEfeito, boolean p95) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (CostProbe.Result x : r) {
            if (!x.group().equals(grupo)) continue;
            double a = porEfeito ? x.drawn() : x.passes(), b = p95 ? x.p95() : x.median();
            sx += a; sy += b; sxx += a * a; sxy += a * b; n++;
        }
        double d = n * sxx - sx * sx;
        return d == 0 ? 0 : (n * sxy - sx * sy) / d;
    }

    private void captura(String nome) throws Exception {
        noCliente(() -> { Screenshot.grab(mc.gameDirectory, nome + ".png", mc.getMainRenderTarget(), (Component c) -> {}); return null; });
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
        int alvo = server().getTickCount() + n;
        while (server().getTickCount() < alvo) Thread.sleep(5);
    }

    private boolean aguardar(java.util.function.BooleanSupplier condicao, int maxTicks) throws Exception {
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
