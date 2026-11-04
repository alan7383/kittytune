import os
import re
import xml.etree.ElementTree as ET
from PIL import Image

WORKSPACE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DRAWABLE_DIR = os.path.join(WORKSPACE, "app", "src", "main", "res", "drawable")
OUTPUT_DIR = os.path.join(WORKSPACE, "cat_gifs")

# Color definitions
def hex_to_rgba(c):
    c = c.lstrip('#')
    if len(c) == 6:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    elif len(c) == 8:
        return (int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16), int(c[0:2], 16))
    return (0, 0, 0, 255)

def int_to_rgba(val):
    a = (val >> 24) & 0xFF
    r = (val >> 16) & 0xFF
    g = (val >> 8) & 0xFF
    b = val & 0xFF
    return (r, g, b, a)

# Presets
PALETTES = {
    "cream":   {"fur": 0xFFFFF7EE, "shade": 0xFFF0D9C8, "mark": 0xFFF7B27A, "white": 0xFFFFFDF8},
    "ginger":  {"fur": 0xFFFFC58A, "shade": 0xFFF0A060, "mark": 0xFFD77A35, "white": 0xFFFFFDF8},
    "grey":    {"fur": 0xFF9AA3B8, "shade": 0xFF7D869E, "mark": 0xFF5E6780, "white": 0xFFFFFDF8},
    "black":   {"fur": 0xFF5A5270, "shade": 0xFF473F5C, "mark": 0xFF35304A, "white": 0xFFFFFDF8},
    "snow":    {"fur": 0xFFFFFFFF, "shade": 0xFFE3E7F2, "mark": 0xFFC4CCE3, "white": 0xFFF4F7FF},
    "siamese": {"fur": 0xFFF5E8D6, "shade": 0xFFD9C3A8, "mark": 0xFF6E5444, "white": 0xFFFFFDF8},
    "brown":   {"fur": 0xFFC9A27E, "shade": 0xFFAD8664, "mark": 0xFF6E4B33, "white": 0xFFFFFDF8},
    "pink":    {"fur": 0xFFFFD6E8, "shade": 0xFFF6B5D2, "mark": 0xFFEF8DBB, "white": 0xFFFFFDF8},
    "mint":    {"fur": 0xFFCFF2E0, "shade": 0xFFA9DEC4, "mark": 0xFF7FC7A5, "white": 0xFFFFFDF8},
    "lilac":   {"fur": 0xFFE3D6FF, "shade": 0xFFC9B6F5, "mark": 0xFF9C84E0, "white": 0xFFFFFDF8},
}

EYES = {
    "onyx":   0xFF3D2B3D,
    "amber":  0xFFF0B030,
    "green":  0xFF7CC45A,
    "blue":   0xFF6AB0F0,
    "copper": 0xFFD2783C,
    "violet": 0xFFA77CF0,
}

WALL_COLORS = {
    "morning": 0xFFFFF3E3,
    "day":     0xFFE8F5EC,
    "evening": 0xFFFCE6EC,
    "night":   0xFF2A2C57,
    "rain":    0xFFE3E8F3,
    "sleep":   0xFF22244A,
    "space":   0xFF101A33,
}

_LAYER_CACHE = {}

def parse_layer(xml_name, tint=None):
    key = (xml_name, tint)
    if key in _LAYER_CACHE:
        return _LAYER_CACHE[key].copy()
    xml_path = os.path.join(DRAWABLE_DIR, f"{xml_name}.xml")
    if not os.path.exists(xml_path):
        return None
    tree = ET.parse(xml_path)
    root = tree.getroot()
    img = Image.new("RGBA", (46, 32), (0, 0, 0, 0))
    for path in root.findall(".//{http://schemas.android.com/apk/res/android}path") + root.findall(".//path"):
        raw_c = path.attrib.get("{http://schemas.android.com/apk/res/android}fillColor", "#000000")
        col = int_to_rgba(tint) if tint is not None else hex_to_rgba(raw_c)
        d = path.attrib.get("{http://schemas.android.com/apk/res/android}pathData", "")
        for m in re.finditer(r"M\s*(\d+)\s*,\s*(\d+)\s*h\s*(\d+)", d):
            xi, yi, wi = int(m.group(1)), int(m.group(2)), int(m.group(3))
            for dx in range(wi):
                if 0 <= xi + dx < 46 and 0 <= yi < 32:
                    # Alpha blending if necessary
                    if col[3] == 255:
                        img.putpixel((xi + dx, yi), col)
                    elif col[3] > 0:
                        prev = img.getpixel((xi + dx, yi))
                        a_norm = col[3] / 255.0
                        inv_a = 1.0 - a_norm
                        r = int(col[0] * a_norm + prev[0] * inv_a)
                        g = int(col[1] * a_norm + prev[1] * inv_a)
                        b = int(col[2] * a_norm + prev[2] * inv_a)
                        a = min(255, int(col[3] + prev[3] * inv_a))
                        img.putpixel((xi + dx, yi), (r, g, b, a))
    _LAYER_CACHE[key] = img
    return img.copy()

def render_composite_frame(frame_name, palette, eye_color, pattern="classic", room=None):
    # Base canvas
    if room:
        wall_c = int_to_rgba(WALL_COLORS.get(room, 0xFFE8F5EC))
        base = Image.new("RGBA", (46, 32), wall_c)
        room_layer = parse_layer(f"pet_room_{room}")
        if room_layer:
            base = Image.alpha_composite(base, room_layer)
    else:
        base = Image.new("RGBA", (46, 32), (0, 0, 0, 0))

    # Layers: fur, shade, mark, white, eye, ink
    fur = parse_layer(f"pet_{frame_name}_f", palette["fur"])
    if fur:
        base = Image.alpha_composite(base, fur)

    shade = parse_layer(f"pet_{frame_name}_s", palette["shade"])
    if shade:
        base = Image.alpha_composite(base, shade)

    if pattern == "classic":
        mark = parse_layer(f"pet_{frame_name}_m1", palette["mark"])
        if mark:
            base = Image.alpha_composite(base, mark)
    elif pattern == "tabby":
        mark = parse_layer(f"pet_{frame_name}_m2", palette["mark"])
        if mark:
            base = Image.alpha_composite(base, mark)
    elif pattern == "bicolor":
        mark = parse_layer(f"pet_{frame_name}_m3", palette["mark"])
        if mark:
            base = Image.alpha_composite(base, mark)
        white = parse_layer(f"pet_{frame_name}_w3", palette["white"])
        if white:
            base = Image.alpha_composite(base, white)
    elif pattern == "calico":
        mark = parse_layer(f"pet_{frame_name}_m4", palette["mark"])
        if mark:
            base = Image.alpha_composite(base, mark)
        white = parse_layer(f"pet_{frame_name}_w4", palette["white"])
        if white:
            base = Image.alpha_composite(base, white)
    elif pattern == "points":
        mark = parse_layer(f"pet_{frame_name}_m5", palette["mark"])
        if mark:
            base = Image.alpha_composite(base, mark)

    eye = parse_layer(f"pet_{frame_name}_e", eye_color)
    if eye:
        base = Image.alpha_composite(base, eye)

    ink = parse_layer(f"pet_{frame_name}_i", None)
    if ink:
        base = Image.alpha_composite(base, ink)

    return base

# Activity list: (Name, frames, frame_ms, default_room)
def x2(*f):
    return list(f) + list(f)

def x4(a, b):
    return [a, b, a, b, a, b, a, b]

ACTIVITIES = {
    # Dances
    "dance_bob":      (x2("dance_bob_1", "dance_bob_2", "dance_bob_3", "dance_bob_4"), 500, "day"),
    "dance_bounce":   (x2("dance_bounce_1", "dance_bounce_2", "dance_bounce_3", "dance_bounce_4"), 500, "day"),
    "dance_paws":     (x2("dance_paws_1", "dance_paws_2", "dance_paws_3", "dance_paws_4"), 500, "day"),
    "dance_headbang": (x2("dance_hb_1", "dance_hb_2", "dance_hb_3", "dance_hb_4"), 500, "day"),
    "dance_sway":     (x2("dance_sway_1", "dance_sway_2", "dance_sway_3", "dance_sway_4"), 500, "day"),
    "dance_guitar":   (x2("dance_guitar_1", "dance_guitar_2", "dance_guitar_3", "dance_guitar_4"), 500, "day"),
    "dance_dj":       (x2("dance_dj_1", "dance_dj_2", "dance_dj_3", "dance_dj_4"), 500, "day"),

    # Common activities & reactions
    "boop":           (x4("boop_1", "boop_2"), 300, "day"),
    "boop_dance":     (x4("boop_dance_1", "boop_dance_2"), 300, "day"),
    "idle":           (["idle_open", "idle_look_l", "idle_blink", "idle_open", "idle_look_r", "idle_look_r", "idle_happy", "idle_happy"], 450, "day"),
    "wave":           (x4("wave_1", "wave_2"), 350, "day"),
    "hello":          (["hello_1", "hello_2", "hello_1", "hello_2", "hello_3", "hello_3", "hello_1", "hello_2"], 420, "day"),
    "yawn":           (["idle_open", "yawn_1", "yawn_2", "yawn_2", "yawn_3", "idle_blink", "idle_open", "idle_open"], 500, "day"),
    "groom":          (x4("groom_1", "groom_2"), 400, "day"),
    "eat":            (["eat_1", "eat_2", "eat_1", "eat_2", "eat_1", "eat_2", "eat_3", "eat_3"], 400, "day"),
    "play":           (["play_1", "play_2", "play_1", "play_2", "play_3", "play_4", "play_3", "play_4"], 400, "day"),
    "butterfly":      (["fly_1", "fly_2", "fly_3", "fly_2", "fly_1", "fly_2", "fly_4", "fly_4"], 450, "day"),
    "box":            (["box_peek", "box_peek", "box_blink", "box_look", "box_peek", "box_hide", "box_hide", "box_peek"], 500, "day"),
    "loaf":           (["loaf_open", "loaf_open", "loaf_blink", "loaf_open", "loaf_open", "loaf_happy", "loaf_happy", "loaf_open"], 500, "day"),
    "doze":           (["doze_1", "doze_1", "doze_2", "doze_2", "doze_2", "doze_1", "doze_2", "doze_2"], 650, "night"),
    "laser":          (["laser_1", "laser_2", "laser_1", "laser_3", "laser_3", "laser_4", "laser_4", "laser_1"], 400, "day"),
    "bird":           (["bird_1", "bird_2", "bird_1", "bird_2", "bird_1", "bird_2", "bird_3", "bird_3"], 450, "day"),
    "knead":          (x4("knead_1", "knead_2"), 450, "day"),
    "purr":           (x4("purr_1", "purr_2"), 550, "day"),
    "bubbles":        (["bubbles_1", "bubbles_2", "bubbles_1", "bubbles_2", "bubbles_3", "bubbles_3", "bubbles_1", "bubbles_2"], 450, "day"),
    "mouse":          (["mouse_1", "mouse_1", "mouse_2", "mouse_2", "mouse_3", "mouse_3", "mouse_1", "mouse_2"], 400, "day"),
    "hum":            (x4("hum_1", "hum_2"), 500, "day"),
    "stargaze":       (x4("stargaze_1", "stargaze_2"), 650, "night"),
    "nod":            (["nod_1", "nod_1", "nod_2", "nod_2", "nod_1", "nod_1", "nod_2", "nod_2"], 600, "night"),
    "sleep":          (["sleep_1", "sleep_1", "sleep_2", "sleep_2", "sleep_3", "sleep_3", "sleep_2", "sleep_2"], 600, "sleep"),

    # Event activities
    "sneeze":         (["idle_open", "sneeze_1", "sneeze_1", "sneeze_2", "sneeze_3", "sneeze_4", "sneeze_4", "idle_blink"], 400, "day"),
    "love":           (x4("love_1", "love_2"), 400, "day"),
    "treat":          (["treat_1", "treat_2", "treat_1", "treat_2", "treat_1", "treat_2", "treat_3", "treat_3"], 400, "day"),
    "gift":           (x4("gift_1", "gift_2"), 450, "day"),

    # Rare activities
    "rainbow":        (x4("rainbow_1", "rainbow_2"), 500, "day"),
    "shooting_star":  (["star_1", "star_2", "star_3", "star_3", "star_1", "star_2", "star_3", "star_3"], 450, "night"),
    "ufo":            (["ufo_1", "ufo_2", "ufo_3", "ufo_3", "ufo_1", "ufo_2", "ufo_3", "ufo_3"], 450, "night"),
    "logo_cameo":     (["cameo_1", "cameo_2", "cameo_3", "cameo_4", "cameo_5", "cameo_6", "cameo_4", "cameo_6"], 600, "day"),
    "ghost":          (["ghost_1", "ghost_1", "ghost_2", "ghost_2", "ghost_3", "ghost_3", "ghost_3", "ghost_1"], 450, "night"),
    "kitten":         (["kitten_1", "kitten_2", "kitten_1", "kitten_2", "kitten_3", "kitten_3", "kitten_1", "kitten_2"], 500, "day"),
    "goldfish":       (x4("goldfish_1", "goldfish_2"), 400, "day"),
    "royal":          (x4("royal_1", "royal_2"), 500, "day"),
    "disco":          (["disco_1", "disco_2", "disco_3", "disco_2", "disco_1", "disco_2", "disco_3", "disco_2"], 380, "night"),

    # Sad moods
    "sad":            (["sad_1", "sad_1", "sad_2", "sad_2", "sad_look", "sad_look", "sad_1", "sad_2"], 500, "rain"),
    "sad_window":     (x4("sadwin_1", "sadwin_2"), 700, "rain"),
    "sad_phones":     (x4("sadphones_1", "sadphones_2"), 650, "rain"),
    "sad_loaf":       (x4("sadloaf_1", "sadloaf_2"), 700, "rain"),
    "sad_call":       (["sadcall_1", "sadcall_1", "sadcall_2", "sadcall_2", "sadcall_1", "sadcall_1", "sadcall_2", "sadcall_2"], 600, "rain"),

    # Exclusive & seasonal
    "dig":            (["dig_1", "dig_2", "dig_1", "dig_2", "dig_3", "dig_4", "dig_3", "dig_4"], 500, "day"),
    "judge":          (["judge_1", "judge_2", "judge_2", "judge_1", "judge_3", "judge_3", "judge_1", "judge_2"], 550, "day"),
    "fan":            (x4("fan_1", "fan_2"), 450, "day"),
    "ny_eve":         (x4("eve_1", "eve_2"), 650, "night"),
    "ny_countdown":   (["count_10", "count_9", "count_8", "count_7", "count_6", "count_5", "count_4", "count_3", "count_2", "count_1", "count_0"], 1000, "night"),
    "ny_fireworks":   (["ny_2027_1", "ny_2027_2", "ny_2027_3", "ny_2027_2", "ny_2027_1", "ny_2027_2", "ny_2027_3", "ny_2027_3"], 450, "night"),
    "ivy_space":      (["ivy_float_1", "ivy_float_2", "ivy_float_3", "ivy_float_2", "ivy_float_1", "ivy_float_2", "ivy_float_3", "ivy_float_2"], 550, "space"),
}

def export_gif(act_name, scale=8, transparent=True, color="ginger", pattern="classic", eye="onyx", out_dir=OUTPUT_DIR):
    if act_name not in ACTIVITIES:
        print(f"Unknown activity: {act_name}")
        return
    frames, ms, room = ACTIVITIES[act_name]
    pal = PALETTES.get(color, PALETTES["ginger"])
    eye_c = EYES.get(eye, EYES["onyx"])

    rendered_frames = []
    for f in frames:
        img = render_composite_frame(f, pal, eye_c, pattern=pattern, room=None if transparent else room)
        scaled = img.resize((46 * scale, 32 * scale), Image.Resampling.NEAREST)
        rendered_frames.append(scaled)

    os.makedirs(out_dir, exist_ok=True)
    suffix = "_transparent" if transparent else f"_{room}"
    out_path = os.path.join(out_dir, f"{act_name}{suffix}.gif")

    # Save animated GIF
    rendered_frames[0].save(
        out_path,
        save_all=True,
        append_images=rendered_frames[1:],
        duration=ms,
        loop=0,
        disposal=2
    )
    return out_path

if __name__ == "__main__":
    import sys
    print("Exporting all cat animations as GIFs...")
    color = "ginger"
    if len(sys.argv) > 1:
        color = sys.argv[1]

    # Export all transparent and with background
    for name in ACTIVITIES:
        p_trans = export_gif(name, scale=8, transparent=True, color=color)
        p_room = export_gif(name, scale=8, transparent=False, color=color)
        print(f"Exported: {name}")

    print(f"\nDone! All GIFs are saved in: {OUTPUT_DIR}")
