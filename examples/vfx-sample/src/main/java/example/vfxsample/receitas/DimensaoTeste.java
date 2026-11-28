package example.vfxsample.receitas;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.SkyProfiles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Receita 26 — a dimensão de teste com céu próprio (docs/ai/receitas/26-dimensao-teste.md). A dimensão
 * {@code vfxsample:dimensao_teste} vem do datapack do próprio exemplo (data/vfxsample/dimension e dimension_type,
 * terreno plano) e o tipo dela usa os efeitos {@code vfxsample:dimensao_teste}, registrados no cliente como
 * {@code ProfileSkyEffects}: assim o motor pode tirar o céu do jogo inteiro no estado "caos".
 * Três estados: calmo (aurora e uma lua gigante), ameaca (planeta vermelho com anéis, fenda, céu avermelhado, sem o
 * sol do jogo) e caos (buraco negro, raios ao longe, fenda aberta e um céu só do motor).
 * Teste: /vfxsample dimensao (vai para lá), /vfxsample ceu calmo|ameaca|caos (5 s de transição).
 */
public final class DimensaoTeste {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "dimensao_teste");

    static final SkyProfile CEU = SkyProfile.builder()
        .state("calmo", SkyProfile.State.builder()
            .layers(Sky.aurora().progress(.55f).build(),
                Sky.body().direction(60, 28).size(26).progress(.75f).color(0xffd8d2c4, 0xff9ac4ff).build())
            .build())
        .state("ameaca", SkyProfile.State.builder()
            .layers(Sky.body().direction(-30, 22).size(44).tilt(18).progress(.6f).color(0xffc0503a, 0xffff9060).build(),
                Sky.rift().direction(40, 50).size(70).rotation(-25).progress(.45f).build())
            .atmosphere(Atmosphere.builder().sky(0xff2a0a10, 0xffa03a2a, .65f).fog(0xff6a2018, .35f, 40, 200)
                .exposure(.85f).ambient(0xffff8060, .15f).build())
            .vanillaSun(false)
            .build())
        .state("caos", SkyProfile.State.builder()
            .layers(Sky.blackHole().direction(0, 32).size(60).build(),
                Sky.bolts().progress(1).build(),
                Sky.rift().direction(-70, 45).size(90).rotation(15).progress(1).build())
            .atmosphere(Atmosphere.builder().sky(0xff05020c, 0xff3a1030, 1f).fog(0xff1a0820, .45f, 30, 160)
                .exposure(.7f).stars(1).build())
            .vanillaSky(false)
            .build())
        .initial("calmo")
        .build();

    private DimensaoTeste() {}

    /** Construtor comum do mod, nos dois lados: o perfil da dimensão. */
    public static void register() {
        SkyProfiles.define(ID, CEU);
    }

    /** /vfxsample ceu &lt;estado&gt;: troca o céu da dimensão de teste em 100 ticks (5 s), para todos que estão lá. */
    public static LiteralArgumentBuilder<CommandSourceStack> comandoCeu() {
        return Commands.literal("sky").then(Commands.argument("state", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CEU.states().keySet(), builder))
            .executes(ctx -> {
                String estado = StringArgumentType.getString(ctx, "state");
                if (!CEU.has(estado)) {
                    ctx.getSource().sendFailure(Component.translatable("command.vfxsample.ceu.unknown", estado));
                    return 0;
                }
                boolean ok = SkyProfiles.setState(ctx.getSource().getServer(), ID, estado, 100);
                ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "command.vfxsample.ceu.ok"
                    : "command.vfxsample.ceu.missing", estado), true);
                return ok ? 1 : 0;
            }));
    }

    /** /vfxsample dimensao: leva o jogador para a dimensão de teste (e /vfxsample dimensao voltar, para o Overworld). */
    public static LiteralArgumentBuilder<CommandSourceStack> comandoDimensao() {
        return Commands.literal("dimension")
            .executes(ctx -> ir(ctx.getSource(), ResourceKey.create(Registries.DIMENSION, ID), 0.5, 8, 0.5))
            .then(Commands.literal("back").executes(DimensaoTeste::voltar))
            .then(Commands.literal("voltar").executes(DimensaoTeste::voltar));
    }

    private static int voltar(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var spawn = ctx.getSource().getServer().overworld().getSharedSpawnPos();
        return ir(ctx.getSource(), net.minecraft.world.level.Level.OVERWORLD, spawn.getX() + .5, spawn.getY() + 1,
            spawn.getZ() + .5);
    }

    private static int ir(CommandSourceStack source, ResourceKey<net.minecraft.world.level.Level> key, double x, double y,
                          double z) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = source.getServer().getLevel(key);
        if (level == null) {
            source.sendFailure(Component.translatable("command.vfxsample.ceu.missing", key.location().toString()));
            return 0;
        }
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        return 1;
    }

    /** Só no cliente: os efeitos da dimensão, para o motor poder trocar o céu do jogo inteiro. */
    public static final class Client {
        private Client() {}

        public static void effects(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
            event.register(ID, new dev.calamidade_awakening.api.v1.client.ProfileSkyEffects(
                new net.minecraft.client.renderer.DimensionSpecialEffects.OverworldEffects()));
        }
    }
}
