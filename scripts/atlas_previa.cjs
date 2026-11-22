// Prévias do Atlas Estelar 0.11.2 (fora do jogo): node scripts/atlas_previa.cjs scripts build/atlas-previa
// Lê scripts/atlas_previa.html (direção A, B ou C; aba; tempo da abertura; noite) e grava um PNG por quadro.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const [src, out] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
  const shots = [];
  for (const dir of ['A', 'B', 'C']) {
    for (const [tab, night] of [['hab', 0], ['qual', 0], ['hab', 1]]) shots.push({ dir, tab, t: 1, night, name: `${dir}-${tab}${night ? '-noite' : ''}` });
    for (const t of [.2, .45, .7, 1]) shots.push({ dir, tab: 'hab', t, night: 0, name: `${dir}-abertura-${Math.round(t * 100)}` });
  }
  for (const s of shots) {
    await page.goto('file://' + path.resolve(src, 'atlas_previa.html') + `?dir=${s.dir}&tab=${s.tab}&t=${s.t}&night=${s.night}`);
    await page.waitForFunction(() => document.title === 'ok', null, { timeout: 30000 });
    await page.screenshot({ path: path.join(out, s.name + '.png') });
  }
  await browser.close();
})();
