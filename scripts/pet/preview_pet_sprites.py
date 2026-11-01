"""Preview pet frames as a PNG contact sheet (needs: pip install resvg-py).

  python scripts/pet/preview_pet_sprites.py app/src/main/res/drawable       app/src/main/java/com/alananasss/kittytune/ui/widget/pet/PetFrames.kt out.png all
  ... out.png list:idle_open,dance_bob_1     (specific frames, grey bicolour cat)
  ... out.png custom                          (colours x patterns x accessories x decor)
"""
import sys, re, xml.etree.ElementTree as ET, resvg_py
D = sys.argv[1]; KT = sys.argv[2]; out = sys.argv[3]; mode = sys.argv[4]
A = '{http://schemas.android.com/apk/res/android}'
WALL = {'morning': 'FFF3E3', 'day': 'E8F5EC', 'evening': 'FCE6EC', 'night': '2A2C57', 'rain': 'E3E8F3', 'sleep': '22244A', 'space': '101A33'}
kt = open(KT, encoding='utf-8').read()
FR = {}
for m in re.finditer(r'put\("(\w+)", PetFrame\((.*?)\)\)', kt):
    name = m.group(1); body = m.group(2)
    arrs = re.findall(r'intArrayOf\((.*?)\)', body)
    tail = body.split('), ')[-1]
    parts = [p.strip() for p in body.split(',')]
    acc = int(re.search(r'\), (\d+), (true|false), (true|false)$', body).group(1))
    hat_ok = re.search(r'\), (\d+), (true|false), (true|false)$', body).group(2) == 'true'
    neck_ok = re.search(r'\), (\d+), (true|false), (true|false)$', body).group(3) == 'true'
    def res(s):
        s = s.strip()
        return None if s == '0' else s.replace('R.drawable.', '')
    FR[name] = dict(marks=[res(x) for x in arrs[0].split(',')], whites=[res(x) for x in arrs[1].split(',')], acc=acc, hat=hat_ok, neck=neck_ok)


def paths(name, tint=None):
    r = ET.parse(f'{D}/{name}.xml').getroot(); s = ''
    for p in r.iter('path'):
        c = p.get(A + 'fillColor').lstrip('#'); a = int(c[:2], 16) / 255
        s += f'<path d="{p.get(A + "pathData")}" fill="#{tint or c[2:]}" fill-opacity="{a:.3f}" shape-rendering="crispEdges"/>'
    return s


def room_for(n):
    if n.startswith('sleep'): return 'sleep'
    if n.startswith('sad'): return 'rain'
    if n.startswith(('cameo','dig','judge','fan','phones','dance_')): return 'day'
    if n.startswith(('doze', 'stargaze', 'star_', 'nod')): return 'night'
    if n.startswith(('loaf', 'disco', 'ghost')): return 'evening'
    if n.startswith(('wave', 'yawn', 'eat')): return 'morning'
    return 'day'


def cell(n, pal, pattern, eye, hat=None, neck=None, decor=None):
    PI = ['SOLID', 'CLASSIC', 'TABBY', 'BICOLOR', 'CALICO', 'POINTS'].index(pattern)
    fr = FR[n]; rm = room_for(n)
    s = f'<rect x="-1" y="-1" width="48" height="34" rx="3" fill="#{WALL[rm]}"/>' + paths('pet_room_' + rm)
    if decor: s += paths('pet_decor_' + decor)
    s += paths(f'pet_{n}_f', pal[0]) + paths(f'pet_{n}_s', pal[1])
    if fr['marks'][PI]: s += paths(fr['marks'][PI], pal[2])
    if fr['whites'][PI]: s += paths(fr['whites'][PI], pal[3])
    try:
        s += paths(f'pet_{n}_e', eye)
    except FileNotFoundError:
        pass
    s += paths(f'pet_{n}_i')
    if hat and fr['hat']: s += paths(f'pet_acc_{hat}_{fr["acc"]}')
    if neck and fr['neck']: s += paths(f'pet_acc_{neck}_{fr["acc"]}')
    return s


cells = []
GREY = ('9A989C', '83818A', '5E5C63', 'F4F2F0')
if mode.startswith('list:'):
    for n in mode[5:].split(','):
        cells.append(cell(n, GREY, 'BICOLOR', '9BC25A', None, 'medal'))
elif mode == 'all':
    for n in FR:
        cells.append(cell(n, GREY, 'BICOLOR', '9BC25A', None, 'medal'))
else:
    pals = {'CLASSIC': ('FFF7EE', 'F0D9C8', 'F7B27A', 'FFFFFF'), 'TABBY': ('C9B79C', 'AE9C82', '6B5B47', 'FFFFFF'),
            'BICOLOR': ('5A5270', '473F5C', '35304A', 'FFFFFF'), 'CALICO': ('F2A65A', 'D98E45', '3D3540', 'FFFFFF'),
            'POINTS': ('F5E8D6', 'D9C3A8', '6E5444', 'FFFFFF'), 'SOLID': ('9AA3B8', '7D869E', '5E6780', 'FFFFFF')}
    hats = ['bow', 'flower', 'crown', 'party', 'star', 'sprout', 'glasses']
    necks = ['medal', 'bell', 'scarf', 'bandana', 'bowtie']
    eyes = ['F0B030', '5CC46A', '6AB0F0', 'D2783C', '3D2B3D']
    i = 0
    for pat, pal in pals.items():
        for n in ['idle_open', 'loaf_open', 'wave_1', 'play_2']:
            cells.append(cell(n, pal, pat, eyes[i % 5], hats[i % 7], necks[i % 5], ['pumpkin', 'tree', 'cake', 'hearts', 'snow', None][i % 6]))
            i += 1
cols = 6 if mode == 'all' else 4
body = ''
for i, c in enumerate(cells):
    body += f'<g transform="translate({(i % cols) * 48},{(i // cols) * 34})">{c}</g>'
rows = (len(cells) + cols - 1) // cols
w, h = cols * 48, rows * 34
scale = 5 if mode == 'all' else 7
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * scale}" height="{h * scale}" viewBox="-2 -2 {w + 2} {h + 2}"><rect x="-2" y="-2" width="{w + 4}" height="{h + 4}" fill="white"/>{body}</svg>'
open(out, 'wb').write(bytes(resvg_py.svg_to_bytes(svg_string=svg)))
print(len(cells))
