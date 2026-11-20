uniform vec3 CaCenter;
uniform vec4 CaEffect;
void main() {
    vec3 base = sampleScene(texCoord);
    if (!skyAt(texCoord)) {
        vec3 d = scenePosAt(texCoord) - CaCenter;
        float r = length(d) / max(.01,CaEffect.w);
        float wave = fract(CaEffect.x*2.0 + CaEffect.z*.12) * 1.8;
        float crest = exp(-abs(r-wave)*18.0) * (1.0-smoothstep(1.45,1.8,r));
        base += vec3(.12,.54,.43)*crest*CaEffect.y;
    }
    fragColor = vec4(base,1.0);
}
