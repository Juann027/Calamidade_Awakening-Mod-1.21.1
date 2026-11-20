#version 150
in vec4 tint;
out vec4 fragColor;
void main() {
    float intensity = smoothstep(0.0, 1.0, tint.a);
    vec3 energy = mix(tint.rgb, vec3(1.0), intensity * 0.28);
    fragColor = vec4(energy, intensity);
}
