package dev.bossestesting.registry;

import dev.bossestesting.BossesTesting;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events. The phase 2 track is the provided OGG at assets/bossestesting/sounds/music/guilhermino_phase2.ogg,
 * streamed (sounds.json "stream": true): a resource pack can replace that file without touching the code.
 * Mechanical sounds of the fight are layered vanilla events played by {@link dev.bossestesting.fx.Sfx}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, BossesTesting.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_PHASE2 = REGISTER.register("music.guilhermino_phase2",
        () -> SoundEvent.createVariableRangeEvent(BossesTesting.id("music.guilhermino_phase2")));

    private ModSounds() {}
}
