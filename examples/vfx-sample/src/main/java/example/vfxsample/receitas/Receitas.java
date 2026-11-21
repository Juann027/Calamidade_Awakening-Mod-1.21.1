package example.vfxsample.receitas;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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
 * HERO do motor 0.9.0; só a onda de luz tem camada de cliente.
 */
public final class Receitas {
    /** Nome do comando, efeito e como ele começa: numa posição em relação ao jogador ou preso a ele. */
    record Receita(String nome, ResourceLocation id, Function<ServerPlayer, Vec3> posicao, boolean preso) {
        Receita(String nome, ResourceLocation id, Function<ServerPlayer, Vec3> posicao) { this(nome, id, posicao, false); }

        /** Inicia no servidor: Effects.follow (preso ao jogador) ou Effects.start na posição. */
        Optional<Effects.Handle> iniciar(ServerPlayer player) {
            return preso ? Effects.follow(id, player, Vec3.ZERO) : Effects.start(id, player.serverLevel(), posicao.apply(player));
        }
    }

    static final List<Receita> TODAS = List.of(
        new Receita("anel", AnelImpacto.ID, p -> chao(p, 6)),
        new Receita("cubo", CuboGrade.ID, p -> chao(p, 22).add(0, 8, 0)),
        new Receita("bolha", BolhaArcoIris.ID, p -> olhos(p, 7)),
        new Receita("impacto", ImpactFrame.ID, p -> olhos(p, 3)),
        new Receita("feixe", FeixeVertical.ID, p -> chao(p, 14)),
        new Receita("ambiente", AmbienteVagalumes.ID, p -> chao(p, 5)),
        new Receita("ceu", CeuTingido.ID, p -> p.position().add(0, 2, 0)),
        new Receita("pilar", PilarDeLuz.ID, p -> chao(p, 18)),
        new Receita("laser", LaserMira.ID, p -> p.position(), true),
        new Receita("teia", TeiaDeFeixes.ID, p -> chao(p, 12)),
        new Receita("fogueira", Fogueira.ID, p -> chao(p, 5)),
        new Receita("onda_luz", OndaDeLuz.ID, p -> chao(p, 6)),
        new Receita("protagonista", Protagonista.ID, p -> chao(p, 30)));

    /** Última receita iniciada por jogador, para release e stop. */
    private static final Map<UUID, Effects.Handle> ULTIMA = new HashMap<>();

    private Receitas() {}

    /** Construtor comum do mod. */
    public static void register() {
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
