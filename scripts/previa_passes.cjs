#!/usr/bin/env node
// Prévia dos passes do motor (beams, lights) sem o jogo: roda o shader real, com o common.glsl da RimuLib, no WebGL2
// do Chromium headless (SwiftShader), sobre uma cena sintética com profundidade (chão, um muro, pilares e céu), e
// grava um PNG por cena em build/previa/. Serve para conferir a aparência e a oclusão antes do teste em jogo; não
// substitui o teste numa GPU de verdade.
//
// Requer Node, Playwright com Chromium e o JAR da RimuLib em libs/ (./gradlew build baixa). Uso, na raiz:
//     node scripts/previa_passes.cjs [cena...]
// Cenas: veja CENAS abaixo. Sem argumento, gera todas.
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

const BEAM_TEXELS = 6;
function beamRow(a, b, core, halo, coreRgb, haloRgb, gain, reveal, pulses = 0, pulseTravel = 0, pulseSize = 2,
                 waveAmp = 0, waveLen = 4, waveTravel = 0, noise = 0, noiseTime = 0) {
  return [...a, core / 2, ...b, halo / 2, ...coreRgb.map(v => v * gain), reveal, ...haloRgb.map(v => v * gain), pulses,
    pulseTravel, pulseSize, 1, noiseTime, waveAmp, waveLen, waveTravel, noise];
}

// Linha de luz: posição (ou início/centro), alcance ou raio, fim, queda, cor × intensidade, forma, largura, altura.
const SHAPE = { ponto: 0, segmento: 1, disco: 2, anel: 3 };
function lightRow(shape, a, radius, rgb, gain, { b = a, falloff = 2, width = 0, height = 3 } = {}) {
  return [...a, radius, ...b, falloff, ...rgb.map(v => v * gain), SHAPE[shape], width, height, 0, 0];
}

const CENAS = {
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

// ------------------------------------------------------------------ WebGL2

function commonGlsl() {
  const jar = path.join(ROOT, 'libs/rimulib-1.3.0-neoforge.jar');
  return execFileSync('unzip', ['-p', jar, 'assets/rimulib/shaders/fx/common.glsl'], { encoding: 'utf8' });
}

function assemble(program) {
  const lib = fs.readFileSync(path.join(FX, 'calamidade_fx.glsl'), 'utf8');
  const body = fs.readFileSync(path.join(FX, program + '.fsh'), 'utf8')
    .split('\n').map(l => l.trim() === '#include "calamidade_fx.glsl"' ? lib : l).join('\n');
  const common = commonGlsl().replace('#version 150', '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;');
  return common + '\n' + body;
}

async function render(page, name, cena) {
  const cam = camera(cena.pitch ?? -8, cena.yaw ?? 0);
  const proj = perspective(FOV, W / H, 0.05, 1024);
  const view = viewMatrix(cam);
  const { depth, color, viewProj } = scene(cam, proj, view, cena.night);
  const passes = [];
  if (cena.lights) passes.push({ program: 'lights', data: cena.lights, texels: 4, sampler: 'CaLightData', info: 'CaLightInfo', extra: cena.lightExtra || [0, 0, 0, 0] });
  if (cena.beams) passes.push({ program: 'beams', data: cena.beams, texels: BEAM_TEXELS, sampler: 'CaBeamData', info: 'CaBeamInfo', extra: [0, 0, 0, 0] });
  const shaders = passes.map(p => assemble(p.program));
  const png = await page.evaluate(({ W, H, depth, color, invProj, invView, viewProj, passes, shaders }) => {
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
    const tex = (w, h, internal, format, data) => {
      const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D, 0, internal, w, h, 0, format, gl.FLOAT, data);
      return t;
    };
    const depthTex = tex(W, H, gl.R32F, gl.RED, new Float32Array(depth));
    let colorTex = tex(W, H, gl.RGBA32F, gl.RGBA, new Float32Array(color));
    const vs = compile(gl.VERTEX_SHADER, vsSrc);
    for (let k = 0; k < passes.length; k++) {
      const pass = passes[k];
      const prog = gl.createProgram();
      gl.attachShader(prog, vs); gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, shaders[k])); gl.linkProgram(prog);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
      const rows = pass.data.length;
      const flat = new Float32Array(Math.max(rows, 1) * pass.texels * 4);
      pass.data.forEach((row, i) => flat.set(row, i * pass.texels * 4));
      const dataTex = tex(pass.texels, Math.max(rows, 1), gl.RGBA32F, gl.RGBA, flat);
      const out = tex(W, H, gl.RGBA32F, gl.RGBA, null);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, out, 0);
      gl.viewport(0, 0, W, H); gl.useProgram(prog);
      const bind = (name, unit, t) => { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); gl.uniform1i(gl.getUniformLocation(prog, name), unit); };
      bind('DiffuseSampler', 0, colorTex); bind('DepthSampler', 1, depthTex); bind(pass.sampler, 2, dataTex);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'InvProj'), false, invProj);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'InvView'), false, invView);
      gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'ViewProj'), false, viewProj);
      gl.uniform2f(gl.getUniformLocation(prog, 'OutSize'), W, H);
      gl.uniform4f(gl.getUniformLocation(prog, pass.info), rows, ...pass.extra.slice(1));
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
       viewProj, passes, shaders });
  const file = path.join(OUT, name + '.png');
  fs.writeFileSync(file, Buffer.from(png.split(',')[1], 'base64'));
  return file;
}

async function main() {
  const { chromium } = require('playwright');
  const wanted = process.argv.slice(2);
  const names = wanted.length ? wanted : Object.keys(CENAS);
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const page = await browser.newPage();
    for (const name of names) {
      if (!CENAS[name]) throw new Error('cena desconhecida: ' + name);
      console.log('prévia ' + path.relative(ROOT, await render(page, name, CENAS[name])));
    }
  } finally {
    await browser.close();
  }
}

main().catch(err => { console.error(err.message || err); process.exit(1); });
