#!/usr/bin/env node
// Prévia dos passes do motor (beams, lights e, desde a 0.12.0, sky e atmosphere) sem o jogo: roda o shader real, com
// o common.glsl da RimuLib, no WebGL2 do Chromium headless (SwiftShader), sobre uma cena sintética com profundidade, e
// grava um PNG por cena em build/previa/. Serve para conferir a aparência e a oclusão antes do teste em jogo; não
// substitui o teste numa GPU de verdade.
//
// Cenas de feixes e luzes: chão, um muro, pilares e céu. Cenas de céu (0.12.0, prefixos buraco_negro_, portal_ e
// eclipse_): montanhas num mapa de altura, céu e névoa no modelo do jogo e o sol do jogo somado como o LevelRenderer
// soma (mesmo quadrado, mesma textura; a textura vem do JAR de recursos do Minecraft que o build baixa, ou um sol
// sintético sem ele), depois os passes reais sky e atmosphere, com os mesmos dados que o SkyFrame e o
// AtmosphereFrame mandam.
//
// Requer Node, Playwright com Chromium e o JAR da RimuLib em libs/ (./gradlew build baixa). Uso, na raiz:
//     node scripts/previa_passes.cjs [cena...]
// Cenas: veja CENAS abaixo. Sem argumento, gera todas. "ceu" gera só as de céu e monta build/previa/ceu-0120.png.
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.join(__dirname, '..');
const FX = path.join(ROOT, 'src/main/resources/assets/calamidade_awakening/shaders/fx');
const OUT = path.join(ROOT, 'build/previa');
const W = 640, H = 360, FOV = 70;

// ------------------------------------------------------------------ cena sintética (CPU)

// Câmera na origem, a 1,62 bloco do chão, olhando para +z com leve inclinação para baixo.
function camera(pitchDeg = -8, yawDeg = 0) {
  const p = pitchDeg * Math.PI / 180, y = yawDeg * Math.PI / 180;
  const f = [Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p)];
  const r = norm(cross(f, [0, 1, 0]));
  const u = cross(r, f);
  return { f, r, u };
}
function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function norm(a) { const l = Math.hypot(a[0], a[1], a[2]); return [a[0] / l, a[1] / l, a[2] / l]; }

// Matrizes coluna-maior (como o GLSL espera em uniformMatrix4fv sem transpor).
function perspective(fovDeg, aspect, near, far) {
  const f = 1 / Math.tan(fovDeg * Math.PI / 360);
  return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0];
}
function viewMatrix(cam) {
  const { r, u, f } = cam;
  return [r[0], u[0], -f[0], 0, r[1], u[1], -f[1], 0, r[2], u[2], -f[2], 0, 0, 0, 0, 1];
}
function mul(a, b) {
  const o = new Array(16).fill(0);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) o[c * 4 + r] += a[k * 4 + r] * b[c * 4 + k];
  return o;
}
function invert(m) {
  const inv = new Array(16);
  inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
  inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
  inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
  inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
  inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
  inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
  inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
  inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
  inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
  inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
  inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
  inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
  inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
  inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
  inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
  inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
  const det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
  return inv.map(v => v / det);
}

// Caixas alinhadas (mín, máx, cor): chão, muro e pilares, em blocos relativos à câmera.
const BOXES = [
  { min: [-400, -400, -400], max: [400, -1.62, 400], color: [0.33, 0.52, 0.22], checker: true },   // chão de grama
  { min: [-9, -1.62, 26], max: [-2, 4.4, 27], color: [0.55, 0.53, 0.5] },                           // muro de pedra
  { min: [5, -1.62, 18], max: [6, 2.4, 19], color: [0.45, 0.32, 0.2] },                              // pilar de madeira
  { min: [-4, -1.62, 9], max: [-3, -0.62, 10], color: [0.6, 0.6, 0.62] },                            // bloco baixo
  { min: [8, -1.62, 34], max: [16, 6.4, 40], color: [0.62, 0.6, 0.55] },                             // casa
];

function rayBox(o, d, b) {
  let t0 = -Infinity, t1 = Infinity, axis = 0, sign = 1;
  for (let i = 0; i < 3; i++) {
    const inv = 1 / (Math.abs(d[i]) < 1e-9 ? 1e-9 * Math.sign(d[i] || 1) : d[i]);
    let a = (b.min[i] - o[i]) * inv, c = (b.max[i] - o[i]) * inv, s = -1;
    if (a > c) { [a, c] = [c, a]; s = 1; }
    if (a > t0) { t0 = a; axis = i; sign = s * (inv < 0 ? 1 : 1); }
    t1 = Math.min(t1, c);
  }
  if (t1 < Math.max(t0, 0)) return null;
  const n = [0, 0, 0]; n[axis] = d[axis] > 0 ? -1 : 1;
  return { t: Math.max(t0, 0), n };
}

function scene(cam, proj, view, night) {
  const viewProj = mul(proj, view);
  const depth = new Float32Array(W * H), color = new Float32Array(W * H * 4);
  const sun = norm([0.4, 0.8, -0.3]);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const ndc = [(x + 0.5) / W * 2 - 1, (y + 0.5) / H * 2 - 1];
    const tan = Math.tan(FOV * Math.PI / 360);
    const d = norm([cam.r[0] * ndc[0] * tan * W / H + cam.u[0] * ndc[1] * tan + cam.f[0],
      cam.r[1] * ndc[0] * tan * W / H + cam.u[1] * ndc[1] * tan + cam.f[1],
      cam.r[2] * ndc[0] * tan * W / H + cam.u[2] * ndc[1] * tan + cam.f[2]]);
    let best = null;
    for (const b of BOXES) { const h = rayBox([0, 0, 0], d, b); if (h && (!best || h.t < best.t)) best = { ...h, b }; }
    const i = y * W + x;
    if (!best || best.t > 380) {
      depth[i] = 1.0;
      const k = Math.max(d[1], 0);
      const sky = night ? [0.01 + 0.02 * (1 - k), 0.015 + 0.02 * (1 - k), 0.05 + 0.03 * (1 - k)]
        : [0.45 + 0.3 * (1 - k), 0.62 + 0.25 * (1 - k), 0.95];
      color.set([...sky, 1], i * 4);
      continue;
    }
    const p = [d[0] * best.t, d[1] * best.t, d[2] * best.t];
    const clip = [0, 0, 0, 0];
    for (let r = 0; r < 4; r++) clip[r] = viewProj[r] * p[0] + viewProj[4 + r] * p[1] + viewProj[8 + r] * p[2] + viewProj[12 + r];
    depth[i] = Math.min((clip[2] / clip[3]) * 0.5 + 0.5, 0.99998);
    let albedo = best.b.color.slice();
    if (best.b.checker) {
      const c = (Math.floor(p[0]) + Math.floor(p[2])) & 1;
      const n = 0.85 + 0.15 * Math.sin(p[0] * 3.1) * Math.sin(p[2] * 2.7);
      albedo = albedo.map(v => v * (c ? 0.92 : 1.0) * n);
    } else {
      const g = 0.9 + 0.1 * ((Math.floor(p[0] * 2) + Math.floor(p[1] * 2) + Math.floor(p[2] * 2)) & 1);
      albedo = albedo.map(v => v * g);
    }
    const lit = night ? 0.06 : 0.45 + 0.55 * Math.max(dot(best.n, sun), 0);
    const fog = Math.min(Math.max((best.t - 96) / 96, 0), 1);
    const fogColor = night ? [0.02, 0.03, 0.06] : [0.7, 0.8, 0.95];
    color.set([0, 1, 2].map(k => albedo[k] * lit * (1 - fog) + fogColor[k] * fog).concat([1]), i * 4);
  }
  return { depth, color, viewProj };
}

// ------------------------------------------------------------------ cenas

const BEAM_TEXELS = 7;
function beamRow(a, b, core, halo, coreRgb, haloRgb, gain, reveal, pulses = 0, pulseTravel = 0, pulseSize = 2,
                 waveAmp = 0, waveLen = 4, waveTravel = 0, noise = 0, noiseTime = 0) {
  return [...a, core / 2, ...b, halo / 2, ...coreRgb.map(v => v * gain), reveal, ...haloRgb.map(v => v * gain), pulses,
    pulseTravel, pulseSize, 1, noiseTime, waveAmp, waveLen, waveTravel, noise,
    Math.min(Math.hypot(...a), Math.hypot(...b)) < 2 ? 1 : 0, 0, 0, 0];
}

// Linha de luz: posição (ou início/centro), alcance ou raio, fim, queda, cor × intensidade, forma, largura, altura.
const SHAPE = { ponto: 0, segmento: 1, disco: 2, anel: 3 };
function lightRow(shape, a, radius, rgb, gain, { b = a, falloff = 2, width = 0, height = 3 } = {}) {
  return [...a, radius, ...b, falloff, ...rgb.map(v => v * gain), SHAPE[shape], width, height, 0, 0];
}

const CENAS = {
  // Laser que sai dos olhos (ponta a 0,3 bloco da câmera) até um bloco: fino na tela de quem atira.
  laser: {
    night: true, pitch: -4,
    beams: [beamRow([0, -0.3, 0.1], [-3.5, -0.6, 9.5], 0.12, 0.9, [1, 1, 1], [1, 0.19, 0.31], 1, 1, 4, 10, 1.5)],
    lights: [lightRow('ponto', [-3.5, -0.6, 9.4], 5, [1, 0.31, 0.38], 2.5)],
  },
  // Fogueira de noite: luz pontual laranja ao lado do bloco baixo, sem geometria nenhuma.
  fogueira: {
    night: true, pitch: -12,
    lights: [lightRow('ponto', [-1.6, -1.0, 8.5], 10, [1, 0.6, 0.25], 3, { falloff: 1.2 })],
  },
  // Pilar de luz de noite: feixe do céu ao chão, luz ao longo dele e disco no chão.
  pilar: {
    night: true, pitch: 4,
    lights: [
      lightRow('segmento', [0, -1.62, 18], 10, [1, 0.75, 0.35], 2.0, { b: [0, 60, 18] }),
      lightRow('disco', [0, -1.62, 18], 7, [1, 0.8, 0.4], 1.2, { width: 3, height: 3 }),
    ],
    beams: [beamRow([0, -1.62, 18], [0, 140, 18], 0.6, 4, [1, 1, 1], [1, 0.69, 0.25], 1, 1, 3, 30, 6)],
  },
  // Anel de luz no chão (onda de choque) e o mesmo de dia.
  onda: {
    night: true, pitch: -14,
    lights: [lightRow('anel', [0, -1.62, 14], 7, [0.45, 0.8, 1], 2.5, { width: 2.5, height: 2 })],
  },
  onda_dia: {
    night: false, pitch: -14,
    lights: [lightRow('anel', [0, -1.62, 14], 7, [0.45, 0.8, 1], 2.5, { width: 2.5, height: 2 })],
  },
  // Pilar do céu ao chão atrás do muro (o muro esconde a base), feixe fino na frente e teia com pulsos.
  feixes: {
    night: false, pitch: 2,
    beams: [
      beamRow([-5, -1.62, 40], [-5, 140, 40], 0.6, 4, [1, 1, 1], [1, 0.69, 0.25], 1, 1, 3, 30, 6),
      beamRow([1.5, -0.4, 4], [7, 3, 30], 0.12, 0.8, [1, 1, 1], [1, 0.25, 0.38], 1.2, 1),
      ...[...Array(8).keys()].map(k => {
        const a = k / 8 * Math.PI * 2;
        return beamRow([0, 1.5, 16], [7 * Math.cos(a), -1.62 + 2 * (k & 1), 16 + 7 * Math.sin(a)], 0.12, 0.8,
          [1, 1, 1], [0.4, 0.8, 1], 1, 1, 2, 9, 1.5, 0.15, 3, 5, 0.05, k * 3.1);
      }),
    ],
  },
  // Feixe nascendo (revelação 0,45) e feixe ondulado com ruído.
  nascendo: {
    night: true, pitch: 6,
    beams: [
      beamRow([-3, -1.62, 20], [-3, 60, 20], 0.5, 3, [1, 1, 1], [0.6, 0.4, 1], 1.5, 0.45),
      beamRow([2, 0, 10], [12, 5, 34], 0.2, 1.2, [1, 1, 1], [0.3, 1, 0.6], 1.2, 1, 0, 0, 2, 0.6, 5, 2, 0.3, 4),
    ],
  },
};

// ------------------------------------------------------------------ céu (0.12.0): montanhas, sol do jogo e camadas

// Mapa de altura das montanhas (blocos, relativo à câmera): planície perto, cordilheiras de 120 a 260 blocos.
const HM = { size: 1024, cell: 2 };
function noise2(x, z) {
  const xi = Math.floor(x), zi = Math.floor(z), xf = x - xi, zf = z - zi;
  const h = (a, b) => { let n = (a * 374761393 + b * 668265263) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; };
  const u = xf * xf * (3 - 2 * xf), v = zf * zf * (3 - 2 * zf);
  return (h(xi, zi) * (1 - u) + h(xi + 1, zi) * u) * (1 - v) + (h(xi, zi + 1) * (1 - u) + h(xi + 1, zi + 1) * u) * v;
}
function heightAt(x, z) {
  const d = Math.hypot(x, z);
  let ridge = 0, amp = 1, f = 1 / 90;
  for (let o = 0; o < 5; o++) { ridge += amp * (1 - Math.abs(noise2(x * f + o * 7.1, z * f - o * 3.3) * 2 - 1)); amp *= 0.5; f *= 2.1; }
  const mountains = Math.pow(ridge / 1.9, 2.2) * 95 * smooth(90, 200, d);
  const hills = (noise2(x / 40, z / 40) - 0.5) * 6 * smooth(20, 60, d);
  return -1.62 + mountains + hills;
}
function smooth(a, b, x) { const t = Math.min(Math.max((x - a) / (b - a), 0), 1); return t * t * (3 - 2 * t); }
let heightMap = null;
function buildHeightMap() {
  if (heightMap) return heightMap;
  const n = HM.size, half = n * HM.cell / 2, data = new Float32Array(n * n);
  for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) data[j * n + i] = heightAt(i * HM.cell - half, j * HM.cell - half);
  heightMap = { data, half };
  return heightMap;
}
function hm(x, z) {
  const { data, half } = heightMap, n = HM.size;
  const gx = (x + half) / HM.cell, gz = (z + half) / HM.cell;
  if (gx < 0 || gz < 0 || gx >= n - 1 || gz >= n - 1) return -1.62;
  const i = Math.floor(gx), j = Math.floor(gz), u = gx - i, v = gz - j;
  return (data[j * n + i] * (1 - u) + data[j * n + i + 1] * u) * (1 - v) + (data[(j + 1) * n + i] * (1 - u) + data[(j + 1) * n + i + 1] * u) * v;
}

// Céu do jogo no modelo do caVanillaSky (cor do céu no alto, névoa no horizonte, distância de renderização).
function vanillaSky(d, sky, fog, renderDistance) {
  if (d[1] <= 0) return fog.slice();
  const horizontal = 16 * Math.sqrt(Math.max(1 - d[1] * d[1], 0)) / Math.max(d[1], 1e-3);
  const k = smooth(0, Math.max(renderDistance, 16), horizontal);
  return [0, 1, 2].map(c => sky[c] * (1 - k) + fog[c] * k);
}

function skyScene(cam, proj, view, env) {
  buildHeightMap();
  const viewProj = mul(proj, view);
  const depth = new Float32Array(W * H), color = new Float32Array(W * H * 4);
  const sun = env.sunDir, tan = Math.tan(FOV * Math.PI / 360);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const ndc = [(x + 0.5) / W * 2 - 1, (y + 0.5) / H * 2 - 1];
    const d = norm([0, 1, 2].map(k => cam.r[k] * ndc[0] * tan * W / H + cam.u[k] * ndc[1] * tan + cam.f[k]));
    const i = y * W + x;
    let t = 0.5, hit = false;
    for (let s = 0; s < 400 && t < env.renderDistance * 1.6; s++) {
      const px = d[0] * t, py = d[1] * t, pz = d[2] * t;
      const gap = py - hm(px, pz);
      if (gap < 0.05) { hit = true; break; }
      t += Math.min(Math.max(gap * 0.45, 0.25), 10);
    }
    if (!hit) {
      depth[i] = 1.0;
      color.set([...vanillaSky(d, env.sky, env.fog, env.renderDistance), 1], i * 4);
      continue;
    }
    const p = [d[0] * t, d[1] * t, d[2] * t];
    const e = 1.0, hx = hm(p[0] + e, p[2]) - hm(p[0] - e, p[2]), hz = hm(p[0], p[2] + e) - hm(p[0], p[2] - e);
    const n = norm([-hx, 2 * e, -hz]);
    const slope = 1 - n[1];
    let albedo = p[1] > 58 + 8 * noise2(p[0] / 13, p[2] / 13) ? [0.88, 0.9, 0.94]
      : slope > 0.28 || p[1] > 34 ? [0.42, 0.4, 0.38] : [0.3, 0.48, 0.2];
    const grain = 0.9 + 0.1 * noise2(p[0] * 0.7, p[2] * 0.7);
    albedo = albedo.map(v => v * grain);
    const light = env.ambient + env.sunLight * Math.max(dot(n, sun), 0);
    const fog = smooth(env.renderDistance * 0.55, env.renderDistance, t);
    const clip = [0, 0, 0, 0];
    for (let r = 0; r < 4; r++) clip[r] = viewProj[r] * p[0] + viewProj[4 + r] * p[1] + viewProj[8 + r] * p[2] + viewProj[12 + r];
    depth[i] = Math.min((clip[2] / clip[3]) * 0.5 + 0.5, 0.99998);
    color.set([0, 1, 2].map(k => albedo[k] * light * (1 - fog) + env.fog[k] * fog).concat([1]), i * 4);
  }
  return { depth, color, viewProj };
}

// Referencial do sol e da lua do jogo (SkyMath.vanillaAxes): colunas x, y, z de Ry(-90)·Rx(hora·360).
function vanillaAxes(timeOfDay) {
  const a = timeOfDay * Math.PI * 2, s = Math.sin(a), c = Math.cos(a);
  return [[0, 0, 1], [-s, c, 0], [-c, -s, 0]];
}
// Sky.Direction.of: azimute 0 = sul, 90 = oeste; elevação em graus.
function skyDir(az, el) {
  const a = az * Math.PI / 180, e = el * Math.PI / 180;
  return [-Math.sin(a) * Math.cos(e), Math.sin(e), Math.cos(a) * Math.cos(e)];
}
function lookAt(dir, pitchOffset = 0, yawOffset = 0) {
  const el = Math.asin(dir[1]) * 180 / Math.PI + pitchOffset;
  const az = Math.atan2(-dir[0], dir[2]) * 180 / Math.PI + yawOffset;
  const f = skyDir(az, el);
  const r = norm(cross(f, [0, 1, 0]));
  return { f, r, u: cross(r, f) };
}
const rgb = argb => [((argb >> 16) & 255) / 255, ((argb >> 8) & 255) / 255, (argb & 255) / 255];
const TYPES = { BLACK_HOLE: 0, PORTAL: 1, ECLIPSE: 2, RIFT: 3, CELESTIAL_BODY: 4, AURORA: 5, DISTANT_BOLTS: 6 };
// Uma linha da textura do passe sky, igual ao SkyFrame.draw (10 texels: 6 da camada e 4 raios ao longe).
function skyRow(l) {
  const p = rgb(l.primary).map(v => v * l.intensity), q = rgb(l.secondary).map(v => v * l.intensity);
  const spin = l.spinDegrees || 0, roll = (l.rotation + (l.type === 'PORTAL' ? spin : 0)) * Math.PI / 180;
  return [...l.dir, l.size / 2 * Math.PI / 180,
    TYPES[l.type], roll % (Math.PI * 2), l.tilt * Math.PI / 180, l.progress,
    ...p, l.weight,
    ...q, 0,
    l.seed, l.seconds, l.flash ?? 1, l.anchor || 0,
    (spin % 360) * Math.PI / 180, 0, 0, 0,
    ...[0, 1, 2, 3].flatMap(b => l.bolts && l.bolts[b] ? l.bolts[b] : [0, 0, 0, 0])];
}
// Peso de uma camada no momento, como Sky.State: o mesmo que o Java faria com as trilhas da receita.
function layer(type, o) {
  const d = { BLACK_HOLE: { size: 40, tilt: 80, lens: 0.6, intensity: 1.4, primary: 0xffffe6c0, secondary: 0xffff6a20 },
    PORTAL: { size: 30, tilt: 0, intensity: 1.2, primary: 0xffb8ecff, secondary: 0xffb040ff },
    ECLIPSE: { size: 13, tilt: 0, intensity: 1, primary: 0xffeaf2ff, secondary: 0xffff5a3a } }[type];
  return { type, rotation: 0, progress: 1, weight: 1, seed: 0.37, seconds: 12, lens: 0, ...d, ...o };
}

// Momentos: formação, auge, dissipação. Cada cena de céu tem o ambiente (céu do jogo, sol, névoa), a câmera, as
// camadas e a atmosfera do efeito naquele instante.
const DAY = { sky: [0.47, 0.65, 1.0], fog: [0.73, 0.84, 1.0], renderDistance: 256, ambient: 0.45, sunLight: 0.6 };
const DUSK = { sky: [0.16, 0.22, 0.48], fog: [0.62, 0.47, 0.52], renderDistance: 256, ambient: 0.16, sunLight: 0.2 };
function env(base, timeOfDay) {
  const axes = vanillaAxes(timeOfDay);
  return { ...base, timeOfDay, axes, sunDir: axes[1] };
}
const BH_DIR = skyDir(-25, 16), PORTAL_DIR = skyDir(15, 28);
const ECL_TIME = 0.8194;   // manhã: sol a ~25 graus, a leste
const ECL_DIR = vanillaAxes(ECL_TIME)[1];
function bh(o) { return layer('BLACK_HOLE', { dir: BH_DIR, rotation: -8, ...o }); }
function portal(o) { return layer('PORTAL', { dir: PORTAL_DIR, rotation: 0, ...o }); }
function eclipse(o) { return layer('ECLIPSE', { dir: ECL_DIR, rotation: 200, anchor: 1, ...o }); }
const NIGHTFALL = (w, stars, blend) => ({ skyTop: [0.03, 0.03, 0.09], skyHorizon: [0.32, 0.16, 0.24], blend, fogColor: [0.3, 0.2, 0.28],
  fogDensity: 0.25 * w, fogStart: 60, fogEnd: 240, exposure: 1, tint: [1, 1, 1], tintStrength: 0, stars: stars * w, weight: w });
const ECLIPSE_SKY = (w, stars) => ({ skyTop: [0.02, 0.03, 0.08], skyHorizon: [0.42, 0.3, 0.38], blend: 0.85 * w, fogColor: [0.25, 0.22, 0.32],
  fogDensity: 0.2 * w, fogStart: 80, fogEnd: 256, exposure: 1, tint: [0.75, 0.82, 1.0], tintStrength: 0.15 * w, stars: stars, weight: w });
const SKY_CENAS = {
  buraco_negro_formacao: { env: env(DUSK, 0.21), look: [BH_DIR, -8, 4], layers: [bh({ size: 18, progress: 0.3, weight: 0.8, seconds: 2 })],
    atmo: NIGHTFALL(0.6, 0.6, 0.6) },
  buraco_negro_auge: { env: env(DUSK, 0.21), look: [BH_DIR, -8, 4], layers: [bh({ size: 64, progress: 1, seconds: 14, spinDegrees: 260 })],
    atmo: NIGHTFALL(1, 0.8, 0.9) },
  buraco_negro_dissipacao: { env: env(DUSK, 0.21), look: [BH_DIR, -8, 4], layers: [bh({ size: 12, progress: 0.55, weight: 0.85, seconds: 30, spinDegrees: 600 })],
    atmo: NIGHTFALL(0.7, 0.8, 0.7) },
  portal_formacao: { env: env(DUSK, 0.23), look: [PORTAL_DIR, -12, 0], layers: [portal({ size: 34, progress: 0.05, seconds: 1.13 })],
    atmo: NIGHTFALL(0.5, 0.5, 0.5) },
  portal_auge: { env: env(DUSK, 0.23), look: [PORTAL_DIR, -12, 0], layers: [portal({ size: 34, progress: 1, seconds: 9, spinDegrees: 90 })],
    atmo: NIGHTFALL(0.9, 0.8, 0.8) },
  portal_dissipacao: { env: env(DUSK, 0.23), look: [PORTAL_DIR, -12, 0], layers: [portal({ size: 34, progress: 0.42, weight: 0.85, seconds: 20, spinDegrees: 200 })],
    atmo: NIGHTFALL(0.6, 0.6, 0.6) },
  eclipse_formacao: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], layers: [eclipse({ progress: 0.62, weight: 1, seconds: 3 })],
    atmo: ECLIPSE_SKY(0.25, 0), darkening: true },
  eclipse_auge: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], layers: [eclipse({ progress: 1, seconds: 15 })],
    atmo: ECLIPSE_SKY(1, 0.9), darkening: true },
  // Raios ao longe (DISTANT_BOLTS) durante a totalidade: pés relativos à câmera, envelope, semente (SkyMath.bolts).
  raios_ao_longe: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], atmo: ECLIPSE_SKY(1, 0.9), darkening: true,
    layers: [eclipse({ progress: 1, seconds: 15 }), { ...layer('ECLIPSE', {}), type: 'DISTANT_BOLTS', dir: ECL_DIR, size: 180,
      progress: 0.6, intensity: 1.2, primary: 0xffeef4ff, secondary: 0xff9a7cff, rotation: 0, tilt: 0, anchor: 0,
      bolts: [[200, 60, 1, 0.31], [230, -110, 0.55, 0.77]] }] },
  eclipse_dissipacao: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], layers: [eclipse({ progress: 0.955, seconds: 25 })],
    atmo: ECLIPSE_SKY(0.8, 0.5), darkening: true },
};

// 0.14 (fase 1): cenas de referência visual da Sky API da 0.12.0 (scripts/referencia_ceu.cjs compara os PNGs com
// src/test/resources/referencia-ceu/). Cobrem os tipos que as cenas acima não mostram (fenda, astro com anéis, aurora),
// quatro camadas juntas e a atmosfera sozinha (sem passe sky, como no jogo quando não há camada). Fora da folha.
const RIFT_DIR = skyDir(-10, 35), BODY_DIR = skyDir(30, 22), AURORA_DIR = skyDir(0, 20);
const rift = o => ({ ...layer('PORTAL', {}), type: 'RIFT', dir: RIFT_DIR, size: 40, tilt: 0, rotation: 25, intensity: 1.3,
  primary: 0xffd8b0ff, secondary: 0xff100018, ...o });
const body = o => ({ ...layer('PORTAL', {}), type: 'CELESTIAL_BODY', dir: BODY_DIR, size: 24, tilt: 70, rotation: 15, intensity: 1,
  primary: 0xffc89070, secondary: 0xffe8d0a0, progress: 0.7, ...o });
const aurora = o => ({ ...layer('PORTAL', {}), type: 'AURORA', dir: AURORA_DIR, size: 160, tilt: 0, rotation: 0, intensity: 1,
  primary: 0xff40ff90, secondary: 0xff8040ff, progress: 0.8, ...o });
const REF_CENAS = {
  ref_fenda: { env: env(DUSK, 0.23), look: [RIFT_DIR, -10, 0], layers: [rift({ progress: 0.8, seconds: 6 })], atmo: NIGHTFALL(0.6, 0.5, 0.6) },
  ref_astro: { env: env(DUSK, 0.23), look: [BODY_DIR, -8, 0], layers: [body({ seconds: 6, spinDegrees: 40 })], atmo: NIGHTFALL(0.6, 0.5, 0.6) },
  ref_aurora: { env: env(DUSK, 0.25), look: [AURORA_DIR, -14, 0], layers: [aurora({ seconds: 9 })], atmo: NIGHTFALL(0.9, 0.8, 0.8) },
  ref_quatro: { env: env(DUSK, 0.23), look: [skyDir(5, 28), -14, 0], atmo: NIGHTFALL(0.8, 0.7, 0.7),
    layers: [portal({ size: 26, progress: 1, seconds: 9, spinDegrees: 90 }), body({ seconds: 6 }), rift({ progress: 0.6, seconds: 6 }),
      aurora({ seconds: 9, weight: 0.7 })] },
  ref_atmosfera: { env: env(DUSK, 0.21), look: [BH_DIR, -8, 4], layers: [], atmo: NIGHTFALL(1, 0.8, 0.9), noSkyPass: true },
};

// 0.14 (fase 2): fundos de céu (Backdrop). Texturas de teste geradas aqui, 8 bits como no jogo (linha 0 = topo):
//   grade     cubemap 3 × 2 de 32 px, uma cor por face (as mesmas do experimento da fase 1) e linhas escuras a cada 8 px;
//   nebulosa  panorama 128 × 64 opaco, cores suaves que mudam com o azimute e a altura;
//   meia      o mesmo panorama com alfa 128 (cobertura 0,5) em toda parte;
//   furada    o panorama com alfa 0 na metade de baixo do céu (elevação < 20°) e 255 acima.
const FACE_RGB = [[0xC8, 0x32, 0x32], [0x32, 0xB4, 0x32], [0x3C, 0x50, 0xE6], [0xDC, 0xC8, 0x28], [0xC8, 0x32, 0xC8], [0x28, 0xC8, 0xC8]];
function backdropTexture(name) {
  if (name === 'grade') {
    const S = 32, w = S * 3, h = S * 2, u8 = new Array(w * h * 4);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const f = Math.floor(y / S) * 3 + Math.floor(x / S), line = x % 8 === 0 || y % 8 === 0;
      const c = line ? [16, 16, 16] : FACE_RGB[f];
      u8.splice((y * w + x) * 4, 4, c[0], c[1], c[2], 255);
    }
    return { w, h, u8 };
  }
  const w = 128, h = 64, u8 = new Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const u = (x + 0.5) / w, v = (y + 0.5) / h, el = 90 - v * 180;
    const c = [0.35 + 0.3 * Math.sin(u * Math.PI * 2), 0.25 + 0.2 * Math.cos(u * Math.PI * 4 + 1), 0.55 + 0.35 * (1 - v)];
    let a = 255;
    if (name === 'meia') a = 128;
    if (name === 'furada') a = el < 20 ? 0 : 255;
    u8.splice((y * w + x) * 4, 4, ...c.map(k => Math.round(Math.min(Math.max(k, 0), 1) * 255)), a);
  }
  return { w, h, u8 };
}
function backdropImages(cena) {
  const out = {};
  for (const b of cena.backdrop || []) out[b.texture] = backdropTexture(b.texture);
  return out;
}
const fundo = (texture, kind, o) => ({ texture, kind, alpha: 1, rotation: 0, tint: [1, 1, 1], ...o });
// Atmosfera sem estrelas nem névoa: o teste de composição compara com mix(tela, gradiente, blend) exatamente.
const TINGE = { skyTop: [0.05, 0.02, 0.12], skyHorizon: [0.85, 0.35, 0.2], blend: 0.7, fogColor: [0.3, 0.2, 0.28],
  fogDensity: 0, fogStart: 60, fogEnd: 240, exposure: 1, tint: [1, 1, 1], tintStrength: 0, stars: 0, weight: 1 };
const SUN_TIME = 0.97;   // sol a ~11 graus, a leste: dentro do quadro com a câmera virada para ele
const SUN_DIR = vanillaAxes(SUN_TIME)[1];
const FUNDO_CENAS = {
  fundo_atmosfera_opaco: { env: env(DUSK, 0.21), look: [skyDir(0, 25), -10, 0], layers: [], atmo: TINGE, noSkyPass: true,
    backdrop: [fundo('nebulosa', 2)] },
  fundo_atmosfera_opaco_passe: { env: env(DUSK, 0.21), look: [skyDir(0, 25), -10, 0], layers: [], atmo: TINGE,
    backdrop: [fundo('nebulosa', 2)] },
  fundo_atmosfera_parcial: { env: env(DUSK, 0.21), look: [skyDir(0, 25), -10, 0], layers: [], atmo: TINGE, noSkyPass: true,
    backdrop: [fundo('meia', 2)] },
  fundo_atmosfera_furado: { env: env(DUSK, 0.21), look: [skyDir(0, 25), -10, 0], layers: [], atmo: TINGE,
    backdrop: [fundo('furada', 2)] },
  fundo_sol_opaco: { env: env(DAY, SUN_TIME), look: [SUN_DIR, 0, 0], layers: [], hideSun: 1, backdrop: [fundo('nebulosa', 2)] },
  fundo_sol_parcial: { env: env(DAY, SUN_TIME), look: [SUN_DIR, 0, 0], layers: [], hideSun: 1, backdrop: [fundo('meia', 2)] },
  fundo_eclipse_opaco: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], layers: [eclipse({ progress: 1, seconds: 15 })],
    atmo: ECLIPSE_SKY(1, 0.9), darkening: true, backdrop: [fundo('nebulosa', 2)] },
  fundo_eclipse_parcial: { env: env(DAY, ECL_TIME), look: [ECL_DIR, -9, 10], layers: [eclipse({ progress: 1, seconds: 15 })],
    atmo: ECLIPSE_SKY(1, 0.9), darkening: true, backdrop: [fundo('meia', 2)] },
  fundo_buraco_negro: { env: env(DUSK, 0.21), look: [BH_DIR, -8, 4], layers: [bh({ size: 40, progress: 1, seconds: 14, spinDegrees: 260 })],
    atmo: NIGHTFALL(1, 0.8, 0.9), backdrop: [fundo('grade', 1)] },
  fundo_cubemap_girado: { env: env(DAY, 0.25), look: [skyDir(45, 20), 0, 0], layers: [], noSkyPass: true,
    backdrop: [fundo('grade', 1, { rotation: 30 })] },
  fundo_transicao: { env: env(DUSK, 0.21), look: [skyDir(0, 25), -10, 0], layers: [], atmo: TINGE,
    backdrop: [fundo('grade', 1), fundo('nebulosa', 2, { alpha: 0.4 })] },
};

// Cobertura do eclipse (Sky.eclipseCoverage) e exposição do mundo (SkyMath.exposure), para o escurecimento.
function eclipseCoverage(progress) {
  const p = Math.min(Math.max(progress, 0), 1), d = (1 - p) * 2.2, big = 1, small = 0.97;
  if (d >= big + small) return 0;
  if (d <= big - small) return 1;
  const a = Math.acos(Math.min(Math.max((d * d + small * small - big * big) / (2 * d * small), -1), 1));
  const b = Math.acos(Math.min(Math.max((d * d + big * big - small * small) / (2 * d * big), -1), 1));
  const area = small * small * a + big * big * b - 0.5 * Math.sqrt(Math.max(0, (-d + small + big) * (d + small - big) * (d - small + big) * (d + small + big)));
  return Math.min(Math.max(area / (Math.PI * small * small), 0), 1);
}

// Lentes (SkyFrame.buildLenses e SkyMath.einstein/lensExtent): até 2 buracos negros.
function skyLenses(layers) {
  const out = new Array(12).fill(0);
  let n = 0;
  for (const l of layers) {
    if (n >= 2 || l.type !== 'BLACK_HOLE' || !(l.lens > 0)) continue;
    const s = l.lens * l.weight, r = l.size / 2 * Math.PI / 180;
    out.splice(n * 4, 4, ...l.dir, r * 0.2 * (1.05 + 2.2 * s));
    out[8 + n] = r * (2.2 + 2.0 * s);
    n++;
  }
  out[10] = n;
  return out;
}

// Sol do jogo: a textura de verdade (JAR de recursos do Minecraft baixado pelo build) ou um sol sintético.
function sunTexture() {
  const dir = path.join(ROOT, 'build/moddev/artifacts');
  const jar = fs.existsSync(dir) ? fs.readdirSync(dir).find(f => f.includes('client-extra')) : null;
  if (jar) {
    try {
      const png = execFileSync('unzip', ['-p', path.join(dir, jar), 'assets/minecraft/textures/environment/sun.png']);
      return { png: png.toString('base64') };
    } catch (e) { /* sol sintético */ }
  }
  const data = new Float32Array(32 * 32 * 4);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const core = x >= 12 && x < 20 && y >= 12 && y < 20 ? 1 : 0, glow = Math.max(0, 1 - Math.hypot(x - 15.5, y - 15.5) / 11) * 0.12;
    data.set([Math.min(1, core + glow), Math.min(1, core * 0.95 + glow), Math.min(1, core * 0.6 + glow), 1], (y * 32 + x) * 4);
  }
  return { w: 32, h: 32, data: Array.from(data) };
}

// Pré-passe só da prévia: soma o sol do jogo como o LevelRenderer (SRC_ALPHA, ONE; quadrado de meio lado 30 a 100).
const VANILLA_FSH = `
uniform sampler2D PreSun;
uniform vec3 PreAxisX; uniform vec3 PreAxisY; uniform vec3 PreAxisZ;
void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (skyAt(texCoord)) {
        vec3 d = viewDir(texCoord);
        vec3 l = vec3(dot(d, PreAxisX), dot(d, PreAxisY), dot(d, PreAxisZ));
        if (l.y > 1e-4) {
            vec2 p = l.xz * (100.0 / l.y);
            if (abs(p.x) <= 30.0 && abs(p.y) <= 30.0) {
                ivec2 size = textureSize(PreSun, 0);
                vec4 t = texelFetch(PreSun, clamp(ivec2((p + 30.0) / 60.0 * vec2(size)), ivec2(0), size - 1), 0);
                color = min(color + t.rgb * t.a, vec3(1.0));
            }
        }
    }
    fragColor = vec4(color, 1.0);
}`;

// 0.14 (fase 2): pré-passe só da prévia que faz o papel da geometria do Backdrop em AFTER_SKY: depois do céu e do sol
// do jogo, antes dos passes do motor, desenha os fundos sobre os pixels de céu com a mesma mistura por alfa da
// geometria (caBackdropOver de calamidade_sky.glsl, a consulta que o BackdropMath faz em Java).
const BACKDROP_FSH = `
#include "calamidade_fx.glsl"
#include "calamidade_sky.glsl"
void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (skyAt(texCoord)) {
        float coverage;
        color = caBackdropOver(color, viewDir(texCoord), coverage);
    }
    fragColor = vec4(color, 1.0);
}`;

// Uniforms e texturas dos fundos de uma cena (BackdropFrame.uniforms): slot 0 embaixo, slot 1 em cima.
function backdropUniforms(cena) {
  const f4 = {}, f3 = {}, textures = {};
  for (let i = 0; i < 2; i++) {
    const b = (cena.backdrop || [])[i];
    f4['CaBd' + i] = b ? [b.kind, b.alpha, (b.rotation || 0) * Math.PI / 180, 0] : [0, 0, 0, 0];
    f3['CaBdTint' + i] = b ? (b.tint || [1, 1, 1]) : [1, 1, 1];
    if (b) textures['CaBdTex' + i] = b.texture;
  }
  return { f4, f3, textures };
}

function skyPasses(cena) {
  const e = cena.env;
  const layers = cena.layers;
  const rows = layers.map(skyRow);
  let darkening = 0;
  for (const l of layers) darkening = Math.max(darkening, l.type === 'ECLIPSE' ? 0.75 * l.weight * Math.pow(eclipseCoverage(l.progress), 4) : 0);
  const exposure = 1 - 0.85 * darkening;
  const hideSun = cena.hideSun ?? Math.max(0, ...layers.filter(l => l.anchor === 1).map(l => l.weight));
  // Controle negativo (backdropOff): o fundo é desenhado, mas os passes não sabem dele (o protótipo da fase 1).
  const bd = backdropUniforms(cena), bdPass = cena.backdropOff ? backdropUniforms({}) : bd;
  const lens = skyLenses(layers);
  const a = cena.atmo;
  const lensUniforms = prefix => ({ [prefix + 'Lens0']: lens.slice(0, 4), [prefix + 'Lens1']: lens.slice(4, 8),
    [prefix + 'LensExt']: [lens[8], lens[9], lens[10], 0] });
  const passes = [
    { source: VANILLA_FSH, textures: { PreSun: 'sun' },
      f3: { PreAxisX: e.axes[0], PreAxisY: e.axes[1], PreAxisZ: e.axes[2] } },
    { program: 'sky', data: { name: 'CaSkyData', texels: 10, rows },
      textures: { CaSkySunTex: 'sun', CaSkyMoonTex: 'sun', ...bdPass.textures },
      f4: { ...bdPass.f4, CaSkyInfo: [rows.length, exposure, 2, layers.some(l => l.type === 'DISTANT_BOLTS') ? 1 : 0],
        CaSkyVanilla: [hideSun, 0, 0, 1], CaSkyMisc: [e.renderDistance, 0, 0, 0], ...lensUniforms('CaSky'),
        // Céu da atmosfera pintado aqui, debaixo das camadas (SkyFrame.draw).
        CaSkyAtmoA: a ? [a.blend, a.fogDensity, a.stars, 1] : [0, 0, 0, 0], CaSkyAtmoB: [0, 0, 0, 0] },
      f3: { ...bdPass.f3, CaSkyAxisX: e.axes[0], CaSkyAxisY: e.axes[1], CaSkyAxisZ: e.axes[2], CaSkyVanillaSky: e.sky, CaSkyVanillaFog: e.fog,
        CaSkyAtmoTop: a ? a.skyTop : [0, 0, 0], CaSkyAtmoHorizon: a ? a.skyHorizon : [0, 0, 0], CaSkyAtmoFog: a ? a.fogColor : [1, 1, 1] },
      f1: { GTime: 37.0 } },
  ];
  if (cena.noSkyPass) passes.splice(1, 1);
  if (cena.noVanillaSun) passes[0].textures = {}, passes[0].f3 = { PreAxisX: [0, 0, 1], PreAxisY: [0, -1, 0], PreAxisZ: [1, 0, 0] };
  if (cena.backdrop) passes.splice(1, 0, { source: BACKDROP_FSH, textures: bd.textures, f4: bd.f4, f3: bd.f3 });
  if (a) {
    passes.push({ program: 'atmosphere', textures: bdPass.textures,
      f3: { ...bdPass.f3, CaAtmoSkyTop: a.skyTop, CaAtmoSkyHorizon: a.skyHorizon, CaAtmoFogColor: a.fogColor, CaAtmoTint: a.tint,
        CaAtmoVanillaSky: e.sky, CaAtmoVanillaFog: e.fog },
      f4: { ...bdPass.f4, CaAtmoA: [a.blend, a.fogDensity, a.fogStart, a.fogEnd], CaAtmoB: [a.exposure, a.tintStrength, a.stars, 0],
        CaAtmoC: [e.renderDistance, cena.noSkyPass ? 0 : 1, 0, 0], ...lensUniforms('CaAtmo') },
      f1: { GTime: 37.0 } });
  }
  return passes;
}

// ------------------------------------------------------------------ WebGL2

function commonGlsl() {
  const jar = path.join(ROOT, 'libs/rimulib-1.3.0-neoforge.jar');
  return execFileSync('unzip', ['-p', jar, 'assets/rimulib/shaders/fx/common.glsl'], { encoding: 'utf8' });
}

// Como a RimuLib: cada linha '#include "arquivo"' vira o arquivo da pasta fx do motor, sem recursão.
function assemble(program, source) {
  const body = (source ?? fs.readFileSync(path.join(FX, program + '.fsh'), 'utf8'))
    .split('\n').map(l => {
      const m = l.trim().match(/^#include "(.+)"$/);
      return m ? fs.readFileSync(path.join(FX, m[1]), 'utf8') : l;
    }).join('\n');
  const common = commonGlsl().replace('#version 150', '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;');
  return common + '\n' + body;
}

// Passes de feixes e luzes no formato genérico.
function effectPasses(cena) {
  const passes = [];
  if (cena.lights) passes.push({ program: 'lights', data: { name: 'CaLightData', texels: 4, rows: cena.lights },
    f4: { CaLightInfo: [cena.lights.length, ...(cena.lightExtra || [0, 0, 0, 0]).slice(1)] } });
  if (cena.beams) passes.push({ program: 'beams', data: { name: 'CaBeamData', texels: BEAM_TEXELS, rows: cena.beams },
    f4: { CaBeamInfo: [cena.beams.length, 0, 0, 0] } });
  return passes;
}

const sceneCache = new Map();
async function render(page, name, cena) {
  const sky = !!cena.env;
  const cam = sky ? lookAt(...cena.look) : camera(cena.pitch ?? -8, cena.yaw ?? 0);
  const proj = perspective(FOV, W / H, 0.05, 1024);
  const view = viewMatrix(cam);
  const key = sky ? JSON.stringify([cena.look, cena.env.timeOfDay, cena.env.sky]) : null;
  let base = key && sceneCache.get(key);
  if (!base) {
    base = sky ? skyScene(cam, proj, view, cena.env) : scene(cam, proj, view, cena.night);
    if (key) sceneCache.set(key, base);
  }
  const { depth, color, viewProj } = base;
  // PREVIA_PASSES=n roda só os n primeiros passes (depuração: 1 = só a cena e o sol do jogo).
  const limit = process.env.PREVIA_PASSES ? Number(process.env.PREVIA_PASSES) : Infinity;
  const passes = (sky ? skyPasses(cena) : effectPasses(cena)).slice(0, limit);
  const shaders = passes.map(p => assemble(p.program, p.source));
  const png = await page.evaluate(async ({ W, H, depth, color, invProj, invView, viewProj, passes, shaders, images }) => {
    const canvas = document.createElement('canvas');
    canvas.width = W; canvas.height = H;
    const gl = canvas.getContext('webgl2', { preserveDrawingBuffer: true });
    if (!gl.getExtension('EXT_color_buffer_float')) throw new Error('sem EXT_color_buffer_float');
    const vsSrc = '#version 300 es\nout vec2 texCoord;\nvoid main(){vec2 p[3]=vec2[](vec2(-1.0,-1.0),vec2(3.0,-1.0),vec2(-1.0,3.0));texCoord=p[gl_VertexID]*0.5+0.5;gl_Position=vec4(p[gl_VertexID],0.0,1.0);}';
    const compile = (type, src) => {
      const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
      return s;
    };
    const params = () => {
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    };
    const tex = (w, h, internal, format, data) => {
      const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); params();
      gl.texImage2D(gl.TEXTURE_2D, 0, internal, w, h, 0, format, gl.FLOAT, data);
      return t;
    };
    const named = {};
    for (const [key, img0] of Object.entries(images || {})) {
      if (img0.png) {
        const img = new Image();
        img.src = 'data:image/png;base64,' + img0.png;
        await img.decode();
        const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); params();
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, img);
        named[key] = t;
      } else if (img0.u8) {
        // 0.14: texturas de 8 bits como as do jogo (fundos de céu), linha 0 = topo da imagem (v = 0), como no jogo.
        const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); params();
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, img0.w, img0.h, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(img0.u8));
        named[key] = t;
      } else named[key] = tex(img0.w, img0.h, gl.RGBA32F, gl.RGBA, new Float32Array(img0.data));
    }
    const depthTex = tex(W, H, gl.R32F, gl.RED, new Float32Array(depth));
    let colorTex = tex(W, H, gl.RGBA32F, gl.RGBA, new Float32Array(color));
    const vs = compile(gl.VERTEX_SHADER, vsSrc);
    for (let k = 0; k < passes.length; k++) {
      const pass = passes[k];
      const prog = gl.createProgram();
      gl.attachShader(prog, vs); gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, shaders[k])); gl.linkProgram(prog);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
      // Texturas criadas antes de qualquer bind: tex() liga a textura nova na unidade ativa.
      let dataTex = null;
      if (pass.data) {
        const rows = pass.data.rows.length;
        const flat = new Float32Array(Math.max(rows, 1) * pass.data.texels * 4);
        pass.data.rows.forEach((row, i) => flat.set(row, i * pass.data.texels * 4));
        dataTex = tex(pass.data.texels, Math.max(rows, 1), gl.RGBA32F, gl.RGBA, flat);
      }
      // Saída em 8 bits por canal, como o framebuffer do jogo: valores acima de 1 são cortados entre os passes.
      const out = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, out); params();
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, W, H, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, out, 0);
      gl.viewport(0, 0, W, H); gl.useProgram(prog);
      let unit = 0;
      const bind = (name, t) => { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); gl.uniform1i(gl.getUniformLocation(prog, name), unit); unit++; };
      bind('DiffuseSampler', colorTex); bind('DepthSampler', depthTex);
      if (dataTex) bind(pass.data.name, dataTex);
      for (const [uniform, which] of Object.entries(pass.textures || {})) bind(uniform, named[which]);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'InvProj'), false, invProj);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'InvView'), false, invView);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'ViewProj'), false, viewProj);
      gl.uniform2f(gl.getUniformLocation(prog, 'OutSize'), W, H);
      for (const [n, v] of Object.entries(pass.f1 || {})) gl.uniform1f(gl.getUniformLocation(prog, n), v);
      for (const [n, v] of Object.entries(pass.f3 || {})) gl.uniform3f(gl.getUniformLocation(prog, n), ...v);
      for (const [n, v] of Object.entries(pass.f4 || {})) gl.uniform4f(gl.getUniformLocation(prog, n), ...v);
      for (const [n, v] of Object.entries(pass.vec4s || {})) gl.uniform4fv(gl.getUniformLocation(prog, n), new Float32Array(v));
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      colorTex = out;
    }
    // Copia para o canvas (sRGB 8 bits) com uma passada simples.
    const blit = gl.createProgram();
    gl.attachShader(blit, vs);
    gl.attachShader(blit, compile(gl.FRAGMENT_SHADER, '#version 300 es\nprecision highp float;\nin vec2 texCoord;uniform sampler2D S;out vec4 o;void main(){vec3 c=texture(S,texCoord).rgb;o=vec4(min(c,vec3(1.0)),1.0);}'));
    gl.linkProgram(blit);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, W, H); gl.useProgram(blit);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, colorTex); gl.uniform1i(gl.getUniformLocation(blit, 'S'), 0);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    return canvas.toDataURL('image/png');
  }, { W, H, depth: Array.from(depth), color: Array.from(color), invProj: invert(proj), invView: invert(view),
       viewProj, passes, shaders, images: sky ? { sun: sunTexture(), ...backdropImages(cena) } : {} });
  const file = path.join(OUT, name + '.png');
  fs.writeFileSync(file, Buffer.from(png.split(',')[1], 'base64'));
  return file;
}

// Monta uma folha 3 × 3 (linhas: buraco negro, portal, eclipse; colunas: formação, auge, dissipação) com rótulos.
async function contactSheet(page, files) {
  const images = files.map(f => fs.readFileSync(f).toString('base64'));
  const png = await page.evaluate(async ({ images, W, H }) => {
    const cols = 3, rows = images.length / 3, pad = 6, label = 22;
    const canvas = document.createElement('canvas');
    canvas.width = cols * W + (cols + 1) * pad; canvas.height = rows * (H + label) + (rows + 1) * pad;
    const g = canvas.getContext('2d');
    g.fillStyle = '#101418'; g.fillRect(0, 0, canvas.width, canvas.height);
    const names = ['formação', 'auge', 'dissipação'], kinds = ['BLACK_HOLE', 'PORTAL', 'ECLIPSE'];
    for (let i = 0; i < images.length; i++) {
      const img = new Image(); img.src = 'data:image/png;base64,' + images[i]; await img.decode();
      const c = i % cols, r = Math.floor(i / cols);
      const x = pad + c * (W + pad), y = pad + r * (H + label + pad);
      g.fillStyle = '#d2e9f4'; g.font = '15px sans-serif'; g.fillText(kinds[r] + ' · ' + names[c], x + 2, y + 16);
      g.drawImage(img, x, y + label);
    }
    return canvas.toDataURL('image/png');
  }, { images, W, H });
  const file = path.join(OUT, 'ceu-0120.png');
  fs.writeFileSync(file, Buffer.from(png.split(',')[1], 'base64'));
  return file;
}

const ALL = { ...CENAS, ...SKY_CENAS, ...REF_CENAS, ...FUNDO_CENAS };
module.exports = { SKY_CENAS, REF_CENAS, FUNDO_CENAS, ALL, render, lookAt, skyDir, vanillaAxes, skyScene, perspective, viewMatrix,
  W, H, FOV, OUT, backdropTexture };

async function main() {
  const { chromium } = require('playwright');
  const wanted = process.argv.slice(2);
  const sheet = wanted.length === 1 && wanted[0] === 'ceu';
  const names = sheet ? Object.keys(SKY_CENAS) : wanted.length ? wanted : Object.keys(ALL);
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const page = await browser.newPage();
    const files = [];
    for (const name of names) {
      if (!ALL[name]) throw new Error('cena desconhecida: ' + name);
      const file = await render(page, name, ALL[name]);
      files.push(file);
      console.log('prévia ' + path.relative(ROOT, file));
    }
    if (sheet || (!wanted.length)) {
      const skyFiles = Object.keys(SKY_CENAS).filter(n => n !== 'raios_ao_longe').map(n => path.join(OUT, n + '.png'));
      if (skyFiles.every(f => fs.existsSync(f))) console.log('folha ' + path.relative(ROOT, await contactSheet(page, skyFiles)));
    }
  } finally {
    await browser.close();
  }
}

if (require.main === module) main().catch(err => { console.error(err.message || err); process.exit(1); });
