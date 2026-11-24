#!/usr/bin/env python3
"""Compara os arquivos de eventos com as animações exportadas e gera Anims.java + docs/EVENTOS.md.

Fonte de verdade: os keyframes da animação exportada (é o que o GeckoLib toca). Os eventos documentados que não
existem como instrução interna (walk, run, spawn_landing, punch_reactor) entram com o tempo documentado e marcados
como "externos": o addon os sincroniza explicitamente (servidor por tick, cliente pela fase do laço).
Uso: python3 -I tools/eventos.py   (a partir de examples/bossestesting)
"""
import json, math, os
BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(BASE)
RES = os.path.join(ROOT, 'src/main/resources/assets/bossestesting/animations')
PAIRS = [  # (prefixo Java, arquivo de eventos ou None, animação, prefixo da instrução)
    ('JG', 'guilhermino_events.json', 'jar_golem.animation.json', 'guilhermino:'),
    ('P2', 'guilhermino_phase2_events.json', 'guilhermino_phase2.animation.json', 'guilhermino2:'),
    ('MS', None, 'guilhermino_missile.animation.json', None),
]

def tick(s):
    return int(s * 20 + 0.5 + 1e-9)

def maxkey(anim):
    m = 0.0
    for bone in anim.get('bones', {}).values():
        for ch in ('rotation', 'position', 'scale'):
            v = bone.get(ch)
            if isinstance(v, dict):
                for k in v:
                    try: m = max(m, float(k))
                    except ValueError: pass
    return m

names = []
java, md = [], ['# Eventos × animações (gerado por `tools/eventos.py`)', '',
    'Tempos em segundos e ticks (20/s, arredondados). **interno** = instrução `timeline` do GeckoLib na animação exportada; '
    '**externo** = só no arquivo de eventos (sincronizado pelo addon). Divergências abaixo de meio tick não mudam o tick.', '']
for prefix, evf, anf, instr in PAIRS:
    anims = json.load(open(os.path.join(RES, anf)))['animations']
    doc = json.load(open(os.path.join(BASE, 'eventos', evf)))['animations'] if evf else {}
    md += [f'## {anf}', '']
    for name, a in anims.items():
        length = float(a.get('animation_length', 0)); mk = maxkey(a)
        loop = a.get('loop', False)
        loopk = 'LOOP' if loop is True else ('HOLD' if loop == 'hold_on_last_frame' else 'ONCE')
        internal = {}
        for t, v in (a.get('timeline') or {}).items():
            for part in str(v).split(';'):
                part = part.strip()
                if part.startswith(instr or '\0'):
                    internal[part[len(instr):]] = float(t)
        documented = {e['id']: float(e['time']) for e in doc.get(name, {}).get('events', [])}
        events, rows = {}, []
        for eid in list(dict.fromkeys(list(internal) + list(documented))):
            ti, td = internal.get(eid), documented.get(eid)
            if ti is not None:
                events[eid] = (tick(ti), True)
            else:
                events[eid] = (tick(td), False)
            note = ''
            if ti is not None and td is not None and abs(ti - td) > 1e-6:
                note = f'doc {td:.2f} s (Δ {abs(ti-td)*1000:.0f} ms, mesmo tick)' if tick(ti) == tick(td) else f'doc {td:.2f} s: TICK DIFERENTE, vale a animação'
            elif ti is None: note = 'só no arquivo de eventos'
            elif td is None and evf: note = 'só na animação'
            rows.append(f'| `{eid}` | {(ti if ti is not None else td):.2f} | {events[eid][0]} | {"interno" if ti is not None else "externo"} | {note} |')
        end_t = max(tick(length), math.ceil(mk * 20 - 1e-9))  # o último keyframe inteiro cabe
        warn = f' · **último keyframe {mk:.3f} s > duração {length:.3f} s: fim efetivo {end_t} ticks**' if mk > length + 1e-6 else ''
        chain = doc.get(name, {}).get('chain_to')
        md += [f'### `{name}` — {length:.2f} s ({tick(length)} ticks), {loopk}' + (f', encadeia em `{chain}`' if chain else '') + warn, '']
        if rows: md += ['| evento | s | tick | origem | nota |', '|---|---|---|---|---|'] + rows + ['']
        short = name.split('.')[-1].upper()
        ev = ', '.join(f'"{k}", {v[0]}' for k, v in events.items())
        ext = ', '.join(f'"{k}"' for k, v in events.items() if not v[1])
        names.append(f'{prefix}_{short}')
        java.append(f'    public static final Clip {prefix}_{short} = new Clip("{name}", {tick(length)}, {end_t}, Loop.{loopk},\n'
                    f'        Clip.eventMap({ev}),\n        java.util.Set.of({ext}));')
src = '''package dev.bossestesting.anim;

/**
 * GERADO por tools/eventos.py a partir das animações exportadas e dos arquivos de eventos: não edite à mão.
 * Ticks a 20/s. {@code external} = evento que não existe como instrução interna do GeckoLib (sincronizado pelo addon).
 */
public final class Anims {
    private Anims() {}

%s

    /** Every clip, indexed by the synced animation id of the boss entities. */
    public static final Clip[] ALL = {%s};

    /** Index of a clip in {@link #ALL}, or -1. */
    public static int index(Clip clip) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i] == clip) return i;
        return -1;
    }
}
''' % ('\n'.join(java), ', '.join(names))
out = os.path.join(ROOT, 'src/main/java/dev/bossestesting/anim/Anims.java')
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'w').write(src)
open(os.path.join(ROOT, 'docs/EVENTOS.md'), 'w').write('\n'.join(md) + '\n')
print('ok', out)
