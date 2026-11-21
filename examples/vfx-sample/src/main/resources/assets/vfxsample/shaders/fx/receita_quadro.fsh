// Receita 4 — impact frame de duas cores. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 QuadroClaro;   // cor das áreas claras
uniform vec3 QuadroEscuro;  // cor das áreas escuras
uniform vec4 QuadroEstado;  // x: força (0..1, já com a acessibilidade), y: inverter (0/1), z: limiar de luminância

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float forca = QuadroEstado.x;
    if (forca > 0.001) {
        float claro = smoothstep(QuadroEstado.z - 0.04, QuadroEstado.z + 0.04, caLuma(color));
        if (QuadroEstado.y > 0.5) claro = 1.0 - claro;
        color = mix(color, mix(QuadroEscuro, QuadroClaro, claro), forca);
    }
    fragColor = vec4(color, 1.0);
}
