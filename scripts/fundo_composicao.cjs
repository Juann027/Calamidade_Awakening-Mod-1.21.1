#!/usr/bin/env node
// Composição dos fundos de céu (0.14, fase 2) com a Atmosphere, o apagamento do sol do jogo e o eclipse, provada por
// imagem: roda os passes reais "sky" e "atmosphere" pela prévia (scripts/previa_passes.cjs, WebGL2/SwiftShader,
// 8 bits entre os passes) e compara sondas de pixel com o resultado esperado, escrito como intenção e não como cópia
// do shader:
//   A. Atmosphere sobre fundo (opaco, parcial, com buraco de alfa, com e sem passe "sky"): em todo pixel de céu,
//      a tela final é mix(tela sem atmosfera, gradiente da atmosfera, blend) — a atmosfera tinge o céu que aparece.
//   B. Sol do jogo apagado sob fundo opaco: nada muda (o fundo já cobriu o sol); sob fundo parcial: a tela fica igual
//      à do mesmo fundo sobre um céu que nunca teve sol.
//   C. Eclipse: o fundo escurece pela exposição do mundo, longe do disco.
// Cada caso tem um controle negativo (os passes sem saber do fundo, como no protótipo da fase 1) que precisa FALHAR:
// prova que a sonda detecta o defeito que a fase 2 corrige.
//
//   node scripts/fundo_composicao.cjs     (sai com 1 se algum caso falhar)
'use strict';
const path = require('path');
const fs = require('fs');
const P = require('./previa_passes.cjs');
const { decode } = require('./referencia_ceu.cjs');

const TOL = 3;   // /255 por canal: arredondamento de 8 bits entre os passes
const smooth = (a, b, x) => { const t = Math.min(Math.max((x - a) / (b - a), 0), 1); return t * t * (3 - 2 * t); };
const norm = a => { const l = Math.hypot(...a); return a.map(v => v / l); };

function geometry(cena) {
  const cam = P.lookAt(...cena.look);
  const proj = P.perspective(P.FOV, P.W / P.H, 0.05, 1024), view = P.viewMatrix(cam);
  const { depth } = P.skyScene(cam, proj, view, cena.env);
  const tan = Math.tan(P.FOV * Math.PI / 360);
  // Linha do PNG (topo = 0) → y do GL (base = 0).
  const dir = (x, row) => {
    const y = P.H - 1 - row, nx = (x + 0.5) / P.W * 2 - 1, ny = (y + 0.5) / P.H * 2 - 1;
    return norm([0, 1, 2].map(k => cam.r[k] * nx * tan * P.W / P.H + cam.u[k] * ny * tan + cam.f[k]));
  };
  const sky = (x, row) => depth[(P.H - 1 - row) * P.W + x] === 1;
  return { dir, sky };
}

async function shot(page, name, cena) {
  await P.render(page, name, cena);
  return decode(path.join(P.OUT, name + '.png'));
}
const px = (img, x, row) => { const i = (row * img.width + x) * 4; return [img.data[i], img.data[i + 1], img.data[i + 2]]; };
const maxDiff = (a, b) => Math.max(...a.map((v, k) => Math.abs(v - b[k])));

// Pior diferença entre a imagem e o esperado em pixels de céu (grade de 4 px), e quantos pixels foram comparados.
function compare(img, expect, filter) {
  let worst = 0, n = 0;
  for (let row = 2; row < P.H - 2; row += 4) for (let x = 2; x < P.W - 2; x += 4) {
    const e = expect(x, row);
    if (!e || (filter && !filter(x, row))) continue;
    worst = Math.max(worst, maxDiff(px(img, x, row), e));
    n++;
  }
  return { worst, n };
}

const results = [];
function check(name, ok, detail) {
  results.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} — ${detail}`);
}

async function atmosphere(page, base, label) {
  const cena = P.FUNDO_CENAS[base];
  const g = geometry(cena), a = cena.atmo;
  const semAtmo = await shot(page, base + '__sem_atmosfera', { ...cena, atmo: null, noSkyPass: true });
  const expect = (x, row) => {
    if (!g.sky(x, row)) return null;
    const d = g.dir(x, row), s = px(semAtmo, x, row), k = smooth(0, 0.6, d[1]);
    return s.map((v, c) => { const grad = (a.skyHorizon[c] + (a.skyTop[c] - a.skyHorizon[c]) * k) * 255; return v + (grad - v) * a.blend; });
  };
  const ok = compare(await shot(page, base, cena), expect);
  check(`A ${label}: atmosfera tinge o fundo que aparece`, ok.worst <= TOL && ok.n > 1000, `pior ${ok.worst.toFixed(1)}/255 em ${ok.n} pixels de céu`);
  return { expect, cena };
}

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(P.OUT, { recursive: true });
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const page = await browser.newPage();
    // A. Atmosphere.
    const opaco = await atmosphere(page, 'fundo_atmosfera_opaco', 'fundo opaco, só o passe de atmosfera');
    await atmosphere(page, 'fundo_atmosfera_opaco_passe', 'fundo opaco, céu pintado pelo passe sky');
    await atmosphere(page, 'fundo_atmosfera_parcial', 'fundo com alfa 0,5');
    await atmosphere(page, 'fundo_atmosfera_furado', 'fundo com metade de baixo transparente, passe sky');
    const neg = compare(await shot(page, 'fundo_atmosfera_opaco__controle', { ...opaco.cena, backdropOff: true }), opaco.expect);
    check('A controle negativo: passes sem saber do fundo (protótipo da fase 1) erram', neg.worst > 20,
      `pior ${neg.worst.toFixed(1)}/255 (o protótipo media ~(60,80,230) → (0,0,55) em jogo)`);

    // B. Sol do jogo apagado (hideSun = 1, como sob um eclipse ou um perfil sem sol). Base: o apagamento da 0.12.0, sem
    // fundo, já erra alguns /255 no quadrado (sol de 8 bits tirado de uma tela de 8 bits); sob um fundo de alfa a, só
    // (1 - a) do sol estava na tela, então o erro esperado é no máximo (1 - a) vezes o de base.
    let baseline = 0;
    {
      const cena = { ...P.FUNDO_CENAS.fundo_sol_opaco, backdrop: undefined }, g = geometry(cena);
      const ref = await shot(page, 'fundo_sol_base__sem_sol', { ...cena, noVanillaSun: true, hideSun: 0 });
      const inSun = (x, row) => Math.abs(x - P.W / 2) < 60 && Math.abs(row - P.H / 2) < 60;
      baseline = compare(await shot(page, 'fundo_sol_base', cena), (x, row) => g.sky(x, row) ? px(ref, x, row) : null, inSun).worst;
      console.log(`INFO apagamento do sol da 0.12.0 sem fundo (base): pior ${baseline.toFixed(1)}/255`);
    }
    for (const [base, label] of [['fundo_sol_opaco', 'opaco'], ['fundo_sol_parcial', 'alfa 0,5']]) {
      const cena = P.FUNDO_CENAS[base], g = geometry(cena);
      // Esperado: o mesmo fundo sobre um céu do jogo que nunca teve sol (só os passes, sem o sol na tela).
      const ref = await shot(page, base + '__sem_sol', { ...cena, noVanillaSun: true, hideSun: 0 });
      const img = await shot(page, base, cena);
      // Só o quadrado do sol (centro da tela, ±60 px ≈ o quadrado de meio lado 30 a 100 blocos com FOV 70 a 640 px).
      const inSun = (x, row) => Math.abs(x - P.W / 2) < 60 && Math.abs(row - P.H / 2) < 60;
      const expect = (x, row) => g.sky(x, row) ? px(ref, x, row) : null;
      const r = compare(img, expect, inSun);
      const limit = label === 'opaco' ? TOL : baseline * 0.5 + TOL;
      check(`B sol do jogo apagado sob fundo ${label}: sem marca no fundo`, r.worst <= limit && r.n > 50,
        `pior ${r.worst.toFixed(1)}/255 em ${r.n} pixels do quadrado do sol (limite ${limit.toFixed(1)})`);
      if (label === 'opaco') {
        const c = compare(await shot(page, base + '__controle', { ...cena, backdropOff: true }), expect, inSun);
        check('B controle negativo: sem saber do fundo, o passe subtrai o sol do fundo', c.worst > 20, `pior ${c.worst.toFixed(1)}/255`);
      }
    }

    // C. Eclipse sem atmosfera: longe do disco, o fundo escurece pela exposição do mundo (SkyMath.exposure).
    {
      const cena = { ...P.FUNDO_CENAS.fundo_eclipse_opaco, atmo: null }, g = geometry(cena);
      const fundoSo = await shot(page, 'fundo_eclipse__so_fundo', { ...cena, layers: [], noSkyPass: true });
      const img = await shot(page, 'fundo_eclipse__sem_atmosfera', cena);
      const exposure = 1 - 0.85 * 0.75;   // progresso 1: cobertura 1, escurecimento 0,75 do eclipse padrão
      // Fora do disco e da coroa: a mais de 45 graus do eclipse (a coroa vai até 6 raios, ~39 graus).
      const ecl = cena.layers[0].dir;
      const far = (x, row) => { const d = g.dir(x, row); return Math.acos(Math.min(1, d[0] * ecl[0] + d[1] * ecl[1] + d[2] * ecl[2])) > Math.PI / 4; };
      const r = compare(img, (x, row) => g.sky(x, row) ? px(fundoSo, x, row).map(v => v * exposure) : null, far);
      check('C eclipse escurece o fundo pela exposição do mundo', r.worst <= TOL + 1 && r.n > 500, `pior ${r.worst.toFixed(1)}/255 em ${r.n} pixels`);
    }
  } finally {
    await browser.close();
  }
  const failed = results.filter(ok => !ok).length;
  console.log(`composição dos fundos: ${results.length - failed}/${results.length}`);
  if (failed) process.exit(1);
}

main().catch(err => { console.error(err.message || err); process.exit(1); });
