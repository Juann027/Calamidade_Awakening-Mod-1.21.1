#version 150
// Nebulosas (0.14.0, fase 3). Duas malhas estáticas compartilhadas por todas as nebulosas:
//   nuvem: grade 24 × 24 em UV0 (−1..1)², aberta em volta do centro pela projeção gnomônica com tan(raio) × 1,15
//          (só os pixels perto da nuvem pagam o shader);
//   faixa: esfera UV (Position = direção × 50), o céu inteiro.
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 CaWorld;       // referencial da nebulosa → mundo (só rotação)
uniform vec4 CaNebA;        // x: forma (0 nuvem, 1 faixa), y: raio da nuvem ou meia largura da faixa (rad), z: densidade, w: escala do ruído
uniform vec3 CaNebCenter;   // centro da nuvem ou polo da faixa (referencial da nebulosa)
uniform vec3 CaNebRight;    // base perpendicular ao centro (já girada pelo roll)
uniform vec3 CaNebUp;

out vec3 vLocal;            // direção no referencial da nebulosa
out vec3 vWorld;

void main() {
    vec3 p = Position;
    if (CaNebA.x < 0.5) {
        float reach = tan(min(CaNebA.y * 1.15, 1.45));
        p = normalize(CaNebCenter + reach * (UV0.x * CaNebRight + UV0.y * CaNebUp)) * 50.0;
    }
    vLocal = p;
    vWorld = (CaWorld * vec4(p, 0.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
}
