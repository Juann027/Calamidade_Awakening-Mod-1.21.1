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
    // Uma estrela fraca cobre pouco: a cobertura (NORMAL) segue o brilho até 1; acima disso só a luz cresce.
    float cover = min(vGain, 1.0);
    vec4 pixel = caObjPixel(vColor.rgb, shape * cover * vColor.a, vGain / max(cover, 1e-4), normalize(vWorld));
    if (pixel.r + pixel.g + pixel.b + pixel.a < 1.0 / 1024.0) discard;
    fragColor = pixel;
}
