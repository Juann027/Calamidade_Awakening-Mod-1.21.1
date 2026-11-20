package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Publish a previously registered effect as a player-selectable skill during mod construction. */
public final class Addons {
    public record Shot(int endTick, Vec3 camera, float fov) {
        public Shot {
            Objects.requireNonNull(camera);
            if (endTick < 1 || !Double.isFinite(camera.lengthSqr()) || camera.length()>128 ||
                !Float.isFinite(fov) || fov < 25 || fov > 110) throw new IllegalArgumentException("Invalid camera shot");
        }
    }
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
    @FunctionalInterface public interface Cast {
        /** Server thread. Return true when accepted to apply the card's cooldown. */
        boolean cast(ServerPlayer player, Vec3 target);
    }
    public record Range(float min, float initial, float max) {
        public Range {
            if (!Float.isFinite(min) || !Float.isFinite(initial) || !Float.isFinite(max) ||
                min <= 0 || min > initial || initial > max || min >= max)
                throw new IllegalArgumentException("Invalid power range");
        }
        public float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value,min,max) : initial; }
    }
    @FunctionalInterface public interface TunedCast {
        boolean cast(ServerPlayer player, Vec3 target, float power);
    }
    public record Entry(ResourceLocation effect, Card card, Range power, TunedCast action) {
        public String skillKey() { return "addon/" + effect.getNamespace() + "/" + effect.getPath(); }
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
    public static void publish(ResourceLocation id, Card card) {
        publish(id, card, (player, target) -> Effects.start(id, player, target).isPresent());
    }
    public static synchronized void publish(ResourceLocation id, Card card, Cast action) {
        Objects.requireNonNull(action);
        publish(id,card,null,(player,target,power) -> action.cast(player,target));
    }
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
    public static synchronized List<Entry> entries() { return List.copyOf(entries.values()); }
    public static synchronized void finishRegistration() { sealed = true; }
    public static Entry find(ResourceLocation id) { return entries.get(id); }
}
