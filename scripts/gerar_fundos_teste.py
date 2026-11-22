#!/usr/bin/env python3
"""Gera as texturas de fundo de céu do mod de teste (0.14, fase 2), byte a byte iguais a cada execução.

    python3 scripts/gerar_fundos_teste.py

Saída em src/testeJogo/resources/assets/calamidade_teste/textures/sky/:
  cubo.png   cubemap 3 × 2 de 32 px, uma cor lisa por face (ordem do Backdrop: norte, leste, sul / oeste, cima, baixo);
  grade.png  o mesmo com linhas escuras a cada 8 px (para ver a lente entortar o fundo);
  meia.png   panorama 64 × 32 de uma cor só com alfa 128 (cobertura 0,5);
  faixas.png panorama 128 × 64 opaco com quatro faixas de azimute (norte, leste, sul, oeste) e o topo mais claro.
As cores ficam longe das do céu, do terreno e da névoa do jogo, para as sondas de pixel em jogo.
"""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/testeJogo/resources/assets/calamidade_teste/textures/sky"
# Norte, leste, sul, oeste, cima, baixo (as mesmas constantes do RoteiroBackdrop).
FACES = [(0xC8, 0x32, 0x32), (0x32, 0xB4, 0x32), (0x3C, 0x50, 0xE6), (0xDC, 0xC8, 0x28), (0xC8, 0x32, 0xC8), (0x28, 0xC8, 0xC8)]
MEIA = (0x20, 0xE0, 0x90)
# Faixas centradas no norte (u = 0), leste (0,25), sul (0,5) e oeste (0,75).
FAIXAS = [(0xE0, 0x60, 0x20), (0x20, 0x60, 0xE0), (0x60, 0xE0, 0x20), (0xE0, 0x20, 0xA0)]


def png(path, w, h, pixel):
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(w)) for y in range(h))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(data)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = 32
    png(OUT / "cubo.png", s * 3, s * 2, lambda x, y: (*FACES[(y // s) * 3 + x // s], 255))
    png(OUT / "grade.png", s * 3, s * 2, lambda x, y: (16, 16, 16, 255) if x % 8 == 0 or y % 8 == 0
        else (*FACES[(y // s) * 3 + x // s], 255))
    png(OUT / "meia.png", 64, 32, lambda x, y: (*MEIA, 128))
    png(OUT / "faixas.png", 128, 64, lambda x, y: (*[min(255, c + (40 if y < 16 else 0)) for c in FAIXAS[((x + 16) // 32) % 4]], 255))
    print("fundos de teste em", OUT)


if __name__ == "__main__":
    main()
