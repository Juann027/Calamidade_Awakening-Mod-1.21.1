#!/usr/bin/env python3
"""Gera os assets originais do BossesTesting (determinístico: mesma semente, mesmos bytes).

- textures/gui/bar/*: painel militar (fase 1), moldura danificada (fase 2), preenchimento, emblemas, brilho.
- textures/sky/*: panorama industrial alaranjado, panorama de tempestade ciano/vermelha, sol de fumaça,
  flipbook 4x4 do olho da tempestade.
- textures/particle/*: brasa, fumaça e arco elétrico (spritesheets em quadros) + particles/*.json.
- textures/entity/guilhermino_phase2_glow_{cyan,red}.png: a glowmask FORNECIDA separada por matiz (o original
  não é alterado), para animar o reator ciano e os indicadores vermelhos separadamente.
Uso: python3 -I tools/gerar_assets.py   (a partir de examples/bossestesting)
"""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, 'src/main/resources/assets/bossestesting')
rng = np.random.default_rng(20261124)

def save(img, rel):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True)

# ------------------------------------------------------------------ noise helpers
def value_noise(h, w, cell, seed, wrap_x=True):
    r = np.random.default_rng(seed)
    gh, gw = h // cell + 2, w // cell + (0 if wrap_x else 2)
    gw = max(gw, 2)
    g = r.random((gh, gw))
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0 = np.floor(ys).astype(int); fy = ys - y0
    x0 = np.floor(xs).astype(int); fx = xs - x0
    x1 = (x0 + 1) % gw if wrap_x else x0 + 1
    x0m = x0 % gw if wrap_x else x0
    fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0m]; b = g[y0][:, x1]; c = g[y0 + 1][:, x0m]; d = g[y0 + 1][:, x1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]

def fbm(h, w, cell, octaves, seed, wrap_x=True):
    out = np.zeros((h, w)); amp = 1.0; tot = 0
    for o in range(octaves):
        c = max(2, cell >> o)
        if wrap_x and w % c: c = max(2, w // max(1, round(w / c)))
        out += amp * value_noise(h, w, c, seed + o * 17, wrap_x); tot += amp; amp *= .5
    return out / tot

def lerp(a, b, t): return a + (b - a) * t

def rgba(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')

# ------------------------------------------------------------------ sky panoramas (equirectangular, south in the middle)
def panorama_p1():
    W, H = 2048, 1024
    el = (0.5 - (np.arange(H) + .5) / H) * math.pi   # +pi/2 top
    elg = np.repeat(el[:, None], W, 1)
    clouds = fbm(H, W, 256, 5, 11)
    smog = fbm(H, W, 128, 4, 23)
    # Horizon glow (orange industrial), smoke bands, dark zenith.
    t = np.clip((elg + .15) / 1.2, 0, 1)
    top = np.array([28, 16, 12]); mid = np.array([150, 66, 22]); hor = np.array([235, 132, 48])
    col = np.where(t[..., None] > .35, lerp(mid, top, ((t - .35) / .65)[..., None]), lerp(hor, mid, (t / .35)[..., None]))
    band = np.exp(-((elg - .12) / .16) ** 2) * (smog * 1.4 - .3)
    col = col * (1 - np.clip(band, 0, 1)[..., None] * .55) + np.array([60, 32, 20]) * np.clip(band, 0, 1)[..., None] * .4
    lit = np.clip(clouds * 1.6 - .55, 0, 1) * np.exp(-np.maximum(elg, 0) * 1.3)
    col = col + np.array([255, 150, 60]) * lit[..., None] * .35
    # Distant industrial silhouettes along the horizon: stacks and gantries.
    sil = np.zeros((H, W))
    hpx = int(H * .5)
    xs = np.arange(W)
    skyline = (fbm(1, W, 64, 3, 5)[0] * 26 + 6).astype(int)
    for s in range(70):
        x = int(rng.integers(0, W)); w = int(rng.integers(4, 14)); h = int(rng.integers(30, 110))
        skyline[x:x + w] = np.maximum(skyline[x:x + w], h)
    for x in range(W):
        sil[hpx - skyline[x]:hpx + 4, x] = 1
    col = col * (1 - sil[..., None]) + np.array([18, 12, 10]) * sil[..., None]
    alpha = np.clip(255 * (0.55 + 0.45 * np.exp(-np.maximum(elg, 0) * 1.6)), 0, 255)
    alpha = np.where(elg < -.05, 255, alpha)
    col = np.where(elg[..., None] < -.02, np.array([22, 14, 10]), col)
    img = np.dstack([col, alpha])
    save(rgba(img), 'textures/sky/industrial_p1.png')

def panorama_p2():
    W, H = 2048, 1024
    el = (0.5 - (np.arange(H) + .5) / H) * math.pi
    elg = np.repeat(el[:, None], W, 1)
    c1 = fbm(H, W, 256, 6, 31)
    c2 = fbm(H, W, 64, 4, 37)
    t = np.clip((elg + .1) / 1.3, 0, 1)
    top = np.array([4, 10, 16]); hor = np.array([10, 52, 66])
    col = lerp(hor, top, t[..., None])
    clouds = np.clip(c1 * 1.8 - .6, 0, 1)
    edge = np.clip(1 - np.abs(c1 - .58) * 9, 0, 1) * np.clip(c2 * 1.6 - .3, 0, 1)
    col = col * (1 - clouds[..., None] * .55) + np.array([6, 20, 26]) * clouds[..., None] * .5
    col = col + np.array([60, 230, 255]) * edge[..., None] * .7 + np.array([10, 60, 80]) * clouds[..., None] * .35
    red = np.clip(fbm(H, W, 512, 3, 41) * 2.2 - 1.15, 0, 1) * np.exp(-((elg - .25) / .35) ** 2)
    col = col + np.array([255, 40, 60]) * red[..., None] * .45
    col = np.where(elg[..., None] < -.02, np.array([6, 12, 14]), col)
    img = np.dstack([col, np.full((H, W), 255.0)])
    save(rgba(img), 'textures/sky/storm_p2.png')

def smog_sun():
    S = 256
    y, x = np.mgrid[0:S, 0:S] / (S - 1) * 2 - 1
    r = np.sqrt(x * x + y * y)
    disc = np.clip((1 - r) * 6, 0, 1)
    halo = np.exp(-r * 3) * .6
    bands = 0.8 + 0.2 * np.sin(y * 18 + fbm(S, S, 32, 3, 3, False) * 5)
    col = np.dstack([255 * np.ones_like(r), 110 + 70 * disc * bands, 40 + 20 * disc]) * (disc * bands + halo)[..., None]
    a = np.clip(disc * .95 + halo, 0, 1) * 255
    save(rgba(np.dstack([col, a])), 'textures/sky/smog_sun.png')

def storm_eye():
    F, S = 16, 256
    sheet = np.zeros((4 * S, 4 * S, 4))
    y, x = np.mgrid[0:S, 0:S] / (S - 1) * 2 - 1
    r = np.sqrt(x * x + y * y) + 1e-6
    ang = np.arctan2(y, x)
    base = fbm(S, S, 32, 4, 51, False)
    for f in range(F):
        ph = f / F * math.tau
        spiral = np.sin(ang * 3 + np.log(r) * 7 - ph * 3 + base * 4)
        arms = np.clip(spiral * .8 + .3, 0, 1) ** 2 * np.exp(-r * .8) * np.clip((1 - r) * 4, 0, 1)
        core = np.exp(-r * 6)
        fil = np.clip(1 - np.abs(spiral) * 5, 0, 1) * np.exp(-r * 1.2) * np.clip((1 - r) * 4, 0, 1) * .7
        cyan = np.array([50, 230, 255]); red = np.array([255, 40, 70])
        col = cyan * (arms + core)[..., None] + red * fil[..., None] + np.array([255, 255, 255]) * (core ** 2)[..., None]
        a = np.clip((arms + core + fil) * 255, 0, 255)
        tile = np.dstack([col, a])
        sy, sx = divmod(f, 4)
        sheet[sy * S:(sy + 1) * S, sx * S:(sx + 1) * S] = tile
    save(rgba(sheet), 'textures/sky/storm_eye.png')

# ------------------------------------------------------------------ boss bar art
def panel(phase):
    W, H = 256, 40
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if phase == 1:
        plate, plate2, edge = (38, 44, 36, 255), (58, 70, 50, 255), (92, 104, 80, 255)
    else:
        plate, plate2, edge = (26, 30, 34, 255), (40, 46, 52, 255), (70, 80, 88, 255)
    # Main bevelled plate with an angled cut on both ends, window for the fill (transparent).
    poly = [(10, 4), (W - 10, 4), (W - 2, 14), (W - 2, 30), (W - 10, H - 2), (10, H - 2), (2, 30), (2, 14)]
    d.polygon(poly, fill=plate)
    d.line(poly + [poly[0]], fill=edge, width=1)
    d.rectangle((40, 8, W - 14, 12), fill=plate2)
    # rivets
    for x in range(46, W - 14, 18):
        d.point((x, 10), fill=(140, 150, 120, 255) if phase == 1 else (110, 116, 120, 255))
        d.point((x, H - 7), fill=(140, 150, 120, 255) if phase == 1 else (110, 116, 120, 255))
    # hazard stripes on the emblem bay, clipped to the plate
    stripes = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ds = ImageDraw.Draw(stripes)
    for i in range(-6, 12):
        x = 4 + i * 6
        ds.polygon([(x, H - 4), (x + 3, H - 4), (x + 9, 6), (x + 6, 6)], fill=(200, 150, 30, 255) if phase == 1 else (180, 30, 40, 255))
    clip = Image.new('L', (W, H), 0)
    ImageDraw.Draw(clip).polygon([(10, 6), (40, 6), (40, H - 4), (10, H - 4), (4, 30), (4, 14)], fill=255)
    img.paste(stripes, (0, 0), Image.composite(stripes, Image.new('RGBA', (W, H)), clip).split()[3])
    d.ellipse((4, 4, 36, 36), fill=(20, 22, 18, 255), outline=edge)
    # fill window: 44..244 x 16..27 (transparent, drawn over by the code)
    d.rectangle((43, 15, 245, 28), fill=(8, 10, 8, 255), outline=edge)
    d.rectangle((44, 16, 244, 27), fill=(0, 0, 0, 0))
    a = np.array(img).astype(float)
    if phase == 2:
        # damage: cracks, scorch, a missing chunk at the top right, bent cap
        n = fbm(H, W, 16, 3, 61, False)
        scorch = np.clip(n * 2 - 1.1, 0, 1)
        a[..., :3] *= (1 - scorch[..., None] * .6)
        im2 = Image.fromarray(a.astype(np.uint8), 'RGBA')
        d2 = ImageDraw.Draw(im2)
        d2.polygon([(190, 4), (214, 4), (208, 12), (198, 9)], fill=(0, 0, 0, 0))
        for k in range(5):
            x0 = int(rng.integers(50, 240)); y0 = int(rng.choice([6, 31, 34]))
            pts = [(x0, y0)]
            for s in range(5):
                pts.append((pts[-1][0] + int(rng.integers(-5, 6)), pts[-1][1] + int(rng.integers(-2, 3))))
            d2.line(pts, fill=(10, 10, 12, 255), width=1)
        a = np.array(im2).astype(float)
    save(rgba(a), f'textures/gui/bar/panel_p{phase}.png')

def fill():
    W, H = 64, 12
    y, x = np.mgrid[0:H, 0:W]
    stripes = (((x + y * 2) // 6) % 2) * .18 + .82
    grad = 1 - np.abs(y - H * .35) / H * .9
    v = np.clip(stripes * grad, 0, 1) * 255
    save(rgba(np.dstack([v, v, v, np.full((H, W), 255.0)])), 'textures/gui/bar/fill.png')

def emblem(phase):
    S = 32
    y, x = np.mgrid[0:S, 0:S] / (S - 1) * 2 - 1
    r = np.sqrt(x * x + y * y); ang = np.arctan2(y, x)
    if phase == 1:
        hexr = np.max(np.stack([np.abs(x) * .866 + np.abs(y) * .5, np.abs(y)]), axis=0)
        ring = np.clip(1 - np.abs(hexr - .78) * 14, 0, 1)
        core = np.clip(1 - r * 2.2, 0, 1)
        blades = np.clip(np.sin(ang * 3) * .5 + .5 - r * .4, 0, 1) * (r < .62)
        col = np.array([255, 150, 40]) * (ring + core)[..., None] + np.array([255, 220, 160]) * (core ** 2)[..., None] + np.array([200, 90, 20]) * blades[..., None] * .7
        a = np.clip(ring + core + blades * .7, 0, 1) * 255
    else:
        ring = np.clip(1 - np.abs(r - .78) * 12, 0, 1) * (np.sin(ang * 5 + 1) > -.6)
        crack = np.clip(1 - np.abs(np.sin(ang * 2.5 + r * 6)) * 10, 0, 1) * (r < .7)
        core = np.clip(1 - r * 2.0, 0, 1)
        col = np.array([50, 230, 255]) * (ring + core)[..., None] + np.array([255, 40, 60]) * crack[..., None] + np.array([230, 255, 255]) * (core ** 2)[..., None]
        a = np.clip(ring + core + crack, 0, 1) * 255
    save(rgba(np.dstack([col, a])), f'textures/gui/bar/emblem_p{phase}.png')

def glow():
    S = 32
    y, x = np.mgrid[0:S, 0:S] / (S - 1) * 2 - 1
    r = np.sqrt(x * x + y * y)
    v = np.exp(-r * r * 4)
    save(rgba(np.dstack([np.full((S, S), 255.0)] * 3 + [v * 255])), 'textures/gui/bar/glow.png')

# ------------------------------------------------------------------ particles
def particles():
    def write(name, frames):
        for i, f in enumerate(frames):
            save(rgba(f), f'textures/particle/{name}_{i}.png')
        p = os.path.join(A, 'particles', name + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump({'textures': [f'bossestesting:{name}_{i}' for i in range(len(frames))]}, open(p, 'w'), indent=2)
    S = 16
    y, x = np.mgrid[0:S, 0:S] / (S - 1) * 2 - 1
    r = np.sqrt(x * x + y * y)
    ember = []
    for i in range(4):
        k = 1 - i * .2
        core = np.exp(-r * r * (10 + i * 4)) * k
        v = np.clip(core * 1.6, 0, 1)
        ember.append(np.dstack([np.full((S, S), 255.0), 255 * np.clip(.6 + v * .4, 0, 1), 255 * np.clip(.3 + v * .7, 0, 1), v * 255]))
    write('ember', ember)
    S2 = 32
    y2, x2 = np.mgrid[0:S2, 0:S2] / (S2 - 1) * 2 - 1
    r2 = np.sqrt(x2 * x2 + y2 * y2)
    smoke = []
    for i in range(8):
        n = fbm(S2, S2, 8, 3, 90 + i, False)
        m = np.clip((1 - r2 * (1.15 - i * .03)) * 1.6, 0, 1) * np.clip(n * 1.8 - .25, 0, 1)
        shade = 255 * (.75 + .25 * n)
        smoke.append(np.dstack([shade, shade, shade, m * 255 * (1 - i * .07)]))
    write('smoke', smoke)
    arc = []
    for i in range(4):
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        pts = [(int(rng.integers(0, 4)), int(rng.integers(0, S)))]
        while pts[-1][0] < S - 1:
            pts.append((min(S - 1, pts[-1][0] + int(rng.integers(2, 5))), int(np.clip(pts[-1][1] + rng.integers(-4, 5), 1, S - 2))))
        d.line(pts, fill=(255, 255, 255, 255), width=1)
        g = im.filter(ImageFilter.GaussianBlur(1.2))
        a = np.maximum(np.array(im).astype(float), np.array(g).astype(float) * 1.6)
        arc.append(a)
    write('arc', arc)

# ------------------------------------------------------------------ glowmask split (derived, the original stays untouched)
def split_glow():
    src = np.array(Image.open(os.path.join(A, 'textures/entity/guilhermino_phase2_glowmask.png')).convert('RGBA')).astype(float)
    r, g, b, a = src[..., 0], src[..., 1], src[..., 2], src[..., 3]
    redish = (r > g * 1.6) & (r > b * 1.6) & (a > 0)
    red = src.copy(); red[~redish] = 0
    cyan = src.copy(); cyan[redish] = 0
    save(rgba(cyan), 'textures/entity/guilhermino_phase2_glow_cyan.png')
    save(rgba(red), 'textures/entity/guilhermino_phase2_glow_red.png')

if __name__ == '__main__':
    panorama_p1(); panorama_p2(); smog_sun(); storm_eye()
    panel(1); panel(2); fill(); emblem(1); emblem(2); glow()
    particles(); split_glow()
    print('ok')
