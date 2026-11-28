package example.vfxsample;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Command aliases (1.14.1). English names are canonical; the Portuguese names of earlier versions stay as deprecated
 * aliases so existing scripts and tests keep working. An alias is a literal with the canonical node's requirement and
 * command whose children are the canonical node's own child nodes (shared, not copied), so both share one
 * implementation and one argument tree. Unlike a Brigadier redirect, this keeps the arguments parsed before the alias
 * (for example {@code load <n> mesmo}) in the same command context.
 */
public final class Cmd {
    private Cmd() {}

    /**
     * Adds {@code canonical} under {@code parent}, then one redirecting alias per name in {@code legacy}. Returns the
     * built canonical node.
     */
    public static <S, T extends ArgumentBuilder<S, T>> LiteralCommandNode<S> then(T parent, LiteralArgumentBuilder<S> canonical,
                                                                              String... legacy) {
        LiteralCommandNode<S> node = canonical.build();
        parent.then(node);
        for (String name : legacy) {
            if (name.equals(node.getLiteral())) continue;
            LiteralArgumentBuilder<S> alias = LiteralArgumentBuilder.<S>literal(name).requires(node.getRequirement());
            if (node.getCommand() != null) alias.executes(node.getCommand());
            for (var child : node.getChildren()) alias.then(child);
            parent.then(alias);
        }
        return node;
    }
}
