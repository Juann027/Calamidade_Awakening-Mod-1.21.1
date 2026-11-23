#!/usr/bin/env node
// Composição dos objetos do céu (0.14, fase 3: estrelas, constelações, nebulosas) provada por imagem: roda os shaders
// de núcleo reais (sky_stars, sky_nebula) e os passes reais "sky" e "atmosphere" pela prévia (scripts/previa_passes.cjs,
// WebGL2/SwiftShader, 8 bits entre os passes) e compara sondas de pixel com o resultado esperado, escrito como intenção:
//   A. Estrelas LUMINOUS sobre fundo opaco: longe das estrelas a tela é a do fundo sozinho; perto, nunca mais escura
//      que o fundo (o fundo atrás não some).
//   B. Cor: uma estrela NORMAL brilhante mostra a cor exata no miolo; a mesma LUMINOUS deixa o fundo aparecer.
//   C. Nebulosa NORMAL semitransparente: fora dela, a tela é a do fundo; dentro, mistura por alfa coerente nos três
//      canais, sem chegar à cor da nebulosa (o fundo continua visível). COVER: só a silhueta esconde o fundo.
//   D. Atmosphere sobre fundo, nebulosa e estrelas: em todo pixel de céu, mix(tela sem atmosfera, gradiente, blend),
//      pelo passe sky e pelo passe de atmosfera.
//   E. Eclipse: estrelas REVEAL aparecem com a visibilidade prevista (não escurecidas pelo passe); nebulosa DIM escurece
//      como o fundo; IGNORE não.
//   F. Sol do jogo apagado sob uma nuvem COVER: sem marca.
//   G. Ordem: a nuvem COVER na frente esconde a estrela; atrás, não.
//   H. Lente: as estrelas perto do buraco negro saem do lugar; longe, ficam.
// 0.14 (fase 4): imagens com o shader sky_sprite: I1 cor exata e transparência do PNG, I2 alfa suave (NORMAL e COVER),
//   I3 LUMINOUS, I4 Atmosphere, I5 FRONT na frente do portal (e só no céu), I6 quadros sem vazamento em qualquer
//   tamanho, I7 mistura de vistas, I8 espelho.
// Os casos têm controles negativos (passes sem a camada de cobertura, modo trocado) que precisam FALHAR.
//
//   node scripts/objetos_composicao.cjs     (sai com 1 se algum caso falhar)
'use strict';
const path = require('path');
const fs = require('fs');
const P = require('./previa_passes.cjs');
const { decode } = require('./referencia_ceu.cjs');

const TOL = 3;
const smooth = (a, b, x) => { const t = Math.min(Math.max((x - a) / (b - a), 0), 1); return t * t * (3 - 2 * t); };
const norm = a => { const l = Math.hypot(...a); return a.map(v => v / l); };
const dotp = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const angle = (a, b) => Math.acos(Math.min(1, Math.max(-1, dotp(a, b)))) * 180 / Math.PI;

function geometry(cena) {
  const cam = P.lookAt(...cena.look);
  const proj = P.perspective(P.FOV, P.W / P.H, 0.05, 1024), view = P.viewMatrix(cam);
  const { depth } = P.skyScene(cam, proj, view, cena.env);
  const tan = Math.tan(P.FOV * Math.PI / 360);
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
function each(step, fn) { for (let row = 1; row < P.H - 1; row += step) for (let x = 1; x < P.W - 1; x += step) fn(x, row); }

const results = [];
function check(name, ok, detail) {
  results.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} — ${detail}`);
}
const starDirs = o => (o.stars || []).map(s => P.skyDir(s.az, s.el));

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(P.OUT, { recursive: true });
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const page = await browser.newPage();
    const C = P.OBJ_CENAS;

    // A. Estrelas LUMINOUS sobre fundo opaco.
    {
      const cena = C.obj_estrelas_fundo, g = geometry(cena), dirs = starDirs(cena.objetos[0]);
      const sem = await shot(page, 'obj_estrelas_fundo__sem', C.obj_estrelas_fundo__sem);
      const img = await shot(page, 'obj_estrelas_fundo', cena);
      let far = 0, farWorst = 0, nearWorst = 0, lit = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row)) return;
        const d = g.dir(x, row), near = dirs.some(s => angle(s, d) < 0.6), a = px(img, x, row), b = px(sem, x, row);
        if (!near) { far++; farWorst = Math.max(farWorst, maxDiff(a, b)); return; }
        nearWorst = Math.max(nearWorst, ...b.map((v, k) => v - a[k]));
        if (Math.max(...a.map((v, k) => v - b[k])) > 10) lit++;
      });
      check('A estrelas LUMINOUS: longe delas a tela é a do fundo sozinho', farWorst <= 1 && far > 10000, `pior ${farWorst}/255 em ${far} pixels`);
      check('A estrelas LUMINOUS: perto delas, nunca mais escuro que o fundo (o fundo não some)', nearWorst <= 1 && lit >= dirs.length,
        `pior escurecimento ${nearWorst}/255; ${lit} pixels acesos (> 10/255) para ${dirs.length} estrelas`);
      // Controle negativo: estrelas escuras em NORMAL cobrem o fundo; a sonda de "nunca mais escuro" tem que pegar.
      const neg = await shot(page, 'obj_estrelas_fundo__controle', { ...cena, objetos: [{ ...cena.objetos[0], blend: 'NORMAL',
        stars: cena.objetos[0].stars.map(s => ({ ...s, mag: -1, argb: 0xff101010 })) }] });
      let negWorst = 0;
      each(1, (x, row) => { if (g.sky(x, row)) negWorst = Math.max(negWorst, ...px(sem, x, row).map((v, k) => v - px(neg, x, row)[k])); });
      check('A controle negativo: estrelas NORMAL escuras escondem o fundo (a sonda detecta)', negWorst > 40, `pior escurecimento ${negWorst}/255`);
    }

    // B. Cor exata (NORMAL) e luz somada (LUMINOUS): a estrela está no centro da tela.
    {
      const sem = await shot(page, 'obj_cor__sem', { ...C.obj_cor_normal, objetos: undefined });
      const n = await shot(page, 'obj_cor_normal', C.obj_cor_normal), l = await shot(page, 'obj_cor_luminosa', C.obj_cor_luminosa);
      const cx = P.W / 2, cy = P.H / 2;
      let best = null;
      for (let dy = -2; dy <= 1; dy++) for (let dx = -2; dx <= 1; dx++) {
        const v = px(n, cx + dx, cy + dy);
        if (!best || v[0] - v[1] > best[0] - best[1]) best = v;
      }
      const want = [0xff, 0x20, 0x10];
      check('B estrela NORMAL: cor exata no miolo sobre fundo claro', maxDiff(best, want) <= 4, `miolo (${best}) contra (${want})`);
      const lc = px(l, cx - 1, cy - 1), bg = px(sem, cx - 1, cy - 1);
      check('B estrela LUMINOUS: o fundo continua por trás (verde e azul do fundo não somem)', lc[1] >= bg[1] - 1 && lc[2] >= bg[2] - 1 && lc[0] > bg[0] + 20,
        `miolo (${lc}) sobre fundo (${bg})`);
    }

    // C. Nebulosa semitransparente (NORMAL) e silhueta (COVER).
    {
      const cena = C.obj_nebulosa_normal, g = geometry(cena), o = cena.objetos[0], c = P.skyDir(o.az, o.el);
      const N = [0xff, 0x40, 0xa0];
      const sem = await shot(page, 'obj_nebulosa__sem', { ...cena, objetos: undefined });
      const img = await shot(page, 'obj_nebulosa_normal', cena);
      let outWorst = 0, out = 0, inside = 0, mixWorst = 0, amax = 0;
      each(2, (x, row) => {
        if (!g.sky(x, row)) return;
        const d = g.dir(x, row), a = px(img, x, row), b = px(sem, x, row);
        if (angle(d, c) > o.extent * 1.2) { out++; outWorst = Math.max(outWorst, maxDiff(a, b)); return; }
        const k = [0, 1, 2].reduce((m, i) => Math.abs(N[i] - b[i]) > Math.abs(N[m] - b[m]) ? i : m, 0);
        if (Math.abs(N[k] - b[k]) < 40) return;
        const al = (a[k] - b[k]) / (N[k] - b[k]);
        if (al < 0.05) return;
        inside++;
        amax = Math.max(amax, al);
        mixWorst = Math.max(mixWorst, maxDiff(a, b.map((v, i) => v + al * (N[i] - v))));
      });
      check('C nebulosa NORMAL: fora dela a tela é a do fundo (não substitui a skybox)', outWorst <= 1 && out > 5000, `pior ${outWorst}/255 em ${out} pixels`);
      check('C nebulosa NORMAL: dentro, mistura por alfa coerente nos três canais', mixWorst <= 4 && inside > 300, `pior ${mixWorst.toFixed(1)}/255 em ${inside} pixels`);
      check('C nebulosa NORMAL de densidade 0,5: o fundo continua visível no miolo', amax > 0.25 && amax < 0.6, `alfa máximo ${amax.toFixed(2)}`);
      const cov = await shot(page, 'obj_nebulosa_cover', C.obj_nebulosa_cover);
      let full = 0, none = 0, between = 0;
      each(2, (x, row) => {
        if (!g.sky(x, row) || angle(g.dir(x, row), c) > o.extent * 1.2) return;
        const a = px(cov, x, row), b = px(sem, x, row);
        if (maxDiff(a, N) <= 2) full++; else if (maxDiff(a, b) <= 2) none++; else between++;
      });
      check('C nebulosa COVER: só a silhueta esconde o fundo (opaca dentro, intacto fora)', full > 200 && none > 50 && between < (full + none) * 0.15,
        `${full} pixels com a cor da nebulosa, ${none} com o fundo, ${between} na borda`);
    }

    // D. Atmosphere sobre fundo, nebulosa e estrelas.
    {
      const base = C.obj_atmosfera, a = base.atmo;
      for (const [label, cena] of [['passe sky', base], ['só o passe de atmosfera', { ...base, noSkyPass: true }]]) {
        const g = geometry(cena);
        const semAtmo = await shot(page, 'obj_atmosfera__sem_atmosfera', { ...cena, atmo: null, noSkyPass: true });
        const expect = (x, row) => {
          if (!g.sky(x, row)) return null;
          const d = g.dir(x, row), s = px(semAtmo, x, row), k = smooth(0, 0.6, d[1]);
          return s.map((v, c) => { const grad = (a.skyHorizon[c] + (a.skyTop[c] - a.skyHorizon[c]) * k) * 255; return v + (grad - v) * a.blend; });
        };
        const run = async (name, c2) => {
          const img = await shot(page, name, c2);
          let worst = 0, n = 0;
          let clipped = 0;
          each(3, (x, row) => {
            const e = expect(x, row);
            if (!e) return;
            // Pixels que estouraram em 255 sem atmosfera (luz somada de estrela) já perderam a cor: fora da conta.
            if (Math.max(...px(semAtmo, x, row)) >= 254) { clipped++; return; }
            worst = Math.max(worst, maxDiff(px(img, x, row), e)); n++;
          });
          return { worst, n, clipped };
        };
        const ok = await run('obj_atmosfera__' + (cena.noSkyPass ? 'atmo' : 'sky'), cena);
        check(`D atmosfera sobre fundo, nebulosa e estrelas (${label}): tinge o céu como ele aparece`, ok.worst <= TOL + 1 && ok.n > 5000,
          `pior ${ok.worst.toFixed(1)}/255 em ${ok.n} pixels de céu (${ok.clipped} estourados fora da conta)`);
        const neg = await run('obj_atmosfera__controle', { ...cena, coverageOff: true });
        check(`D controle negativo (${label}): passes sem a camada de cobertura erram`, neg.worst > 20, `pior ${neg.worst.toFixed(1)}/255`);
      }
    }

    // E. Eclipse: REVEAL, DIM e IGNORE.
    {
      const cena = C.obj_eclipse, g = geometry(cena), ecl = cena.layers[0].dir;
      const exposure = 1 - 0.85 * 0.75, reveal = (1 - exposure) / 0.85;   // progresso 1; visibilidade de dia 0 → reveal
      const stars = cena.objetos[1], nebula = cena.objetos[0], dirs = starDirs(stars), cN = P.skyDir(nebula.az, nebula.el);
      const far = (x, row) => angle(g.dir(x, row), ecl) > 45;
      const noite = { ...cena, layers: [], night: 1, objetos: [stars] }, noiteSem = { ...noite, objetos: undefined };
      const eclStars = { ...cena, objetos: [stars] }, eclSem = { ...cena, objetos: undefined };
      const [a1, a0, b1, b0] = [await shot(page, 'obj_ecl__noite', noite), await shot(page, 'obj_ecl__noite_sem', noiteSem),
        await shot(page, 'obj_ecl__estrelas', eclStars), await shot(page, 'obj_ecl__sem', eclSem)];
      const probe = img1 => {
        let worst = 0, n = 0;
        each(1, (x, row) => {
          if (!g.sky(x, row) || !far(x, row) || !dirs.some(s => angle(s, g.dir(x, row)) < 0.6)) return;
          const night = px(a1, x, row).map((v, k) => v - px(a0, x, row)[k]);
          if (Math.max(...night) < 20 || Math.max(...px(a1, x, row)) >= 254 || Math.max(...px(img1, x, row)) >= 254) return;
          const got = px(img1, x, row).map((v, k) => v - px(b0, x, row)[k]);
          worst = Math.max(worst, maxDiff(got, night.map(v => v * reveal)));
          n++;
        });
        return { worst, n };
      };
      const r = probe(b1);
      check('E eclipse: estrelas REVEAL aparecem de dia com a visibilidade prevista (o passe não as escurece)', r.worst <= 5 && r.n >= 8,
        `pior ${r.worst.toFixed(1)}/255 em ${r.n} pixels de estrela (visibilidade ${reveal.toFixed(2)})`);
      const neg = probe(await shot(page, 'obj_ecl__controle', { ...eclStars, coverageOff: true }));
      check('E controle negativo: sem a camada de cobertura o passe escurece as estrelas de novo', neg.worst > 12, `pior ${neg.worst.toFixed(1)}/255`);
      const dia = await shot(page, 'obj_ecl__dia', { ...cena, layers: [], objetos: [stars] }), diaSem = await shot(page, 'obj_ecl__dia_sem', { ...cena, layers: [], objetos: undefined });
      let shown = 0;
      each(1, (x, row) => { if (g.sky(x, row)) shown = Math.max(shown, maxDiff(px(dia, x, row), px(diaSem, x, row))); });
      check('E de dia sem eclipse, estrelas com daylight 1 não aparecem', shown <= 1, `maior diferença ${shown}/255`);
      // DIM: com o fundo e a nebulosa escurecendo juntos, a tela inteira da nebulosa é a de sem eclipse × exposição.
      const dimCase = async (mode) => {
        const o = { ...nebula, darkening: mode };
        const withE = await shot(page, 'obj_ecl__neb_' + mode, { ...cena, objetos: [o] });
        const noE = await shot(page, 'obj_ecl__neb_sem_eclipse_' + mode, { ...cena, layers: [], objetos: [o] });
        let worst = 0, n = 0;
        each(2, (x, row) => {
          if (!g.sky(x, row) || !far(x, row) || angle(g.dir(x, row), cN) > nebula.extent * 0.6) return;
          worst = Math.max(worst, maxDiff(px(withE, x, row), px(noE, x, row).map(v => v * exposure)));
          n++;
        });
        return { worst, n };
      };
      const dim = await dimCase('DIM');
      check('E nebulosa DIM escurece com o mundo (exposição do eclipse)', dim.worst <= TOL + 1 && dim.n > 100, `pior ${dim.worst.toFixed(1)}/255 em ${dim.n} pixels`);
      const ign = await dimCase('IGNORE');
      check('E controle: nebulosa IGNORE não escurece (configurável por objeto)', ign.worst > 15, `pior ${ign.worst.toFixed(1)}/255 contra o escurecimento`);
    }

    // F. Sol do jogo sob uma nuvem COVER.
    {
      const cena = C.obj_sol, g = geometry(cena);
      const ref = await shot(page, 'obj_sol__sem_sol', { ...cena, noVanillaSun: true, hideSun: 0 });
      // Só os pixels do quadrado do sol cobertos pela nuvem (onde a referência mostra a cor dela); fora da silhueta vale
      // o apagamento da 0.12.0, que a fase 2 já mede (erro de 8 bits próprio dele).
      const cloud = [0x60, 0x80, 0xc0];
      const inSun = (x, row) => Math.abs(x - P.W / 2) < 60 && Math.abs(row - P.H / 2) < 60 && maxDiff(px(ref, x, row), cloud) <= 2;
      const run = async (name, c2) => {
        const img = await shot(page, name, c2);
        let worst = 0, n = 0;
        each(2, (x, row) => { if (!g.sky(x, row) || !inSun(x, row)) return; worst = Math.max(worst, maxDiff(px(img, x, row), px(ref, x, row))); n++; });
        return { worst, n };
      };
      const ok = await run('obj_sol', cena);
      check('F sol do jogo apagado sob nuvem COVER: sem marca na nuvem', ok.worst <= TOL && ok.n > 100, `pior ${ok.worst.toFixed(1)}/255 em ${ok.n} pixels`);
      const neg = await run('obj_sol__controle', { ...cena, coverageOff: true });
      check('F controle negativo: sem a camada, o passe subtrai o sol da nuvem', neg.worst > 20, `pior ${neg.worst.toFixed(1)}/255`);
    }

    // G. Ordem entre objetos.
    {
      const front = await shot(page, 'obj_ordem_frente', C.obj_ordem_frente), back = await shot(page, 'obj_ordem_atras', C.obj_ordem_atras);
      const cx = P.W / 2, cy = P.H / 2, cover = [0x20, 0x30, 0x40];
      let fMax = 0, bMax = 0;
      for (let dy = -2; dy <= 1; dy++) for (let dx = -2; dx <= 1; dx++) {
        fMax = Math.max(fMax, maxDiff(px(front, cx + dx, cy + dy), cover));
        bMax = Math.max(bMax, ...px(back, cx + dx, cy + dy).map((v, k) => v - cover[k]));
      }
      check('G nuvem COVER com ordem 300 esconde a estrela (ordem 200)', fMax <= 2, `miolo difere ${fMax}/255 da nuvem`);
      check('G nuvem COVER com ordem 100 fica atrás da estrela', bMax > 60, `estrela ${bMax}/255 acima da nuvem`);
    }

    // H. Lente sobre as estrelas.
    {
      const cena = C.obj_lente, g = geometry(cena), bh = cena.layers[0].dir;
      const lens = await shot(page, 'obj_lente', cena), plain = await shot(page, 'obj_lente__sem_lente', { ...cena, layers: [] });
      const ref = await shot(page, 'obj_lente__sem_estrelas', { ...cena, layers: [], objetos: undefined });
      const bright = (img, x, row) => Math.max(...px(img, x, row).map((v, k) => v - px(ref, x, row)[k])) > 25;
      let nearMoved = 0, nearTotal = 0, farSame = 0, farTotal = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row)) return;
        const a = angle(g.dir(x, row), bh), sp = bright(plain, x, row);
        if (!sp) return;
        // Tamanho 12: raio 6 graus, alcance da lente 6 × (2,2 + 2 × 0,6) ≈ 20 graus (SkyFrame.buildLenses).
        if (a > 7.5 && a < 16) { nearTotal++; if (maxDiff(px(lens, x, row), px(plain, x, row)) > 25) nearMoved++; }
        if (a > 26) { farTotal++; if (maxDiff(px(lens, x, row), px(plain, x, row)) <= 2) farSame++; }
      });
      check('H lente: estrelas perto do buraco negro saem do lugar', nearTotal > 5 && nearMoved / nearTotal > 0.5, `${nearMoved}/${nearTotal} pixels de estrela mudaram`);
      check('H lente: estrelas longe dele ficam iguais', farTotal > 5 && farSame / farTotal > 0.95, `${farSame}/${farTotal} iguais`);
    }

    // ----------------------------------------------------------------------------------------- 0.14 (fase 4): imagens
    // I1. Imagem NORMAL: cor exata no miolo; fora do disco (PNG transparente) a tela é a do fundo sozinho.
    {
      const cena = C.img_normal, g = geometry(cena), o = cena.objetos[0], c = P.skyDir(o.az, o.el);
      const sem = await shot(page, 'img_normal__sem', { ...cena, objetos: undefined });
      const img = await shot(page, 'img_normal', cena);
      const cx = P.W / 2, cy = P.H / 2, centro = px(img, cx, cy), want = [255, 32, 16];
      check('I1 imagem NORMAL: cor exata no miolo', maxDiff(centro, want) <= 3, `miolo (${centro}) contra (${want})`);
      // O disco tem raio 28/32 da meia largura (8°): fora de ~7,3° só sobra a parte transparente do PNG.
      const outside = (sh) => {
        let worst = 0, n = 0;
        each(1, (x, row) => {
          if (!g.sky(x, row)) return;
          const a = angle(g.dir(x, row), c);
          if (a < 7.6 || a > 11.5) return;
          worst = Math.max(worst, maxDiff(px(sh, x, row), px(sem, x, row))); n++;
        });
        return { worst, n };
      };
      const r = outside(img);
      check('I1 imagem NORMAL: a parte transparente do PNG não toca o céu', r.worst <= 1 && r.n > 2000, `pior ${r.worst}/255 em ${r.n} pixels`);
      const neg = outside(await shot(page, 'img_normal__controle', { ...cena, objetos: [{ ...o, texture: 'metades', rects: undefined }] }));
      check('I1 controle negativo: um PNG opaco no mesmo lugar cobre os cantos (a sonda detecta)', neg.worst > 40, `pior ${neg.worst}/255`);
    }

    // I2. Alfa suave: NORMAL mistura coerente nos três canais; COVER só a silhueta (alfa ≥ 0,5).
    {
      const cena = C.img_suave, g = geometry(cena), o = cena.objetos[0], c = P.skyDir(o.az, o.el), N = [40, 200, 255];
      const sem = await shot(page, 'img_suave__sem', { ...cena, objetos: undefined });
      const img = await shot(page, 'img_suave', cena);
      let mixWorst = 0, partial = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row) || angle(g.dir(x, row), c) > 9) return;
        const a = px(img, x, row), b = px(sem, x, row);
        const k = [0, 1, 2].reduce((m, i) => Math.abs(N[i] - b[i]) > Math.abs(N[m] - b[m]) ? i : m, 0);
        if (Math.abs(N[k] - b[k]) < 40) return;
        const al = (a[k] - b[k]) / (N[k] - b[k]);
        if (al < 0.05) return;
        if (al > 0.2 && al < 0.8) partial++;
        mixWorst = Math.max(mixWorst, maxDiff(a, b.map((v, i) => v + al * (N[i] - v))));
      });
      check('I2 imagem NORMAL de alfa suave: mistura por alfa coerente nos três canais', mixWorst <= 4 && partial > 200,
        `pior ${mixWorst.toFixed(1)}/255; ${partial} pixels semitransparentes`);
      const cov = await shot(page, 'img_cover', C.img_cover);
      let full = 0, none = 0, between = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row) || angle(g.dir(x, row), c) > 9) return;
        const a = px(cov, x, row), b = px(sem, x, row);
        if (maxDiff(a, N) <= 2) full++; else if (maxDiff(a, b) <= 2) none++; else between++;
      });
      check('I2 imagem COVER: só a silhueta esconde o fundo', full > 300 && none > 300 && between < (full + none) * 0.1,
        `${full} pixels com a cor da imagem, ${none} com o fundo, ${between} na borda`);
    }

    // I3. LUMINOUS: o fundo nunca fica mais escuro; a luz soma.
    {
      const cena = C.img_luminosa, g = geometry(cena), c = P.skyDir(0, 30);
      const sem = await shot(page, 'img_luminosa__sem', { ...cena, objetos: undefined });
      const img = await shot(page, 'img_luminosa', cena);
      let darker = 0, lit = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row) || angle(g.dir(x, row), c) > 9) return;
        const a = px(img, x, row), b = px(sem, x, row);
        darker = Math.max(darker, ...b.map((v, k) => v - a[k]));
        if (a[0] - b[0] > 20 && b[0] < 230) lit++;
      });
      check('I3 imagem LUMINOUS: nunca mais escura que o fundo e a luz soma', darker <= 1 && lit > 300, `pior escurecimento ${darker}/255; ${lit} pixels acesos`);
    }

    // I4. Atmosphere sobre imagens (pelos dois caminhos), com o controle sem a camada de cobertura.
    {
      const base = C.img_atmosfera, a = base.atmo;
      for (const [label, cena] of [['passe sky', base], ['só o passe de atmosfera', { ...base, noSkyPass: true }]]) {
        const g = geometry(cena);
        const semAtmo = await shot(page, 'img_atmosfera__sem_atmosfera', { ...cena, atmo: null, noSkyPass: true });
        const run = async (name, c2) => {
          const img = await shot(page, name, c2);
          let worst = 0, n = 0;
          each(3, (x, row) => {
            if (!g.sky(x, row)) return;
            const d = g.dir(x, row), s0 = px(semAtmo, x, row), k = smooth(0, 0.6, d[1]);
            const e = s0.map((v, ch) => { const grad = (a.skyHorizon[ch] + (a.skyTop[ch] - a.skyHorizon[ch]) * k) * 255; return v + (grad - v) * a.blend; });
            worst = Math.max(worst, maxDiff(px(img, x, row), e)); n++;
          });
          return { worst, n };
        };
        const ok = await run('img_atmosfera__' + (cena.noSkyPass ? 'atmo' : 'sky'), cena);
        check(`I4 atmosfera sobre imagens (${label}): tinge o céu como ele aparece`, ok.worst <= TOL + 1 && ok.n > 5000, `pior ${ok.worst.toFixed(1)}/255 em ${ok.n} pixels`);
        const neg = await run('img_atmosfera__controle', { ...cena, coverageOff: true });
        check(`I4 controle negativo (${label}): passes sem a camada de cobertura erram`, neg.worst > 20, `pior ${neg.worst.toFixed(1)}/255`);
      }
    }

    // I5. FRONT: a imagem COVER fica na frente do portal (cor exata) só nos pixels de céu; BEHIND fica atrás dele.
    {
      const cena = C.img_frente, g = geometry(cena), o = cena.objetos[0];
      const sem = await shot(page, 'img_frente__sem', { ...cena, objetos: undefined });
      const front = await shot(page, 'img_frente', cena);
      const behind = await shot(page, 'img_frente__atras', { ...cena, objetos: [{ ...o, front: false }] });
      const off = await shot(page, 'img_frente__controle', { ...cena, frontOff: true });
      const target = P.skyDir(o.az, o.el), want = [255, 32, 16];
      let fWorst = 0, bWorst = 0, oWorst = 0, n = 0;
      each(1, (x, row) => {
        if (!g.sky(x, row) || angle(g.dir(x, row), target) > 3) return;
        n++;
        fWorst = Math.max(fWorst, maxDiff(px(front, x, row), want));
        bWorst = Math.max(bWorst, maxDiff(px(behind, x, row), want));
        oWorst = Math.max(oWorst, maxDiff(px(off, x, row), want));
      });
      check('I5 FRONT: a imagem fica na frente do portal com a cor exata', fWorst <= 3 && n > 100, `pior ${fWorst}/255 em ${n} pixels`);
      check('I5 BEHIND: a mesma imagem fica atrás do portal (as camadas pintam por cima)', bWorst > 40, `pior ${bWorst}/255 contra a cor da imagem`);
      check('I5 controle negativo: sem compor a camada de frente, a imagem some', oWorst > 40, `pior ${oWorst}/255`);
      let ground = 0, gn = 0;
      each(2, (x, row) => { if (g.sky(x, row)) return; gn++; ground = Math.max(ground, maxDiff(px(front, x, row), px(sem, x, row))); });
      check('I5 FRONT não pinta sobre o terreno (é composição do céu, não profundidade)', ground <= 1 && gn > 1000, `pior ${ground}/255 em ${gn} pixels de terreno`);
    }

    // I6. Folha de quadros sem margem desenhada pequena: nenhum vermelho do quadro 1 no quadro 0 (mipmaps por quadro).
    {
      const cena = C.img_quadros, g = geometry(cena);
      const sem = await shot(page, 'img_quadros__sem', { ...cena, objetos: undefined });
      const probe = async (name, c2) => {
        const img = await shot(page, name, c2);
        let red = 0, green = 0;
        each(1, (x, row) => {
          if (!g.sky(x, row)) return;
          const a = px(img, x, row), b = px(sem, x, row);
          if (maxDiff(a, b) <= 2) return;
          green++;
          red = Math.max(red, a[0] - Math.max(b[0], 20) * 1.0);
        });
        return { red, green };
      };
      const ok = await probe('img_quadros', cena);
      check('I6 folha sem margem, cinco tamanhos: o quadro vizinho nunca vaza', ok.red <= 3 && ok.green > 30, `vermelho a mais ${ok.red.toFixed(0)}/255 em ${ok.green} pixels da imagem`);
      const neg = await probe('img_quadros__controle', { ...cena, objetos: cena.objetos.map(o => ({ ...o, naiveMips: true })) });
      check('I6 controle negativo: mipmaps do driver e recuo só no nível 0 vazam', neg.red > 15, `vermelho a mais ${neg.red.toFixed(0)}/255`);
    }

    // I7. Duas vistas (vermelha e azul) com pesos 0,5 e 0,5: a mistura em alfa pré-multiplicado dá a média exata.
    {
      const cena = C.img_vistas;
      const mid = px(await shot(page, 'img_vistas', cena), P.W / 2, P.H / 2), want = [125, 30, 125];
      check('I7 vistas pesadas 0,5/0,5: média exata das duas', maxDiff(mid, want) <= 2, `miolo (${mid}) contra (${want})`);
      const one = px(await shot(page, 'img_vistas__uma', { ...cena, objetos: [{ ...cena.objetos[0], weights: [1, 0] }] }), P.W / 2, P.H / 2);
      check('I7 peso 1 na vista vermelha: só ela', maxDiff(one, [220, 30, 30]) <= 2, `miolo (${one})`);
    }

    // I8. Espelho: retângulo com u0 > u1 inverte a imagem (vista espelhada do impostor).
    {
      const img = await shot(page, 'img_espelho', C.img_espelho);
      const left = px(img, P.W / 2 - 12, P.H / 2), right = px(img, P.W / 2 + 12, P.H / 2);
      check('I8 espelho: o lado azul aparece à esquerda', left[2] > 180 && left[0] < 60 && right[0] > 180 && right[2] < 60, `esquerda (${left}), direita (${right})`);
    }
  } finally {
    await browser.close();
  }
  const failed = results.filter(ok => !ok).length;
  console.log(`composição dos objetos do céu: ${results.length - failed}/${results.length}`);
  if (failed) process.exit(1);
}

main().catch(err => { console.error(err.message || err); process.exit(1); });
