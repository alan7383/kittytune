"""Preview Ivy's hand-made coat on real frames (needs: pip install resvg-py).

  python scripts/pet/preview_ivy.py app/src/main/res/drawable       app/src/main/java/com/alananasss/kittytune/ui/widget/pet/PetFrames.kt       app/src/main/res/raw/pet_partmap.bin out.png idle_open,ivy_float_1
Keep HEAD/BODY/TAIL here in sync with PetIvy.kt.
"""
"""Ivy's hand-made coat + preview renderer (uses the real generated layers + partmap)."""
import struct, sys, re, xml.etree.ElementTree as ET
import resvg_py

D = sys.argv[1]; KT = sys.argv[2]; RAW = sys.argv[3]; OUT = sys.argv[4]
A = '{http://schemas.android.com/apk/res/android}'

# ---- Ivy (viewer's view). '.' = not coat, 0 coat (light grey), 1 darker grey, 2 white
HEAD = [
    "......................",
    "......................",
    "......................",
    "..0..0..........1..1..",
    "..0000001000011111111.",
    ".000000000221111111111",
    ".000000002221111111111",
    ".000000022221111111111",
    ".000000002221111111111",
    ".220000002222111111111",
    ".222220022222211111111",
    ".222222222222222111111",
    "..2222222222222221111.",
    "...2222222222222211...",
    "......................",
]
BODY = [
    "................",
    "..002222222211..",
    ".00022222222111.",
    ".00022222222111.",
    ".00022222222111.",
    ".00222.2222.211.",
    "................",
]
TAIL = "110000000"  # from the tip
PAL = {'0': 'B4B1B8', 'S': '9C99A2', '1': '6F6C77', '2': 'F8F8FA'}
EYE = 'A8C47A'

for r in HEAD + BODY:
    pass
HEAD = [r.ljust(22, '.')[:22] for r in HEAD]


def role(part, r, c):
    if part == 0:
        ch = HEAD[r][c] if r < len(HEAD) else '0'
    elif part in (1, 4):
        rr, cc = (r, c) if part == 1 else (5, 3)
        ch = BODY[rr][cc]
    elif part == 2:
        ch = BODY[min(6, r * 7 // 8)][c * 15 // 29]
    elif part == 3:
        ch = TAIL[min(r, len(TAIL) - 1)]
    else:
        ch = '0'
    return '0' if ch == '.' else ch


blob = open(RAW, 'rb').read()
n = struct.unpack_from('<H', blob, 0)[0]; o = 2
PM = {}
for _ in range(n):
    L = blob[o]; o += 1
    name = blob[o:o + L].decode(); o += L
    k = struct.unpack_from('<H', blob, o)[0]; o += 2
    PM[name] = [blob[o + i * 5:o + i * 5 + 5] for i in range(k)]; o += k * 5

kt = open(KT, encoding='utf-8').read()
ACC = {}
for m in re.finditer(r'put\("(\w+)", PetFrame\((.*?)\)\)', kt):
    ACC[m.group(1)] = re.search(r'\), (\d+), (true|false), (true|false)$', m.group(2)).groups()


def paths(name, tint=None):
    r = ET.parse(f'{D}/{name}.xml').getroot(); s = ''
    for p in r.iter('path'):
        c = p.get(A + 'fillColor').lstrip('#'); a = int(c[:2], 16) / 255
        s += f'<path d="{p.get(A + "pathData")}" fill="#{tint or c[2:]}" fill-opacity="{a:.3f}" shape-rendering="crispEdges"/>'
    return s


def frame(name, room, wall):
    s = f'<rect x="-1" y="-1" width="48" height="34" rx="3" fill="#{wall}"/>' + paths('pet_room_' + room)
    s += paths(f'pet_{name}_f', PAL['0']) + paths(f'pet_{name}_s', PAL['S'])
    for (x, y, part, r, c) in PM[name]:
        rr = role(part, r, c)
        if rr in '12':
            s += f'<rect x="{x}" y="{y}" width="1" height="1" fill="#{PAL[rr]}" shape-rendering="crispEdges"/>'
    try:
        s += paths(f'pet_{name}_e', EYE)
    except FileNotFoundError:
        pass
    s += paths(f'pet_{name}_i')
    acc, hat, neck = ACC[name]
    if neck == 'true':
        s += paths(f'pet_acc_medal_{acc}')
    return s


names = sys.argv[5].split(',')
body = ''
for i, nm in enumerate(names):
    room, wall = ('space', '101A33') if nm.startswith('ivy') else ('day', 'E8F5EC')
    body += f'<g transform="translate({(i % 4) * 48},{(i // 4) * 34})">{frame(nm, room, wall)}</g>'
rows = (len(names) + 3) // 4
w, h = 4 * 48, rows * 34
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * 7}" height="{h * 7}" viewBox="-2 -2 {w + 2} {h + 2}"><rect x="-2" y="-2" width="{w + 4}" height="{h + 4}" fill="white"/>{body}</svg>'
open(OUT, 'wb').write(bytes(resvg_py.svg_to_bytes(svg_string=svg)))
print("".join(HEAD)); print("".join(BODY)); print(TAIL)
