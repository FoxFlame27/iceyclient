#!/usr/bin/env python3
"""Turns exported pieces of a world into what the scene main menu ships.

Input: a folder of <id>.json files written by the scene exporter (a
temporary in-game tool, kept outside the repository): every block face of
a piece of a dev world that can be seen from the viewer's side, plus every
spot in it where a player could stand in full view.

Output, under src/main/resources/assets/iceymod/scenes:
  <id>.json      the faces (the 3D model the menu draws)
  scenes.json    the list: name, how each place is lit, where friends stand

Usage: python3 tools/build_scenes.py <folder of exports>
"""
import json
import os
import sys

# How a place is lit. sun/day: colour and strength of direct sunlight (or moonlight); shade/ambient: the light
# of the sky where the sun does not reach; glow: lamps, torches, lava; fog*: the haze; sky*: behind everything;
# light: the tint on the players so they sit in the same light.
DAY = dict(day=0.80, sun='fff1d6', shade='a8c4f2', ambient=0.66, glow=0.9, fog='bcd6f4', fogStart=13, fogEnd=42, fogMax=0.7, skyTop='4f8fe6', skyBottom='d4e6fa', light='fff8ee')
DUSK = dict(day=0.78, sun='ff9c5c', shade='7f78c0', ambient=0.52, glow=1.25, fog='e09a80', fogStart=11, fogEnd=40, fogMax=0.74, skyTop='33367a', skyBottom='f7a070', light='f6cdb8')
DAWN = dict(day=0.66, sun='ffb7c4', shade='8f86c8', ambient=0.56, glow=1.2, fog='c0a8d4', fogStart=11, fogEnd=40, fogMax=0.74, skyTop='2c2a62', skyBottom='f0aebc', light='e2d2ea')
NIGHT = dict(day=0.30, sun='9fb6ff', shade='4a5e9e', ambient=0.34, glow=1.7, fog='0e1634', fogStart=10, fogEnd=38, fogMax=0.8, skyTop='050918', skyBottom='1a2650', light='a9b6de')

# export id, name shown in the menu, how likely it snows / rains there at a start, how it is lit
PICKS = [
    ('snowy_village', 'Snowy Village', 0.65, 0.0, DAY),
    ('plains_village', 'Plains Village', 0.0, 0.3, DAY),
    ('village_dusk', 'Village at Dusk', 0.0, 0.2, DUSK),
    ('savanna_village', 'Savanna Village', 0.0, 0.0, DAY),
    ('desert_village', 'Desert Village', 0.0, 0.0, dict(DAY, day=0.56, ambient=0.5, sun='ffe6b8', shade='b9c4e6', fog='ecd9b4', skyBottom='f2e2c0', light='fff0d6')),
    ('taiga_village', 'Taiga Village at Night', 0.0, 0.3, dict(NIGHT, day=0.42, ambient=0.46, shade='6478c0')),
    ('outpost', 'Pillager Outpost', 0.0, 0.25, DUSK),
    ('igloo', 'Igloo at Dawn', 0.7, 0.0, DAWN),
    ('trial_chambers', 'Trial Chambers', 0.0, 0.0, dict(day=0.0, sun='ffffff', shade='ffe2c4', ambient=0.36, glow=1.35, fog='120d0c', fogStart=12, fogEnd=36, fogMax=0.85, skyTop='0b0909', skyBottom='0b0909', light='ecccaa')),
    ('end_city', 'End City', 0.0, 0.0, dict(day=0.55, sun='e8d4ff', shade='b9a2e6', ambient=0.95, glow=1.0, fog='0d0a18', fogStart=13, fogEnd=42, fogMax=0.85, skyTop='07050e', skyBottom='1a1030', light='d4c6ec')),
    ('bastion', 'Bastion Remnant', 0.0, 0.0, dict(day=0.0, sun='ffffff', shade='ffb49a', ambient=0.5, glow=1.4, fog='2a0c0b', fogStart=11, fogEnd=36, fogMax=0.85, skyTop='190706', skyBottom='340e0b', light='dcb4a2')),
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'iceymod', 'scenes')


def friends(spots, mobs):
    """Up to five places for friends: in view, clear of the panels, not crowding each other or the player."""
    me = (0.0, -1.78)                                  # the player on screen, in blocks from the middle (chest height)
    cand = []
    for dx, dy, dz, xv, yv, zv in spots:
        if dx == 0 and dz == 0:
            continue
        if any(abs(dx - m[0]) < 2 and abs(dz - m[2]) < 2 for m in mobs):      # a mob stands there
            continue
        if zv < -3.6 or zv > 20 or abs(xv) > 6.4:      # a little in front of the player is fine too
            continue
        if yv > 2.8 or yv < -2.8:                      # head under the top bar, feet above the bottom edge
            continue
        if xv > 1.9 and yv < 1.6:                      # behind the play panel
            continue
        if xv < -3.2 and yv < -2.2:                    # behind the friends bar
            continue
        if abs(xv - me[0]) < 1.5 and abs(yv - me[1]) < 2.6:
            continue
        cand.append((dx, dy, dz, xv, yv, zv))
    chosen = []
    # First the ground the player stands on; a step up or down only if that leaves the place empty.
    for step in (1, 2):
        pool = [s for s in cand if abs(s[1]) <= step and s[4] <= (2.0 if step == 1 else 2.4)]
        pool.sort(key=lambda s: abs(s[5] - 4.0) + abs(abs(s[3]) - 3.2) * 0.6)
        for s in pool:
            if len(chosen) >= 5:
                break
            if s in chosen:
                continue
            if all(abs(s[3] - c[3]) >= 1.5 or abs(s[4] - c[4]) >= 2.7 for c in chosen):
                left = sum(1 for c in chosen if c[3] < 0)
                if (s[3] < 0 and left >= 3) or (s[3] >= 0 and len(chosen) - left >= 3):
                    continue
                chosen.append(s)
        if len(chosen) >= 3:
            break
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
        spots = friends(data['spots'], data.get('mobs', []))
        model = {'yaw': data['yaw'], 'pitch': data['pitch'], 'centre': data['centre'], 'sun': data.get('sun'), 'textures': data['textures'], 'quads': data['quads']}
        with open(os.path.join(OUT, sid + '.json'), 'w') as f:
            json.dump(model, f, separators=(',', ':'))
        out.append(dict(id=sid, name=name, snowfall=snowfall, rain=rain, spots=spots, **lit))
        print('%-18s %5d faces, %2d textures, %d friend spots, %d kB' % (sid, len(data['quads']), len(data['textures']), len(spots), os.path.getsize(os.path.join(OUT, sid + '.json')) // 1024))
    with open(os.path.join(OUT, 'scenes.json'), 'w') as f:
        json.dump(out, f, indent=1)
        f.write('\n')
    print(len(out), 'scenes')


if __name__ == '__main__':
    main()
