// Passe do gancho AddonLayers.post (0.6.0): uniforms próprios, sem o contrato CaCenter/CaEffect.
// A RimuLib antepõe common.glsl e #version.
uniform vec3 RippleCenter;  // centro relativo à câmera, mesmo espaço de scenePosAt
uniform vec4 RippleState;   // x: ticks desde o marcador "impacto", y: peso da fase impacto (0..1), z: opacidade, w: raio
uniform float RippleFlash;  // 0 quando o jogador oculta clarões
uniform float Track_intensidade; // trilha "intensidade" da timeline (1.4.0), enviada por post.uploadTracks

void main() {
    vec3 base = sampleScene(texCoord);
    if (!skyAt(texCoord)) {
        vec3 d = scenePosAt(texCoord) - RippleCenter;
        float r = length(d.xz);
        // Três anéis dourados que saem do impacto e se afastam a 0,35 bloco/tick.
        float travel = max(RippleState.x, 0.0) * 0.35;
        float rings = 0.0;
        for (int i = 0; i < 3; i++) {
            float radius = travel - float(i) * 1.2;
            if (radius > 0.0) rings += exp(-abs(r - radius) * 6.0) * (1.0 - float(i) * 0.3);
        }
        float fade = 1.0 - smoothstep(RippleState.w * 2.0, RippleState.w * 3.5, r);
        base += vec3(1.0, 0.72, 0.32) * rings * fade * RippleState.z * (0.35 + 0.65 * Track_intensidade);
        // Brilho na superfície durante o pulso, só se o jogador permite clarões.
        base *= 1.0 + RippleState.y * RippleFlash * 0.35 * exp(-length(d) / 4.0);
    }
    fragColor = vec4(base, 1.0);
}
