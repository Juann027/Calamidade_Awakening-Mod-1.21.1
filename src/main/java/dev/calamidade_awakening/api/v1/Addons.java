package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Publishes a registered effect as a player-selectable skill: a card in the Star Atlas and a row in the Awakening
 * Focus menu. Call during mod construction, after {@link Effects#register}. Publishing is optional: private effects
 * (boss phases, ambient effects) are started from your own code.
 *
 * <pre>{@code
 * Addons.resources("meumod", MeuMod.class);   // lets RimuLib find shaders in your JAR
 * Addons.publish(ID, new Addons.Card("ability.meumod.golpe", "Strike", "RESONANCE", 120,
 *     ResourceLocation.fromNamespaceAndPath("meumod", "golpe"), List.of()));
 * }</pre>
 */
public final class Addons {
    /**
     * One linear camera shot of a card, played on the caster's client.
     *
     * @param endTick last tick of this shot (ticks since the start), increasing along the list
     * @param camera  camera offset from the instance center, in blocks (up to 128)
     * @param fov     field of view in degrees, 25..110
     */
    public record Shot(int endTick, Vec3 camera, float fov) {
        public Shot {
            Objects.requireNonNull(camera);
            if (endTick < 1 || !Double.isFinite(camera.lengthSqr()) || camera.length()>128 ||
                !Float.isFinite(fov) || fov < 25 || fov > 110) throw new IllegalArgumentException("Invalid camera shot");
        }
    }
    /**
     * Card shown in the Atlas and the Focus.
     *
     * @param titleKey translation key of the title
     * @param fallback title used when the key is missing
     * @param tier     short label shown on the card (for example {@code "RESONANCE"})
     * @param cooldown per-player cooldown in ticks, 1..72000
     * @param shader   optional card shader (assets/&lt;ns&gt;/shaders/fx/&lt;path&gt;.fsh) with the CaCenter/CaEffect
     *                 uniforms, charged to the addon pass budget; null for none
     * @param camera   linear camera shots, possibly empty; replaced by {@code AddonLayers.film} when registered
     */
    public record Card(String titleKey, String fallback, String tier, int cooldown, ResourceLocation shader, List<Shot> camera) {
        public Card {
            if (titleKey.isBlank() || fallback.isBlank() || tier.isBlank() || cooldown < 1 || cooldown > 72000)
                throw new IllegalArgumentException("Invalid card metadata");
            camera = List.copyOf(camera);
            int previous = 0;
            for (Shot shot : camera) {
                if (shot.endTick() <= previous) throw new IllegalArgumentException("Camera ticks must increase");
                previous = shot.endTick();
            }
        }
    }
    /** Server callback of a card without power control. */
    @FunctionalInterface public interface Cast {
        /** Server thread. {@code target}: aimed point in blocks. Return true when accepted to apply the card's cooldown. */
        boolean cast(ServerPlayer player, Vec3 target);
    }
    /**
     * Power slider shown in the Focus for a card; the server clamps the value the client sends.
     *
     * @param min     smallest power, &gt; 0
     * @param initial starting power, min..max
     * @param max     largest power, &gt; min
     */
    public record Range(float min, float initial, float max) {
        public Range {
            if (!Float.isFinite(min) || !Float.isFinite(initial) || !Float.isFinite(max) ||
                min <= 0 || min > initial || initial > max || min >= max)
                throw new IllegalArgumentException("Invalid power range");
        }
        /** {@code value} clamped to min..max; {@code initial} when not finite. */
        public float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value,min,max) : initial; }
    }
    /** Server callback of a card with power control. */
    @FunctionalInterface public interface TunedCast {
        /** Server thread. {@code power} is already clamped to the card's {@link Range}. Return true to apply the cooldown. */
        boolean cast(ServerPlayer player, Vec3 target, float power);
    }
    /**
     * A published card.
     *
     * @param effect registered effect ID
     * @param card   card metadata
     * @param power  power range, or null without power control
     * @param action server callback
     */
    public record Entry(ResourceLocation effect, Card card, Range power, TunedCast action) {
        /** Skill key used by the Focus menu: {@code addon/<namespace>/<path>}. */
        public String skillKey() { return "addon/" + effect.getNamespace() + "/" + effect.getPath(); }
        /**
         * Card art: {@code assets/calamidade_awakening/textures/gui/sprites/skill/focus/addon/<ns>/<path>.png},
         * shipped in your JAR.
         */
        public ResourceLocation art() {
            return ResourceLocation.fromNamespaceAndPath("calamidade_awakening","textures/gui/sprites/skill/focus/" + skillKey() + ".png");
        }
    }

    private static final Map<ResourceLocation, Entry> entries = new TreeMap<>();
    private static boolean sealed;
    private Addons() {}

    /** Bind the resource owner's JAR so RimuLib can resolve shader files in its Java module. */
    public static void resources(String namespace, Class<?> owner) {
        cyou.rimuru.rimulib.mc1211.api.RimulibApi.connect(namespace, Objects.requireNonNull(owner));
    }
    /** Publishes a card whose cast starts the effect at the aimed point for the caster. */
    public static void publish(ResourceLocation id, Card card) {
        publish(id, card, (player, target) -> Effects.start(id, player, target).isPresent());
    }
    /** Publishes a card with your own cast callback (validate costs and permissions there). */
    public static synchronized void publish(ResourceLocation id, Card card, Cast action) {
        Objects.requireNonNull(action);
        publish(id,card,null,(player,target,power) -> action.cast(player,target));
    }
    /**
     * Publishes a card with a power slider. During mod construction only; the effect must be registered and the
     * camera shots must fit within its duration.
     */
    public static synchronized void publish(ResourceLocation id, Card card, Range power, TunedCast action) {
        Objects.requireNonNull(card);
        Objects.requireNonNull(action);
        int local = VisualKind.localId(id.toString());
        if (sealed || local >= -1) throw new IllegalStateException("Publish a registered external effect during mod construction: " + id);
        if (!card.camera().isEmpty() && card.camera().getLast().endTick() > VisualKind.byId(local).duration)
            throw new IllegalArgumentException("Camera exceeds effect duration: " + id);
        var entry = new Entry(id, card, power, action);
        if (entries.putIfAbsent(id,entry) != null)
            throw new IllegalArgumentException("Duplicate card: " + id);
        VisualKind.byId(local).bindCard(entry);
    }
    /** Every published card, ordered by effect ID. */
    public static synchronized List<Entry> entries() { return List.copyOf(entries.values()); }
    /** Engine use: seals publishing at the end of mod loading. */
    public static synchronized void finishRegistration() { sealed = true; }
    /** Published card of an effect, or null. */
    public static Entry find(ResourceLocation id) { return entries.get(id); }
}
