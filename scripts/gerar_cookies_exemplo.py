#!/usr/bin/env python3
"""Gera os light cookies do laboratório de luz do vfx-sample (0.15.0), byte a byte iguais a cada execução.

Saída em examples/vfx-sample/src/main/resources/assets/vfxsample/textures/lights/:
  runas.png       256 x 256  círculo arcano branco sobre transparente (MASK)
  hexagonos.png   222 x 192  grade hexagonal que repete nas duas direções (MASK, REPEAT)
  causticas.png   512 x 512  folha 4 x 4 de cáusticas que repetem e fecham o laço no tempo (MASK, REPEAT)
  vitral.png      256 x 256  rosácea colorida com chumbo (COLOR)
  selo.png        512 x 256  folha 4 x 2: um selo que carrega de 0 a 100% (MASK, trilha FRAME)
  alvo.png        128 x 128  retícula tecnológica (MASK)
  persiana.png    128 x 128  sombra de persiana para SUBTRACT
Uso: python3 scripts/gerar_cookies_exemplo.py
Requer Pillow. Cada imagem é desenhada por pixel (sem fontes nem antialias do sistema), por isso o resultado não
depende da máquina.
"""
import math
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'examples', 'vfx-sample', 'src', 'main', 'resources', 'assets',
                   'vfxsample', 'textures', 'lights')


def save(name, img):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name), optimize=True)
    print('cookie', name, img.size)


def clamp(x):
    return max(0.0, min(1.0, x))


def line_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-12))
    return math.hypot(px - ax - dx * t, py - ay - dy * t)


def mask_image(w, h, f, ss=2, color=(255, 255, 255)):
    """Imagem branca com alfa = média de f(u, v) em ss x ss amostras por pixel (u, v em 0..1, v para baixo)."""
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            a = 0.0
            for sy in range(ss):
                for sx in range(ss):
                    a += clamp(f((x + (sx + .5) / ss) / w, (y + (sy + .5) / ss) / h))
            px[x, y] = (*color, round(255 * a / (ss * ss)))
    return img


def runas():
    rnd = random.Random(15)
    segs = []
    for k in range(16):
        a0 = k / 16 * 2 * math.pi
        for _ in range(3):
            r1, r2 = .33 + rnd.random() * .08, .33 + rnd.random() * .08
            d1, d2 = (rnd.random() - .5) * .16, (rnd.random() - .5) * .16
            segs.append((a0 + d1, r1, a0 + d2, r2))
    star = [(math.cos(i * 2 * math.pi / 6 + math.pi / 2) * .26, math.sin(i * 2 * math.pi / 6 + math.pi / 2) * .26) for i in range(6)]

    def f(u, v):
        x, y = u - .5, .5 - v
        r = math.hypot(x, y)
        val = 0.0
        for rr, w in ((.46, .010), (.43, .005), (.30, .006), (.27, .004)):
            val = max(val, 1 - abs(r - rr) / w)
        for a1, r1, a2, r2 in segs:
            d = line_dist(x, y, math.cos(a1) * r1, math.sin(a1) * r1, math.cos(a2) * r2, math.sin(a2) * r2)
            val = max(val, 1 - d / .006)
        for i in range(6):
            ax, ay = star[i]
            bx, by = star[(i + 2) % 6]
            val = max(val, (1 - line_dist(x, y, ax, ay, bx, by) / .005) * .9)
        val = max(val, (1 - r / .05) * .8)
        return val
    save('runas.png', mask_image(256, 256, f))


def hexagonos():
    w, h = 222, 192
    s = 32.0  # raio do hexágono em pixels; 222 = 4 * sqrt(3) * 32 (arredondado), 192 = 6 * 32

    def f(u, v):
        x, y = u * 4 * math.sqrt(3) * s, v * 6 * s
        best = 1e9
        # Centros de uma rede hexagonal (pontas para cima) e vizinhos, com periodicidade.
        q = y / (1.5 * s)
        for row in range(int(q) - 1, int(q) + 3):
            off = (row % 2) * math.sqrt(3) * s / 2
            col0 = int((x - off) / (math.sqrt(3) * s))
            for col in range(col0 - 1, col0 + 3):
                cx, cy = col * math.sqrt(3) * s + off, row * 1.5 * s
                dx, dy = abs(x - cx), abs(y - cy)
                d = max(dx, dx * .5 + dy * math.sqrt(3) / 2)  # distância hexagonal (apótema)
                best = min(best, d)
        edge = math.sqrt(3) / 2 * s
        line = 1 - abs(best - edge) / 1.6
        fill = .12 if best < edge * .8 else 0
        node = 0.0
        return max(line, fill, node)
    save('hexagonos.png', mask_image(w, h, f))


def causticas():
    frames, cols, side = 16, 4, 128
    sheet = Image.new('RGBA', (side * cols, side * (frames // cols)))
    rnd = random.Random(7)
    # Rede de Voronoi periódica (o ladrilho repete) com pontos em órbita: F2 - F1 pequeno = borda clara. Cada ponto dá
    # um número inteiro de voltas no laço, então o quadro 16 emenda no 0.
    pts = [(rnd.random(), rnd.random(), .05 + rnd.random() * .06, rnd.random() * 2 * math.pi, rnd.choice((-1, 1)))
           for _ in range(10)]
    for fr in range(frames):
        t = fr / frames
        cur = [(x + r * math.cos(ph + 2 * math.pi * n * t), y + r * math.sin(ph + 2 * math.pi * n * t)) for x, y, r, ph, n in pts]

        def f(u, v):
            u2 = u + .025 * math.sin(2 * math.pi * (v + t))
            v2 = v + .025 * math.cos(2 * math.pi * (u - t))
            d1 = d2 = 9.0
            for px_, py_ in cur:
                for ox in (-1, 0, 1):
                    for oy in (-1, 0, 1):
                        d = math.hypot((u2 - (px_ % 1) - ox), (v2 - (py_ % 1) - oy))
                        if d < d1:
                            d1, d2 = d, d1
                        elif d < d2:
                            d2 = d
            e = d2 - d1
            return .08 + .92 * (1 - min(1.0, e / .035)) ** 2
        img = mask_image(side, side, f, ss=1)
        sheet.paste(img, ((fr % cols) * side, (fr // cols) * side))
    save('causticas.png', sheet)


def vitral():
    img = Image.new('RGBA', (256, 256))
    px = img.load()
    palette = [(230, 40, 40), (250, 200, 30), (40, 120, 255), (40, 200, 90), (160, 60, 230), (255, 120, 20)]
    for yy in range(256):
        for xx in range(256):
            col, alpha = [0, 0, 0], 0.0
            for sy in range(2):
                for sx in range(2):
                    x, y = (xx + .25 + sx * .5) / 256 - .5, .5 - (yy + .25 + sy * .5) / 256
                    r, a = math.hypot(x, y), math.atan2(y, x)
                    seg = int(((a / (2 * math.pi)) % 1) * 12)
                    if r > .48:
                        continue
                    lead = abs(r - .2) < .008 or abs(r - .36) < .008 or abs(r - .47) < .01
                    frac = ((a / (2 * math.pi)) % 1) * 12 % 1
                    if r > .2 and (frac < .03 or frac > .97):
                        lead = True
                    if lead:
                        alpha += .25
                        continue
                    c = palette[(seg + (0 if r < .2 else 2 if r < .36 else 4)) % len(palette)] if r >= .08 else (255, 255, 240)
                    shade = .8 + .2 * math.cos(r * 40)
                    col = [col[i] + c[i] * shade for i in range(3)]
                    alpha += .25
            n = max(1, sum(1 for _ in range(1)))
            px[xx, yy] = (min(255, round(col[0] / 4 / max(alpha, .25) * (1 if alpha else 0))),
                          min(255, round(col[1] / 4 / max(alpha, .25) * (1 if alpha else 0))),
                          min(255, round(col[2] / 4 / max(alpha, .25) * (1 if alpha else 0))), round(255 * alpha))
    save('vitral.png', img)


def selo():
    frames, side = 8, 128
    sheet = Image.new('RGBA', (side * 4, side * 2))
    for fr in range(frames):
        p = (fr + 1) / frames

        def f(u, v):
            x, y = u - .5, .5 - v
            r, a = math.hypot(x, y), (math.atan2(x, y) / (2 * math.pi)) % 1  # 0 no topo, horário
            val = 0.0
            if a <= p:
                val = max(val, 1 - abs(r - .42) / .03)
            val = max(val, (1 - abs(r - .46) / .008) * .5)
            for k in range(3):
                ang = k / 3 * 2 * math.pi + p * math.pi
                ax, ay = math.sin(ang) * .3, math.cos(ang) * .3
                bx, by = math.sin(ang + 2 * math.pi / 3) * .3, math.cos(ang + 2 * math.pi / 3) * .3
                val = max(val, (1 - line_dist(x, y, ax, ay, bx, by) / .012) * (.3 + .7 * p))
            val = max(val, (1 - r / (.04 + .1 * p)) * p)
            return val
        img = mask_image(side, side, f)
        sheet.paste(img, ((fr % 4) * side, (fr // 4) * side))
    save('selo.png', sheet)


def alvo():
    def f(u, v):
        x, y = u - .5, .5 - v
        r = math.hypot(x, y)
        val = max(1 - abs(r - .44) / .012, 1 - abs(r - .2) / .01)
        for ax, ay in ((1, 0), (0, 1)):
            d = abs(x * ay - y * ax)
            along = abs(x * ax + y * ay)
            if .1 < along < .38:
                val = max(val, 1 - d / .01)
        a = (math.atan2(y, x) / (2 * math.pi)) % 1
        if abs(r - .33) < .02 and (a * 8) % 1 < .4:
            val = max(val, .7)
        return val
    save('alvo.png', mask_image(128, 128, f))


def persiana():
    def f(u, v):
        return 1.0 if (v * 6) % 1 < .45 else 0.0
    save('persiana.png', mask_image(128, 128, f))


if __name__ == '__main__':
    runas()
    hexagonos()
    causticas()
    vitral()
    selo()
    alvo()
    persiana()
