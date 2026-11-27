package example.vfxsample.receitas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laboratório de integridade visual (motor 0.15.0, fase 4: coverage first). Cenas feitas para comparar os quatro perfis
 * com a mesma câmera e os mesmos efeitos (F8 alterna LOW, BALANCED, HIGH e ULTRA; F7 página 2 mostra para onde foi cada
 * parte da cena). Nada aqui contorna o motor: são instâncias comuns, com prioridades e políticas da API pública.
 *
 * <ul>
 *   <li>{@code /vfxsample cobertura arena}: 12 instâncias num anel de 14 blocos, cada uma uma runa grande, seis hexágonos,
 *       um disco e oito pontos (192 luzes, 84 cookies). No LOW a arena continua inteira, mais simples.</li>
 *   <li>{@code combate}: duas retículas {@code CRITICAL} (OVERRIDE) no meio de 64 luzes decorativas.</li>
 *   <li>{@code mista}: convencionais, vitrais RGB (COLOR), neon EMISSIVE e grade OVERRIDE na mesma cena.</li>
 *   <li>{@code cinema}: 8 instâncias com volume do motor, dois feixes e oito luzes: as que passam do limite de efeitos
 *       desenhados perdem o volume e os feixes, não a luz.</li>
 *   <li>{@code estresse <32|64|128|192> <grade|anel|aglomerado>}: carga com distribuições espaciais diferentes.</li>
 *   <li>{@code parar}: encerra as cenas do jogador.</li>
 * </ul>
 * Todas as cenas ficam 18 blocos à frente do jogador e viradas para ele, então a mesma posição e o mesmo olhar repetem
 * a mesma imagem (comparação de perfis e capturas).
 */
public final class LaboratorioCobertura {
    private LaboratorioCobertura() {}

    private static ResourceLocation id(String nome) { return ResourceLocation.fromNamespaceAndPath("vfxsample", "cobertura_" + nome); }

    static final int[] CORES = {0xff8ab4ff, 0xffffa040, 0xff60ff90, 0xffff60d0};
    static final ResourceLocation[] ARENA = new ResourceLocation[CORES.length];
    static final ResourceLocation RETICULA = id("reticula"), DECORACAO = id("decoracao"), MISTA = id("mista"),
        CINEMA = id("cinema"), CARGA = id("carga");

    /** Uma peça da arena: runa grande, seis hexágonos, disco e oito pontos (16 luzes, 7 cookies). */
    static Light[] arena(int cor) {
        int c = CORES[cor], c2 = CORES[(cor + 1) % CORES.length];
        List<Light> out = new ArrayList<>();
        out.add(Light.projector(Point.center(0, 6, 0), 7, 7).cookie(LaboratorioLuz.RUNAS).color(c).intensity(2.6f).radius(14)
            .falloff(.6f).softness(.05f).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build());
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            out.add(Light.projector(Point.center(Math.cos(a) * 4.5, 5, Math.sin(a) * 4.5), 2.4f, 2.4f).cookie(LaboratorioLuz.HEX)
                .color(c2).intensity(2).radius(9).falloff(.5f).roll(i * 30).priority(Light.Priority.DECORATIVE)
                .track(Light.Param.WEIGHT, "entrada").build());
        }
        out.add(Light.disc(Point.center(), 6).color(c).intensity(.7f).priority(Light.Priority.DECORATIVE)
            .track(Light.Param.WEIGHT, "entrada").build());
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + .4;
            out.add(Light.point(Point.center(Math.cos(a) * 3, 1, Math.sin(a) * 3)).color(c2).intensity(1.6f).radius(4)
                .priority(Light.Priority.DECORATIVE).track(Light.Param.WEIGHT, "entrada").build());
        }
        return out.toArray(Light[]::new);
    }

    /** Retícula de perigo: informação de combate, nunca trocada por decoração (CRITICAL, OVERRIDE, sem fallback). */
    static final Light[] RETICULA_LUZES = {
        Light.projector(Point.center(0, 7, 0), 7, 7).cookie(Light.Cookie.of(LaboratorioLuz.ATAQUE).sharpness(1)
                .fallback(Light.Cookie.Fallback.NONE))
            .color(0xffff1020).intensity(1.1f).radius(10).attenuation(Light.Attenuation.CONSTANT).softness(.01f).contrast(4)
            .blend(Light.Blend.OVERRIDE).priority(Light.Priority.CRITICAL).track(Light.Param.ROLL, "giro")
            .track(Light.Param.WEIGHT, "entrada").build(),
        Light.projector(Point.center(0, 7, 0), 7, 7).cookie(Light.Cookie.of(LaboratorioLuz.ATAQUE).sharpness(1))
            .color(0xffff3020).intensity(.35f).radius(10).attenuation(Light.Attenuation.CONSTANT).softness(.01f).contrast(4)
            .blend(Light.Blend.EMISSIVE).priority(Light.Priority.CRITICAL).track(Light.Param.ROLL, "giro")
            .track(Light.Param.WEIGHT, "entrada").build()};

    /** 16 luzes decorativas (8 cookies de runas e 8 pontos) em volta do centro. */
    static Light[] decoracao() {
        List<Light> out = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8, r = 3 + (i % 2) * 2.5;
            Point at = Point.center(Math.cos(a) * r, i % 2 == 0 ? 5 : 1, Math.sin(a) * r);
            out.add(i % 2 == 0
                ? Light.projector(at, 3, 3).cookie(LaboratorioLuz.RUNAS).color(0xff60c0ff).intensity(3).radius(9).falloff(.5f)
                    .roll(i * 20).priority(Light.Priority.DECORATIVE).track(Light.Param.WEIGHT, "entrada").build()
                : Light.point(at).color(0xffffb070).intensity(1.8f).radius(5).priority(Light.Priority.DECORATIVE)
                    .track(Light.Param.WEIGHT, "entrada").build());
        }
        return out.toArray(Light[]::new);
    }

    /** Iluminação mista: 4 convencionais, 4 vitrais RGB, 4 neon EMISSIVE e 4 grades OVERRIDE (16 luzes). */
    static Light[] mista() {
        List<Light> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2, x = Math.cos(a), z = Math.sin(a);
            out.add(Light.point(Point.center(x * 2, 1.2, z * 2)).color(0xffffa050).intensity(2.2f).radius(6).build());
            out.add(Light.projector(Point.center(x * 5, 6, z * 5), 4, 4).cookie(Light.Cookie.of(LaboratorioLuz.VITRAL).colored())
                .intensity(3).radius(9).falloff(.5f).build());
            out.add(Light.projector(Point.center((x - z) * 3.5, 6, (z + x) * 3.5), 3, 3).cookie(LaboratorioLuz.NEON)
                .color(0xffff40ff).intensity(2.5f).radius(9).blend(Light.Blend.EMISSIVE).contrast(2).track(Light.Param.ROLL, "giro").build());
            out.add(Light.projector(Point.center((x + z) * 7, 6, (z - x) * 7), 3.5f, 3.5f)
                .cookie(Light.Cookie.of(LaboratorioLuz.HEX).repeat().scale(2)).color(0xff40e0ff).intensity(1).radius(9)
                .blend(Light.Blend.OVERRIDE).blendStrength(.6f).build());
        }
        return out.toArray(Light[]::new);
    }

    /** Evento cinematográfico: dois pilares (feixes) e oito luzes; o volume do motor é a parte cara. */
    static final Beam[] CINEMA_FEIXES = {
        Beam.builder(Point.center(-1.5, 0, 0), Point.center(-1.5, 16, 0)).width(.18f, .9f).color(0xffffffff, 0xff8ab4ff)
            .pulses(3, 1.2f, .25f).track(Beam.Param.WEIGHT, "entrada").build(),
        Beam.builder(Point.center(1.5, 0, 0), Point.center(1.5, 16, 0)).width(.18f, .9f).color(0xffffffff, 0xffffa040)
            .pulses(3, 1.2f, .25f).track(Beam.Param.WEIGHT, "entrada").build()};

    static Light[] cinema() {
        List<Light> out = new ArrayList<>();
        out.add(Light.projector(Point.center(0, 6, 0), 6, 6).cookie(LaboratorioLuz.RUNAS).color(0xffc080ff).intensity(2.6f)
            .radius(14).track(Light.Param.ROLL, "giro").track(Light.Param.WEIGHT, "entrada").build());
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            out.add(Light.point(Point.center(Math.cos(a) * 3, 1, Math.sin(a) * 3)).color(i % 2 == 0 ? 0xff8ab4ff : 0xffffa040)
                .intensity(1.8f).radius(5).track(Light.Param.WEIGHT, "entrada").build());
        }
        return out.toArray(Light[]::new);
    }

    /** Carga de 16 luzes (8 cookies) numa grade 4 x 4 de 3 blocos. */
    static Light[] carga() {
        Light[] out = new Light[16];
        for (int i = 0; i < 16; i++) {
            double x = (i % 4 - 1.5) * 3, z = (i / 4 - 1.5) * 3;
            out[i] = i % 2 == 0
                ? Light.projector(Point.center(x, 5, z), 2.6f, 2.6f).cookie(LaboratorioLuz.RUNAS).color(0xff80b0ff).intensity(2)
                    .radius(9).falloff(.5f).roll(i * 22.5f).softness(.04f).build()
                : Light.point(Point.center(x, 1.2, z)).color(0xffffa050).intensity(1.6f).radius(5).build();
        }
        return out;
    }

    public static void register() {
        for (int c = 0; c < CORES.length; c++) {
            ARENA[c] = id("arena_" + c);
            registrar(ARENA[c], 7, Effects.Shape.NONE, Effects.Material.NONE, arena(c));
        }
        registrar(RETICULA, 7, Effects.Shape.NONE, Effects.Material.NONE, RETICULA_LUZES);
        registrar(DECORACAO, 7, Effects.Shape.NONE, Effects.Material.NONE, decoracao());
        registrar(MISTA, 9, Effects.Shape.NONE, Effects.Material.NONE, mista());
        registrar(CINEMA, 4, Effects.Shape.RINGS, Effects.Material.NEBULA, cinema());
        Effects.beams(CINEMA, CINEMA_FEIXES);
        registrar(CARGA, 7, Effects.Shape.NONE, Effects.Material.NONE, carga());
    }

    private static void registrar(ResourceLocation id, double raio, Effects.Shape forma, Effects.Material material, Light... luzes) {
        Effects.register(id, new Effects.Definition(LaboratorioLuz.LACO.duration(), raio, 0xff8ab4ff, forma, material, null, null)
            .withRange(128));
        Effects.timeline(id, LaboratorioLuz.LACO);
        Effects.lights(id, luzes);
    }

    private static final Map<UUID, List<Effects.Handle>> VIVAS = new HashMap<>();

    /** /vfxsample cobertura ... */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        var raiz = Commands.literal("cobertura");
        raiz.then(Commands.literal("arena").executes(ctx -> arena(ctx.getSource())));
        raiz.then(Commands.literal("combate").executes(ctx -> combate(ctx.getSource())));
        raiz.then(Commands.literal("mista").executes(ctx -> grade(ctx.getSource(), MISTA, 4, 16, "mista: convencionais, RGB, EMISSIVE e OVERRIDE")));
        raiz.then(Commands.literal("cinema").executes(ctx -> grade(ctx.getSource(), CINEMA, 8, 9,
            "cinema: 8 instâncias com volume, feixes e luzes; acima do limite de efeitos sobra a luz")));
        var n = Commands.argument("n", IntegerArgumentType.integer(16, 192))
            .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("32", "64", "128", "192"), b));
        for (String d : List.of("grade", "anel", "aglomerado"))
            n.then(Commands.literal(d).executes(ctx -> estresse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"), d)));
        raiz.then(Commands.literal("estresse").then(n));
        raiz.then(Commands.literal("parar").executes(ctx -> {
            int k = parar(ctx.getSource().getPlayerOrException().getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("cobertura: " + k + " instâncias encerradas"), false);
            return k;
        }));
        return raiz;
    }

    /** Centro das cenas: 18 blocos à frente, no chão do jogador; frente = direção do olhar na horizontal. */
    private static Vec3[] eixo(ServerPlayer player) {
        Vec3 frente = player.getLookAngle().multiply(1, 0, 1);
        if (frente.lengthSqr() < 1e-4) frente = new Vec3(0, 0, 1);
        frente = frente.normalize();
        Vec3 direita = new Vec3(-frente.z, 0, frente.x);
        return new Vec3[] {player.position().add(frente.scale(18)), frente, direita};
    }

    private static boolean iniciar(ServerPlayer player, ResourceLocation id, Vec3 pos) {
        var h = Effects.start(id, player.serverLevel(), Placement.at(pos, player.getYRot(), 0).withCaster(player));
        h.ifPresent(handle -> VIVAS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(handle));
        return h.isPresent();
    }

    private static int arena(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3[] e = eixo(player);
        int ok = 0;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            Vec3 pos = e[0].add(e[1].scale(Math.cos(a) * 14)).add(e[2].scale(Math.sin(a) * 14));
            if (iniciar(player, ARENA[i % ARENA.length], pos)) ok++;
        }
        int k = ok;
        source.sendSuccess(() -> Component.literal("arena: " + k + " instâncias, " + k * 16 + " luzes (" + k * 7
            + " cookies). F8 compara perfis; F7 página 2 explica cada perda."), false);
        return ok;
    }

    private static int combate(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3[] e = eixo(player);
        int ok = 0;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            if (iniciar(player, DECORACAO, e[0].add(e[1].scale(Math.cos(a) * 9)).add(e[2].scale(Math.sin(a) * 9)))) ok++;
        }
        if (iniciar(player, RETICULA, e[0].add(e[2].scale(-4)))) ok++;
        if (iniciar(player, RETICULA, e[0].add(e[2].scale(4)).add(e[1].scale(-3)))) ok++;
        int k = ok;
        source.sendSuccess(() -> Component.literal("combate: 2 retículas CRITICAL entre 64 luzes decorativas (" + k + " instâncias)"), false);
        return ok;
    }

    private static int grade(CommandSourceStack source, ResourceLocation id, int n, double passo, String texto) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3[] e = eixo(player);
        int lado = (int) Math.ceil(Math.sqrt(n)), ok = 0;
        for (int i = 0; i < n; i++) {
            double gx = (i % lado - (lado - 1) / 2.0) * passo, gz = (i / lado - (lado - 1) / 2.0) * passo;
            if (iniciar(player, id, e[0].add(e[2].scale(gx)).add(e[1].scale(gz)))) ok++;
        }
        int k = ok;
        source.sendSuccess(() -> Component.literal(texto + " (" + k + " instâncias)"), false);
        return ok;
    }

    private static int estresse(CommandSourceStack source, int luzes, String distribuicao) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3[] e = eixo(player);
        int n = (luzes + 15) / 16, ok = 0;
        for (int i = 0; i < n; i++) {
            Vec3 pos = switch (distribuicao) {
                case "anel" -> {
                    double a = i * Math.PI * 2 / n;
                    yield e[0].add(e[1].scale(Math.cos(a) * 22)).add(e[2].scale(Math.sin(a) * 22));
                }
                case "aglomerado" -> {
                    // Três grupos de instâncias quase sobrepostas (pior caso por tile).
                    int g = i % 3;
                    yield e[0].add(e[2].scale((g - 1) * 16)).add(e[1].scale((i / 3) * 1.5));
                }
                default -> {
                    int lado = (int) Math.ceil(Math.sqrt(n));
                    yield e[0].add(e[2].scale((i % lado - (lado - 1) / 2.0) * 14)).add(e[1].scale((i / lado - (lado - 1) / 2.0) * 14));
                }
            };
            if (iniciar(player, CARGA, pos)) ok++;
        }
        int k = ok;
        source.sendSuccess(() -> Component.literal("estresse " + distribuicao + ": " + k * 16 + " luzes em " + k + " instâncias"), false);
        return ok;
    }

    /** Encerra as cenas de um jogador (também ao sair). */
    public static int parar(UUID player) {
        int n = 0;
        var lista = VIVAS.remove(player);
        if (lista != null) for (var h : lista) if (h.stop()) n++;
        return n;
    }

    public static void limpar() { VIVAS.clear(); }
}
