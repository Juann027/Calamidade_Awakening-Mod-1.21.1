#!/usr/bin/env node
// Ferramenta de impostors do Calamidade (0.14, fase 4). Trabalha só com imagens JÁ pré-renderizadas: não abre modelos
// nem renderiza nada (modelos Blockbench reais ficam para a 0.15). Node puro, sem dependências; saída byte a byte igual
// a cada execução com a mesma versão do Node.
//
// MONTAR: organiza uma pasta de vistas nas páginas da grade, gera o descritor e (opcional) níveis de LOD menores.
//
//   node scripts/gerar_impostor.cjs montar <pasta-das-vistas> --id meumod:nave --saida <pasta-resources>
//        [--mirror] [--fps 8] [--play loop|once|ping_pong] [--pivot 0.5,0.5] [--gutter 0]
//        [--lod 128:96,64:40] [--colunas N] [--textura textures/sky/impostors]
//
//   Nomes das vistas: y<yaw>_p<pitch>.png ou, animado, y<yaw>_p<pitch>_f<quadro>.png (graus inteiros, pitch pode ser
//   negativo: p-30). As yaws precisam ser k × 360 / n (0, 45, 90, …); com --mirror, só de 0 a 180 (o resto é a mesma
//   imagem espelhada). Todas as vistas com o mesmo tamanho, PNG de 8 bits RGBA ou RGB sem entrelaçamento, fundo
//   transparente.
//   Grava <saida>/assets/<ns>/<textura>/<nome>_p<k>.png (páginas), os níveis de LOD <nome>_<largura>_p<k>.png e
//   <saida>/assets/<ns>/calamidade/impostors/<nome>.json. --lod largura:abaixo — largura do quadro do nível menor (o
//   quadro original dividido por 2, 4, 8…) e a largura na tela, em pixels, abaixo da qual ele é usado (no máximo a
//   própria largura, para a troca não aparecer).
//
// VALIDAR: confere um descritor existente contra as páginas (campos, contagens, tamanhos, proporção e limiares de LOD).
//
//   node scripts/gerar_impostor.cjs validar <pasta-resources> meumod:nave
//
// EXEMPLO: grava uma pasta de vistas sintéticas (uma seta colorida por yaw, mais clara por pitch) para experimentar.
//
//   node scripts/gerar_impostor.cjs exemplo <pasta> [--yaw 8] [--pitch 0,30] [--frames 1] [--tamanho 64]
//
// Como preparar as vistas no Blockbench (ou em qualquer programa 3D): câmera ortográfica (ou de campo de visão
// estreito) apontada para o centro do objeto, fundo transparente, o modelo de frente para +Z (sul) na yaw 0, e então
// girar o MODELO em passos fixos de yaw (e inclinar a câmera para cada pitch), exportando um PNG por vista com o
// mesmo enquadramento e o "para cima" da câmera sempre o +Y do objeto. A yaw cresce no sentido do azimute do motor:
// yaw 90 é a câmera do lado −X do objeto (o lado oeste quando ele olha para o sul). Ver docs/CEU-PERSONALIZADO.md.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

class Falha extends Error {}
const falha = msg => { throw new Falha(msg); };

// ------------------------------------------------------------------------------------------------ PNG

const CRC = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

/** PNG de 8 bits RGBA, RGB, cinza ou cinza+alfa, sem entrelaçamento, filtros 0 a 4 → { w, h, rgba }. */
function lerPng(file) {
  let buf;
  try { buf = fs.readFileSync(file); } catch (e) { falha(`não consegui ler ${file}: ${e.message}`); }
  if (buf.length < 8 || buf.readUInt32BE(0) !== 0x89504e47) falha(`${file} não é um PNG`);
  let pos = 8, w = 0, h = 0, type = -1;
  const idat = [];
  while (pos + 8 <= buf.length) {
    const len = buf.readUInt32BE(pos), kind = buf.toString('ascii', pos + 4, pos + 8), data = buf.subarray(pos + 8, pos + 8 + len);
    if (kind === 'IHDR') {
      w = data.readUInt32BE(0); h = data.readUInt32BE(4); type = data[9];
      if (data[8] !== 8) falha(`${file}: só PNG de 8 bits por canal (este tem ${data[8]})`);
      if (data[12] !== 0) falha(`${file}: PNG entrelaçado não é suportado; exporte sem entrelaçamento`);
      if (![0, 2, 4, 6].includes(type)) falha(`${file}: tipo de cor ${type} não suportado (use RGBA)`);
    } else if (kind === 'IDAT') idat.push(data);
    pos += 12 + len;
  }
  if (!w || !h) falha(`${file}: PNG sem cabeçalho`);
  const ch = { 0: 1, 2: 3, 4: 2, 6: 4 }[type], stride = w * ch;
  let raw;
  try { raw = zlib.inflateSync(Buffer.concat(idat)); } catch (e) { falha(`${file}: dados corrompidos (${e.message})`); }
  if (raw.length < h * (stride + 1)) falha(`${file}: dados incompletos`);
  const rgba = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(stride), cur = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? cur[x - ch] : 0, b = prev[x], c = x >= ch ? prev[x - ch] : 0;
      let v = line[x];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      cur[x] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * 4, i = x * ch;
      if (ch >= 3) { rgba[o] = cur[i]; rgba[o + 1] = cur[i + 1]; rgba[o + 2] = cur[i + 2]; rgba[o + 3] = ch === 4 ? cur[i + 3] : 255; }
      else { rgba[o] = rgba[o + 1] = rgba[o + 2] = cur[i]; rgba[o + 3] = ch === 2 ? cur[i + 1] : 255; }
    }
    [prev, cur] = [cur, prev];
  }
  return { w, h, rgba };
}

/** PNG RGBA de 8 bits, filtro 0, zlib nível 9: os mesmos bytes a cada execução. */
function gravarPng(file, w, h, rgba) {
  const chunk = (kind, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(kind, 'ascii'), data]), c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
    return Buffer.concat([len, td, c]);
  };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
}

/** Redução por um fator inteiro (caixa, ponderada pelo alfa para não escurecer bordas). */
function reduzir(img, fator) {
  const w = img.w / fator, h = img.h / fator, out = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let r = 0, g = 0, b = 0, a = 0;
    for (let dy = 0; dy < fator; dy++) for (let dx = 0; dx < fator; dx++) {
      const i = ((y * fator + dy) * img.w + x * fator + dx) * 4, pa = img.rgba[i + 3];
      r += img.rgba[i] * pa; g += img.rgba[i + 1] * pa; b += img.rgba[i + 2] * pa; a += pa;
    }
    const o = (y * w + x) * 4;
    if (a > 0) { out[o] = Math.round(r / a); out[o + 1] = Math.round(g / a); out[o + 2] = Math.round(b / a); }
    out[o + 3] = Math.round(a / (fator * fator));
  }
  return { w, h, rgba: out };
}

// ------------------------------------------------------------------------------------------------ argumentos

function opcoes(args) {
  const pos = [], o = {};
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (!a.startsWith('--')) { pos.push(a); continue; }
    const key = a.slice(2);
    if (['mirror'].includes(key)) o[key] = true;
    else { if (i + 1 >= args.length) falha(`faltou o valor de ${a}`); o[key] = args[++i]; }
  }
  return { pos, o };
}
const ID = /^([a-z0-9_.-]+):([a-z0-9_./-]+)$/;
function id(texto) {
  const m = ID.exec(texto || '');
  if (!m) falha(`--id precisa ser "namespace:nome" em minúsculas (ex.: meumod:nave), recebi "${texto}"`);
  return { ns: m[1], nome: m[2] };
}

// ------------------------------------------------------------------------------------------------ montar

function montar(args) {
  const { pos, o } = opcoes(args);
  if (pos.length !== 1) falha('uso: montar <pasta-das-vistas> --id ns:nome --saida <pasta-resources> [opções]');
  if (!o.saida) falha('faltou --saida (a pasta resources do addon, onde fica assets/)');
  const { ns, nome } = id(o.id);
  const pasta = pos[0];
  if (!fs.existsSync(pasta) || !fs.statSync(pasta).isDirectory()) falha(`a pasta de vistas ${pasta} não existe`);
  const nomeVista = /^y(-?\d+)_p(-?\d+)(?:_f(\d+))?\.png$/;
  const vistas = new Map();
  const ignoradas = [];
  for (const f of fs.readdirSync(pasta).sort()) {
    if (!f.toLowerCase().endsWith('.png')) continue;
    const m = nomeVista.exec(f);
    if (!m) { ignoradas.push(f); continue; }
    const yaw = Number(m[1]), pitch = Number(m[2]), quadro = m[3] === undefined ? 0 : Number(m[3]);
    if (yaw < 0 || yaw >= 360) falha(`${f}: yaw ${yaw} fora de 0..359`);
    if (pitch < -90 || pitch > 90) falha(`${f}: pitch ${pitch} fora de −90..90`);
    const chave = `${yaw}|${pitch}|${quadro}`;
    if (vistas.has(chave)) falha(`${f}: vista repetida (yaw ${yaw}, pitch ${pitch}, quadro ${quadro})`);
    vistas.set(chave, { yaw, pitch, quadro, file: path.join(pasta, f) });
  }
  if (ignoradas.length) falha(`nomes fora do padrão y<yaw>_p<pitch>[_f<quadro>].png: ${ignoradas.join(', ')}`);
  if (!vistas.size) falha(`nenhuma vista y<yaw>_p<pitch>.png em ${pasta}`);
  const lista = [...vistas.values()];
  const yawsVistas = [...new Set(lista.map(v => v.yaw))].sort((a, b) => a - b);
  const pitches = [...new Set(lista.map(v => v.pitch))].sort((a, b) => a - b);
  const quadros = Math.max(...lista.map(v => v.quadro)) + 1;
  const mirror = !!o.mirror;
  // Número de vistas numa volta: o passo entre yaws consecutivas.
  const passo = yawsVistas.length > 1 ? yawsVistas[1] - yawsVistas[0] : 360;
  const nYaw = mirror ? Math.round(360 / passo) : yawsVistas.length;
  if (360 % passo !== 0 && yawsVistas.length > 1) falha(`o passo de yaw ${passo}° não divide 360°`);
  const esperadas = [];
  if (mirror) {
    if (nYaw % 2) falha(`com --mirror o número de vistas numa volta precisa ser par (passo ${passo}°)`);
    for (let k = 0; k <= nYaw / 2; k++) esperadas.push(k * passo);
  } else for (let k = 0; k < nYaw; k++) esperadas.push(Math.round(k * 360 / nYaw * 1000) / 1000);
  const faltam = [], sobram = [];
  for (const y of yawsVistas) if (!esperadas.includes(y)) sobram.push(y);
  if (sobram.length)
    falha(`yaws ${sobram.join(', ')} não seguem o passo de ${passo}°${mirror ? ' de 0 a 180 (com --mirror)' : ''}; esperadas: ${esperadas.join(', ')}`);
  for (let q = 0; q < quadros; q++) for (const p of pitches) for (const y of esperadas)
    if (!vistas.has(`${y}|${p}|${q}`)) faltam.push(`y${y}_p${p}${quadros > 1 ? '_f' + q : ''}.png`);
  if (faltam.length)
    falha(`faltam ${faltam.length} vista(s) para fechar ${esperadas.length} yaw × ${pitches.length} pitch × ${quadros} quadro(s): ${faltam.slice(0, 12).join(', ')}${faltam.length > 12 ? ' …' : ''}`);
  // Tamanhos.
  let fw = 0, fh = 0;
  const imagens = new Map();
  for (const v of lista) {
    const img = lerPng(v.file);
    if (!fw) { fw = img.w; fh = img.h; }
    else if (img.w !== fw || img.h !== fh) falha(`${path.basename(v.file)} tem ${img.w} × ${img.h}, as outras vistas ${fw} × ${fh}`);
    imagens.set(`${v.yaw}|${v.pitch}|${v.quadro}`, img);
  }
  const gutter = o.gutter === undefined ? 0 : Number(o.gutter);
  if (!Number.isInteger(gutter) || gutter < 0 || gutter > 64) falha('--gutter precisa ser um inteiro de 0 a 64');
  const fps = o.fps === undefined ? 8 : Number(o.fps);
  if (!(fps > 0 && fps <= 120)) falha('--fps precisa estar em (0, 120]');
  const play = o.play || 'loop';
  if (!['loop', 'once', 'ping_pong'].includes(play)) falha('--play precisa ser loop, once ou ping_pong');
  const pivot = (o.pivot || '0.5,0.5').split(',').map(Number);
  if (pivot.length !== 2 || pivot.some(v => !(v >= 0 && v <= 1))) falha('--pivot precisa ser x,y entre 0 e 1');
  const textura = (o.textura || 'textures/sky/impostors').replace(/^\/+|\/+$/g, '');
  // Grade: uma linha por pitch, colunas = vistas de yaw guardadas; quadros inteiros empilhados por página (até 4096 px).
  const vistasPorLinha = esperadas.length, cellW = fw + 2 * gutter, cellH = fh + 2 * gutter;
  const colunas = o.colunas ? Number(o.colunas) : vistasPorLinha;
  if (!Number.isInteger(colunas) || colunas < 1 || colunas > 64) falha('--colunas precisa ser um inteiro de 1 a 64');
  const porQuadro = vistasPorLinha * pitches.length;
  if (colunas * cellW > 4096) falha(`a página teria ${colunas * cellW} px de largura (máximo 4096): reduza as vistas ou use --colunas`);
  const linhasPorQuadro = Math.ceil(porQuadro / colunas);
  const maxLinhas = Math.min(64, Math.floor(4096 / cellH));
  if (linhasPorQuadro > maxLinhas) falha(`um quadro precisa de ${linhasPorQuadro} linhas de ${cellH} px; uma página comporta ${maxLinhas}`);
  // Linhas da página: as que cabem (até 4096 px e 64 linhas), sem passar do necessário. Quadros por página pela regra do
  // motor (SkySpriteMath.Descriptor.framesPerPage: células ÷ vistas por quadro, quadros inteiros em cada página).
  const linhas = Math.max(linhasPorQuadro, Math.min(maxLinhas, Math.ceil(quadros * porQuadro / colunas)));
  const quadrosPorPagina = Math.floor(colunas * linhas / porQuadro);
  const paginas = Math.ceil(quadros / quadrosPorPagina);
  if (paginas > 16) falha(`seriam ${paginas} páginas (máximo 16): reduza as vistas, os quadros ou o tamanho`);
  // Níveis de LOD.
  const lods = [];
  if (o.lod) for (const item of o.lod.split(',')) {
    const [lw, below] = item.split(':').map(Number);
    if (!Number.isInteger(lw) || !Number.isInteger(below)) falha(`--lod "${item}" precisa ser largura:abaixo (inteiros)`);
    const fator = fw / lw;
    if (!Number.isInteger(fator) || fator < 2 || (fator & (fator - 1)) || fh % fator)
      falha(`--lod ${lw}: a largura do nível precisa ser ${fw} dividido por 2, 4, 8… (e a altura ${fh} também divisível)`);
    if (below < 1 || below > lw) falha(`--lod ${lw}:${below}: "abaixo" precisa ser de 1 a ${lw} (no máximo a largura do nível)`);
    if (lods.length && (lw >= lods[lods.length - 1].lw || below >= lods[lods.length - 1].below))
      falha('--lod: liste do maior para o menor, com larguras e limiares decrescentes');
    if (gutter % fator) falha(`--lod ${lw}: a margem ${gutter} precisa ser divisível por ${fator}`);
    lods.push({ lw, below, fator });
  }
  const indice = (q, p, y) => ((q % quadrosPorPagina) * pitches.length + p) * vistasPorLinha + y;
  const montarPaginas = (fator, sufixo) => {
    const w = colunas * cellW / fator, h = linhas * cellH / fator, nomes = [];
    for (let pg = 0; pg < paginas; pg++) {
      const rgba = Buffer.alloc(w * h * 4);
      for (let q = pg * quadrosPorPagina; q < Math.min(quadros, (pg + 1) * quadrosPorPagina); q++)
        pitches.forEach((p, pi) => esperadas.forEach((y, yi) => {
          let img = imagens.get(`${y}|${p}|${q}`);
          if (fator > 1) img = reduzir(img, fator);
          const cell = indice(q, pi, yi), cx = (cell % colunas) * cellW / fator + gutter / fator, cy = Math.floor(cell / colunas) * cellH / fator + gutter / fator;
          for (let yy = 0; yy < img.h; yy++) img.rgba.copy(rgba, ((cy + yy) * w + cx) * 4, yy * img.w * 4, (yy + 1) * img.w * 4);
        }));
      const rel = `${textura}/${nome}${sufixo}_p${pg}.png`;
      gravarPng(path.join(o.saida, 'assets', ns, rel), w, h, rgba);
      nomes.push(`${ns}:${rel}`);
    }
    return nomes;
  };
  const descritor = {
    format: 1,
    textures: montarPaginas(1, ''),
    frame: [fw, fh], grid: [colunas, linhas], gutter,
    views: { yaw: nYaw, pitch: pitches, mirror },
    frames: quadros, fps, play, pivot,
  };
  if (lods.length) descritor.lod = lods.map(l => ({ textures: montarPaginas(l.fator, '_' + l.lw), frame: [l.lw, fh / l.fator],
    gutter: gutter / l.fator, below: l.below }));
  const arquivo = path.join(o.saida, 'assets', ns, 'calamidade/impostors', nome + '.json');
  fs.mkdirSync(path.dirname(arquivo), { recursive: true });
  fs.writeFileSync(arquivo, JSON.stringify(descritor, null, 2) + '\n');
  validarDescritor(o.saida, `${ns}:${nome}`, true);
  console.log(`impostor ${ns}:${nome}: ${esperadas.length} yaw${mirror ? ' (espelhadas: ' + nYaw + ' numa volta)' : ''} × ${pitches.length} pitch × ${quadros} quadro(s), `
    + `${paginas} página(s) de ${colunas} × ${linhas} células de ${cellW} × ${cellH}${lods.length ? ', ' + lods.length + ' nível(is) de LOD' : ''}`);
  console.log(`  ${path.relative(process.cwd(), arquivo)}`);
  console.log(`  uso: SkyImpostor.of("${ns}:${nome}").direction(az, el).size(graus).build()`);
}

// ------------------------------------------------------------------------------------------------ validar

/** As mesmas regras do SkySpriteMath.parse e checkPage do motor, com mensagens para quem monta o addon. */
function validarDescritor(saida, texto, silencioso) {
  const { ns, nome } = id(texto);
  const arquivo = path.join(saida, 'assets', ns, 'calamidade/impostors', nome + '.json');
  if (!fs.existsSync(arquivo)) falha(`descritor não encontrado: ${arquivo}`);
  let d;
  try { d = JSON.parse(fs.readFileSync(arquivo, 'utf8')); } catch (e) { falha(`${arquivo}: JSON inválido (${e.message})`); }
  const em = msg => falha(`${path.basename(arquivo)}: ${msg}`);
  const inteiro = (v, campo, min, max) => { if (!Number.isInteger(v) || v < min || v > max) em(`"${campo}" precisa ser um inteiro de ${min} a ${max}`); return v; };
  const par = (v, campo) => { if (!Array.isArray(v) || v.length !== 2) em(`"${campo}" precisa ser [largura, altura]`); return v.map((x, i) => inteiro(x, `${campo}[${i}]`, 1, 4096)); };
  if (d.format !== 1) em(`"format" precisa ser 1`);
  const paginasDe = (lista, campo) => {
    if (!Array.isArray(lista) || !lista.length || lista.length > 16) em(`"${campo}" precisa listar de 1 a 16 páginas PNG`);
    return lista.map((t, i) => {
      const m = /^([a-z0-9_.-]+):([a-z0-9_./-]+\.png)$/.exec(t);
      if (!m) em(`${campo}[${i}] "${t}" não é um PNG como "namespace:textures/x.png"`);
      return { t, file: path.join(saida, 'assets', m[1], m[2]) };
    });
  };
  const paginas = paginasDe(d.textures, 'textures');
  const [fw, fh] = par(d.frame, 'frame'), [cols, rows] = par(d.grid, 'grid');
  if (cols > 64 || rows > 64) em('"grid" até 64 × 64');
  const gutter = d.gutter === undefined ? 0 : inteiro(d.gutter, 'gutter', 0, 64);
  if (!d.views || typeof d.views !== 'object') em('falta o objeto "views"');
  const yaw = inteiro(d.views.yaw, 'views.yaw', 1, 256), mirror = !!d.views.mirror;
  if (mirror && yaw % 2) em('"views.mirror" pede "views.yaw" par');
  const pitch = d.views.pitch === undefined ? [0] : d.views.pitch;
  if (!Array.isArray(pitch) || !pitch.length || pitch.some((p, i) => typeof p !== 'number' || p < -90 || p > 90 || (i && p <= pitch[i - 1])))
    em('"views.pitch" precisa ser uma lista crescente de ângulos em −90..90');
  const quadros = d.frames === undefined ? 1 : inteiro(d.frames, 'frames', 1, 4096);
  const porQuadro = (mirror ? yaw / 2 + 1 : yaw) * pitch.length, celulas = cols * rows;
  if (porQuadro > celulas) em(`um quadro tem ${porQuadro} vistas e a página só ${celulas} células`);
  const porPagina = Math.floor(celulas / porQuadro), precisa = Math.ceil(quadros / porPagina);
  if (paginas.length !== precisa) em(`${quadros} quadro(s) de ${porQuadro} vistas pedem ${precisa} página(s), "textures" lista ${paginas.length}`);
  const conferir = (lista, lw, lh, g, rotulo) => {
    for (const p of lista) {
      if (!fs.existsSync(p.file)) em(`${rotulo}: a página ${p.t} não existe em ${p.file}`);
      const img = lerPng(p.file), w = cols * (lw + 2 * g), h = rows * (lh + 2 * g);
      if (img.w !== w || img.h !== h) em(`${rotulo}: a página ${p.t} tem ${img.w} × ${img.h}, o descritor pede ${w} × ${h}`);
    }
  };
  conferir(paginas, fw, fh, gutter, 'nível 0');
  let anterior = { w: fw, below: Infinity };
  (d.lod || []).forEach((l, i) => {
    const rotulo = `lod[${i}]`;
    const lp = paginasDe(l.textures, `${rotulo}.textures`);
    if (lp.length !== paginas.length) em(`${rotulo} precisa de ${paginas.length} página(s), como o nível 0`);
    const [lw, lh] = par(l.frame, `${rotulo}.frame`);
    if (lw * fh !== lh * fw) em(`${rotulo}.frame ${lw} × ${lh} não mantém a proporção de ${fw} × ${fh}`);
    if (lw >= anterior.w) em(`${rotulo}.frame precisa ser menor que o nível anterior`);
    const below = inteiro(l.below, `${rotulo}.below`, 1, lw);
    if (below >= anterior.below) em(`${rotulo}.below precisa ser menor que o do nível anterior`);
    conferir(lp, lw, lh, l.gutter === undefined ? gutter : l.gutter, rotulo);
    anterior = { w: lw, below };
  });
  if (!silencioso) console.log(`ok: ${texto} — ${paginas.length} página(s), ${porQuadro} vistas por quadro, ${quadros} quadro(s), ${(d.lod || []).length} nível(is) de LOD`);
}

// ------------------------------------------------------------------------------------------------ exemplo

function exemplo(args) {
  const { pos, o } = opcoes(args);
  if (pos.length !== 1) falha('uso: exemplo <pasta> [--yaw 8] [--pitch 0,30] [--frames 1] [--tamanho 64]');
  const nYaw = Number(o.yaw || 8), pitches = (o.pitch || '0').split(',').map(Number), quadros = Number(o.frames || 1), s = Number(o.tamanho || 64);
  for (let q = 0; q < quadros; q++) pitches.forEach((p, pi) => {
    for (let k = 0; k < nYaw; k++) {
      const yaw = Math.round(k * 360 / nYaw), rgba = Buffer.alloc(s * s * 4);
      // Uma seta (o "nariz" do objeto) que gira com a yaw; cor por yaw, mais clara por pitch, um ponto por quadro.
      const hue = k / nYaw, luz = 0.55 + 0.3 * pi / Math.max(1, pitches.length - 1);
      const cor = [0, 1, 2].map(c => Math.round(255 * Math.min(1, Math.max(0, luz + 0.45 * Math.cos(2 * Math.PI * (hue - c / 3))))));
      const ang = -yaw * Math.PI / 180;
      for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
        const u = (x + 0.5) / s * 2 - 1, v = (y + 0.5) / s * 2 - 1;
        const a = u * Math.cos(ang) - v * Math.sin(ang), b = u * Math.sin(ang) + v * Math.cos(ang);
        const corpo = Math.hypot(a / 0.85, b / 0.35) <= 1, nariz = a > 0.4 && a < 0.95 && Math.abs(b) < (0.95 - a) * 0.6;
        const ponto = Math.hypot(u + 0.7, v - 0.7 + q * 0.2) < 0.12;
        if (corpo || nariz || ponto) cor.concat([255]).forEach((c, i) => { rgba[(y * s + x) * 4 + i] = ponto ? 255 : nariz ? Math.min(255, c + 60) : c; });
      }
      gravarPng(path.join(pos[0], `y${yaw}_p${p}${quadros > 1 ? '_f' + q : ''}.png`), s, s, rgba);
    }
  });
  console.log(`exemplo: ${nYaw * pitches.length * quadros} vistas em ${pos[0]}`);
}

// ------------------------------------------------------------------------------------------------ main

function main() {
  const [cmd, ...args] = process.argv.slice(2);
  try {
    if (cmd === 'montar') montar(args);
    else if (cmd === 'validar') {
      const { pos } = opcoes(args);
      if (pos.length !== 2) falha('uso: validar <pasta-resources> ns:nome');
      validarDescritor(pos[0], pos[1], false);
    } else if (cmd === 'exemplo') exemplo(args);
    else {
      console.log(fs.readFileSync(__filename, 'utf8').split('\n').slice(1, 30).filter(l => l.startsWith('//')).map(l => l.slice(3)).join('\n'));
      process.exit(cmd ? 2 : 0);
    }
  } catch (e) {
    if (e instanceof Falha) { console.error('erro: ' + e.message); process.exit(1); }
    throw e;
  }
}

module.exports = { lerPng, gravarPng, reduzir, validarDescritor };
if (require.main === module) main();
