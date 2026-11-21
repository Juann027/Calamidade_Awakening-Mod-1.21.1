// calamidade_terrain.glsl — ruído e formas da Terrain API (motor 0.11.0), usados pelo passe "terrain" do motor.
// Cópia fiel de TerrainNoise.java e TerrainShape.Primitive.distance: o servidor planeja a edição e o cliente desenha o
// calor com as mesmas fórmulas e a mesma seed, sem receber a lista de blocos. Aritmética inteira de 32 bits sem sinal
// (GLSL 1.50 e GLSL ES 3.00); scripts/testar_glsl.cjs compara cada função com valores calculados pelo Java.
// Arquivo interno do motor (namespace calamidade_awakening): addons não precisam dele.

// ---------------------------------------------------------------------------- ruído igual ao TerrainNoise.java

uint caTerrainHash(ivec3 c, uint seed) {
    uint h = seed;
    h ^= uint(c.x + 32768) * 0x8da6b343u;
    h ^= uint(c.y + 32768) * 0xd8163841u;
    h ^= uint(c.z + 32768) * 0xcb1ab31fu;
    h ^= h >> 15u;
    h *= 0x2c1b3c6du;
    h ^= h >> 12u;
    h *= 0x297a2d39u;
    h ^= h >> 15u;
    return h;
}

float caTerrainUnit(ivec3 c, uint seed) {
    return float(caTerrainHash(c, seed) >> 8u) * (1.0 / 16777216.0);
}

float caTerrainValue(vec3 p, uint seed) {
    vec3 f = floor(p);
    ivec3 i = ivec3(f);
    vec3 t = p - f;
    vec3 u = t * t * (3.0 - 2.0 * t);
    float c000 = caTerrainUnit(i, seed), c100 = caTerrainUnit(i + ivec3(1, 0, 0), seed);
    float c010 = caTerrainUnit(i + ivec3(0, 1, 0), seed), c110 = caTerrainUnit(i + ivec3(1, 1, 0), seed);
    float c001 = caTerrainUnit(i + ivec3(0, 0, 1), seed), c101 = caTerrainUnit(i + ivec3(1, 0, 1), seed);
    float c011 = caTerrainUnit(i + ivec3(0, 1, 1), seed), c111 = caTerrainUnit(i + ivec3(1, 1, 1), seed);
    float x00 = mix(c000, c100, u.x), x10 = mix(c010, c110, u.x);
    float x01 = mix(c001, c101, u.x), x11 = mix(c011, c111, u.x);
    return mix(mix(x00, x10, u.y), mix(x01, x11, u.y), u.z);
}

float caTerrainFbm(vec3 p, uint seed) {
    return 0.62 * caTerrainValue(p, seed) + 0.38 * caTerrainValue(p * 2.0 + vec3(17.0, 31.0, 47.0), seed);
}

// ---------------------------------------------------------------------------- formas iguais ao TerrainShape.java

float caTerrainPrimitive(vec3 p, vec4 a, vec4 b) {
    int kind = int(mod(a.x + 0.5, 4.0));
    vec3 q = p - b.xyz;
    if (kind == 0) return length(q) - a.y;
    if (kind == 2) {
        float hlen = a.z * 0.5;
        q.z -= clamp(q.z, -hlen, hlen);
        return length(q) - a.y;
    }
    if (kind == 3) {
        vec2 d = vec2(length(q.xz) - a.y, abs(q.y) - a.z * 0.5);
        return min(max(d.x, d.y), 0.0) + length(max(d, vec2(0.0)));
    }
    float rho = length(q.xz);
    float t = min(rho / a.y, 1.0);
    float floorY = -a.z * (1.0 - pow(t, a.w));
    float slope = rho < a.y ? a.z * a.w * pow(max(t, 1e-4), a.w - 1.0) / a.y : 0.0;
    float bowl = (floorY - q.y) / sqrt(1.0 + slope * slope);
    return max(bowl, max(rho - a.y, q.y - b.w));
}
