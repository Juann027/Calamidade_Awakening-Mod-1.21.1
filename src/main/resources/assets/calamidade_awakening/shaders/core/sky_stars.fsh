#version 150
#moj_import <calamidade_awakening:sky_objects.glsl>

in vec4 vColor;
in vec2 vCorner;
in vec3 vWorld;
in float vGain;
flat in int vKind;

out vec4 fragColor;

void main() {
    float shape;
    if (vKind == 0) {
        float r2 = dot(vCorner, vCorner);
        if (r2 >= 1.0) discard;
        // Miolo gaussiano e um halo fraco, zero na borda do quadrado.
        shape = (exp(-r2 * 5.0) * 0.85 + exp(-r2 * 1.6) * 0.15) * (1.0 - r2);
    } else {
        float across = vCorner.y;
        shape = exp(-across * across * 2.5) * (1.0 - across * across);
    }
    vec4 pixel;
    if (int(CaObjA.x + 0.5) == 1) {
        // LUMINOUS: luz somada, o brilho acima de 1 estoura para branco como luz de verdade.
        pixel = caObjPixel(vColor.rgb, shape * vColor.a, vGain, normalize(vWorld));
    } else {
        // NORMAL e COVER: cor exata. Uma estrela fraca cobre pouco (a cobertura segue o brilho até 1); o brilho acima
        // de 1 alarga o disco em vez de lavar a cor.
        float cover = min(vGain, 1.0);
        float disc = 1.0 - pow(1.0 - clamp(shape, 0.0, 1.0), max(vGain, 1.0));
        pixel = caObjPixel(vColor.rgb, disc * cover * vColor.a, 1.0, normalize(vWorld));
    }
    if (pixel.r + pixel.g + pixel.b + pixel.a < 1.0 / 1024.0) discard;
    fragColor = pixel;
}
