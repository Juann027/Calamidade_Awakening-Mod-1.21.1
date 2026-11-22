package dev.calamidade_awakening.teste;

import dev.calamidade_awakening.client.holo.AtlasWorld;
import dev.calamidade_awakening.net.StageNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Teste em jogo do Atlas Estelar no mundo (0.11.2), com {@code calamidade.teste=atlas}. Toda entrada passa pelo caminho
 * real do jogo: {@code MouseHandler.onPress} e {@code onScroll} (o evento do NeoForge, o roteador do Atlas e, quando o
 * Atlas não é dono, o vanilla). Em criativo, onde o clique esquerdo quebra um bloco na hora: clique numa placa com um
 * bloco atrás (bloco intacto, a aba muda), clique num cartão com uma vaca atrás (vaca sem dano, a habilidade é conjurada
 * pelo servidor e o Atlas se fecha), o mesmo clique sem o Atlas (o bloco quebra: controle), roda sobre a placa (página
 * muda, slot da barra igual), Recall, fechamento por distância, troca de dimensão e morte. Capturas de cada aba em
 * {@code screenshots/}.
 */
final class RoteiroAtlas implements Runnable {
    private static final Path RELATORIO = Path.of("calamidade-atlas.txt");
    private static boolean iniciado;

    private final Minecraft mc = Minecraft.getInstance();
    private int passou, falhou;

    static void registrar() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (iniciado || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            iniciado = true;
            Thread thread = new Thread(new RoteiroAtlas(), "Calamidade teste do Atlas");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public void run() {
        try {
            escrever("# Calamidade_Awakening: teste em jogo do Atlas Estelar, " + java.time.LocalDateTime.now(), false);
            escrever("# " + noCliente(() -> org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)), true);
            cenarios();
            escrever("FIM atlas", true);
        } catch (Throwable failure) {
            check("roteiro do Atlas sem exceção", false, failure.toString());
            failure.printStackTrace();
        } finally {
            System.out.println("[TESTE] atlas: " + passou + " passaram, " + falhou + " falharam");
            mc.execute(mc::stop);
        }
    }

    private MinecraftServer server() { return mc.getSingleplayerServer(); }

    private ServerLevel level() { return server().overworld(); }

    private ServerPlayer jogador() { return server().getPlayerList().getPlayers().get(0); }

    private static final int X = -6000, Z = 0;

    private void cenarios() throws Exception {
        ticks(40);
        noServidor(() -> {
            server().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            level().setDayTime(6000);
            server().getPlayerList().op(jogador().getGameProfile());
            for (int cx = (X - 48) >> 4; cx <= (X + 48) >> 4; cx++)
                for (int cz = (Z - 16) >> 4; cz <= (Z + 48) >> 4; cz++) level().setChunkForced(cx, cz, true);
            jogador().setGameMode(GameType.CREATIVE);
            jogador().getAbilities().flying = true;
            jogador().onUpdateAbilities();
            jogador().teleportTo(level(), X + .5, 2, Z + .5, 0, 0);
        });
        noCliente(() -> { mc.options.pauseOnLostFocus = false; mc.mouseHandler.grabMouse(); return null; });
        ticks(40);
        aguardar(() -> noClienteSemErro(() -> mc.levelRenderer.hasRenderedAllSections()), 1200);
        ticks(20);

        abrir();
        abasECapturas();
        cliqueComBlocoAtras();
        roda();
        recall();
        cartaoComVacaAtras();
        controleSemAtlas();
        fechamentos();
    }

    // ------------------------------------------------------------------------------------------------ cenários

    private void abrir() throws Exception {
        noCliente(() -> { AtlasWorld.summon(); return null; });
        Thread.sleep(120);
        captura("atlas-abertura-1");
        Thread.sleep(250);
        captura("atlas-abertura-2");
        Thread.sleep(1500);
        List<String> paineis = noCliente(AtlasWorld::visiblePanels);
        check("Atlas aberto no mundo: abas, 5 cartões e páginas", noCliente(AtlasWorld::isOpen)
            && paineis.containsAll(List.of("tab0", "tab4", "card0", "card1", "card2", "card3", "card4", "pages")), paineis.toString());
        // O jogador anda: o Atlas fica parado no mundo.
        Vec3 antes = noCliente(() -> AtlasWorld.worldPoint("card0", 75, 52));
        noServidor(() -> jogador().teleportTo(level(), X + 1.5, 2, Z - 1.5, 20, 5));
        ticks(10);
        Thread.sleep(500);
        Vec3 depois = noCliente(() -> AtlasWorld.worldPoint("card0", 75, 52));
        check("placas paradas no mundo enquanto o jogador anda e olha", antes != null && depois != null && antes.distanceTo(depois) < 1e-3,
            antes + " → " + depois);
        captura("atlas-andando");
        noServidor(() -> jogador().teleportTo(level(), X + .5, 2, Z + .5, 0, 0));
        ticks(10);
        Thread.sleep(500);
    }

    private void abasECapturas() throws Exception {
        String[] nomes = {"habilidades", "qualidade", "acessibilidade", "terreno", "diagnostico"};
        for (int i = nomes.length - 1; i >= 0; i--) {
            int aba = i;
            noCliente(() -> { AtlasWorld.selectTab(aba); return null; });
            Thread.sleep(1600);
            captura("atlas-" + nomes[i]);
            check("aba " + nomes[i] + " mostrada", noCliente(AtlasWorld::tab) == i, "");
        }
    }

    /** Clique esquerdo (criativo: quebra na hora) numa aba com pedra atrás: a aba muda, a pedra fica. */
    private void cliqueComBlocoAtras() throws Exception {
        Vec3 alvo = noCliente(() -> AtlasWorld.worldPoint("tab1", 46, 10));
        Vec3 olho = noCliente(() -> mc.player.getEyePosition());
        Vec3 dir = alvo.subtract(olho).normalize();
        BlockPos pedra = BlockPos.containing(olho.add(dir.scale(4.2)));
        noServidor(() -> level().setBlockAndUpdate(pedra, Blocks.STONE.defaultBlockState()));
        mirar(alvo);
        int slot = noCliente(() -> mc.player.getInventory().selected);
        clicar(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        clicar(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ticks(10);
        boolean intacta = noServidor(() -> level().getBlockState(pedra).is(Blocks.STONE));
        check("clique na placa não quebra a pedra atrás (criativo)", intacta, pedra.toString());
        check("clique na placa muda a aba (Qualidade)", noCliente(AtlasWorld::tab) == 1, "aba " + noCliente(AtlasWorld::tab));
        check("clique na placa não troca o item da mão", noCliente(() -> mc.player.getInventory().selected) == slot, "");
        // Clique direito (usar) com a pedra atrás e um bloco na mão: nada é colocado.
        noServidor(() -> jogador().getInventory().setItem(jogador().getInventory().selected,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64)));
        ticks(5);
        clicar(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        ticks(10);
        int tabuas = noServidor(() -> jogador().getInventory().getSelected().getCount());
        boolean semTabua = noServidor(() -> !level().getBlockState(pedra.relative(net.minecraft.core.Direction.NORTH)).is(Blocks.OAK_PLANKS)
            && !level().getBlockState(pedra.above()).is(Blocks.OAK_PLANKS));
        check("clique direito na placa não coloca bloco atrás", semTabua, tabuas + " tábuas na mão");
        noServidor(() -> jogador().getInventory().setItem(jogador().getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY));
        noCliente(() -> { AtlasWorld.selectTab(0); return null; });
        Thread.sleep(1200);
    }

    /** Roda com a mira num cartão: a página muda (há páginas de addons) e o slot da barra não. */
    private void roda() throws Exception {
        mirar(noCliente(() -> AtlasWorld.worldPoint("card0", 75, 60)));
        int slot = noCliente(() -> mc.player.getInventory().selected);
        int[] antes = noCliente(AtlasWorld::page);
        rolar(-1);
        Thread.sleep(800);
        int depois = noCliente(() -> mc.player.getInventory().selected);
        check("roda sobre a placa não troca o slot da barra", depois == slot, slot + " → " + depois);
        int[] depoisPagina = noCliente(AtlasWorld::page);
        check(String.format(Locale.ROOT, "roda sobre um cartão troca a página (%d páginas com o vfx-sample)", antes[1]),
            antes[1] > 1 && depoisPagina[0] != antes[0], antes[0] + " → " + depoisPagina[0]);
        captura("atlas-pagina-addon");
        rolar(1);
        Thread.sleep(800);
        // Fora das placas, a roda volta a ser da barra.
        mirarDirecao(180, 0);
        rolar(-1);
        ticks(5);
        check("fora das placas a roda troca o slot da barra", noCliente(() -> mc.player.getInventory().selected) != slot, "");
        mirarDirecao(0, 0);
    }

    private void recall() throws Exception {
        Vec3 antes = noCliente(() -> AtlasWorld.worldPoint("tab2", 46, 10));
        mirarDirecao(90, 0);
        noCliente(() -> { AtlasWorld.recall(); return null; });
        Thread.sleep(1500);
        Vec3 depois = noCliente(() -> AtlasWorld.worldPoint("tab2", 46, 10));
        Vec3 olho = noCliente(() -> mc.player.getEyePosition());
        Vec3 olhar = noCliente(() -> mc.player.getLookAngle());
        double frente = depois == null ? -1 : depois.subtract(olho).normalize().dot(olhar);
        check("Recall leva o Atlas para a frente da mira", antes != null && depois != null && antes.distanceTo(depois) > 1 && frente > .7,
            String.format(Locale.ROOT, "frente %.2f", frente));
        mirarDirecao(0, 0);
        noCliente(() -> { AtlasWorld.recall(); return null; });
        Thread.sleep(1500);
    }

    /** Clique num cartão com uma vaca atrás: a vaca não sofre dano e a habilidade é conjurada pelo servidor. */
    private void cartaoComVacaAtras() throws Exception {
        Vec3 alvo = noCliente(() -> AtlasWorld.worldPoint("card0", 75, 70));
        Vec3 olho = noCliente(() -> mc.player.getEyePosition());
        Vec3 dir = alvo.subtract(olho).normalize();
        Vec3 onde = olho.add(dir.scale(4.0)).subtract(0, .7, 0);
        int vaca = noServidor(() -> {
            Cow cow = EntityType.COW.create(level());
            cow.moveTo(onde.x, onde.y, onde.z, 180, 0);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            level().addFreshEntity(cow);
            return cow.getId();
        });
        ticks(20);
        mirar(alvo);
        float vida = noServidor(() -> ((Cow) level().getEntity(vaca)).getHealth());
        int vivas = StageNetwork.liveCount();
        clicar(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ticks(10);
        float depois = noServidor(() -> ((Cow) level().getEntity(vaca)).getHealth());
        check("clique no cartão não fere a vaca atrás", depois == vida, vida + " → " + depois);
        boolean conjurou = aguardar(() -> StageNetwork.liveCount() > vivas, 40);
        check("cartão conjura pela requisição ao servidor", conjurou, "ativas " + vivas + " → " + StageNetwork.liveCount());
        boolean fechou = aguardar(() -> !noClienteSemErro(AtlasWorld::isOpen), 60);
        check("depois de conjurar, o Atlas se fecha", fechou, "");
        noServidor(() -> { var e = level().getEntity(vaca); if (e != null) e.discard(); });
        ticks(60);
    }

    /** Controle: sem o Atlas, o mesmo clique quebra o bloco (o teste acima é significativo). */
    private void controleSemAtlas() throws Exception {
        Vec3 olho = noCliente(() -> mc.player.getEyePosition());
        BlockPos pedra = BlockPos.containing(olho.add(0, 0, 3.5));
        noServidor(() -> level().setBlockAndUpdate(pedra, Blocks.STONE.defaultBlockState()));
        mirar(Vec3.atCenterOf(pedra));
        ticks(5);
        clicar(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ticks(10);
        check("controle: sem o Atlas o clique quebra a pedra", noServidor(() -> level().getBlockState(pedra).isAir()), "");
    }

    private void fechamentos() throws Exception {
        // Distância: mais de 24 blocos da âncora dispensa.
        noCliente(() -> { AtlasWorld.summon(); return null; });
        Thread.sleep(1200);
        noServidor(() -> jogador().teleportTo(level(), X + .5, 2, Z + 40.5, 0, 0));
        boolean longe = aguardar(() -> !noClienteSemErro(AtlasWorld::isOpen), 60);
        check("a mais de 24 blocos o Atlas se fecha", longe, "");
        noServidor(() -> jogador().teleportTo(level(), X + .5, 2, Z + .5, 0, 0));
        ticks(20);
        // Troca de dimensão.
        noCliente(() -> { AtlasWorld.summon(); return null; });
        Thread.sleep(1200);
        noServidor(() -> {
            ServerLevel nether = server().getLevel(Level.NETHER);
            jogador().teleportTo(nether, 0.5, 70, 0.5, 0, 0);
        });
        boolean dimensao = aguardar(() -> !noClienteSemErro(AtlasWorld::isOpen), 200);
        check("troca de dimensão fecha o Atlas", dimensao, "");
        ticks(40);
        noServidor(() -> jogador().teleportTo(level(), X + .5, 2, Z + .5, 0, 0));
        aguardar(() -> noClienteSemErro(() -> mc.level != null && mc.level.dimension() == Level.OVERWORLD), 400);
        ticks(60);
        // Morte.
        noCliente(() -> { AtlasWorld.summon(); return null; });
        Thread.sleep(1200);
        check("Atlas aberto antes da morte", noCliente(AtlasWorld::isOpen), "");
        noServidor(() -> jogador().kill());
        boolean morte = aguardar(() -> !noClienteSemErro(AtlasWorld::isOpen), 100);
        check("morte fecha o Atlas", morte, "");
        noCliente(() -> { if (mc.player != null) mc.player.respawn(); return null; });
        ticks(60);
        noCliente(() -> { mc.setScreen(null); return null; });
        ticks(20);
        noCliente(() -> { AtlasWorld.summon(); return null; });
        Thread.sleep(800);
        check("depois do renascimento o Atlas abre de novo", noCliente(AtlasWorld::isOpen), "");
        noCliente(() -> { AtlasWorld.toggle(); return null; });
        boolean fechou = aguardar(() -> !noClienteSemErro(AtlasWorld::isOpen), 60);
        check("N fecha com a animação", fechou, "");
    }

    // ------------------------------------------------------------------------------------------------ apoio

    /** Vira a cabeça do jogador (cliente e servidor) para um ponto e espera o vanilla escolher o alvo. */
    private void mirar(Vec3 alvo) throws Exception {
        Vec3 olho = noCliente(() -> mc.player.getEyePosition());
        Vec3 d = alvo.subtract(olho).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.asin(d.y));
        mirarDirecao(yaw, pitch);
    }

    private void mirarDirecao(float yaw, float pitch) throws Exception {
        noCliente(() -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
            return null;
        });
        ticks(4);
        Thread.sleep(400);
    }

    private void clicar(int botao) throws Exception {
        Method press = MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        press.setAccessible(true);
        noCliente(() -> {
            try {
                press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), botao, GLFW.GLFW_PRESS, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        ticks(3);
        noCliente(() -> {
            try {
                press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), botao, GLFW.GLFW_RELEASE, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        ticks(2);
    }

    private void rolar(double passo) throws Exception {
        Method scroll = MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
        scroll.setAccessible(true);
        noCliente(() -> {
            try {
                scroll.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0.0, passo);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        ticks(2);
    }

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
