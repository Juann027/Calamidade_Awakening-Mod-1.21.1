package dev.calamidade_awakening.api;

/**
 * Deterministic integer hash and value noise of the Terrain API (0.11.0). Pure Java, no Minecraft types.
 *
 * <p>Every operation is 32-bit integer arithmetic (wrapping multiply, xor, logical shifts) or a float-exact
 * conversion, so the GLSL copy in {@code terrain.fsh} (unsigned ints) yields the same lattice values: the server plan
 * and the client heat shader draw the same rough edge from the same seed. Lattice coordinates get a bias of
 * {@value #BIAS} before hashing so they stay positive (GLSL converts negative ints to uint by bit pattern only since
 * 4.x; the bias avoids relying on it).
 */
public final class TerrainNoise {
    /** Added to every lattice coordinate before hashing (keeps them positive within ±32768). */
    public static final int BIAS = 32768;

    private TerrainNoise() {}

    /** 32-bit seed of a 64-bit instance seed: {@code (int) (seed ^ (seed >>> 32))}. */
    public static int seed32(long seed) { return (int) (seed ^ (seed >>> 32)); }

    /** Hash of a lattice point and a seed, any int. Same in {@code terrain.fsh}. */
    public static int hash(int x, int y, int z, int seed) {
        int h = seed;
        h ^= (x + BIAS) * 0x8da6b343;
        h ^= (y + BIAS) * 0xd8163841;
        h ^= (z + BIAS) * 0xcb1ab31f;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h;
    }

    /** Hash of a lattice point as a value in [0, 1): the top 24 bits, exact in a float. */
    public static double unit(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) >>> 8) * (1.0 / 16777216.0);
    }

    /** Smooth value noise in [0, 1]: trilinear blend of the lattice values with a smoothstep weight. */
    public static double value(double x, double y, double z, int seed) {
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        int ix = (int) fx, iy = (int) fy, iz = (int) fz;
        double tx = x - fx, ty = y - fy, tz = z - fz;
        double ux = tx * tx * (3 - 2 * tx), uy = ty * ty * (3 - 2 * ty), uz = tz * tz * (3 - 2 * tz);
        double c000 = unit(ix, iy, iz, seed), c100 = unit(ix + 1, iy, iz, seed);
        double c010 = unit(ix, iy + 1, iz, seed), c110 = unit(ix + 1, iy + 1, iz, seed);
        double c001 = unit(ix, iy, iz + 1, seed), c101 = unit(ix + 1, iy, iz + 1, seed);
        double c011 = unit(ix, iy + 1, iz + 1, seed), c111 = unit(ix + 1, iy + 1, iz + 1, seed);
        double x00 = c000 + (c100 - c000) * ux, x10 = c010 + (c110 - c010) * ux;
        double x01 = c001 + (c101 - c001) * ux, x11 = c011 + (c111 - c011) * ux;
        double y0 = x00 + (x10 - x00) * uy, y1 = x01 + (x11 - x01) * uy;
        return y0 + (y1 - y0) * uz;
    }

    /** Two octaves of {@link #value}, in [0, 1]: {@code 0.62 n(p) + 0.38 n(2p + (17, 31, 47))}. */
    public static double fbm(double x, double y, double z, int seed) {
        return .62 * value(x, y, z, seed) + .38 * value(x * 2 + 17, y * 2 + 31, z * 2 + 47, seed);
    }
}
