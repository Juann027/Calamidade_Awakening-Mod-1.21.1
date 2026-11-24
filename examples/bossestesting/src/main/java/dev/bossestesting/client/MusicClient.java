package dev.bossestesting.client;

import dev.bossestesting.net.MusicPayload;
import dev.bossestesting.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * Phase 2 track on the client: streamed (sounds.json), non-positional (relative, no attenuation), MUSIC category
 * (the player's music volume), looping while the fight lasts, faded out on defeat or when leaving. One instance per
 * encounter: a repeated start is ignored (no restart on attacks or Overloaded). While it plays, vanilla music is
 * stopped and kept from starting (SelectMusicEvent in {@link ClientSetup}).
 */
public final class MusicClient {
    private static Track current;
    private static UUID encounter;

    private MusicClient() {}

    public static void handle(MusicPayload payload) {
        if (payload.play()) {
            if (current != null && !current.isStopped() && payload.encounter().equals(encounter) && !current.fading()) return;
            if (current != null) current.fadeOut(10);
            encounter = payload.encounter();
            current = new Track();
            Minecraft.getInstance().getMusicManager().stopPlaying();
            Minecraft.getInstance().getSoundManager().play(current);
        } else if (current != null && payload.encounter().equals(encounter)) {
            current.fadeOut(Math.max(1, payload.fade()));
        }
    }

    /** True while the track owns the music channel (vanilla music stays off). */
    public static boolean playing() { return current != null && !current.isStopped(); }

    /** Disconnect or level change: stop at once / fade. */
    public static void stopAll(int fade) {
        if (current != null) {
            if (fade <= 0) Minecraft.getInstance().getSoundManager().stop(current);
            else current.fadeOut(fade);
        }
        if (fade <= 0) { current = null; encounter = null; }
    }

    static final class Track extends AbstractTickableSoundInstance {
        private int fade = -1, fadeTotal;

        Track() {
            super(ModSounds.MUSIC_PHASE2.get(), SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 1;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0; this.y = 0; this.z = 0;
        }

        boolean fading() { return fade >= 0; }

        void fadeOut(int ticks) {
            if (fade >= 0) return;
            fade = ticks;
            fadeTotal = ticks;
        }

        @Override
        public void tick() {
            if (fade < 0) return;
            fade--;
            volume = Math.max(0, (float) fade / fadeTotal);
            if (fade <= 0) stop();
        }

        @Override public boolean canStartSilent() { return true; }
    }
}
