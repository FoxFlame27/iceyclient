# Draws the glass widget skin (buttons, sliders, text fields, tabs, checkboxes,
# scroll bars) that the mod ships over the vanilla GUI sprites.
# Pure Python (no PIL on the dev Mac): python3 mod/tools/gen_glass_sprites.py
# Every sprite is drawn at 4 texture pixels per GUI pixel, so the rounded
# corners stay smooth at any GUI scale. The .mcmeta files carry the nominal
# size and a 3 px nine-slice border wide enough for the corner radius.
import os, struct, zlib, json, math

S = 4                                   # texture pixels per GUI pixel
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'assets', 'minecraft', 'textures', 'gui', 'sprites', 'widget')
os.makedirs(OUT, exist_ok=True)

def png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(v for p in px[y * w:(y + 1) * w] for v in p) for y in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def inside(x, y, w, h, r, tl=True, tr=True, bl=True, br=True):
    """Is the point inside the rounded rect 0..w x 0..h (corners can be square)?"""
    if x < 0 or y < 0 or x > w or y > h: return False
    cx = r if x < r else (w - r if x > w - r else None)
    cy = r if y < r else (h - r if y > h - r else None)
    if cx is None or cy is None: return True
    left, top = x < r, y < r
    rounded = (tl if top else bl) if left else (tr if top else br)
    return (not rounded) or (x - cx) ** 2 + (y - cy) ** 2 <= r * r

def over(dst, src):
    sa = src[3] / 255.0; da = dst[3] / 255.0; oa = sa + da * (1 - sa)
    if oa <= 0: return (0, 0, 0, 0)
    return tuple(int(round((src[i] * sa + dst[i] * da * (1 - sa)) / oa)) for i in range(3)) + (int(round(oa * 255)),)

def rrect(w, h, r, fill, stroke=None, sw=0.0, corners=(True, True, True, True), open_bottom=False, shine=0):
    """w,h,r,sw in GUI pixels. Returns the pixel list at S per GUI pixel, 4x4 supersampled."""
    W, H = w * S, h * S; N = 4; px = []
    for Y in range(H):
        for X in range(W):
            cf = cs = 0
            for j in range(N):
                for i in range(N):
                    x = (X + (i + .5) / N) / S; y = (Y + (j + .5) / N) / S
                    if not inside(x, y, w, h, r, *corners): continue
                    edge = sw > 0 and not inside(x - sw, y - sw, w - 2 * sw, (h + (sw if open_bottom else 0)) - 2 * sw, max(0, r - sw), *corners)
                    if edge: cs += 1
                    else: cf += 1
            n = N * N
            f = fill
            if shine:                    # a little lighter towards the top, like light on glass
                t = 1 - Y / max(1, H - 1)
                f = tuple(min(255, int(fill[i] + shine * t)) for i in range(3)) + (fill[3],)
            c = (f[0], f[1], f[2], int(f[3] * cf / n))
            if stroke and cs: c = over(c, (stroke[0], stroke[1], stroke[2], int(stroke[3] * cs / n)))
            px.append(c)
    return W, H, px

def save(name, w, h, px_tuple, border=None, stretch=False):
    W, H, px = px_tuple
    png(os.path.join(OUT, name + '.png'), W, H, px)
    if border is not None:
        scaling = {'type': 'nine_slice', 'width': w, 'height': h, 'border': border}
        # The middle of a pane is one flat colour: stretch it. Left to the default the game repeats
        # the two-pixel middle as tiles, on 1.21.8 one draw each: tens of thousands for a big pane.
        if stretch:
            scaling['stretch_inner'] = True
        json.dump({'gui': {'scaling': scaling}}, open(os.path.join(OUT, name + '.png.mcmeta'), 'w'))
    print(name, W, H)

NAVY = (14, 19, 32); ICE = (91, 200, 245)
LINE = (255, 255, 255, 120); LINE_HOT = (255, 255, 255, 235); LINE_OFF = (255, 255, 255, 46)

# Buttons
save('button', 200, 20, rrect(200, 20, 3.5, NAVY + (150,), LINE, 0.5, shine=14), 3)
save('button_highlighted', 200, 20, rrect(200, 20, 3.5, (38, 50, 78, 190), LINE_HOT, 0.75, shine=20), 3)
save('button_disabled', 200, 20, rrect(200, 20, 3.5, NAVY + (90,), LINE_OFF, 0.5), 3)
# Sliders: a darker well, and a bright handle
save('slider', 200, 20, rrect(200, 20, 3.5, (6, 9, 18, 170), LINE_OFF, 0.5), 3)
save('slider_highlighted', 200, 20, rrect(200, 20, 3.5, (6, 9, 18, 170), LINE, 0.5), 3)
HB = {'left': 3, 'top': 3, 'right': 3, 'bottom': 3}
save('slider_handle', 8, 20, rrect(8, 20, 3, (58, 104, 150, 215), (255, 255, 255, 200), 0.5), HB)
save('slider_handle_highlighted', 8, 20, rrect(8, 20, 3, (52, 132, 190, 235), (255, 255, 255, 255), 0.5), HB)
# Text fields
save('text_field', 200, 20, rrect(200, 20, 3.5, (6, 9, 18, 185), LINE_OFF, 0.5), 3)
save('text_field_highlighted', 200, 20, rrect(200, 20, 3.5, (6, 9, 18, 205), ICE + (230,), 0.75), 3)
# Tabs: round on top, open at the bottom
TB = {'left': 3, 'top': 3, 'right': 3, 'bottom': 0}
TOPC = (True, True, False, False)
save('tab', 130, 24, rrect(130, 24, 3.5, NAVY + (110,), LINE_OFF, 0.5, TOPC, True), TB)
save('tab_highlighted', 130, 24, rrect(130, 24, 3.5, (38, 50, 78, 160), LINE, 0.5, TOPC, True), TB)
save('tab_selected', 130, 24, rrect(130, 24, 3.5, (38, 50, 78, 200), LINE_HOT, 0.75, TOPC, True), TB)
save('tab_selected_highlighted', 130, 24, rrect(130, 24, 3.5, (48, 62, 96, 215), LINE_HOT, 0.75, TOPC, True), TB)
# Scroll bar
save('scroller', 6, 32, rrect(6, 32, 3, (214, 226, 242, 190)), {'left': 1, 'top': 3, 'right': 1, 'bottom': 3})
save('scroller_background', 6, 32, rrect(6, 32, 3, (255, 255, 255, 26)), {'left': 1, 'top': 3, 'right': 1, 'bottom': 3})

# Checkboxes (17x17 on screen, stretched from this square): the ticked ones carry an ice square
def checkbox(hot, on):
    W, H, px = rrect(20, 20, 4, (6, 9, 18, 185), LINE_HOT if hot else LINE, 1.0)
    if on:
        _, _, dot = rrect(10, 10, 2.5, ICE + (255,))
        for y in range(10 * S):
            for x in range(10 * S):
                i = (y + 5 * S) * W + (x + 5 * S); px[i] = over(px[i], dot[y * 10 * S + x])
    return W, H, px
save('checkbox', 20, 20, checkbox(False, False))
save('checkbox_highlighted', 20, 20, checkbox(True, False))
save('checkbox_selected', 20, 20, checkbox(False, True))
save('checkbox_selected_highlighted', 20, 20, checkbox(True, True))

# ── Smooth panes for the mod's own menus (Glass.java) ─────────────────
# White shapes that the code tints: filled rounded rects, ones rounded only
# on top or only at the bottom, and hairline outlines, per corner radius.
# Drawn with the same 4x supersampling as the buttons above, so the Y menu
# gets the same smooth corners instead of stair-stepped ones.
PANE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                    'assets', 'iceymod', 'textures', 'gui', 'sprites', 'glass')
os.makedirs(PANE, exist_ok=True)
OUT = PANE
WHITE = (255, 255, 255, 255)
for r in (2, 3, 4, 5, 6, 8, 10, 12):
    n = 2 * r + 4
    save('fill_r%d' % r, n, n, rrect(n, n, r, WHITE), r + 1, True)
    save('outline_r%d' % r, n, n, rrect(n, n, r, (255, 255, 255, 0), WHITE, 0.6), r + 1, True)
    save('top_r%d' % r, n, n, rrect(n, n, r, WHITE, None, 0.0, (True, True, False, False)), {'left': r + 1, 'top': r + 1, 'right': r + 1, 'bottom': 1}, True)
    save('bottom_r%d' % r, n, n, rrect(n, n, r, WHITE, None, 0.0, (False, False, True, True)), {'left': r + 1, 'top': 1, 'right': r + 1, 'bottom': r + 1}, True)


# A soft shadow for the panes of the scene menu: dark just outside a rounded
# rectangle (corner radius 10, 12 pixels of fall-off), nothing under it.
def shadow(r=10, m=12):
    n = 2 * (r + m) + 4
    S = 4
    W = H = n * S
    px = []
    for Y in range(H):
        for X in range(W):
            x, y = (X + 0.5) / S, (Y + 0.5) / S
            # distance to the rounded rectangle [m, n-m] x [m, n-m]
            qx = abs(x - n / 2) - (n / 2 - m - r)
            qy = abs(y - n / 2) - (n / 2 - m - r)
            d = (max(qx, 0) ** 2 + max(qy, 0) ** 2) ** 0.5 + min(max(qx, qy), 0) - r
            if d <= -0.5:
                a = 0.0
            else:
                t = max(0.0, 1.0 - max(d, 0.0) / m)
                a = t * t * (min(1.0, d + 0.5) if d < 0.5 else 1.0)
            px.append((255, 255, 255, int(round(255 * a))))
    return W, H, px

save('shadow', 2 * 22 + 4, 2 * 22 + 4, shadow(), 23, True)
