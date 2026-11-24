package dev.bossestesting.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;

/** The addon's particles: embers (fullbright, falling), smoke (rolling, growing, darkening) and arcs (flicker). */
public final class BtParticle extends TextureSheetParticle {
    public enum Kind { EMBER, SMOKE, ARC }

    private final SpriteSet sprites;
    private final Kind kind;
    private final float startSize;

    public BtParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, ColorParticleOption color,
                      SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = kind;
        this.xd = vx + (random.nextDouble() - .5) * .05;
        this.yd = vy + (random.nextDouble() - .5) * .05;
        this.zd = vz + (random.nextDouble() - .5) * .05;
        setColor(color.getRed(), color.getGreen(), color.getBlue());
        switch (kind) {
            case EMBER -> { lifetime = 14 + random.nextInt(16); gravity = .55f; friction = .95f; quadSize = .1f + random.nextFloat() * .12f; }
            case SMOKE -> { lifetime = 40 + random.nextInt(45); gravity = -.015f; friction = .93f; quadSize = .7f + random.nextFloat() * .9f; alpha = .85f; }
            case ARC -> { lifetime = 3 + random.nextInt(4); gravity = 0; friction = .5f; quadSize = .25f + random.nextFloat() * .35f; }
        }
        startSize = quadSize;
        hasPhysics = kind == Kind.EMBER;
        if (kind == Kind.ARC) pickSprite(sprites); else setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) age / lifetime;
        switch (kind) {
            case EMBER -> { setSpriteFromAge(sprites); alpha = 1 - t * t; }
            case SMOKE -> {
                setSpriteFromAge(sprites);
                quadSize = startSize * (1 + t * 1.6f);
                alpha = .85f * (1 - t);
                rCol *= .985f; gCol *= .985f; bCol *= .985f;
            }
            case ARC -> { pickSprite(sprites); alpha = 1 - t; }
        }
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    protected int getLightColor(float partial) {
        return kind == Kind.SMOKE ? super.getLightColor(partial) : 0xF000F0;
    }
}
