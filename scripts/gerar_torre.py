#!/usr/bin/env python3
"""Gera examples/vfx-sample/src/main/resources/data/vfxsample/structure/torre.nbt (receita 21, torre_caindo).

Uma torre de pedra 5 x 10 x 5 feita para o exemplo (só blocos vanilla): base de pedregulho, paredes de tijolos de
pedra com alguns rachados e musgosos, porta na frente (z = 0), janelas de vidro, piso de tábuas no meio, um barril
com pão (testa os dados de block entity do template), plataforma no topo com ameias e uma lanterna. Formato de
estrutura do jogo (NBT comprimido com gzip, DataVersion 3955 = Minecraft 1.21.1). Sem dependências:

    python3 scripts/gerar_torre.py
"""
import gzip
import os
import struct

DATA_VERSION = 3955
SX, SY, SZ = 5, 10, 5
OUT = os.path.join(os.path.dirname(__file__), '..', 'examples/vfx-sample/src/main/resources/data/vfxsample/structure/torre.nbt')

# ------------------------------------------------------------------ NBT mínimo (tags: byte, int, string, list, compound)
END, BYTE, INT, STRING, LIST, COMPOUND = 0, 1, 3, 8, 9, 10


def name(text):
    raw = text.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def payload(tag, value):
    if tag == BYTE:
        return struct.pack('>b', value)
    if tag == INT:
        return struct.pack('>i', value)
    if tag == STRING:
        return name(value)
    if tag == LIST:
        element, items = value
        return struct.pack('>bi', element if items else END, len(items)) + b''.join(payload(element, i) for i in items)
    if tag == COMPOUND:
        out = b''
        for key, (t, v) in value.items():
            out += struct.pack('>b', t) + name(key) + payload(t, v)
        return out + struct.pack('>b', END)
    raise ValueError(tag)


# ------------------------------------------------------------------ desenho da torre
palette = []


def state(block, **props):
    entry = (block, tuple(sorted(props.items())))
    if entry not in palette:
        palette.append(entry)
    return palette.index(entry)


AIR = state('minecraft:air')
COBBLE = state('minecraft:cobblestone')
BRICK = state('minecraft:stone_bricks')
CRACKED = state('minecraft:cracked_stone_bricks')
MOSSY = state('minecraft:mossy_stone_bricks')
GLASS = state('minecraft:glass')
PLANKS = state('minecraft:spruce_planks')
LANTERN = state('minecraft:lantern', hanging='false', waterlogged='false')
BARREL = state('minecraft:barrel', facing='up', open='false')

grid = {}
for x in range(SX):
    for z in range(SZ):
        grid[(x, 0, z)] = COBBLE
        edge = x in (0, SX - 1) or z in (0, SZ - 1)
        for y in range(1, 8):
            if edge:
                # Variação determinística: alguns rachados e musgosos, mais musgo embaixo.
                k = (x * 7 + y * 13 + z * 5) % 11
                grid[(x, y, z)] = MOSSY if (k == 0 and y < 5) else CRACKED if k == 3 else BRICK
            else:
                grid[(x, y, z)] = AIR
        grid[(x, 4, z)] = PLANKS if not edge else grid[(x, 4, z)]
        grid[(x, 8, z)] = BRICK
        battlement = edge and (x % 2 == 0) and (z % 2 == 0)
        grid[(x, 9, z)] = BRICK if battlement else AIR
# Porta na frente (z = 0), janelas no meio de cada lado, lanterna no topo e no térreo, barril com pão.
grid[(2, 1, 0)] = AIR
grid[(2, 2, 0)] = AIR
for (x, z) in ((2, 0), (2, 4), (0, 2), (4, 2)):
    grid[(x, 6, z)] = GLASS
grid[(2, 9, 2)] = LANTERN
grid[(2, 1, 2)] = LANTERN
grid[(1, 1, 3)] = BARREL
grid[(2, 4, 3)] = AIR   # passagem no piso

barrel_nbt = (COMPOUND, {
    'id': (STRING, 'minecraft:barrel'),
    'Items': (LIST, (COMPOUND, [{
        'Slot': (BYTE, 0), 'id': (STRING, 'minecraft:bread'), 'count': (INT, 3),
    }])),
})

blocks = []
for (x, y, z), s in sorted(grid.items(), key=lambda e: (e[0][1], e[0][2], e[0][0])):
    entry = {'pos': (LIST, (INT, [x, y, z])), 'state': (INT, s)}
    if s == BARREL:
        entry['nbt'] = barrel_nbt
    blocks.append(entry)


def palette_entry(block, props):
    entry = {'Name': (STRING, block)}
    if props:
        entry['Properties'] = (COMPOUND, {k: (STRING, v) for k, v in props})
    return entry


root = {
    'DataVersion': (INT, DATA_VERSION),
    'size': (LIST, (INT, [SX, SY, SZ])),
    'palette': (LIST, (COMPOUND, [palette_entry(b, p) for b, p in palette])),
    'blocks': (LIST, (COMPOUND, blocks)),
    'entities': (LIST, (COMPOUND, [])),
}
data = struct.pack('>b', COMPOUND) + name('') + payload(COMPOUND, root)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'wb') as f:
    # mtime fixo: o arquivo sai byte a byte igual a cada execução.
    f.write(gzip.compress(data, mtime=0))
print(f'{os.path.relpath(OUT)}: {len(blocks)} blocos, paleta {len(palette)}')
