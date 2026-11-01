"""KittyTune pixel pet sprite generator. See docs/KITTY_PET.md.

  python -I scripts/pet/gen_pet_sprites.py app/src/main/res/drawable \
      app/src/main/java/com/alananasss/kittytune/ui/widget/pet/PetFrames.kt

Sprites are ASCII grids composed into 46x32 frames. Every frame is exported as layered vectors:
  _f  fur silhouette          (white, tinted fur)
  _s  shade pixels            (white, tinted shade)
  _e  eye iris pixels         (white, tinted eye colour)
  _i  ink: outline, face, props, effects (fixed colours)
  _m<P> markings for pattern P (white, tinted mark colour)   only when non-empty
  _w<P> white parts for pattern P (white, tinted "white" colour)  only when non-empty
Accessories are exported per head position (a handful), not per frame.
Also writes rooms, seasonal decor and the Kotlin index (PetFrames.kt).
"""
import os, sys, glob

RES_DIR = sys.argv[1]
KT_FILE = sys.argv[2]
W, H = 46, 32
OX = 8

INK = {
    'K': '#FF3D2B3D', 'P': '#FFFFA3C0', 'N': '#FFFF6F9C', 'w': '#FFFFFFFF', 'M': '#FFC2416B',
    'T': '#FF7FCBFF', 'B': '#FFBFE6FF', 'Q': '#FF7FA7E8', 'Y': '#FFFFD84D', 'R': '#FFFF4F8B',
    'Z': '#FFFFFFFF', 'V': '#FF8B7BFF', 'X': '#FFFF5FA2', 'x': '#FFD9478A', 'H': '#FF8B7BFF',
    'C': '#FFFF6FA8', 'c': '#FFD9478A', 'A': '#FFE8B07A', 'a': '#FFC98A52', 'y': '#FFF6DDB0',
    'd': '#FF5B4A7A', 'g': '#FFB9A8FF', 'o': '#FFF7B27A', 'G': '#FF7CCB7C', 'n': '#FF4FA05F',
    'J': '#FFE0A92E', 'f': '#FFFFB3D1', 'L': '#FFFF3355', 'l': '#FFFF9AAE', 'r': '#FFFF6B6B',
    'O': '#FFFFA34D', 'U': '#FFC9CFDA', 'u': '#FF9AA2B4', 'b': '#80FFF3A0', 'k': '#FF2A2140',
    'i': '#FF8FE3FF', 'p': '#FFC58CFF', 'h': '#FFFFFFFF',
    # default palette (preview only)
    'W': '#FFFFF7EE', 'S': '#FFF0D9C8', 'E': '#FF3D2B3D',
}
PATTERNS = ['SOLID', 'CLASSIC', 'TABBY', 'BICOLOR', 'CALICO', 'POINTS']


def grid(s):
    rows = [r.strip().strip('|') for r in s.strip('\n').split('\n')]
    assert len({len(r) for r in rows}) == 1, rows
    return rows


def mirror(sprite):
    return [r[::-1] for r in sprite]


def recolor(sprite, src, dst):
    return [r.replace(src, dst) for r in sprite]


# ================================================================ cat parts
HEAD = grid('''
|..KK..............KK..|
|..KPK............KPK..|
|..KPPK..........KPPK..|
|.KWPPWKKKKKKKKKKWPPWK.|
|.KWWWWWWWWWWWWWWWWWWK.|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWWWWWWWK|
|.KWWWWWWWWWWWWWWWWWWK.|
|..KSSWWWWWWWWWWWWSSK..|
|...KKKKKKKKKKKKKKKK...|
''')
BODY = grid('''
|..KKKKKKKKKKKK..|
|.KWWWWWWWWWWWWK.|
|KWWWWWWWWWWWWWWK|
|KWWWWWWWWWWWWWWK|
|KSWWWWWWWWWWWWSK|
|KSSWWKWWWWKWWSSK|
|.KKKKKKKKKKKKKK.|
''')
LOAF = grid('''
|.....KKKKKKKKKKKKKKKKKKKK.....|
|...KKWWWWWWWWWWWWWWWWWWWWKK...|
|..KWWWWWWWWWWWWWWWWWWWWWWWWK..|
|.KWWWWWWWWWWWWWWWWWWWWWWWWWWK.|
|KWWWWWWWWWWWWWWWWWWWWWWWWWWWWK|
|KSWWWWWWWWWWWWWWWWWWWWWWWWWWSK|
|KSSWWWWWWWWWWWWWWWWWWWWWWWWSSK|
|.KKKKKKKKKKKKKKKKKKKKKKKKKKKK.|
''')
TAIL = grid('''
|....KK|
|...KWK|
|...KWK|
|...KWK|
|...KWK|
|.KKWWK|
|KWWWK.|
|KKKK..|
''')
TAIL_B = grid('''
|..KK..|
|.KWK..|
|..KWK.|
|..KWK.|
|...KWK|
|.KKWWK|
|KWWWK.|
|KKKK..|
''')
TAIL_LOAF = grid('''
|.KKKKKKKK.|
|KWWWWWWWWK|
|.KKKKKKKK.|
''')
TAIL_LOAF_B = grid('''
|.KKKKKKK..|
|KWWWWWWWWK|
|.KKKKKKKK.|
''')
PAW_UP = grid('''
|.KK.|
|KWWK|
|KWWK|
|KWWK|
''')
PAW_SIDE_L = grid('''
|.KKK|
|KWWW|
|.KKK|
''')
PAW_SIDE_R = mirror(PAW_SIDE_L)
KPAW = grid('''
|KWWK|
|KKKK|
''')
KITTEN = grid('''
|.K....K.|
|KPK..KPK|
|KWWKKWWK|
|KWKWWKWK|
|KWWNNWWK|
|.KWWWWK.|
|.KWWWWK.|
|..KKKK..|
''')
KITTEN_BLINK = grid('''
|.K....K.|
|KPK..KPK|
|KWWKKWWK|
|KWWWWWWK|
|KKWNNWKK|
|.KWWWWK.|
|.KWWWWK.|
|..KKKK..|
''')
HEADPHONES = grid('''
|....KKKKKKKKKKKKKKKKKK....|
|..KKHHHHHHHHHHHHHHHHHHKK..|
|.KHHKKKKKKKKKKKKKKKKKKHHK.|
|KHK....................KHK|
|KHK....................KHK|
|KHK....................KHK|
|KKKK..................KKKK|
|KCCCK................KCCCK|
|KCwCK................KCwCK|
|KCwCK................KCwCK|
|KCCCK................KCCCK|
|KcccK................KcccK|
|.KKK..................KKK.|
''')


def cells(spec):
    out = set()
    for r, cols in spec.items():
        for c in cols:
            out.add((r, c))
    return out


def rng(a, b):
    return list(range(a, b + 1))


# pattern masks: part -> {(row, col): 'm' | 'w'}
def mask(m=None, w=None):
    out = {}
    for rc in cells(m or {}):
        out[rc] = 'm'
    for rc in cells(w or {}):
        out[rc] = 'w'
    return out


CLASSIC_FOREHEAD = {4: [8, 10, 11, 13], 5: [10, 11]}
PMASK = {
    'SOLID': {},
    'CLASSIC': {
        'head': mask(CLASSIC_FOREHEAD),
    },
    'TABBY': {
        'head': mask({3: [2, 19], 4: [7, 14], 5: [7, 8, 10, 11, 13, 14], 6: [9, 12], 9: [1, 2, 19, 20], 11: [1, 2, 19, 20]}),
        'body': mask({1: [3, 12], 2: [2, 3, 12, 13], 3: [1, 14], 4: [2, 13]}),
        'loaf': mask({2: [4, 25], 3: [3, 4, 25, 26], 4: [2, 3, 26, 27], 5: [2, 27]}),
    },
    'BICOLOR': {
        'head': mask(CLASSIC_FOREHEAD, {8: [10, 11], 9: rng(9, 12), 10: rng(8, 13), 11: rng(5, 16), 12: rng(2, 19), 13: rng(3, 18)}),
        'body': mask(None, {1: rng(5, 10), 2: rng(5, 10), 3: rng(5, 10), 4: rng(6, 9), 5: [3, 4, 6, 7, 8, 9, 11, 12]}),
        'loaf': mask(None, {5: rng(8, 21), 6: rng(7, 22)}),
    },
    'CALICO': {
        'head': mask({3: [2, 5], 4: rng(2, 8), 5: rng(1, 8) + [16, 17, 18], 6: rng(1, 7) + [17, 18], 7: rng(1, 5), 8: rng(1, 3)},
                     {10: rng(9, 12), 11: rng(6, 15), 12: rng(3, 18), 13: rng(4, 17)}),
        'body': mask({1: rng(10, 13), 2: [1, 2] + rng(11, 14), 3: [1, 2] + rng(12, 14)},
                     {2: rng(6, 9), 3: rng(5, 10), 4: rng(5, 10), 5: [3, 4, 6, 7, 8, 9, 11, 12]}),
        'loaf': mask({2: rng(20, 26), 3: rng(21, 27), 4: rng(22, 28), 5: rng(23, 27)}, {5: rng(10, 19), 6: rng(9, 20)}),
    },
    'POINTS': {
        'head': mask({3: [2, 5, 16, 19], 4: [2, 3, 18, 19], 9: rng(9, 12), 10: rng(7, 14), 11: rng(6, 15), 12: rng(7, 14), 13: rng(9, 12)}),
        'body': mask({4: [1, 14], 5: [1, 2, 3, 4, 11, 12, 13, 14]}),
        'loaf': mask({5: rng(7, 10) + rng(19, 22), 6: rng(7, 10) + rng(19, 22)}),
    },
}


def tail_role(pattern, sprite, r, c):
    wcells = sorted((rr, cc) for rr, row in enumerate(sprite) for cc, ch in enumerate(row) if ch == 'W')
    if (r, c) not in wcells:
        return None
    horizontal = len({rr for rr, _ in wcells}) == 1
    order = sorted(wcells, key=lambda t: (-t[1]) if horizontal else (t[0], -t[1]))  # tip first
    idx = order.index((r, c))
    if pattern in ('CLASSIC', 'BICOLOR'):
        return 'm' if idx < (2 if horizontal else 1) else None
    if pattern == 'TABBY':
        return 'm' if idx % 2 == 0 else None
    if pattern == 'CALICO':
        return 'm' if idx < 4 else None
    if pattern == 'POINTS':
        return 'm'
    return None


def paw_role(pattern):
    return {'BICOLOR': 'w', 'CALICO': 'w', 'POINTS': 'm'}.get(pattern)


def role_for(pattern, part, sprite, r, c):
    if part in ('head', 'body', 'loaf'):
        return PMASK[pattern].get(part, {}).get((r, c))
    if part == 'tail':
        return tail_role(pattern, sprite, r, c)
    if part == 'paw':
        return paw_role(pattern)
    return None


# ================================================================ faces (head-local)
def pts(row, cols, ch):
    return [(row, c, ch) for c in cols]


def eyes_open(dx=0, dy=0):
    o = []
    for c in (6, 14):
        c += dx
        r = 8 + dy
        o += [(r, c, 'K'), (r, c + 1, 'w'), (r + 1, c, 'E'), (r + 1, c + 1, 'E'), (r + 2, c, 'K'), (r + 2, c + 1, 'K')]
    return o


def eyes_big(dx=0, dy=0):
    """Round, sparkly eyes (3 wide) for wonder moments."""
    o = []
    for c in (5, 14):
        c += dx
        r = 7 + dy
        o += [(r, c, 'K'), (r, c + 1, 'K'), (r, c + 2, 'w'),
              (r + 1, c, 'E'), (r + 1, c + 1, 'E'), (r + 1, c + 2, 'K'),
              (r + 2, c, 'E'), (r + 2, c + 1, 'E'), (r + 2, c + 2, 'E'),
              (r + 3, c, 'K'), (r + 3, c + 1, 'w'), (r + 3, c + 2, 'K')]
    return o


EYES_HAPPY = [(8, 6, 'K'), (9, 5, 'K'), (9, 7, 'K'), (8, 15, 'K'), (9, 14, 'K'), (9, 16, 'K')]
EYES_SQUEE = [(8, 5, 'K'), (8, 6, 'K'), (9, 7, 'K'), (10, 5, 'K'), (10, 6, 'K'),
              (8, 16, 'K'), (8, 15, 'K'), (9, 14, 'K'), (10, 16, 'K'), (10, 15, 'K')]
EYES_DASH = pts(9, [5, 6, 7, 14, 15, 16], 'K')
EYES_HALF = pts(9, [5, 6, 7, 14, 15, 16], 'K') + pts(10, [6, 7, 14, 15], 'E')
EYES_HEART = []
for _c in (5, 14):
    EYES_HEART += [(8, _c, 'R'), (8, _c + 2, 'R'), (9, _c, 'R'), (9, _c + 1, 'R'), (9, _c + 2, 'R'), (10, _c + 1, 'R')]
EYES_X = []
for _c in (5, 14):
    EYES_X += [(8, _c, 'K'), (8, _c + 2, 'K'), (9, _c + 1, 'K'), (10, _c, 'K'), (10, _c + 2, 'K')]
EYES_SPARK = []
for _c in (5, 14):
    EYES_SPARK += [(8, _c + 1, 'K'), (9, _c, 'K'), (9, _c + 1, 'w'), (9, _c + 2, 'K'), (10, _c + 1, 'K')]
EYES_WIDE = []  # shocked: tiny pupils in big whites
for _c in (5, 14):
    EYES_WIDE += [(7, _c, 'K'), (7, _c + 1, 'K'), (7, _c + 2, 'K'), (8, _c, 'K'), (8, _c + 1, 'E'), (8, _c + 2, 'K'),
                  (9, _c, 'K'), (9, _c + 1, 'K'), (9, _c + 2, 'K')]

NOSE = pts(11, [10, 11], 'N')
OMEGA = [(12, 8, 'K'), (12, 10, 'K'), (12, 11, 'K'), (12, 13, 'K'), (13, 9, 'K'), (13, 12, 'K')]
OPEN = [(11, 9, 'K'), (11, 10, 'M'), (11, 11, 'M'), (11, 12, 'K'), (12, 10, 'K'), (12, 11, 'K')]
BIG = [(11, 9, 'K'), (11, 10, 'M'), (11, 11, 'M'), (11, 12, 'K'), (12, 9, 'K'), (12, 10, 'P'), (12, 11, 'P'),
       (12, 12, 'K'), (13, 10, 'K'), (13, 11, 'K')]
OOO = [(12, 10, 'K'), (12, 11, 'K'), (13, 10, 'K'), (13, 11, 'K'), (11, 10, 'N'), (11, 11, 'N')]
SMALL = pts(12, [10, 11], 'K')
FROWN = [(12, 10, 'K'), (12, 11, 'K'), (13, 9, 'K'), (13, 12, 'K')]
TONGUE = [(12, 10, 'K'), (12, 11, 'K'), (13, 11, 'P')]
BLUSH = pts(11, [3, 4, 17, 18], 'P')
BLUSH_BIG = pts(11, [2, 3, 4, 17, 18, 19], 'P') + pts(12, [3, 18], 'P')
NOSE_WIGGLE = pts(11, [10, 11], 'N') + [(10, 10, 'N')]


def face(*parts):
    out = []
    for p in parts:
        out += p
    return out


# ================================================================ props & fx
NOTE = grid('''
|..X..|
|..XX.|
|..X.X|
|..X..|
|.XX..|
|XXX..|
|.X...|
''')
BEAMED = grid('''
|..XXXXX|
|..X...X|
|..X...X|
|.XX..XX|
|XXX.XXX|
|.X...X.|
''')
SPARKLE = grid('''
|..Y..|
|..Y..|
|YYwYY|
|..Y..|
|..Y..|
''')
TWINKLE = grid('''
|.Y.|
|YwY|
|.Y.|
''')
HEART = grid('''
|.KK.KK.|
|KRRKRRK|
|KRwRRRK|
|.KRRRK.|
|..KRK..|
|...K...|
''')
HEART_S = grid('''
|R.R|
|RRR|
|.R.|
''')
Z4 = grid('''
|ZZZZ|
|..Z.|
|.Z..|
|ZZZZ|
''')
Z5 = grid('''
|ZZZZZ|
|...Z.|
|..Z..|
|.Z...|
|ZZZZZ|
''')
BUBBLE_S = grid('''
|BB|
|BB|
''')
BUBBLE_L = grid('''
|.BB.|
|BwBB|
|BBBB|
|.BB.|
''')
SOAP = grid('''
|.ii.|
|iw.i|
|i..i|
|.ii.|
''')
SOAP_S = grid('''
|.i.|
|iwi|
|.i.|
''')
POP = grid('''
|i.i|
|...|
|i.i|
''')
BALL = grid('''
|.KKK.|
|KXwXK|
|KxXxK|
|KXxXK|
|.KKK.|
''')
BALL_B = grid('''
|.KKK.|
|KxXXK|
|KXxwK|
|KXXxK|
|.KKK.|
''')
YARN_TAIL = grid('''
|XX.|
|..X|
''')
BOWL = grid('''
|.KKKKKKKKKK.|
|KoYoYoYoYoYK|
|KCCCCCCCCCCK|
|.KCCwCCCCCK.|
|..KKKKKKKK..|
''')
CRUMBS = grid('''
|o...o|
|..o..|
''')
FISH = grid('''
|.KKKK.K|
|KQwQQKK|
|.KKKK.K|
''')
GOLDFISH = grid('''
|.KKKK.K|
|KYwJYKK|
|.KKKK.K|
''')
BONE = grid('''
|K...K|
|.KKK.|
|K...K|
''')
BUTTERFLY_OPEN = grid('''
|VV.K.VV|
|VgVKVgV|
|.VV.VV.|
''')
BUTTERFLY_SHUT = grid('''
|..VKV..|
|..VKV..|
|...K...|
''')
PUFF = grid('''
|.BBB.|
|BBwBB|
|.B.B.|
''')
BANG = grid('''
|K|
|K|
|K|
|.|
|K|
''')
SWEAT = grid('''
|.T|
|TT|
''')
BOX = grid('''
|KK......................KK|
|KaK....................KaK|
|KKKKKKKKKKKKKKKKKKKKKKKKKK|
|KaaaaaaaaaaaayyaaaaaaaaaaK|
|KAAAAAAAAAAAAyyAAAAAAAAAAK|
|KAAAAAAAAAAAAAAAAAAAAAAAAK|
|KAAAAAAAAAAAAAAAAAAAAAAAAK|
|KAAAAAAAAAAAAAAAAAAAAAAAAK|
|KAAAAAAAAAAAAAAAAAAAAAAAAK|
|KKKKKKKKKKKKKKKKKKKKKKKKKK|
''')
SPEAKER = grid('''
|KKKKKK|
|KddddK|
|KdggdK|
|KddddK|
|KdggdK|
|KgwwgK|
|KdggdK|
|KddddK|
|KKKKKK|
''')
SPEAKER_PUMP = grid('''
|KKKKKK|
|KdggdK|
|KdwgdK|
|KddddK|
|KggggK|
|KgwwgK|
|KggggK|
|KddddK|
|KKKKKK|
''')
LASER = grid('''
|.l.|
|lLl|
|.l.|
''')
BIRD = grid('''
|..KK..|
|.KQKY.|
|KQQQK.|
|.KKK..|
''')
BIRD_FLY = grid('''
|K...K.|
|QK.KQ.|
|.KQKY.|
|..K...|
''')
MOUSE = grid('''
|.PK.....|
|KUUUK...|
|KKUUUKKK|
|.KKKK...|
''')
MOUSE_FLIP = grid('''
|.KKKK...|
|KKUUUKKK|
|KUUUK...|
|.PK.....|
''')
GIFT = grid('''
|.R...R.|
|..RRR..|
|KKKRKKK|
|KQQRQQK|
|KQQRQQK|
|KQQRQQK|
|KKKKKKK|
''')
PURR_L = grid('''
|.K|
|K.|
|.K|
''')
PURR_R = mirror(PURR_L)
GHOST = grid('''
|..KKK..|
|.KhhhK.|
|KhKhKhK|
|KhhhhhK|
|KhPhPhK|
|KhhhhhK|
|KhKhKhK|
|K.K.K.K|
''')
UFO = grid('''
|...KKK...|
|..KiiiK..|
|.KKKKKKK.|
|KgUgUgUgK|
|.KYKYKYK.|
''')
BEAM = grid('''
|..bbbbb..|
|..bbbbb..|
|.bbbbbbb.|
|.bbbbbbb.|
|bbbbbbbbb|
|bbbbbbbbb|
|bbbbbbbbb|
|bbbbbbbbb|
''')
DISCO = grid('''
|..K..|
|.KUK.|
|KUwUK|
|KuUuK|
|.KuK.|
''')
CROWN = grid('''
|J..JJ..J|
|YJ.YY.JY|
|YYJYYJYY|
|YRYYYYQY|
|JJJJJJJJ|
''')
STREAK = grid('''
|Y.....|
|.ww...|
|...ww.|
|.....h|
''')
RAINBOW = grid('''
|.rrrrrr.|
|rOOOOOOr|
|OYYYYYYO|
|YGG..GGY|
|GQ....QG|
|Q......Q|
''')

# ================================================================ accessories (head-local)
ACC_HATS = {
    'bow': (grid('''
|KK...KK|
|KXK.KXK|
|KXXKXXK|
|KXK.KXK|
|KK...KK|
'''), 14, 0),
    'flower': (grid('''
|.f.f.|
|fffff|
|.fYf.|
|fffff|
|.f.f.|
'''), 1, 1),
    'crown': (CROWN, 7, -1),
    'party': (grid('''
|..YY..|
|..KK..|
|.KXVK.|
|.KVXK.|
|KXVXVK|
|KVXVXK|
|KKKKKK|
'''), 8, -3),
    'star': (grid('''
|..Y..|
|.YYY.|
|YYwYY|
|.YYY.|
|.Y.Y.|
'''), 16, 3),
    'sprout': (grid('''
|GG.GG|
|.GnG.|
|..n..|
'''), 8, 0),
    'beanie': (grid('''
|.....KKK....|
|....KwwwK...|
|...KKKKKKK..|
|..KQQQQQQQK.|
|.KQQwQQwQQQK|
|KQwQQwQQwQQK|
|KwKwKwKwKwKK|
|KKKKKKKKKKKK|
'''), 5, -4),
    'santa': (grid('''
|.....KKK....|
|....KRRRK...|
|...KRRRRRKKK|
|..KRRRRRRKwK|
|.KRRRRRRRKKK|
|KwwwwwwwwwwK|
|KwwwwwwwwwwK|
|KKKKKKKKKKKK|
'''), 5, -4),
    'bunny': (grid('''
|...KK....KK...|
|..KwwK..KwwK..|
|..KwPK..KwPK..|
|..KwPK..KwPK..|
|..KwPK..KwPK..|
|.KKKKKKKKKKKK.|
'''), 4, -2),
    'glasses': (grid('''
|KKKKKK..KKKKKK|
|K....KKKK....K|
|K....K..K....K|
|K....K..K....K|
|KKKKKK..KKKKKK|
'''), 4, 7),
}
ACC_NECKS = {
    'medal': (grid('''
|KMMMMMMMMMMMMK|
|.....KKKK.....|
|....KUUwUK....|
|....KUUUuK....|
|.....KKKK.....|
'''), 4, 14),
    'bell': (grid('''
|KQQQQQQQQQQQQK|
|.....KKKK.....|
|....KYYwYK....|
|....KJKKJK....|
|.....KKKK.....|
'''), 4, 14),
    'scarf': (grid('''
|KRRRRRRRRRRRRRRRRK|
|KRwRRwRRwRRwRRwRRK|
|.KKKKKKKKKKKKKKKK.|
|..KRK.............|
|..KRK.............|
|..KwK.............|
|..KKK.............|
'''), 2, 13),
    'bandana': (grid('''
|KQQQQQQQQQQQQK|
|.KQwQQQQQQwQK.|
|..KQQQwQQQQK..|
|...KQQQQQQK...|
|....KQwQQK....|
|.....KKKK.....|
'''), 4, 14),
    'aprilfish': (grid('''
|..KKKK.K.|
|.KQQQQKQK|
|KQwQQQQK.|
|.KQQQQKQK|
|..KKKK.K.|
'''), 16, 15),
    'bowtie': (grid('''
|KK...KK|
|KVKKKVK|
|KVVKVVK|
|KVKKKVK|
|KK...KK|
'''), 7, 13),
}


# ================================================================ canvas
class Canvas:
    def __init__(self):
        self.px = {}  # (x, y) -> (char, is_cat, part, sprite, r, c)

    def blit(self, sprite, x, y, cat=False, part=None):
        for r, line in enumerate(sprite):
            for c, ch in enumerate(line):
                if ch != '.':
                    self.px[(x + c, y + r)] = (ch, cat, part, sprite, r, c)

    def dots(self, points, x, y, cat=True):
        for r, c, ch in points:
            prev = self.px.get((x + c, y + r))
            part = prev[2] if prev else None
            self.px[(x + c, y + r)] = (ch, cat, None, None, r, c)

    def inside(self, x, y):
        return 0 <= x < W and 0 <= y < H

    def layers(self):
        fur, shade, eye, ink = set(), set(), set(), {}
        pm = {p: set() for p in PATTERNS}
        pw = {p: set() for p in PATTERNS}
        for (x, y), (ch, cat, part, sprite, r, c) in self.px.items():
            if not self.inside(x, y):
                continue
            if cat:
                fur.add((x, y))
                if ch in ('W', 'S'):
                    if ch == 'S':
                        shade.add((x, y))
                    for p in PATTERNS:
                        role = role_for(p, part, sprite, r, c)
                        if role == 'm':
                            pm[p].add((x, y))
                        elif role == 'w':
                            pw[p].add((x, y))
                    continue
                if ch == 'E':
                    eye.add((x, y))
                    continue
            if ch == 'E':
                eye.add((x, y))
                continue
            ink.setdefault(INK[ch], set()).add((x, y))
        return fur, shade, eye, ink, pm, pw

    def full_colour(self, pattern='CLASSIC'):
        by = {}
        for (x, y), (ch, cat, part, sprite, r, c) in self.px.items():
            if not self.inside(x, y):
                continue
            col = INK[ch]
            if cat and ch in ('W', 'S'):
                role = role_for(pattern, part, sprite, r, c)
                if role == 'm':
                    col = '#FFF7B27A'
                elif role == 'w':
                    col = '#FFFFFFFF'
            by.setdefault(col, set()).add((x, y))
        return by


def runs(points):
    out, rows = [], {}
    for x, y in points:
        rows.setdefault(y, []).append(x)
    for y in sorted(rows):
        xs = sorted(rows[y])
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            out.append(f"M{start},{y}h{prev - start + 1}v1h{-(prev - start + 1)}z")
            if x is not None:
                start = prev = x
    return "".join(out)


def vector(paths, w=W, h=H):
    if not paths:
        paths = [('#00000000', 'M0,0h1v1h-1z')]
    body = "\n".join(f'    <path android:fillColor="{c}" android:pathData="{d}" />' for c, d in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w * 6}dp" android:height="{h * 6}dp"\n'
            f'    android:viewportWidth="{w}" android:viewportHeight="{h}">\n'
            f'{body}\n</vector>\n')


written = set()


def write(name, text):
    written.add(name)
    with open(os.path.join(RES_DIR, name + ".xml"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


# ================================================================ cat builder
class Frame:
    def __init__(self, canvas, hx, hy, pose, hat_ok, neck_ok):
        self.canvas, self.hx, self.hy, self.pose, self.hat_ok, self.neck_ok = canvas, hx, hy, pose, hat_ok, neck_ok


def cat(face_parts, dx=0, dy=0, pose='sit', tail=None, phones=False, lift=0, extras=(), front=(), hat=True, neck=True,
        bx=0, behind=()):
    """bx shifts the whole cat sideways (sway); behind = props drawn before the cat."""
    c = Canvas()
    for fn in behind:
        fn(c, 0, 0)
    by = -lift
    if pose == 'sit':
        c.blit(tail or TAIL, 23 + OX + bx, 20 + by, cat=True, part='tail')
        c.blit(BODY, 8 + OX + bx, 21 + by, cat=True, part='body')
        hx, hy = 5 + OX + dx + bx, 8 + dy + by
    else:
        c.blit(LOAF, 1 + OX + bx, 20, cat=True, part='loaf')
        hx, hy = 5 + OX + dx + bx, 10 + dy
    for fn in extras:
        fn(c, hx, hy)
    c.blit(HEAD, hx, hy, cat=True, part='head')
    c.dots(face_parts, hx, hy)
    if pose == 'loaf':
        c.blit(tail or TAIL_LOAF, 20 + OX + bx, 25, cat=True, part='tail')
    if phones:
        c.blit(HEADPHONES, hx - 2, hy - 3)
    for fn in front:
        fn(c, hx, hy)
    return Frame(c, hx, hy, pose, hat and not phones, neck)


def at(sprite, x, y, rel=False, catpx=False, part=None):
    def f(c, hx, hy):
        c.blit(sprite, (hx + x) if rel else x, (hy + y) if rel else y, cat=catpx, part=part)
    return f


def paw(sprite, x, y, rel=False):
    return at(sprite, x, y, rel=rel, catpx=True, part='paw')


def dots_rel(points):
    def f(c, hx, hy):
        c.dots(points, hx, hy)
    return f


F_OPEN = face(eyes_open(), NOSE, OMEGA, BLUSH)
F_BLINK = face(EYES_DASH, NOSE, OMEGA, BLUSH)
F_HAPPY = face(EYES_HAPPY, OPEN, BLUSH)
F_SQUEE = face(EYES_SQUEE, NOSE, OMEGA, BLUSH_BIG)
F_SLEEP = face(EYES_DASH, NOSE, SMALL, BLUSH)
F_BOOP = face(EYES_SQUEE, OPEN, BLUSH_BIG)
F_SAD = face(eyes_open(), [(10, 6, 'T'), (10, 15, 'T')], NOSE, FROWN, BLUSH)
F_WONDER = face(eyes_big(), NOSE, OOO, BLUSH)
TEAR_1 = [(11, 6, 'T'), (12, 6, 'T')]
TEAR_2 = [(12, 6, 'T'), (13, 6, 'T'), (11, 15, 'T'), (12, 15, 'T')]
SPK = lambda pump: at(SPEAKER_PUMP if pump else SPEAKER, 39, 18)
WIN = lambda sprite, x, y: at(sprite, 2 + x, 3 + y)  # inside the window (8x8 sky)

frames = {
    # ---- dancing (3 styles)
    'dance_bob_1': cat(F_HAPPY, -1, 0, phones=True, extras=[SPK(True)], front=[at(recolor(BEAMED, 'X', 'X'), 38, 7), at(TWINKLE, 2, 19)]),
    'dance_bob_2': cat(F_SQUEE, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(SPARKLE, 1, 15), at(recolor(NOTE, 'X', 'V'), 40, 10)]),
    'dance_bob_3': cat(F_HAPPY, 1, 0, phones=True, extras=[SPK(True)], front=[at(recolor(BEAMED, 'X', 'V'), 0, 14), at(NOTE, 41, 6)]),
    'dance_bob_4': cat(F_SQUEE, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(SPARKLE, 36, 3), at(HEART, 1, 15)]),
    'dance_bounce_1': cat(F_SQUEE, 0, 0, phones=True, lift=2, extras=[SPK(True)], front=[at(NOTE, 40, 8), at(TWINKLE, 2, 19)]),
    'dance_bounce_2': cat(F_HAPPY, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(recolor(BEAMED, 'X', 'V'), 0, 14)]),
    'dance_bounce_3': cat(face(EYES_HEART, OPEN, BLUSH_BIG), 0, 0, phones=True, lift=2, extras=[SPK(True)], front=[at(HEART, 38, 6), at(SPARKLE, 1, 16)]),
    'dance_bounce_4': cat(F_HAPPY, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(recolor(NOTE, 'X', 'V'), 41, 9)]),
    'dance_paws_1': cat(F_HAPPY, -1, 0, phones=True, extras=[SPK(True)], front=[paw(PAW_UP, -5, 1, rel=True), at(NOTE, 40, 8)]),
    'dance_paws_2': cat(F_SQUEE, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(SPARKLE, 1, 15)]),
    'dance_paws_3': cat(F_HAPPY, 1, 0, phones=True, extras=[SPK(True)], front=[paw(PAW_UP, 23, 1, rel=True), at(recolor(BEAMED, 'X', 'V'), 0, 14)]),
    'dance_paws_4': cat(F_SQUEE, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(HEART, 1, 15)]),

    # ---- idle
    'idle_open': cat(F_OPEN),
    'idle_blink': cat(F_BLINK),
    'idle_happy': cat(F_HAPPY, tail=TAIL_B, front=[at(TWINKLE, 3, 17), at(TWINKLE, 39, 5)]),
    'idle_look_l': cat(face(eyes_open(-1), NOSE, OMEGA, BLUSH), -1),
    'idle_look_r': cat(face(eyes_open(1), NOSE, OMEGA, BLUSH), 1, tail=TAIL_B),

    'wave_1': cat(F_HAPPY, front=[paw(PAW_UP, 21, 6, rel=True), at(TWINKLE, 39, 4)]),
    'wave_2': cat(F_HAPPY, 0, 0, tail=TAIL_B, front=[paw(PAW_UP, 22, 4, rel=True), at(SPARKLE, 38, 2)]),

    'yawn_1': cat(face(EYES_HALF, NOSE, SMALL, BLUSH), 0, 1),
    'yawn_2': cat(face(EYES_SQUEE, BIG, BLUSH), 0, 0, front=[at(SWEAT, 0, 4, rel=True)]),
    'yawn_3': cat(face(EYES_DASH, BIG, BLUSH), 0, -1),

    'groom_1': cat(face(EYES_HAPPY, NOSE, BLUSH), neck=False, front=[paw(PAW_UP, 12, 11, rel=True), dots_rel([(12, 11, 'P')])]),
    'groom_2': cat(face(EYES_DASH, NOSE, BLUSH), 0, 1, tail=TAIL_B, neck=False, front=[paw(PAW_UP, 12, 10, rel=True), dots_rel([(11, 11, 'P'), (12, 11, 'P')]), at(TWINKLE, 3, 17)]),

    'eat_1': cat(F_HAPPY, 0, 3, neck=False, front=[at(BOWL, 18, 23)]),
    'eat_2': cat(face(EYES_DASH, NOSE, SMALL, BLUSH_BIG), 0, 2, tail=TAIL_B, neck=False, front=[at(BOWL, 18, 23), at(CRUMBS, 32, 21)]),
    'eat_3': cat(face(EYES_SPARK, NOSE, TONGUE, BLUSH_BIG), 0, 0, neck=False, front=[at(BOWL, 18, 23), at(SPARKLE, 1, 15)]),

    'play_1': cat(face(eyes_open(-1, 1), NOSE, OMEGA, BLUSH), -1, 1, front=[at(BALL, 2, 23), at(YARN_TAIL, 7, 26)]),
    'play_2': cat(face(EYES_SQUEE, OPEN, BLUSH), -1, 1, front=[paw(PAW_SIDE_L, 12, 24), at(BALL_B, 5, 22), at(BANG, 9, 1, rel=True)]),
    'play_3': cat(face(eyes_open(1, 1), NOSE, OMEGA, BLUSH), 1, 1, tail=TAIL_B, front=[at(BALL_B, 39, 23), at(YARN_TAIL, 37, 26)]),
    'play_4': cat(face(EYES_SQUEE, OPEN, BLUSH), 1, 1, tail=TAIL_B, front=[paw(PAW_SIDE_R, 32, 24), at(BALL, 36, 22)]),

    'fly_1': cat(face(eyes_open(-1, -1), NOSE, SMALL, BLUSH), -1, front=[at(BUTTERFLY_OPEN, 1, 15)]),
    'fly_2': cat(face(eyes_open(0, -1), NOSE, SMALL, BLUSH), 0, front=[at(BUTTERFLY_SHUT, 20, 1)]),
    'fly_3': cat(face(eyes_open(1, -1), NOSE, OMEGA, BLUSH), 1, tail=TAIL_B, front=[at(BUTTERFLY_OPEN, 36, 3)]),
    'fly_4': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), 0, 0, hat=False, front=[at(BUTTERFLY_SHUT, 8, -3, rel=True), at(HEART_S, 1, 4, rel=True)]),

    'box_peek': cat(face(eyes_open(), BLUSH), 0, 2, neck=False, front=[at(BOX, 11, 19)]),
    'box_blink': cat(face(EYES_DASH, BLUSH), 0, 2, neck=False, front=[at(BOX, 11, 19)]),
    'box_look': cat(face(eyes_open(1), BLUSH), 1, 2, neck=False, front=[at(BOX, 11, 19)]),
    'box_hide': cat(F_OPEN, 0, 9, neck=False, front=[at(BOX, 11, 19), at(TWINKLE, 40, 14)]),

    'loaf_open': cat(F_OPEN, pose='loaf'),
    'loaf_blink': cat(F_BLINK, pose='loaf'),
    'loaf_happy': cat(F_HAPPY, pose='loaf', tail=TAIL_LOAF_B, front=[at(HEART_S, 39, 10)]),
    'doze_1': cat(face(EYES_HALF, NOSE, SMALL, BLUSH), pose='loaf'),
    'doze_2': cat(F_SLEEP, 0, 1, pose='loaf', front=[at(Z4, 38, 10)]),

    'sneeze_1': cat(face(EYES_HALF, NOSE_WIGGLE, SMALL, BLUSH), 0, 0),
    'sneeze_2': cat(face(EYES_SQUEE, NOSE, SMALL, BLUSH), 0, -1, front=[at(BANG, 23, -2, rel=True)]),
    'sneeze_3': cat(face(EYES_SQUEE, OPEN, BLUSH), 0, 1, tail=TAIL_B, front=[at(PUFF, 8, 13, rel=True), at(PUFF, 13, 14, rel=True)]),
    'sneeze_4': cat(face(EYES_X, NOSE, SMALL, BLUSH), 0, 0, front=[at(TWINKLE, 3, 4, rel=True), at(TWINKLE, 18, 3, rel=True)]),

    'love_1': cat(face(EYES_HEART, OPEN, BLUSH_BIG), front=[at(HEART, 1, 15), at(HEART_S, 39, 3)]),
    'love_2': cat(face(EYES_HEART, NOSE, OMEGA, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[at(HEART, 36, 2), at(HEART_S, 3, 16)]),

    'treat_1': cat(face(EYES_SPARK, BLUSH_BIG), front=[at(FISH, 8, 11, rel=True), at(SPARKLE, 38, 4)]),
    'treat_2': cat(face(EYES_HAPPY, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[at(FISH, 8, 11, rel=True)]),
    'treat_3': cat(face(EYES_HAPPY, NOSE, TONGUE, BLUSH_BIG), front=[at(BONE, 36, 24), at(TWINKLE, 3, 17)]),

    # ---- new everyday moments
    'laser_1': cat(face(eyes_open(-1, 1), NOSE, SMALL, BLUSH), -1, 1, front=[at(LASER, 3, 25)]),
    'laser_2': cat(face(eyes_open(-1, -1), NOSE, SMALL, BLUSH), -1, 0, front=[at(LASER, 6, 16)]),
    'laser_3': cat(face(eyes_open(1, 1), NOSE, SMALL, BLUSH), 1, 1, tail=TAIL_B, front=[at(LASER, 41, 25)]),
    'laser_4': cat(face(EYES_SQUEE, OPEN, BLUSH), 0, 0, lift=2, front=[at(LASER, 22, 30), at(BANG, 23, -1, rel=True)]),
    'bird_1': cat(face(eyes_open(-1, -1), NOSE, OPEN, BLUSH), -1, front=[WIN(BIRD, 1, 3)]),
    'bird_2': cat(face(eyes_open(-1, -1), NOSE, SMALL, BLUSH), -1, 0, tail=TAIL_B, front=[WIN(BIRD, 2, 2)]),
    'bird_3': cat(face(eyes_open(-1, -2), NOSE, OOO, BLUSH), -1, -1, front=[WIN(BIRD_FLY, 2, 0)]),
    'knead_1': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH), front=[paw(KPAW, 18, 24), paw(KPAW, 26, 26), at(PURR_L, -3, 9, rel=True)]),
    'knead_2': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH), 0, 1, tail=TAIL_B, front=[paw(KPAW, 18, 26), paw(KPAW, 26, 24), at(PURR_R, 23, 9, rel=True)]),
    'purr_1': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH_BIG), front=[at(PURR_L, -3, 8, rel=True), at(PURR_R, 23, 10, rel=True), at(HEART_S, 39, 8)]),
    'purr_2': cat(face(EYES_DASH, NOSE, OMEGA, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[at(PURR_L, -3, 10, rel=True), at(PURR_R, 23, 8, rel=True), at(HEART_S, 3, 17)]),
    'bubbles_1': cat(face(eyes_open(0, -1), NOSE, OOO, BLUSH), front=[at(SOAP, 4, 16), at(SOAP_S, 38, 12), at(SOAP_S, 33, 4)]),
    'bubbles_2': cat(face(eyes_open(1, -1), NOSE, OOO, BLUSH), 1, 0, tail=TAIL_B, front=[at(SOAP, 5, 12), at(SOAP, 37, 7), at(SOAP_S, 3, 22)]),
    'bubbles_3': cat(face(EYES_SQUEE, OPEN, BLUSH_BIG), 1, -1, front=[paw(PAW_UP, 22, 1, rel=True), at(POP, 36, 4), at(SPARKLE, 38, 9)]),
    'mouse_1': cat(face(eyes_open(1, 1), NOSE, SMALL, BLUSH), 1, 1, tail=TAIL_B, front=[at(MOUSE, 37, 24)]),
    'mouse_2': cat(face(EYES_SQUEE, NOSE, SMALL, BLUSH), 1, 2, front=[at(MOUSE, 37, 24), at(SWEAT, 22, 3, rel=True)]),
    'mouse_3': cat(face(EYES_SQUEE, OPEN, BLUSH_BIG), 1, 0, lift=2, front=[paw(PAW_SIDE_R, 32, 22), at(MOUSE_FLIP, 37, 23), at(BANG, 23, -1, rel=True)]),
    'gift_1': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), front=[at(GIFT, 36, 21), at(HEART_S, 39, 15)]),
    'gift_2': cat(face(EYES_SPARK, NOSE, OMEGA, BLUSH_BIG), 1, 0, tail=TAIL_B, front=[paw(PAW_SIDE_R, 32, 24), at(GIFT, 36, 21), at(SPARKLE, 38, 12)]),
    'hum_1': cat(face(EYES_DASH, NOSE, OOO, BLUSH), front=[at(recolor(NOTE, 'X', 'V'), 38, 9)]),
    'hum_2': cat(face(EYES_HAPPY, NOSE, SMALL, BLUSH), 0, 1, tail=TAIL_B, front=[at(NOTE, 40, 4), at(TWINKLE, 37, 15)]),
    'stargaze_1': cat(face(eyes_open(-1, -1), NOSE, SMALL, BLUSH), -1, front=[WIN(TWINKLE, 1, 1)]),
    'stargaze_2': cat(face(eyes_open(-1, -1), NOSE, OMEGA, BLUSH), -1, 0, tail=TAIL_B, front=[WIN(TWINKLE, 4, 3), WIN(SPARKLE, 0, 0)]),
    'nod_1': cat(face(EYES_HALF, NOSE, SMALL, BLUSH), 0, 0),
    'nod_2': cat(F_SLEEP, 0, 2, front=[at(Z4, 38, 12)]),

    # ---- rare moments
    'rainbow_1': cat(face(eyes_big(-1, 0), NOSE, OOO, BLUSH), -1, front=[WIN(RAINBOW, 0, 1), at(SPARKLE, 13, 2), at(TWINKLE, 38, 6)]),
    'rainbow_2': cat(face(EYES_SPARK, OPEN, BLUSH_BIG), -1, 0, tail=TAIL_B, front=[WIN(RAINBOW, 0, 1), at(TWINKLE, 13, 4), at(SPARKLE, 37, 3), at(HEART_S, 40, 12)]),
    'star_1': cat(face(eyes_big(-1, 0), NOSE, OOO, BLUSH), -1, front=[WIN(STREAK, 0, 0)]),
    'star_2': cat(face(EYES_SPARK, NOSE, OOO, BLUSH), -1, 0, front=[WIN(STREAK, 2, 3)]),
    'star_3': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[at(HEART, 37, 4), at(SPARKLE, 1, 16), at(TWINKLE, 13, 2)]),
    'ufo_1': cat(face(EYES_WIDE, NOSE, OOO, BLUSH), 1, 0, front=[at(UFO, 33, 0), at(BEAM, 33, 5), at(BALL, 35, 23)]),
    'ufo_2': cat(face(EYES_WIDE, NOSE, OOO, BLUSH), 1, -1, front=[at(UFO, 34, 0), at(BEAM, 34, 5), at(BALL, 36, 15), at(BANG, 23, -2, rel=True)]),
    'ufo_3': cat(face(EYES_X, NOSE, SMALL, BLUSH), 1, 0, tail=TAIL_B, front=[at(UFO, 35, 0), at(BALL_B, 37, 7), at(TWINKLE, 30, 2)]),
    'ghost_1': cat(face(eyes_open(1), NOSE, SMALL, BLUSH), 1, front=[at(GHOST, 37, 9)]),
    'ghost_2': cat(face(EYES_WIDE, NOSE, OOO, BLUSH), 0, 0, lift=2, front=[at(GHOST, 37, 7), at(BANG, 23, -2, rel=True), at(SWEAT, 0, 3, rel=True)]),
    'ghost_3': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), 1, 1, tail=TAIL_B, front=[at(GHOST, 37, 10), at(HEART_S, 39, 4)]),
    'kitten_1': cat(face(eyes_open(1, 1), NOSE, OMEGA, BLUSH), 1, 1, front=[at(KITTEN, 37, 19, catpx=True, part='kitten')]),
    'kitten_2': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), 1, 1, tail=TAIL_B, front=[at(KITTEN_BLINK, 37, 19, catpx=True, part='kitten'), at(HEART_S, 39, 15)]),
    'kitten_3': cat(face(EYES_HEART, NOSE, OMEGA, BLUSH_BIG), 1, 0, front=[at(KITTEN, 37, 18, catpx=True, part='kitten'), at(HEART, 36, 8), at(HEART_S, 3, 17)]),
    'goldfish_1': cat(face(EYES_SPARK, BLUSH_BIG), front=[at(GOLDFISH, 8, 11, rel=True), at(SPARKLE, 38, 4), at(SPARKLE, 1, 15), at(TWINKLE, 36, 15)]),
    'goldfish_2': cat(face(EYES_HAPPY, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[at(GOLDFISH, 8, 11, rel=True), at(SPARKLE, 37, 8), at(TWINKLE, 3, 18), at(TWINKLE, 40, 2)]),
    'royal_1': cat(face(EYES_SPARK, NOSE, OMEGA, BLUSH), 0, 0, hat=False, front=[at(CROWN, 7, -1, rel=True), at(SPARKLE, 38, 4), at(TWINKLE, 2, 17)]),
    'royal_2': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH_BIG), 0, 1, tail=TAIL_B, hat=False, front=[at(CROWN, 7, -1, rel=True), at(SPARKLE, 1, 15), at(TWINKLE, 39, 8)]),
    'disco_1': cat(F_HAPPY, -1, 0, lift=1, front=[at(DISCO, 20, 0), at(['L'], 4, 18), at(['Y'], 40, 10), at(['Q'], 36, 26), at(['p'], 14, 4)]),
    'disco_2': cat(F_SQUEE, 1, 1, tail=TAIL_B, front=[at(DISCO, 20, 0), at(['G'], 6, 24), at(['L'], 38, 16), at(['Y'], 30, 3), at(['Q'], 2, 15)]),
    'disco_3': cat(face(EYES_HEART, OPEN, BLUSH_BIG), 0, 0, lift=2, front=[at(DISCO, 20, 0), at(['p'], 5, 20), at(['G'], 41, 22), at(['Y'], 12, 2), at(['L'], 33, 8)]),

    # ---- sad / sleep / boop
    'sad_1': cat(F_SAD, 0, 1, front=[dots_rel(TEAR_1)]),
    'sad_2': cat(F_SAD, 0, 1, front=[dots_rel(TEAR_2)]),
    'sad_look': cat(face(eyes_open(-1, -1), NOSE, FROWN, BLUSH), -1, 1),
    'sleep_1': cat(F_SLEEP, 0, 1, pose='loaf', front=[at(BUBBLE_S, 13, 11, rel=True), at(Z4, 38, 12)]),
    'sleep_2': cat(F_SLEEP, 0, 1, pose='loaf', front=[at(BUBBLE_L, 13, 11, rel=True), at(Z4, 38, 12), at(Z5, 40, 4)]),
    'sleep_3': cat(F_SLEEP, 0, 1, pose='loaf', tail=TAIL_LOAF_B, front=[at(BUBBLE_S, 13, 11, rel=True), at(Z5, 40, 4)]),
    'boop_1': cat(F_BOOP, 0, 1, front=[at(HEART, 1, 15), at(TWINKLE, 39, 6)]),
    'boop_2': cat(F_BOOP, 0, 0, tail=TAIL_B, front=[at(HEART, 36, 1), at(TWINKLE, 3, 18)]),
}


# ================================================================ v4: more dances, sad moods, exclusives, cameo
GUITAR = grid('''
|........KK|
|.......KaK|
|..KKK.KaK.|
|.KXXXKaK..|
|KXXwXXK...|
|KXXXXXK...|
|.KKKKK....|
''')
TURNTABLE = grid('''
|KKKKKKKKKKKKKK|
|KdkkkkdddddgdK|
|KkkXkkdddddddK|
|KdkkkkddgggddK|
|KKKKKKKKKKKKKK|
''')
PH_SMALL = grid('''
|.KKKKKK.|
|KHHHHHHK|
|KHK..KHK|
|KCK..KCK|
|KCK..KCK|
|.K....K.|
''')
QUESTION = grid('''
|.KK.|
|K..K|
|...K|
|..K.|
|....|
|..K.|
''')
CRATE = grid('''
|.kk..XX..VV..kk.|
|.kk..XX..VV..kk.|
|KKKKKKKKKKKKKKKK|
|KaaaaaaaaaaaaaaK|
|KAAAAAAAAAAAAAAK|
|KAAAAAAAAAAAAAAK|
|KKKKKKKKKKKKKKKK|
''')
VINYL = grid('''
|.kkkk.|
|kkkkkk|
|kkXXkk|
|kkXwkk|
|kkkkkk|
|.kkkk.|
''')
CLIPBOARD = grid('''
|..KK..|
|KKUUKK|
|KwwwwK|
|KwKKwK|
|KwwwwK|
|KwKKwK|
|KwwwwK|
|KKKKKK|
''')
LIGHTSTICK = grid('''
|YY|
|Yw|
|YY|
|KK|
|Ka|
|Ka|
|KK|
''')
LOGO = grid('''
|..k....k..|
|.kkk..kkk.|
|HkkkkkkkkH|
|CkkkkkkkkC|
|CkwwkkwwkC|
|CkkkkkkkkC|
|.kkkkkkkk.|
|..kkkkkk..|
|.kkkkkkkk.|
|.kk.kk.kk.|
''')
GREY_NOTE = recolor(NOTE, 'X', 'u')
GLASSES = ACC_HATS['glasses'][0]
SIGH = grid('''
|.BB|
|B..|
''')

frames.update({
    # more dances
    'dance_hb_1': cat(face(EYES_DASH, OPEN, BLUSH_BIG), 0, 0, phones=True, extras=[SPK(True)], front=[at(NOTE, 40, 8), at(TWINKLE, 2, 19)]),
    'dance_hb_2': cat(face(EYES_SQUEE, OPEN, BLUSH_BIG), 0, 3, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(SPARKLE, 1, 15)]),
    'dance_hb_3': cat(face(EYES_DASH, BIG, BLUSH_BIG), 0, 0, phones=True, extras=[SPK(True)], front=[at(recolor(BEAMED, 'X', 'V'), 0, 14)]),
    'dance_hb_4': cat(face(EYES_SQUEE, OPEN, BLUSH_BIG), 0, 3, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(HEART, 38, 4)]),
    'dance_sway_1': cat(F_HAPPY, -1, 0, phones=True, bx=-2, extras=[SPK(True)], front=[at(NOTE, 40, 7)]),
    'dance_sway_2': cat(F_SQUEE, 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(SPARKLE, 1, 15)]),
    'dance_sway_3': cat(F_HAPPY, 1, 0, phones=True, bx=2, extras=[SPK(True)], front=[at(recolor(BEAMED, 'X', 'V'), 0, 14)]),
    'dance_sway_4': cat(face(EYES_HEART, OPEN, BLUSH_BIG), 0, 1, phones=True, tail=TAIL_B, extras=[SPK(False)], front=[at(HEART_S, 40, 6)]),
    'dance_guitar_1': cat(F_SQUEE, -1, 0, phones=True, neck=False, extras=[SPK(True)], front=[at(GUITAR, 14, 19), paw(PAW_SIDE_R, 22, 22), at(NOTE, 40, 8)]),
    'dance_guitar_2': cat(face(EYES_DASH, BIG, BLUSH_BIG), -1, 1, phones=True, neck=False, tail=TAIL_B, extras=[SPK(False)], front=[at(GUITAR, 14, 19), paw(PAW_SIDE_R, 22, 24), at(SPARKLE, 1, 15)]),
    'dance_guitar_3': cat(F_SQUEE, 1, 0, phones=True, neck=False, extras=[SPK(True)], front=[at(GUITAR, 14, 19), paw(PAW_SIDE_R, 22, 22), at(recolor(BEAMED, 'X', 'V'), 0, 12)]),
    'dance_guitar_4': cat(face(EYES_DASH, BIG, BLUSH_BIG), 1, 1, phones=True, neck=False, tail=TAIL_B, extras=[SPK(False)], front=[at(GUITAR, 14, 19), paw(PAW_SIDE_R, 22, 24), at(HEART, 38, 3)]),
    'dance_dj_1': cat(F_HAPPY, -1, 0, phones=True, neck=False, front=[at(TURNTABLE, 16, 25), paw(PAW_SIDE_L, 19, 24), at(NOTE, 40, 8)]),
    'dance_dj_2': cat(F_SQUEE, 0, 1, phones=True, neck=False, tail=TAIL_B, front=[at(TURNTABLE, 16, 25), paw(PAW_SIDE_L, 22, 24), at(SPARKLE, 1, 15)]),
    'dance_dj_3': cat(F_HAPPY, 1, 0, phones=True, neck=False, front=[at(TURNTABLE, 16, 25), paw(PAW_SIDE_L, 19, 24), at(recolor(BEAMED, 'X', 'V'), 0, 12)]),
    'dance_dj_4': cat(face(EYES_SPARK, OPEN, BLUSH_BIG), 0, 1, phones=True, neck=False, tail=TAIL_B, front=[at(TURNTABLE, 16, 25), paw(PAW_SIDE_L, 22, 24), at(HEART, 38, 3)]),

    # boop while dancing: a quick, direct squee with the headphones kept on
    'boop_dance_1': cat(F_BOOP, 0, 1, phones=True, front=[at(HEART, 1, 15), at(TWINKLE, 40, 8)]),
    'boop_dance_2': cat(F_BOOP, 0, 0, phones=True, tail=TAIL_B, front=[at(HEART, 37, 6), at(TWINKLE, 3, 19)]),

    # sad moods
    'sadwin_1': cat(face(eyes_open(-1, -1), [(10, 5, 'T')], NOSE, FROWN, BLUSH), -1, 1),
    'sadwin_2': cat(face(eyes_open(-1, -1), [(10, 5, 'T'), (11, 5, 'T')], NOSE, FROWN, BLUSH), -1, 2, front=[at(SIGH, 14, 11, rel=True)]),
    'sadphones_1': cat(F_SAD, 0, 1, neck=False, front=[at(PH_SMALL, 20, 21), dots_rel(TEAR_1)]),
    'sadphones_2': cat(face(EYES_DASH, NOSE, FROWN, BLUSH), 0, 2, neck=False, tail=TAIL_B, front=[at(PH_SMALL, 20, 21), dots_rel(TEAR_2)]),
    'sadloaf_1': cat(F_SAD, 0, 1, pose='loaf', front=[dots_rel(TEAR_1)]),
    'sadloaf_2': cat(face(EYES_DASH, NOSE, FROWN, BLUSH), 0, 2, pose='loaf', tail=TAIL_LOAF_B, front=[dots_rel(TEAR_2)]),
    'sadcall_1': cat(face(eyes_open(0, -1), NOSE, OOO, BLUSH), 0, 0, front=[paw(PAW_UP, 21, 5, rel=True), at(QUESTION, 21, -6, rel=True)]),
    'sadcall_2': cat(F_SAD, 0, 1, tail=TAIL_B, front=[at(GREY_NOTE, 39, 7), dots_rel(TEAR_1)]),

    # KittyTune cameo: the logo cat sneaks behind
    'cameo_1': cat(F_OPEN, behind=[at(LOGO, 40, 16)]),
    'cameo_2': cat(face(eyes_open(1), NOSE, OMEGA, BLUSH), 0, 0, behind=[at(LOGO, 33, 16)]),
    'cameo_3': cat(face(eyes_open(1), NOSE, SMALL, BLUSH), 1, 0, behind=[at(LOGO, 24, 15)], front=[at(SWEAT, 0, 4, rel=True)]),
    'cameo_4': cat(face(eyes_open(1), NOSE, SMALL, BLUSH), 1, 0, front=[at(QUESTION, 19, -6, rel=True)]),
    'cameo_5': cat(face(eyes_open(1), NOSE, SMALL, BLUSH), 1, 0, behind=[at(LOGO, -4, 17)], front=[at(QUESTION, 19, -6, rel=True)]),
    'cameo_6': cat(face(eyes_open(-1), NOSE, OOO, BLUSH), -1, 0, front=[at(QUESTION, 0, -6, rel=True)]),

    # exclusives: digs through your library, judges your likes, cheers for your top artist
    'dig_1': cat(face(EYES_DASH, NOSE, SMALL, BLUSH), 0, 4, neck=False, front=[at(CRATE, 15, 21)]),
    'dig_2': cat(face(eyes_open(0, 1), NOSE, SMALL, BLUSH), 1, 3, neck=False, tail=TAIL_B, front=[at(CRATE, 15, 21), at(TWINKLE, 39, 12)]),
    'dig_3': cat(face(EYES_SPARK, OPEN, BLUSH_BIG), 0, 0, neck=False, front=[at(CRATE, 15, 21), paw(PAW_UP, 21, 2, rel=True), at(VINYL, 34, 3), at(SPARKLE, 41, 0)]),
    'dig_4': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), 0, 1, neck=False, tail=TAIL_B, front=[at(CRATE, 15, 21), paw(PAW_UP, 21, 3, rel=True), at(VINYL, 34, 4), at(HEART_S, 41, 13)]),
    'judge_1': cat(face(EYES_HALF, NOSE, SMALL, BLUSH), 0, 0, hat=False, front=[at(GLASSES, 4, 7, rel=True), at(CLIPBOARD, 36, 17), paw(PAW_SIDE_R, 33, 21)]),
    'judge_2': cat(face(eyes_open(1, 1), NOSE, SMALL, BLUSH), 1, 0, hat=False, front=[at(GLASSES, 4, 7, rel=True), at(CLIPBOARD, 36, 17), paw(PAW_SIDE_R, 33, 21)]),
    'judge_3': cat(face(EYES_HALF, NOSE, OMEGA, BLUSH), 0, 1, hat=False, tail=TAIL_B, front=[at(GLASSES, 4, 7, rel=True), at(CLIPBOARD, 36, 17), paw(PAW_SIDE_R, 33, 21), at(BANG, 23, -1, rel=True)]),
    'fan_1': cat(F_HAPPY, 1, 0, front=[paw(PAW_UP, 22, 3, rel=True), at(LIGHTSTICK, 37, 4), at(HEART, 1, 15)]),
    'fan_2': cat(face(EYES_HEART, OPEN, BLUSH_BIG), 0, 1, tail=TAIL_B, front=[paw(PAW_UP, 22, 2, rel=True), at(LIGHTSTICK, 36, 2), at(HEART_S, 3, 17), at(SPARKLE, 40, 12)]),
})


# ================================================================ v5: "hello" for the new-feature announcement
HI_BUBBLE = grid('''
|.KKKKKKKKKKK.|
|KwwwwwwwwwwwK|
|KwKwKwKKKwRwK|
|KwKKKwwKwwRwK|
|KwKwKwKKKwwwK|
|KwwwwwwwwwRwK|
|.KKKKKKKwKKK.|
|......KwK....|
|.....KK......|
''')
frames.update({
    'hello_1': cat(F_HAPPY, front=[paw(PAW_UP, 21, 6, rel=True), at(HI_BUBBLE, 31, 0), at(TWINKLE, 3, 17)]),
    'hello_2': cat(F_HAPPY, 0, 0, tail=TAIL_B, front=[paw(PAW_UP, 22, 4, rel=True), at(HI_BUBBLE, 31, 0), at(SPARKLE, 1, 15)]),
    'hello_3': cat(face(EYES_HAPPY, OPEN, BLUSH_BIG), 0, 1, front=[at(HEART, 36, 2), at(SPARKLE, 1, 15), at(TWINKLE, 41, 12)]),
})


# ================================================================ Ivy easter egg: floating in space
STARDUST = grid('''
|Y.w|
|.Y.|
''')
frames.update({
    'ivy_float_1': cat(face(EYES_SPARK, NOSE, OMEGA, BLUSH), 0, 0, lift=2, front=[at(SPARKLE, 1, 14), at(STARDUST, 40, 18), at(TWINKLE, 13, 2)]),
    'ivy_float_2': cat(F_HAPPY, 0, 1, lift=3, tail=TAIL_B, front=[at(TWINKLE, 3, 20), at(SPARKLE, 38, 10), at(STARDUST, 12, 4)]),
    'ivy_float_3': cat(face(EYES_HEART, OPEN, BLUSH_BIG), 0, 0, lift=4, front=[at(HEART, 37, 6), at(STARDUST, 2, 15), at(TWINKLE, 41, 20)]),
})


# ================================================================ v6: New Year countdown & fireworks
DIGITS = {
    '0': ["XXX", "X.X", "X.X", "X.X", "XXX"], '1': [".X.", "XX.", ".X.", ".X.", "XXX"],
    '2': ["XXX", "..X", "XXX", "X..", "XXX"], '3': ["XXX", "..X", ".XX", "..X", "XXX"],
    '4': ["X.X", "X.X", "XXX", "..X", "..X"], '5': ["XXX", "X..", "XXX", "..X", "XXX"],
    '6': ["XXX", "X..", "XXX", "X.X", "XXX"], '7': ["XXX", "..X", ".X.", ".X.", ".X."],
    '8': ["XXX", "X.X", "XXX", "X.X", "XXX"], '9': ["XXX", "X.X", "XXX", "..X", "XXX"],
}


def number_sign(n):
    """11x9 white board with the number in the middle (red for the last three seconds)."""
    rows = [list("KKKKKKKKKKK")] + [list("KwwwwwwwwwK") for _ in range(7)] + [list("KKKKKKKKKKK")]
    text = str(n)
    ink = 'R' if n <= 3 else 'K'
    width = len(text) * 3 + (len(text) - 1)
    x0 = (11 - width) // 2
    for i, ch in enumerate(text):
        for r, line in enumerate(DIGITS[ch]):
            for c, px in enumerate(line):
                if px == 'X':
                    rows[2 + r][x0 + i * 4 + c] = ink
    return [''.join(r) for r in rows]


def year_banner(year):
    colors = ['Y', 'X', 'G', 'Q']
    cols = []
    out = [['.'] * 15 for _ in range(5)]
    for i, ch in enumerate(str(year)):
        for r, line in enumerate(DIGITS[ch]):
            for c, px in enumerate(line):
                if px == 'X':
                    out[r][i * 4 + c] = colors[i % 4]
    return [''.join(r) for r in out]


def burst(col):
    return recolor(grid('''
|X.X.X|
|.XXX.|
|XXwXX|
|.XXX.|
|X.X.X|
'''), 'X', col)


SPARK_S = grid('''
|.Y.|
|Y.Y|
|.Y.|
''')
CONFETTI = grid('''
|X...G|
|..Y..|
|Q...X|
''')
PARTY_HAT = ACC_HATS['party'][0]


def party(*fns):
    return [at(PARTY_HAT, 8, -3, rel=True)] + list(fns)


for n in range(0, 11):
    if n >= 7:
        f = face(eyes_open(1), NOSE, OMEGA, BLUSH)
    elif n >= 4:
        f = face(eyes_open(1, -1), NOSE, OOO, BLUSH)
    elif n >= 1:
        f = face(EYES_SQUEE, NOSE, OOO, BLUSH_BIG)
    else:
        f = face(EYES_SPARK, BIG, BLUSH_BIG)
    frames[f'count_{n}'] = cat(f, 1 if n > 0 else 0, 0 if n % 2 else 1, hat=False,
                               front=party(paw(PAW_UP, 21, 6, rel=True), at(number_sign(n), 34, 5)) +
                               ([at(SPARK_S, 2, 16)] if n % 2 == 0 else [at(SPARK_S, 4, 20)]))

for year in (2027, 2028, 2029, 2030):
    b = year_banner(year)
    frames[f'ny_{year}_1'] = cat(face(EYES_SPARK, BIG, BLUSH_BIG), 0, 0, lift=2, hat=False,
                                 front=party(paw(PAW_UP, -4, 3, rel=True), paw(PAW_UP, 22, 3, rel=True),
                                             at(b, 30, 0), at(burst('X'), 2, 14), at(burst('G'), 39, 10)))
    frames[f'ny_{year}_2'] = cat(face(EYES_HAPPY, BIG, BLUSH_BIG), 0, 1, hat=False, tail=TAIL_B,
                                 front=party(at(b, 30, 0), at(burst('Q'), 39, 14), at(burst('Y'), 3, 17), at(CONFETTI, 13, 2)))
    frames[f'ny_{year}_3'] = cat(face(EYES_HEART, BIG, BLUSH_BIG), 0, 0, lift=3, hat=False,
                                 front=party(paw(PAW_UP, -4, 2, rel=True), paw(PAW_UP, 22, 2, rel=True),
                                             at(b, 30, 0), at(burst('V'), 1, 14), at(burst('X'), 40, 16), at(CONFETTI, 13, 2)))

frames.update({
    'eve_1': cat(face(eyes_open(-1, -1), NOSE, OMEGA, BLUSH), -1, 0, hat=False, front=party(at(SPARK_S, 38, 6), at(CONFETTI, 37, 18))),
    'eve_2': cat(face(EYES_HAPPY, NOSE, OMEGA, BLUSH), 0, 1, hat=False, tail=TAIL_B, front=party(at(SPARK_S, 3, 17), at(CONFETTI, 36, 6))),
})


# ================================================================ rooms
def window(sky_rows):
    rows = [list('KKKKKKKKKK')] + [list('K' + r + 'K') for r in sky_rows] + [list('KKKKKKKKKK')]
    for i in range(1, 9):
        rows[i][5] = 'K'
    rows[5] = list('KKKKKKKKKK')
    return [''.join(r) for r in rows]


SKY = {
    'morning': ['bbbbbbbb', 'bbbbbbbb', 'bbbbbbbb', 'bbbbbbbb', 'bbbbbbbb', 'pppbbbbb', 'YYpppppp', 'YYYppppp'],
    'day': ['bbbbbbYY', 'bbbbbbYY', 'bwwbbbbb', 'wwwwbbbb', 'bbbbbbbb', 'bbbbbwwb', 'bbbbwwww', 'bbbbbbbb'],
    'evening': ['vvvvvvvv', 'vvvvvvvv', 'pppppppp', 'pppppppp', 'oooooooo', 'ooYYoooo', 'oYYYYooo', 'rrrrrrrr'],
    'night': ['nnnnnnnn', 'nYnnnnnn', 'nnnnnYYn', 'nnnnnnYn', 'nnYnnnnn', 'nnnnnnnn', 'nnnnnYnn', 'nnnnnnnn'],
    'rain': ['gggggggg', 'gwwggggg', 'wwwwgggg', 'gtggtggt', 'ggtggtgg', 'gtggtggt', 'ggtggtgg', 'gggggggg'],
    'sleep': ['nnnnnnnn', 'nnnnnYnn', 'nnnnnnnn', 'nYnnnnnn', 'nnnnnnnn', 'nnnnYnnn', 'nnnnnnnn', 'nnnnnnnn'],
    'space': ['nnnnnnnn', 'nnnnnnwn', 'nwnnnnnn', 'nnnnYnnn', 'nnnnnnnn', 'nnYnnnnn', 'nnnnnnwn', 'nnnnnnnn'],
}
SKY_PAL = {
    'b': '#FF9FD8FF', 'w': '#FFFFFFFF', 'Y': '#FFFFE27A', 'p': '#FFFFC4D8', 'v': '#FFB79CE8', 'o': '#FFFFB27A',
    'r': '#FFE8789A', 'n': '#FF1C1E46', 'g': '#FFB5BED3', 't': '#FF7FA7E8', 'K': '#FF3D2B3D',
}
WALL = {'morning': '#FFFFF3E3', 'day': '#FFE8F5EC', 'evening': '#FFFCE6EC', 'night': '#FF2A2C57', 'rain': '#FFE3E8F3', 'sleep': '#FF22244A', 'space': '#FF101A33'}
RUG = grid('''
|.....KKKKKKKKKKKKKKKKKKKK.....|
|..KKKrrrrrrrrrrrrrrrrrrrrKKK..|
|.KrrrRRRRRRRRRRRRRRRRRRRRrrrK.|
|KrrRRrrrrrrrrrrrrrrrrrrrrRRrrK|
|.KrrrRRRRRRRRRRRRRRRRRRRRrrrK.|
|..KKKKKKKKKKKKKKKKKKKKKKKKKK..|
''')
RUG_PAL = {'r': '#FFF7B8CC', 'R': '#FFEE8FB0', 'K': '#FF3D2B3D'}
RUG_NIGHT = {'r': '#FF6E5A9A', 'R': '#FF58467F', 'K': '#FF15142E'}

SPACE_DECOR = []
for (x, y) in [(14, 1), (22, 3), (30, 1), (41, 4), (44, 9), (36, 13), (2, 16), (5, 22), (43, 19), (13, 7), (27, 6), (40, 24), (1, 27)]:
    SPACE_DECOR.append((x, y, '#FFFFFFFF'))
for (x, y) in [(19, 5), (33, 8), (8, 19), (44, 14)]:
    SPACE_DECOR += [(x, y, '#FFFFE27A'), (x - 1, y, '#80FFE27A'), (x + 1, y, '#80FFE27A'), (x, y - 1, '#80FFE27A'), (x, y + 1, '#80FFE27A')]
# a little ringed planet and a crescent moon
for (x, y) in [(38, 2), (39, 2), (37, 3), (38, 3), (39, 3), (40, 3), (37, 4), (38, 4), (39, 4), (40, 4), (38, 5), (39, 5)]:
    SPACE_DECOR.append((x, y, '#FF8FD3FF'))
for (x, y) in [(35, 4), (36, 4), (41, 3), (42, 3)]:
    SPACE_DECOR.append((x, y, '#FFB9A8FF'))
for (x, y) in [(3, 20), (2, 21), (2, 22), (3, 23)]:
    SPACE_DECOR.append((x, y, '#FFFFF1B8'))

rooms = list(SKY)
for room in rooms:
    pix = {}
    for r, line in enumerate(window(SKY[room])):
        for c, ch in enumerate(line):
            pix[(1 + c, 2 + r)] = SKY_PAL[ch]
    for c in range(0, 12):
        pix[(c, 12)] = '#FF3D2B3D'
    rp = RUG_NIGHT if room in ('night', 'sleep', 'space') else RUG_PAL
    if room == 'space':
        for (x, y, col) in SPACE_DECOR:
            pix[(x, y)] = col
    for r, line in enumerate(RUG if room != 'space' else []):  # in space it floats, no rug
        for c, ch in enumerate(line):
            if ch != '.':
                pix[(8 + c, 26 + r)] = rp[ch]
    by = {}
    for p, col in pix.items():
        by.setdefault(col, set()).add(p)
    write(f"pet_room_{room}", vector([(col, runs(p)) for col, p in by.items()]))
    write(f"pet_bg_{room}", '<?xml version="1.0" encoding="utf-8"?>\n'
          '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
          f'    <solid android:color="{WALL[room]}" />\n'
          '    <corners android:radius="24dp" />\n</shape>\n')

# seasonal decor overlays (room space)
DECOR = {
    'pumpkin': (grid('''
|...nn...|
|..KnK...|
|.KKOOKK.|
|KOOOOOOK|
|KOYOOYOK|
|KOOOOOOK|
|KOYYYYOK|
|.KKKKKK.|
'''), 1, 20),
    'tree': (grid('''
|...Y...|
|..KGK..|
|..KGK..|
|.KGRGK.|
|.KGGGK.|
|KGYGGRK|
|KGGGGGK|
|KKKaKKK|
|..KaK..|
|..KKK..|
'''), 1, 18),
    'cake': (grid('''
|...Y....|
|...K....|
|.KKKKKK.|
|KffffffK|
|KwRwwRwK|
|KffffffK|
|KKKKKKKK|
'''), 1, 21),
    'hearts': (grid('''
|R.R....R.R....R.R....R.R....R.R|
|RRR....RRR....RRR....RRR....RRR|
|.R......R......R......R......R.|
'''), 13, 1),
    'eggs': (grid('''
|.KK...KK...KK.|
|KXXK.KGGK.KQQK|
|KwwK.KwwK.KwwK|
|KXXK.KGGK.KQQK|
|.KK...KK...KK.|
'''), 0, 22),
    'bunting': (grid('''
|KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK|
|.RRR.YYY.GGG.QQQ.VVV.RRR.YYY.GGG|
|..R...Y...G...Q...V...R...Y...G.|
'''), 13, 1),
    'snow': (grid('''
|w...w...|
|..w...w.|
|w...w...|
|..w....w|
|.w..w...|
|....w.w.|
|w.w.....|
|...w..w.|
'''), 2, 3),
}
for name, (sprite, x, y) in DECOR.items():
    c = Canvas()
    c.blit(sprite, x, y)
    write(f"pet_decor_{name}", vector([(col, runs(p)) for col, p in c.full_colour().items()]))

# ================================================================ accessories by head position
acc_keys = sorted({(f.hx, f.hy) for f in frames.values()})
acc_index = {k: i for i, k in enumerate(acc_keys)}
for slot, table in (('hat', ACC_HATS), ('neck', ACC_NECKS)):
    for name, (sprite, ax, ay) in table.items():
        for (hx, hy) in acc_keys:
            c = Canvas()
            c.blit(sprite, hx + ax, hy + ay)
            write(f"pet_acc_{name}_{acc_index[(hx, hy)]}", vector([(col, runs(p)) for col, p in c.full_colour().items()]))

# ================================================================ export frames
for f in glob.glob(os.path.join(RES_DIR, "pet_*.xml")):
    base = os.path.basename(f)[:-4]
    if base not in written:
        os.remove(f)

WHITE = '#FFFFFFFF'
index = {}
for name, fr in frames.items():
    fur, shade, eye, ink, pm, pw = fr.canvas.layers()
    write(f"pet_{name}_f", vector([(WHITE, runs(fur))]))
    write(f"pet_{name}_s", vector([(WHITE, runs(shade))] if shade else []))
    write(f"pet_{name}_e", vector([(WHITE, runs(eye))] if eye else []))
    write(f"pet_{name}_i", vector([(col, runs(p)) for col, p in ink.items()]))
    marks, whites = [], []
    for i, p in enumerate(PATTERNS):
        if pm[p]:
            write(f"pet_{name}_m{i}", vector([(WHITE, runs(pm[p]))]))
            marks.append(f"R.drawable.pet_{name}_m{i}")
        else:
            marks.append("0")
        if pw[p]:
            write(f"pet_{name}_w{i}", vector([(WHITE, runs(pw[p]))]))
            whites.append(f"R.drawable.pet_{name}_w{i}")
        else:
            whites.append("0")
    index[name] = (marks, whites, acc_index[(fr.hx, fr.hy)], fr.hat_ok, fr.neck_ok, bool(eye), bool(shade))

# full-colour preview for the widget picker
write("pet_preview_cat", vector([(col, runs(p)) for col, p in frames['dance_bob_1'].canvas.full_colour().items()]))
write("pet_widget_preview", '<?xml version="1.0" encoding="utf-8"?>\n'
      '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
      '    <item android:drawable="@drawable/pet_bg_day" />\n'
      '    <item android:drawable="@drawable/pet_room_day" android:top="30dp" android:bottom="30dp" android:left="10dp" android:right="10dp" />\n'
      '    <item android:drawable="@drawable/pet_preview_cat" android:top="30dp" android:bottom="30dp" android:left="10dp" android:right="10dp" />\n'
      '</layer-list>\n')

# ================================================================ kotlin index
L = ["// GENERATED by scripts/pet/gen_pet_sprites.py. Do not edit by hand.",
     "package com.alananasss.kittytune.ui.widget.pet", "",
     "import com.alananasss.kittytune.R", "",
     "/** One animation frame: tinted layers + per-pattern markings/white parts (0 = none). */",
     "internal class PetFrame(",
     "    val fur: Int, val shade: Int, val eye: Int, val ink: Int,",
     "    val marks: IntArray, val whites: IntArray,",
     "    val accKey: Int, val hatOk: Boolean, val neckOk: Boolean,",
     ")", "",
     "internal object PetFrames {",
     f"    /** Pattern order of [PetFrame.marks] / [PetFrame.whites]: {', '.join(PATTERNS)}. */",
     "    val frames: Map<String, PetFrame> by lazy { buildFrames() }", ""]
L.append("    private fun buildFrames(): Map<String, PetFrame> = hashMapOf<String, PetFrame>().apply {")
for name, (marks, whites, key, hat_ok, neck_ok, has_eye, has_shade) in index.items():
    eye = f"R.drawable.pet_{name}_e" if has_eye else "0"
    shade = f"R.drawable.pet_{name}_s" if has_shade else "0"
    L.append(f'        put("{name}", PetFrame(R.drawable.pet_{name}_f, {shade}, {eye}, R.drawable.pet_{name}_i, '
             f'intArrayOf({", ".join(marks)}), intArrayOf({", ".join(whites)}), {key}, {str(hat_ok).lower()}, {str(neck_ok).lower()}))')
L.append("    }")
L.append("")
for slot, table in (('hats', ACC_HATS), ('necks', ACC_NECKS)):
    L.append(f"    val {slot}: Map<String, IntArray> by lazy {{")
    L.append("        mapOf(")
    for name in table:
        ids = ", ".join(f"R.drawable.pet_acc_{name}_{i}" for i in range(len(acc_keys)))
        L.append(f'            "{name}" to intArrayOf({ids}),')
    L.append("        )")
    L.append("    }")
    L.append("")
L.append("    val rooms: Map<String, Int> = mapOf(")
for room in rooms:
    L.append(f'        "{room}" to R.drawable.pet_room_{room},')
L.append("    )")
L.append("")
L.append("    val wallColors: Map<String, Long> = mapOf(")
for room in rooms:
    L.append(f'        "{room}" to 0x{WALL[room][1:]},')
L.append("    )")
L.append("")
L.append("    val decor: Map<String, Int> = mapOf(")
for name in DECOR:
    L.append(f'        "{name}" to R.drawable.pet_decor_{name},')
L.append("    )")
L.append("}")
os.makedirs(os.path.dirname(KT_FILE), exist_ok=True)
with open(KT_FILE, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(L) + "\n")
print("frames", len(frames), "acc positions", len(acc_keys), "files", len(written))


# ================================================================ custom coats (per-pixel, rendered by the app)
# raw/pet_partmap.bin: for every frame, every visible coat pixel -> (x, y, part, r, c)
#   part: 0 head, 1 body, 2 loaf, 3 tail (r = index from the tip), 4 paw
import struct

RAW_DIR = os.path.join(os.path.dirname(RES_DIR), "raw")
os.makedirs(RAW_DIR, exist_ok=True)
PART_CODE = {'head': 0, 'body': 1, 'loaf': 2, 'tail': 3, 'paw': 4}


def tail_index(sprite, r, c):
    wcells = sorted((rr, cc) for rr, row in enumerate(sprite) for cc, ch in enumerate(row) if ch == 'W')
    horizontal = len({rr for rr, _ in wcells}) == 1
    order = sorted(wcells, key=lambda t: (-t[1]) if horizontal else (t[0], -t[1]))
    return order.index((r, c)) if (r, c) in order else 0


blob = bytearray()
blob += struct.pack('<H', len(frames))
for name, fr in frames.items():
    entries = []
    for (x, y), (ch, cat, part, sprite, r, c) in fr.canvas.px.items():
        if not (0 <= x < W and 0 <= y < H) or not cat or ch not in ('W', 'S') or part not in PART_CODE:
            continue
        if part == 'tail':
            r, c = tail_index(sprite, r, c), 0
        entries.append((x, y, PART_CODE[part], r, c))
    nb = name.encode('ascii')
    blob += struct.pack('<B', len(nb)) + nb + struct.pack('<H', len(entries))
    for e in sorted(entries):
        blob += struct.pack('<5B', *e)
with open(os.path.join(RAW_DIR, "pet_partmap.bin"), "wb") as fh:
    fh.write(blob)

# Kotlin: preset masks as strings ('0' coat, '1' markings, '2' white) so presets can seed the pixel editor
def role_char(role):
    return {'m': '1', 'w': '2'}.get(role, '0')


TAIL_LEN = len([1 for row in TAIL for ch in row if ch == 'W'])
P = ["// GENERATED by scripts/pet/gen_pet_sprites.py. Do not edit by hand.",
     "package com.alananasss.kittytune.ui.widget.pet", "",
     "/** Preset coats as role strings ('0' coat, '1' markings, '2' white), row-major. */",
     "internal object PetCoatPresets {",
     f"    const val HEAD_W = {len(HEAD[0])}",
     f"    const val HEAD_H = {len(HEAD)}",
     f"    const val BODY_W = {len(BODY[0])}",
     f"    const val BODY_H = {len(BODY)}",
     f"    const val TAIL_LEN = {TAIL_LEN}",
     f'    const val HEAD_SHAPE = "{"".join(HEAD)}"',
     f'    const val BODY_SHAPE = "{"".join(BODY)}"',
     "",
     "    val presets: Map<String, Triple<String, String, String>> = mapOf("]
for p in PATTERNS:
    head = "".join(role_char(role_for(p, 'head', HEAD, r, c)) for r in range(len(HEAD)) for c in range(len(HEAD[0])))
    body = "".join(role_char(role_for(p, 'body', BODY, r, c)) for r in range(len(BODY)) for c in range(len(BODY[0])))
    order = sorted([(rr, cc) for rr, row in enumerate(TAIL) for cc, ch in enumerate(row) if ch == 'W'], key=lambda t: (t[0], -t[1]))
    tail = "".join(role_char(tail_role(p, TAIL, rr, cc)) for rr, cc in order)
    P.append(f'        "{p}" to Triple("{head}", "{body}", "{tail}"),')
P.append("    )")
P.append("}")
with open(os.path.join(os.path.dirname(KT_FILE), "PetCoatPresets.kt"), "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(P) + "\n")
print("partmap bytes", len(blob))
