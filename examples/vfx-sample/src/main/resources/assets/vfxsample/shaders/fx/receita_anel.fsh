// Receita 1 — anel de impacto no chão. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 AnelCentro;  // centro relativo à câmera (post.center()), mesmo espaço de scenePosAt
uniform vec4 AnelEstado;  // x: ticks desde "impacto", y: peso da fase "onda", z: opacidade, w: velocidade (blocos/tick)

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (!skyAt(texCoord) && AnelEstado.y > 0.0) {
        vec3 d = scenePosAt(texCoord) - AnelCentro;
        // Só no terreno perto da altura do centro (até ~3 blocos acima ou abaixo).
        float altura = 1.0 - smoothstep(2.0, 4.0, abs(d.y));
        float r = length(d.xz);
        float anel = caDistanceWave(r, AnelEstado.x, AnelEstado.w, 0.5, 24.0);
        float rastro = caDistanceWave(r, AnelEstado.x - 3.0, AnelEstado.w, 1.5, 24.0) * 0.35;
        color += vec3(1.0, 0.72, 0.3) * (anel + rastro) * altura * AnelEstado.y * AnelEstado.z * 1.6;
    }
    fragColor = vec4(color, 1.0);
}
