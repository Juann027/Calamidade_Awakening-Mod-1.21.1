// Receita 3 — bolha arco-íris (raio-esfera). A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 BolhaCentro;  // centro relativo à câmera
uniform vec4 BolhaEstado;  // x: raio (blocos), y: trilha "brilho" (0..1), z: opacidade, w: segundos (giro das cores)

#include "calamidade_fx.glsl"

// Película fina: as cores mudam com o ângulo entre a normal e o raio de visão (fresnel).
vec3 bolhaPelicula(vec3 n, vec3 rd) {
    float fresnel = pow(1.0 - abs(dot(n, rd)), 3.0);
    float matiz = fresnel * 0.9 + n.y * 0.2 + BolhaEstado.w * 0.05;
    return caRainbow(matiz) * (0.12 + 0.88 * fresnel);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float raio = BolhaEstado.x;
    if (raio > 0.01 && BolhaEstado.y > 0.001) {
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));
        vec2 t = caRaySphere(vec3(0.0), rd, BolhaCentro, raio);
        if (t.y > 0.0) {
            vec3 luz = vec3(0.0);
            if (t.x > 0.0 && t.x < cena) {                       // casca da frente
                vec3 n = normalize(rd * t.x - BolhaCentro);
                luz += bolhaPelicula(n, rd);
                vec3 sol = normalize(vec3(0.4, 0.8, 0.3));
                luz += vec3(1.0) * pow(max(dot(reflect(rd, n), sol), 0.0), 60.0) * 0.8;
            }
            if (t.y < cena) {                                    // casca de trás, vista por dentro
                vec3 n = normalize(rd * t.y - BolhaCentro);
                luz += 0.4 * bolhaPelicula(-n, rd);
            }
            color += luz * BolhaEstado.y * BolhaEstado.z;
        }
    }
    fragColor = vec4(color, 1.0);
}
