package dev.bossestesting.anim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One exported GeckoLib animation with its event ticks (20 per second), generated in {@link Anims}.
 *
 * @param name      full animation name, such as {@code animation.guilhermino.ground_slam}
 * @param ticks     declared length, ticks
 * @param endTick   effective end: the last keyframe rounded up (a keyframe can sit after the declared length)
 * @param loop      loop type
 * @param events    event id → tick (from the exported animation when present, else from the events file)
 * @param external  events that are not GeckoLib custom instructions (synced explicitly by the addon)
 */
public record Clip(String name, int ticks, int endTick, Loop loop, Map<String, Integer> events, Set<String> external) {
    /** Tick of an event; throws when the event does not exist, so a typo fails the pure check instead of the fight. */
    public int at(String event) {
        Integer t = events.get(event);
        if (t == null) throw new IllegalArgumentException(name + " has no event " + event);
        return t;
    }

    /** True when the event exists. */
    public boolean has(String event) { return events.containsKey(event); }

    /** Short name after the last dot ("ground_slam"). */
    public String shortName() { return name.substring(name.lastIndexOf('.') + 1); }

    /** Builds an ordered event map from alternating id, tick pairs. */
    public static Map<String, Integer> eventMap(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Integer) pairs[i + 1]);
        return Collections.unmodifiableMap(map);
    }
}
