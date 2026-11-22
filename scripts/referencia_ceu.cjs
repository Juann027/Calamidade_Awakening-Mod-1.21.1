// Referência visual da Sky API da 0.12.0 (0.14, fase 1). Roda os passes reais "sky" e "atmosphere" pela prévia
// (scripts/previa_passes.cjs: WebGL2 no Chromium headless com SwiftShader, framebuffers de 8 bits entre os passes) em
// todas as cenas de céu e compara cada PNG, pixel a pixel, com src/test/resources/referencia-ceu/<cena>.png.
//
//   node scripts/referencia_ceu.cjs              confere (sai com 1 se alguma cena divergir)
//   node scripts/referencia_ceu.cjs --atualizar  regrava as referências (só quando a mudança visual é intencional
//                                                e aprovada; os sete fenômenos da 0.12 não devem mudar)
//   node scripts/referencia_ceu.cjs --atualizar fundo_x ...   regrava só as cenas citadas
//
// 0.14 (fase 2): também as cenas com fundos de céu (FUNDO_CENAS, prefixo fundo_): fundo opaco, parcial e com buraco
// de alfa sob a Atmosphere, sol do jogo apagado sob fundo, eclipse, buraco negro sobre cubemap, cubemap girado e
// transição entre dois fundos. A correção da composição é provada por scripts/fundo_composicao.cjs; aqui, a estabilidade.
//
// Tolerância: SwiftShader é determinístico na mesma máquina, mas outra CPU ou versão do Chromium pode mudar o último
// bit de uma conta em ponto flutuante. Uma cena passa quando a diferença média é <= 0,5/255 por canal e no máximo
// 0,1% dos pixels diferem mais de 24/255 em algum canal. Uma regressão de verdade (camada some, cor muda, lente quebra)
// passa longe disso. As diferenças acima do limite vão para build/previa/diferenca-<cena>.png.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const REF = path.join(ROOT, 'src/test/resources/referencia-ceu');
const OUT = path.join(ROOT, 'build/previa');
const MEAN_LIMIT = 0.5, BIG_DIFF = 24, BIG_FRACTION = 0.001;

// PNG RGBA de 8 bits sem entrelaçamento (o que canvas.toDataURL grava); filtros 0 a 4.
function decode(file) {
  const buf = fs.readFileSync(file);
  let pos = 8, width = 0, height = 0, idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos), type = buf.toString('ascii', pos + 4, pos + 8), data = buf.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0); height = data.readUInt32BE(4);
      if (data[8] !== 8 || (data[9] !== 6 && data[9] !== 2) || data[12] !== 0) throw new Error(file + ': PNG não suportado');
      decode.channels = data[9] === 6 ? 4 : 3;
    } else if (type === 'IDAT') idat.push(data);
    pos += 12 + len;
  }
  const ch = decode.channels, stride = width * ch, raw = zlib.inflateSync(Buffer.concat(idat));
  const out = Buffer.alloc(width * height * 4);
  let prev = Buffer.alloc(stride), cur = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? cur[x - ch] : 0, b = prev[x], c = x >= ch ? prev[x - ch] : 0;
      let v = line[x];
      if (f === 1) v += a;
      else if (f === 2) v += b;
      else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      cur[x] = v & 255;
    }
    for (let x = 0; x < width; x++) {
      out[(y * width + x) * 4] = cur[x * ch]; out[(y * width + x) * 4 + 1] = cur[x * ch + 1]; out[(y * width + x) * 4 + 2] = cur[x * ch + 2];
      out[(y * width + x) * 4 + 3] = ch === 4 ? cur[x * ch + 3] : 255;
    }
    [prev, cur] = [cur, prev];
  }
  return { width, height, data: out };
}

// PNG RGB simples (filtro 0) para o mapa de diferenças.
function encode(file, width, height, rgb) {
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = b => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]);
    const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 2;
  const raw = Buffer.alloc((width * 3 + 1) * height);
  for (let y = 0; y < height; y++) rgb.copy(raw, y * (width * 3 + 1) + 1, y * width * 3, (y + 1) * width * 3);
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}

function compare(name) {
  const got = decode(path.join(OUT, name + '.png')), ref = decode(path.join(REF, name + '.png'));
  if (got.width !== ref.width || got.height !== ref.height) return { ok: false, why: `tamanho ${got.width}x${got.height} != ${ref.width}x${ref.height}` };
  const n = got.width * got.height, map = Buffer.alloc(n * 3);
  let sum = 0, big = 0, worst = 0;
  for (let i = 0; i < n; i++) {
    let m = 0;
    for (let c = 0; c < 3; c++) { const d = Math.abs(got.data[i * 4 + c] - ref.data[i * 4 + c]); sum += d; m = Math.max(m, d); }
    worst = Math.max(worst, m);
    if (m > BIG_DIFF) { big++; map[i * 3] = 255; } else map[i * 3 + 1] = Math.min(255, m * 10);
  }
  const mean = sum / (n * 3), fraction = big / n, ok = mean <= MEAN_LIMIT && fraction <= BIG_FRACTION;
  if (!ok) encode(path.join(OUT, 'diferenca-' + name + '.png'), got.width, got.height, map);
  return { ok, why: `média ${mean.toFixed(3)}/255, ${(fraction * 100).toFixed(3)}% acima de ${BIG_DIFF}, pior ${worst}` };
}

function main() {
  const update = process.argv.includes('--atualizar');
  const { SKY_CENAS, REF_CENAS, FUNDO_CENAS } = require('./previa_passes.cjs');
  const names = [...Object.keys(SKY_CENAS), ...Object.keys(REF_CENAS), ...Object.keys(FUNDO_CENAS)];
  const only = update ? process.argv.slice(process.argv.indexOf('--atualizar') + 1) : [];
  execFileSync(process.execPath, [path.join(__dirname, 'previa_passes.cjs'), ...names], { stdio: 'inherit' });
  if (update) {
    fs.mkdirSync(REF, { recursive: true });
    const chosen = only.length ? names.filter(n => only.includes(n)) : names;
    for (const name of chosen) fs.copyFileSync(path.join(OUT, name + '.png'), path.join(REF, name + '.png'));
    console.log(`referência visual: ${chosen.length} cenas gravadas em ${path.relative(ROOT, REF)}`);
    return;
  }
  let failed = 0;
  for (const name of names) {
    if (!fs.existsSync(path.join(REF, name + '.png'))) { console.log(`FALTA  ${name}: sem referência`); failed++; continue; }
    const r = compare(name);
    console.log(`${r.ok ? 'OK    ' : 'DIFERE'} ${name}: ${r.why}`);
    if (!r.ok) failed++;
  }
  console.log(`referência visual: ${names.length - failed}/${names.length} cenas iguais à referência (15 da 0.12.0, ${names.length - 15} com fundos de céu da 0.14)`);
  if (failed) process.exit(1);
}

module.exports = { decode, compare };
if (require.main === module) main();
