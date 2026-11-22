package example.vfxsample.receitas;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Catálogo das receitas de docs/ai/receitas/ e o comando /vfxsample receita &lt;nome&gt;. Cada receita é uma classe com
 * register() (construtor comum do mod) e, quando tem camada de cliente, Client.setup() (FMLClientSetupEvent) e um
 * shader em assets/vfxsample/shaders/fx/receita_*.fsh. As receitas 8 a 13 (1.6.0) usam feixes, luzes e a prioridade
 * HERO do motor 0.9.0; só a onda de luz tem camada de cliente. As receitas 14 a 17 (1.7.0) usam a orientação e os
 * gatilhos do motor 0.10.0: o leque (comando), o cajado (item), os pilares (blocos) e o zumbi (mob vanilla). As
 * receitas 18 a 21 (1.8.0) usam a Terrain API do motor 0.11.0: o raio que abre uma trincheira, o meteoro que vira
 * blocos reais, a cratera simples e a torre que cai e fica; todas se desfazem com /calamidade terrain undo. As receitas
 * 22 a 26 (1.9.0) usam a Sky API, os eventos globais e o céu por dimensão do motor 0.12.0: buraco negro, portal para
 * outra galáxia, lua gigante, o eclipse como evento global (/vfxsample evento eclipse) e a dimensão de teste
 * (/vfxsample dimensao e /vfxsample ceu).
 */
public final class Receitas {
    /** Nome do comando, efeito e como ele começa no servidor. */
    record Receita(String nome, ResourceLocation id, Function<ServerPlayer, Optional<Effects.Handle>> inicio) {
        /** Effects.start numa posição em relação ao jogador. */
        static Receita em(String nome, ResourceLocation id, Function<ServerPlayer, Vec3> posicao) {
            return new Receita(nome, id, p -> Effects.start(id, p.serverLevel(), posicao.apply(p)));
        }

        /** Effects.follow: presa ao jogador. */
        static Receita presa(String nome, ResourceLocation id) {
            return new Receita(nome, id, p -> Effects.follow(id, p, Vec3.ZERO));
        }

        Optional<Effects.Handle> iniciar(ServerPlayer player) { return inicio.apply(player); }
    }

    static final List<Receita> TODAS = List.of(
        Receita.em("anel", AnelImpacto.ID, p -> chao(p, 6)),
        Receita.em("cubo", CuboGrade.ID, p -> chao(p, 22).add(0, 8, 0)),
        Receita.em("bolha", BolhaArcoIris.ID, p -> olhos(p, 7)),
        Receita.em("impacto", ImpactFrame.ID, p -> olhos(p, 3)),
        Receita.em("feixe", FeixeVertical.ID, p -> chao(p, 14)),
        Receita.em("ambiente", AmbienteVagalumes.ID, p -> chao(p, 5)),
        Receita.em("ceu", CeuTingido.ID, p -> p.position().add(0, 2, 0)),
        Receita.em("pilar", PilarDeLuz.ID, p -> chao(p, 18)),
        Receita.presa("laser", LaserMira.ID),
        Receita.em("teia", TeiaDeFeixes.ID, p -> chao(p, 12)),
        Receita.em("fogueira", Fogueira.ID, p -> chao(p, 5)),
        Receita.em("onda_luz", OndaDeLuz.ID, p -> chao(p, 6)),
        Receita.em("protagonista", Protagonista.ID, p -> chao(p, 30)),
        // 1.7.0 — orientação: o leque começa virado com o yaw do jogador (Placement).
        new Receita("leque", LequeOrientado.ID, LequeOrientado::iniciar),
        // 1.8.0 — Terrain API: as formas giram com o yaw do jogador.
        new Receita("raio_trincheira", RaioTrincheira.ID, RaioTrincheira::iniciar),
        new Receita("meteoro", Meteoro.ID, Meteoro::iniciar),
        Receita.em("cratera_simples", CrateraSimples.ID, p -> chao(p, 12)),
        new Receita("torre_caindo", TorreCaindo.ID, TorreCaindo::iniciar),
        // 1.9.0 — Sky API: camadas presas a pontos muito longe à frente do jogador.
        new Receita("buraco_negro", BuracoNegro.ID, BuracoNegro::iniciar),
        new Receita("portal_galaxia", PortalGalaxia.ID, PortalGalaxia::iniciar),
        new Receita("lua_gigante", LuaGigante.ID, LuaGigante::iniciar));

    /** Última receita iniciada por jogador, para release e stop. */
    private static final Map<UUID, Effects.Handle> ULTIMA = new HashMap<>();

    private Receitas() {}

    /** Construtor comum do mod; {@code bus} é o barramento do mod (itens, blocos e abas do criativo). */
    public static void register(IEventBus bus) {
        AnelImpacto.register();
        CuboGrade.register();
        BolhaArcoIris.register();
        ImpactFrame.register();
        FeixeVertical.register();
        AmbienteVagalumes.register();
        CeuTingido.register();
        PilarDeLuz.register();
        LaserMira.register();
        TeiaDeFeixes.register();
        Fogueira.register();
        OndaDeLuz.register();
        Protagonista.register();
        // 1.7.0 — orientação e gatilhos do motor 0.10.0.
        LequeOrientado.register();
        Cajado.register(bus);
        Pilares.register(bus);
        ZumbiFeixe.register();
        // 1.8.0 — Terrain API do motor 0.11.0.
        RaioTrincheira.register();
        Meteoro.register();
        CrateraSimples.register();
        TorreCaindo.register();
        // 1.9.0 — Sky API, eventos globais e céu por dimensão do motor 0.12.0.
        BuracoNegro.register();
        PortalGalaxia.register();
        LuaGigante.register();
        EclipseGlobal.register();
        DimensaoTeste.register();
        bus.addListener(Receitas::abas);
    }

    /** Cajado na aba de combate e pilares na de redstone do modo criativo. */
    private static void abas(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(Cajado.ITEM);
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(Pilares.PILAR_REDSTONE);
            event.accept(Pilares.PILAR_PERIODICO);
        }
    }

    /**
     * Só no cliente. CeuTingido, PilarDeLuz, LaserMira, TeiaDeFeixes, Fogueira e Protagonista não têm camada de
     * cliente: o motor desenha atmosfera, feixes e luzes sozinho.
     */
    public static void clientSetup() {
        AnelImpacto.Client.setup();
        CuboGrade.Client.setup();
        BolhaArcoIris.Client.setup();
        ImpactFrame.Client.setup();
        FeixeVertical.Client.setup();
        AmbienteVagalumes.Client.setup();
        OndaDeLuz.Client.setup();
    }

    /** /vfxsample receita &lt;nome&gt; | release | stop. */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        var raiz = Commands.literal("receita");
        for (Receita receita : TODAS)
            raiz.then(Commands.literal(receita.nome()).executes(ctx -> iniciar(ctx.getSource(), receita)));
        raiz.then(Commands.literal("release").executes(ctx -> liberar(ctx.getSource())));
        raiz.then(Commands.literal("stop").executes(ctx -> parar(ctx.getSource())));
        return raiz;
    }

    /** /vfxsample zumbi on|off: liga ou desliga o feixe dos zumbis (receita 17). */
    public static LiteralArgumentBuilder<CommandSourceStack> comandoZumbi() {
        return Commands.literal("zumbi")
            .then(Commands.literal("on").executes(ctx -> zumbi(ctx.getSource(), true)))
            .then(Commands.literal("off").executes(ctx -> zumbi(ctx.getSource(), false)));
    }

    private static int zumbi(CommandSourceStack source, boolean ligado) {
        ZumbiFeixe.ativo = ligado;
        source.sendSuccess(() -> Component.translatable(ligado ? "command.vfxsample.zumbi.on" : "command.vfxsample.zumbi.off"), true);
        return 1;
    }

    private static int iniciar(CommandSourceStack source, Receita receita) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        parar(player.getUUID());
        var instancia = receita.iniciar(player);
        instancia.ifPresent(handle -> ULTIMA.put(player.getUUID(), handle));
        source.sendSuccess(() -> instancia.isPresent()
            ? Component.translatable("command.vfxsample.receita.started", receita.nome())
            : Component.translatable("command.vfxsample.receita.failed", receita.nome()), false);
        return instancia.isPresent() ? 1 : 0;
    }

    private static int liberar(CommandSourceStack source) throws CommandSyntaxException {
        var handle = ULTIMA.get(source.getPlayerOrException().getUUID());
        boolean ok = handle != null && handle.release();
        source.sendSuccess(() -> Component.translatable(ok ? "command.vfxsample.receita.released"
            : "command.vfxsample.receita.none"), false);
        return ok ? 1 : 0;
    }

    private static int parar(CommandSourceStack source) throws CommandSyntaxException {
        boolean ok = parar(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.translatable(ok ? "command.vfxsample.receita.stopped"
            : "command.vfxsample.receita.none"), false);
        return ok ? 1 : 0;
    }

    /** Encerra a última receita do jogador; usado também ao sair do jogo. */
    public static boolean parar(UUID player) {
        var handle = ULTIMA.remove(player);
        return handle != null && handle.stop();
    }

    /** Servidor parou: esquece os handles. */
    public static void limpar() { ULTIMA.clear(); }

    /** Ponto no chão (altura dos pés), {@code distancia} blocos à frente na horizontal. */
    static Vec3 chao(ServerPlayer player, double distancia) {
        Vec3 frente = player.getLookAngle().multiply(1, 0, 1);
        if (frente.lengthSqr() < 1e-4) frente = new Vec3(0, 0, 1);
        return player.position().add(frente.normalize().scale(distancia));
    }

    /** Ponto na linha do olhar, {@code distancia} blocos à frente. */
    static Vec3 olhos(ServerPlayer player, double distancia) {
        return player.getEyePosition().add(player.getLookAngle().scale(distancia));
    }
}
