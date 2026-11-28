#!/usr/bin/env python3
"""Turns exported pieces of a world into what the scene main menu ships.

Input: a folder of <id>.json files written by the scene exporter (a
temporary in-game tool, kept outside the repository): every block face of
a piece of a dev world that the camera behind the player can see, plus
every spot in it where a player could stand in full view.

Output, under src/main/resources/assets/iceymod/scenes:
  <id>.json      the faces (the 3D model the menu draws)
  scenes.json    the list: name, how each place is lit, where friends stand

Usage: python3 tools/build_scenes.py <folder of exports>
"""
import json
import os
import sys

# How a place is lit. sun/day: colour and strength of direct sunlight (or moonlight); shade/ambient: the light
# of the sky where the sun does not reach; glow: lamps, torches, lava; fog*: the haze (it ends in the colour of
# the sky at the horizon, skyBottom, so the far edge of the model melts into it); sky*: behind everything;
# light: the tint on the players so they sit in the same light.
DAY = dict(day=1.3, sun='ffedc4', shade='a6c4ff', ambient=0.54, glow=0.9, fog='c6defa', fogStart=20, fogEnd=56, fogMax=1.0, skyTop='3f86ea', skyBottom='c6defa', light='fff6e6')
DUSK = dict(day=1.0, sun='ff9a55', shade='8a80d0', ambient=0.5, glow=1.3, fog='f2a47e', fogStart=17, fogEnd=56, fogMax=1.0, skyTop='2f3378', skyBottom='f2a47e', light='f6cdb8')
DAWN = dict(day=0.9, sun='ffb9c6', shade='9a90d6', ambient=0.54, glow=1.2, fog='eab4c4', fogStart=17, fogEnd=56, fogMax=1.0, skyTop='2c2a62', skyBottom='eab4c4', light='e8d6ee')
NIGHT = dict(day=0.5, sun='a9bcff', shade='566cb4', ambient=0.4, glow=1.7, fog='18244c', fogStart=15, fogEnd=56, fogMax=1.0, skyTop='050918', skyBottom='18244c', light='a9b6de')

# export id, name shown in the menu, how likely it snows / rains there at a start, how it is lit
PICKS = [
    ('snowy_village', 'Snowy Village', 0.65, 0.0, DAY),
    ('plains_village', 'Plains Village', 0.0, 0.3, DAY),
    ('village_dusk', 'Village at Dusk', 0.0, 0.2, DUSK),
    ('savanna_village', 'Savanna Village', 0.0, 0.0, DAY),
    ('desert_village', 'Desert Village', 0.0, 0.0, dict(DAY, day=1.0, ambient=0.5, sun='ffe9bd', fog='efdcb6', skyBottom='efdcb6', light='fff0d6')),
    ('taiga_village', 'Taiga Village at Night', 0.0, 0.3, NIGHT),
    ('outpost', 'Pillager Outpost', 0.0, 0.25, DUSK),
    ('igloo', 'Snowfield at Dawn', 0.7, 0.0, DAWN),
    ('trial_chambers', 'Trial Chambers', 0.0, 0.0, dict(day=0.0, sun='ffffff', shade='ffe2c4', ambient=0.4, glow=1.4, fog='120d0c', fogStart=14, fogEnd=40, fogMax=1.0, skyTop='0b0909', skyBottom='120d0c', light='ecccaa')),
    ('end_city', 'End City', 0.0, 0.0, dict(day=0.6, sun='e8d4ff', shade='b9a2e6', ambient=0.95, glow=1.0, fog='110c20', fogStart=20, fogEnd=56, fogMax=1.0, skyTop='07050e', skyBottom='110c20', light='d4c6ec')),
    ('bastion', 'Bastion Remnant', 0.0, 0.0, dict(day=0.0, sun='ffffff', shade='ffb49a', ambient=0.52, glow=1.45, fog='2e0d0b', fogStart=13, fogEnd=42, fogMax=1.0, skyTop='190706', skyBottom='2e0d0b', light='dcb4a2')),
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'iceymod', 'scenes')


def friends(spots, me):
    """Up to four places for friends, the best first. A spot is [dx, dy, dz, x, feet, head, far]: blocks from the player, then
    where it is in the picture (across, and the height of feet and head, in half picture heights from the
    middle; the picture is 1.78 wide each way) and how far from the camera."""
    def half(s):
        return (s[5] - s[4]) * 0.17                    # half a player's width in the picture
    def clash(a, b, gap):
        # Side by side they need room for the name tags over their heads.
        tags = 0.36 if abs(a[5] - b[5]) < 0.16 else 0.0
        if abs(a[3] - b[3]) >= max(half(a) + half(b) + gap, tags):
            return False
        return a[4] < b[5] + 0.16 and b[4] < a[5] + 0.16
    mine = [0, 0, 0, me[0], me[1], me[2], 0]
    cand = []
    for s in spots:
        dx, dy, dz, x, feet, head, far = s
        if dx == 0 and dz == 0:
            continue
        if far < 5.6 or far > 22 or abs(dy) > 3:
            continue
        if head > 0.60 or feet < -0.90 or abs(x) + half(s) > 1.66:   # under the top bar and its name tag, on the canvas
            continue
        if x + half(s) > 0.66 and feet < 0.04:         # behind the play panel
            continue
        if x - half(s) < -0.72 and feet < -0.70:       # behind the friends bar
            continue
        if clash(s, mine, 0.14):
            continue
        cand.append(s)
    # Places wanted: (across, how far). The nearest free spot to each is taken. The menu shows two friends,
    # so the first two are the ones that count: one to each side of the player, a little further back.
    chosen = []
    for tx, tfar in ((-0.8, 9.0), (0.55, 11.5), (-0.3, 15.0), (-1.3, 12.5)):
        best = None
        for s in cand:
            if any(clash(s, c, 0.1) for c in chosen):
                continue
            # Not two on the same block, and not shoulder to shoulder in the world either.
            if any(abs(s[0] - c[0]) < 2 and abs(s[2] - c[2]) < 2 for c in chosen):
                continue
            cost = abs(s[3] - tx) + abs(s[6] - tfar) * 0.07 + abs(s[1]) * 0.5
            if best is None or cost < best[0]:
                best = (cost, s)
        if best and best[0] < 1.6:
            chosen.append(best[1])
    # A place with little room: rather a second friend a bit off the wanted spot than none.
    if len(chosen) < 2:
        rest = [s for s in cand if s not in chosen and not any(clash(s, c, 0.06) for c in chosen)
                and not any(abs(s[0] - c[0]) < 2 and abs(s[2] - c[2]) < 2 for c in chosen)]
        rest.sort(key=lambda s: abs(s[6] - 10.0) * 0.07 + abs(s[1]) * 0.5)
        for s in rest:
            if len(chosen) >= 2:
                break
            if not any(clash(s, c, 0.06) for c in chosen):
                chosen.append(s)
    return [[s[0], s[1], s[2]] for s in chosen]


def main():
    src = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    out = []
    for sid, name, snowfall, rain, lit in PICKS:
        path = os.path.join(src, sid + '.json')
        if not os.path.exists(path):
            print('skip', sid, '(not exported)')
            continue
        data = json.load(open(path))
        quads = data['quads']
        spots = friends(data['spots'], data['me'])
        model = {'yaw': data['yaw'], 'pitch': data['pitch'], 'dist': data['dist'], 'fov': data['fov'], 'far': data['far'], 'sun': data.get('sun'), 'textures': data['textures'], 'quads': quads}
        with open(os.path.join(OUT, sid + '.json'), 'w') as f:
            json.dump(model, f, separators=(',', ':'))
        out.append(dict(id=sid, name=name, snowfall=snowfall, rain=rain, spots=spots, **lit))
        print('%-18s %5d faces, %2d textures, %d friend spots, %d kB' % (sid, len(quads), len(model['textures']), len(spots), os.path.getsize(os.path.join(OUT, sid + '.json')) // 1024))
    with open(os.path.join(OUT, 'scenes.json'), 'w') as f:
        json.dump(out, f, indent=1)
        f.write('\n')
    print(len(out), 'scenes')


if __name__ == '__main__':
    main()
