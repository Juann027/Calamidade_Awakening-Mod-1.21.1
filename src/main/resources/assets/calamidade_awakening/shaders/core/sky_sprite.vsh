#version 150
// Imagens, flipbooks e impostors do céu (0.14.0, fase 4). Um VertexBuffer dinâmico por quadro com os sprites visíveis,
// quatro vértices cada, montados na CPU (SkySprites): posição já no mundo (direção × 50, projeção gnomônica do quadro),
// canto da imagem e a linha do objeto na textura de dados (CaSpriteData, RGBA32F, 8 texels por linha):
//   0 modo, opacidade, brilho, véu (formato de CaObjA)    1 escurecimento, força, luz do dia, horizonte (CaObjB)
//   2 tinta rgb, níveis de mipmap da textura               3 pesos dos quatro retângulos
//   4..7 retângulos (u0, v0, u1, v1) na textura: quadros vizinhos do flipbook e vistas do impostor (u0 > u1 espelha)
in vec3 Position;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D CaSpriteData;

out vec2 vCorner;
out vec3 vWorld;
flat out vec4 vObjA;
flat out vec4 vObjB;
flat out vec4 vTint;
flat out vec4 vWeights;
flat out vec4 vRect0;
flat out vec4 vRect1;
flat out vec4 vRect2;
flat out vec4 vRect3;

void main() {
    int row = UV2.x;
    vObjA = texelFetch(CaSpriteData, ivec2(0, row), 0);
    vObjB = texelFetch(CaSpriteData, ivec2(1, row), 0);
    vTint = texelFetch(CaSpriteData, ivec2(2, row), 0);
    vWeights = texelFetch(CaSpriteData, ivec2(3, row), 0);
    vRect0 = texelFetch(CaSpriteData, ivec2(4, row), 0);
    vRect1 = texelFetch(CaSpriteData, ivec2(5, row), 0);
    vRect2 = texelFetch(CaSpriteData, ivec2(6, row), 0);
    vRect3 = texelFetch(CaSpriteData, ivec2(7, row), 0);
    vCorner = UV0;
    vWorld = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
