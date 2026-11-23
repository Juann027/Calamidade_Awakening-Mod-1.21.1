// sky_objects.glsl — interno do motor (0.14.0, fase 3): composição dos objetos do céu (estrelas, constelações,
// nebulosas e, na fase 4, imagens e impostors), igual nos shaders de núcleo sky_stars, sky_nebula e sky_sprite. Repete o
// SkyObjectsMath (Java).
//
// Todo objeto sai em alfa pré-multiplicado: rgb é a luz que ele traz, a é a cobertura. Com a mistura
// (ONE, ONE_MINUS_SRC_ALPHA) o céu de trás vira trás × (1 − a) + rgb. Os modos (SkyBlend) só mudam o que o objeto traz:
//   NORMAL   cobertura = alfa (transparência real, cores exatas);
//   LUMINOUS cobertura 0 (luz somada, o fundo de trás nunca some);
//   COVER    cobertura 1 onde alfa >= 0,5, 0 fora (silhueta).
// O objeto aplica a própria reação à Atmosphere (véu), à luz do dia, ao escurecimento das camadas e ao horizonte. Os
// passes sky e atmosphere sabem, pela camada de cobertura do quadro (CaElTex), o que os objetos trouxeram em cada
// pixel e não aplicam de novo o véu nem o escurecimento sobre eles.

uniform vec4 CaObjA;        // x: modo (0 NORMAL, 1 LUMINOUS, 2 COVER), y: opacidade, z: brilho, w: véu da atmosfera (0..1)
uniform vec4 CaObjB;        // x: escurecimento (0 DIM, 1 REVEAL, 2 IGNORE), y: força, z: luz do dia (0..1), w: seno do fim do degradê do horizonte (0: sem)
uniform vec4 CaFrame;       // x: exposição do mundo (1 sem escurecimento, 0,15 o mais escuro), y: visibilidade das estrelas do jogo (noite, 0..1)
uniform vec3 CaVeilTop;     // céu da Atmosphere do quadro: zênite, horizonte e névoa
uniform vec3 CaVeilHorizon;
uniform vec3 CaVeilFog;
uniform vec4 CaVeil;        // x: mistura do céu da atmosfera (0 sem atmosfera), y: densidade da névoa

// SkyObjectsMath.dayVisibility, darkening e o degradê do horizonte: (visibilidade, ganho de luz). objB tem o formato de
// CaObjB; a fase 4 passa os parâmetros de cada imagem por aqui (vindos da textura de dados), as estrelas e nebulosas
// passam os uniforms (caObjReaction), com o mesmo resultado.
vec2 caObjReactionWith(vec4 objB, vec3 world) {
    float day = 1.0 - objB.z * (1.0 - CaFrame.y);
    float dark = clamp((1.0 - CaFrame.x) / 0.85, 0.0, 1.0);
    float s = objB.y;
    int mode = int(objB.x + 0.5);
    float vis = mode == 1 ? 1.0 - (1.0 - day) * (1.0 - s * dark) : day;
    float light = mode == 0 ? 1.0 + (CaFrame.x - 1.0) * s : 1.0;
    if (objB.w > 0.0) vis *= smoothstep(-0.02, objB.w, world.y);
    return vec2(vis, light);
}

vec2 caObjReaction(vec3 world) {
    return caObjReactionWith(CaObjB, world);
}

// Pixel pré-multiplicado de um objeto: cor, alfa da forma (0..1), ganho de luz próprio (brilho da estrela, maior que
// 1 brilha mais do que cobre) e direção no mundo. O véu da atmosfera é o do caAtmoSkyDelta: gradiente por mistura e
// névoa no horizonte, na proporção CaObjA.w.
vec4 caObjPixelWith(vec4 objA, vec4 objB, vec3 color, float alpha, float gain, vec3 world) {
    vec2 r = caObjReactionWith(objB, world);
    float veil = CaVeil.x * objA.w;
    float fogv = CaVeil.y * (1.0 - smoothstep(0.0, 0.45, world.y)) * objA.w;
    float op = objA.y * r.x;
    float light = gain * r.y * objA.z;
    int blend = int(objA.x + 0.5);
    if (blend == 1)
        return vec4(color * (alpha * op * light * (1.0 - veil) * (1.0 - fogv)), 0.0);
    float a = (blend == 2 ? step(0.5, alpha) : alpha) * op;
    vec3 grad = mix(CaVeilHorizon, CaVeilTop, smoothstep(0.0, 0.6, world.y));
    vec3 c = mix(mix(color * light, grad, veil), CaVeilFog, fogv);
    return vec4(c * a, a);
}

vec4 caObjPixel(vec3 color, float alpha, float gain, vec3 world) {
    return caObjPixelWith(CaObjA, CaObjB, color, alpha, gain, world);
}
