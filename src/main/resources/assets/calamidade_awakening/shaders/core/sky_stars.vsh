#version 150
// Estrelas e linhas de constelação (0.14.0, fase 3). Um VertexBuffer estático por campo (StarField), quatro vértices por
// estrela ou pedaço de linha, as estrelas mais brilhantes primeiro (os perfis de qualidade desenham um prefixo).
//   Position  direção no referencial do campo × 50 (estrela: o centro, igual nos quatro cantos; linha: o canto já aberto)
//   Color     cor (alfa multiplica o brilho)
//   UV0       canto (−1..1, −1..1); na linha, (ao longo 0..1, através −1..1)
//   UV1       x: brilho × 1000, y: fase do cintilar × 1000
//   UV2       x: meio tamanho angular em 1e-5 rad (estrela), y: tipo (0 estrela, 1 linha)
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform mat4 CaWorld;       // referencial do campo → mundo (só rotação: giro e céu do jogo)
uniform vec4 CaStar;        // x: meio tamanho mínimo (px), y: cintilar (quantidade × acessibilidade), z: velocidade, w: segundos

out vec4 vColor;
out vec2 vCorner;
out vec3 vWorld;
out float vGain;
flat out int vKind;

void main() {
    vWorld = (CaWorld * vec4(Position, 0.0)).xyz;
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int kind = UV2.y;
    float gain = float(UV1.x) / 1000.0;
    if (kind == 0) {
        // Tamanho na tela: nunca abaixo de CaStar.x pixels de meio lado; abaixo disso a estrela perde brilho em vez de
        // tamanho (sem cintilação de aliasing num campo denso).
        float px = tan(float(UV2.x) * 1e-5) * ProjMat[1][1] * ScreenSize.y * 0.5;
        float shown = max(px, CaStar.x);
        gain *= px / shown;
        clip.xy += UV0 * (shown * 2.0) / ScreenSize * clip.w;
        float phase = float(UV1.y) / 1000.0;
        float w = CaStar.w * CaStar.z * 6.2831853;
        gain *= 1.0 - CaStar.y * (0.5 + 0.25 * sin(w * (0.7 + 0.6 * phase) + 6.2831853 * phase)
            + 0.25 * sin(w * 1.7 + 12.5663706 * phase));
    }
    gl_Position = clip;
    vColor = Color;
    vCorner = UV0;
    vGain = gain;
    vKind = kind;
}
