#!/usr/bin/env python3
"""Gera as imagens, folhas e impostors do testeImagens (0.14, fase 4), byte a byte iguais a cada execução.

    python3 scripts/gerar_sprites_teste.py

Saída em src/testeJogo/resources/assets/calamidade_teste/:
  textures/sky/img_disco.png     64 × 64, disco opaco de cor única (IMG_DISCO), transparente fora (raio 28);
  textures/sky/img_suave.png     64 × 64, disco de alfa suave (1 no centro, 0 no raio 30), cor IMG_SUAVE;
  textures/sky/img_quadros.png   folha 4 × 1 de quadros 32 × 32 opacos, uma cor sentinela por quadro (QUADROS);
  textures/sky/img_pisca.png     folha 2 × 1, quadro 0 preto e quadro 1 branco (pisca: proteção de flashes);
  textures/sky/img_corrompido.png  bytes que não são PNG (recurso inválido);
  textures/sky/impostors/nave_p0.png, nave_16_p0.png e calamidade/impostors/nave.json
                                 impostor de 8 vistas (uma cor sentinela por yaw, VISTAS) e um nível de LOD 16 × 16,
                                 montado pela própria ferramenta scripts/gerar_impostor.cjs (Node);
  calamidade/impostors/quebrado.json    descritor inválido (páginas a menos);
  calamidade/impostors/sem_pagina.json  descritor válido apontando para uma página que não existe.
As cores ficam longe das faces do cubemap do testeBackdrop, do céu, do terreno e da névoa do jogo.
"""
import json
import shutil
import struct
import subprocess
import tempfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/testeJogo/resources/assets/calamidade_teste"
SKY = OUT / "textures/sky"
IMG_DISCO = (0xF0, 0x30, 0xA0)
IMG_SUAVE = (0x30, 0xC0, 0xF0)
# Quadros 0..3 (as mesmas constantes do RoteiroImagens).
QUADROS = [(0xE0, 0x40, 0x40), (0x40, 0xE0, 0x40), (0x40, 0x40, 0xE0), (0xE0, 0xE0, 0x40)]
# Vistas do impostor por yaw 0, 45, …, 315.
VISTAS = [(0xF0, 0x40, 0x40), (0xF0, 0xA0, 0x30), (0xE0, 0xE0, 0x30), (0x50, 0xE0, 0x50),
          (0x30, 0xD0, 0xD0), (0x40, 0x60, 0xF0), (0xA0, 0x40, 0xF0), (0xF0, 0x50, 0xC0)]


def png(path, w, h, pixel):
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(w)) for y in range(h))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main():
    def disco(x, y):
        d = ((x + .5 - 32) ** 2 + (y + .5 - 32) ** 2) ** .5
        return (*IMG_DISCO, 255) if d <= 28 else (0, 0, 0, 0)
    png(SKY / "img_disco.png", 64, 64, disco)

    def suave(x, y):
        d = ((x + .5 - 32) ** 2 + (y + .5 - 32) ** 2) ** .5
        return (*IMG_SUAVE, round(255 * max(0, 1 - d / 30)))
    png(SKY / "img_suave.png", 64, 64, suave)
    png(SKY / "img_quadros.png", 128, 32, lambda x, y: (*QUADROS[x // 32], 255))
    png(SKY / "img_pisca.png", 64, 32, lambda x, y: (0, 0, 0, 255) if x < 32 else (255, 255, 255, 255))
    (SKY / "img_corrompido.png").write_bytes(b"isto nao e um PNG\n")

    # Impostor: as vistas viram páginas pela ferramenta (o mesmo caminho de um addon).
    with tempfile.TemporaryDirectory() as tmp:
        vistas = Path(tmp) / "vistas"
        for k, cor in enumerate(VISTAS):
            png(vistas / f"y{k * 45}_p0.png", 32, 32, lambda x, y, c=cor: (*c, 255) if 2 <= x < 30 and 2 <= y < 30 else (0, 0, 0, 0))
        saida = Path(tmp) / "res"
        subprocess.run(["node", str(ROOT / "scripts/gerar_impostor.cjs"), "montar", str(vistas), "--id", "calamidade_teste:nave",
                        "--saida", str(saida), "--lod", "16:12"], check=True, stdout=subprocess.DEVNULL)
        for rel in ["textures/sky/impostors/nave_p0.png", "textures/sky/impostors/nave_16_p0.png", "calamidade/impostors/nave.json"]:
            alvo = OUT / rel
            alvo.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(saida / "assets/calamidade_teste" / rel, alvo)
    imp = OUT / "calamidade/impostors"
    (imp / "quebrado.json").write_text(json.dumps({"format": 1, "textures": ["calamidade_teste:textures/sky/impostors/nave_p0.png"],
        "frame": [32, 32], "grid": [8, 1], "views": {"yaw": 8}, "frames": 3}, indent=2) + "\n")
    (imp / "sem_pagina.json").write_text(json.dumps({"format": 1, "textures": ["calamidade_teste:textures/sky/impostors/nao_existe.png"],
        "frame": [32, 32], "grid": [8, 1], "views": {"yaw": 8}}, indent=2) + "\n")
    print("texturas e impostors de teste em", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
