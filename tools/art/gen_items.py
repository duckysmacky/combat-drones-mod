#!/usr/bin/env python3
"""Hand-drawn 16x16 item sprites (and the goggles armour layer / rearm station block), as character grids.

Run: python3 tools/art/gen_items.py [--preview FILE]
Each sprite is 16 strings of 16 characters; '.' is transparent, other characters index the palette given with it.
"""
import sys
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/combat_drones"

OUTLINE = "#141516"
BASE = {"K": OUTLINE}


def pal(**colors):
    palette = dict(BASE)
    palette.update(colors)
    return palette


def battery(label, label_dark, rows):
    return rows, pal(b="#2a2d31", B="#41464c", Y=label, y=label_dark, w="#ececec", r="#c0392b", k="#3a3a3a",
                     o="#e2b93b", O="#a8862a")


SPRITES = {
    "light_battery": battery("#d9b23a", "#a8862a", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..KKKKKKKKKK....",
        "..KBBBBBBBBK....",
        "..KbYYYYYYbKrr..",
        "..KbYwwyYYbKkkr.",
        "..KbYYYYYYbK..rK",
        "..KbbbbbbbbK..oK",
        "..KKKKKKKKKK..OK",
        "..............KK",
        "................",
        "................",
        "................",
    ]),
    "standard_battery": battery("#3f86d6", "#2b5f9c", [
        "................",
        "................",
        "................",
        ".KKKKKKKKKKKK...",
        ".KBBBBBBBBBBK...",
        ".KbYYYYYYYYbKrr.",
        ".KbYwwwyyYYbKkkr",
        ".KbYYYYYYYYbK..r",
        ".KbYyyYYYYYbK..K",
        ".KbYYYYYYYYbK.oK",
        ".KbbbbbbbbbbK.oK",
        ".KKKKKKKKKKKK.OK",
        "..............KK",
        "................",
        "................",
        "................",
    ]),
    "heavy_battery": battery("#c94a3a", "#8f3327", [
        "................",
        "................",
        "KKKKKKKKKKKKK...",
        "KBBBBBBBBBBBK...",
        "KbYYYYYYYYYbKrr.",
        "KbYwwwwyYYYbKkkr",
        "KbYYYYYYYYYbK..r",
        "KbbbbbbbbbbbK..K",
        "KBBBBBBBBBBBK.oK",
        "KbYYYYYYYYYbK.oK",
        "KbYyyyYYYYYbK.OK",
        "KbYYYYYYYYYbK.KK",
        "KbbbbbbbbbbbK...",
        "KKKKKKKKKKKKK...",
        "................",
        "................",
    ]),
    "fuel_can": ([
        "................",
        "...KKKK.........",
        "..KgGGgK........",
        "..KKKKKKKKKKK...",
        "..KGGGGGGGGGGK..",
        ".KGHHHHHHHHHGK..",
        ".KGHgGGGGGgHGK..",
        ".KGHGgGGGgGHGK..",
        ".KGHGGgGgGGHGK..",
        ".KGHGGGgGGGHGK..",
        ".KGHGGgGgGGHGK..",
        ".KGHGgGGGgGHGK..",
        ".KGHgGGGGGgHGK..",
        ".KGHHHHHHHHHGK..",
        ".KdddddddddddK..",
        "..KKKKKKKKKKK...",
    ], pal(G="#56632c", H="#6b7a38", g="#3c4520", d="#2f3619")),
    "fpv_remote": ([
        "................",
        ".Ks.............",
        ".Ka.............",
        "..Ks............",
        "..Ka..k....k....",
        "...K..S....S....",
        ".KKKKKKKKKKKKKK.",
        ".KddddddddddddK.",
        ".KdcCcdeeedcCcdK",
        ".KdCOCdeEedCOCdK",
        ".KdcCcdeeedcCcdK",
        ".KddsddddddsdddK",
        ".KDDDDDDDDDDDDDK",
        ".KDDK.....KDDDK.",
        "..KK.......KKK..",
        "................",
    ], pal(s="#8d9196", a="#5b6066", d="#3a3e43", D="#2a2d31", c="#22252a", C="#55595e", O="#a9adb2",
           k="#a9adb2", S="#5b6066", e="#2b5f9c", E="#6fb0f0")),
    "fpv_goggles": ([
        "................",
        "................",
        "................",
        "................",
        "..KKKKKKKKKKKK..",
        ".KddddddddddddK.",
        "sKdKKKKddKKKKdKs",
        "sKdKlLKddKlLKdKs",
        "sKdKllKddKllKdKs",
        "sKdKKKKddKKKKdKs",
        ".KDdddKKKKdddDK.",
        ".KKKKKK..KKKKKK.",
        "................",
        "................",
        "................",
        "................",
    ], pal(d="#3a3e43", D="#2a2d31", l="#1f3a5c", L="#7ab8f0", s="#55595e")),
    "mission_planner": ([
        "................",
        ".KKKKKKKKKKKKKK.",
        ".KooooooooooooK.",
        ".KoKKKKKKKKKKoK.",
        ".KoKmmmmMmmmKoK.",
        ".KoKmMMmmMmmKoK.",
        ".KoKmmmMmmmmKoK.",
        ".KoKmmrmMmmwKoK.",
        ".KoKmmmmmMMmKoK.",
        ".KoKMmmmmmmmKoK.",
        ".KoKmMmmwmmmKoK.",
        ".KoKKKKKKKKKKoK.",
        ".KooooboobooooK.",
        ".KOOOOOOOOOOOOK.",
        ".KKKKKKKKKKKKKK.",
        "................",
    ], pal(o="#4f5a2c", O="#3a4220", m="#1f3b26", M="#3f7a4a", r="#e0413a", w="#e8e8e8", b="#8d9196")),
    "missile_control_tablet": ([
        "................",
        "................",
        ".KKKKKKKKKKKKKK.",
        "KooKKKKKKKKKKooK",
        "KoKmmmMmmmmmmKoK",
        "KoKmMMmmmMmmmKoK",
        "KoKmmmmrmmMmmKoK",
        "KbKmmMmmmmmMmKoK",
        "KoKmmmMmmmmmmKoK",
        "KoKMmmmmmMmmmKoK",
        "KoKmmmmmmmmMmKoK",
        "KooKKKKKKKKKKooK",
        ".KOOOOOOOOOOOOK.",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
    ], pal(o="#4f5a2c", O="#3a4220", m="#1f3b26", M="#3f7a4a", r="#ff3b30", b="#8d9196")),
    "frag_grenade": ([
        "................",
        ".........KK.....",
        "........KrK.....",
        ".......KKKKK....",
        "......KsssssK...",
        ".....KKKKKKKsK..",
        "....KGGGGGGGKsK.",
        "...KGHHGGGGGGKsK",
        "...KGHGGGGGGGKK.",
        "...KYYYYYYYYYK..",
        "...KGGGGGGGGGK..",
        "...KGGGGGGGGgK..",
        "....KGGGGGGgK...",
        ".....KgggggK....",
        "......KKKKK.....",
        "................",
    ], pal(G="#566034", H="#6f7b45", g="#3d4524", Y="#d6b23a", s="#8d9196", r="#c9c9c9")),
    "bomb_light": ([
        "................",
        "................",
        "................",
        "......KKK.......",
        ".....KbBbK......",
        "....KGGGGGK.....",
        "....KGHGGGK.....",
        "....KGHGGGK.....",
        "....KYYYYYK.....",
        "....KGGGGgK.....",
        ".....KdddK......",
        "....KKfdfKK.....",
        "....KfffffK.....",
        ".....KKKKK......",
        "................",
        "................",
    ], pal(G="#5c6238", H="#737a48", g="#454a29", Y="#b08d3c", b="#8a6d2e", B="#c9a24a", d="#4a4e53", f="#4a4f45")),
    "bomb": ([
        "......KK........",
        ".....KbBK.......",
        ".....KbBK.......",
        "....KGGGGK......",
        "...KGHGGGgK.....",
        "...KGHGGGgK.....",
        "...KYYYYYYK.....",
        "...KGHGGGgK.....",
        "...KGHGGGgK.....",
        "...KGHGGGgK.....",
        "....KGGGgK......",
        ".....KddK.......",
        "....KKddKK......",
        "...KffddffK.....",
        "...KffffffK.....",
        "....KKKKKK......",
    ], pal(G="#4e5a35", H="#677450", g="#38422a", Y="#d6b23a", b="#8a6d2e", B="#c9a24a", d="#4a4e53", f="#4a4f45")),
    "bomb_heavy": ([
        ".....KKKK.......",
        "....KbBBbK......",
        "...KGGGGGGK.....",
        "..KGHGGGGGgK....",
        "..KGHGGGGGgK....",
        "..KRRRRRRRRK....",
        "..KYYYYYYYYK....",
        "..KGHGGGGGgK....",
        "..KGHGGGGGgK....",
        "..KGHGGGGGgK....",
        "...KGGGGGgK.....",
        "....KKddKK......",
        "...KffddffK.....",
        "..KfffddfffK....",
        "..KffffffffK....",
        "...KKKKKKKK.....",
    ], pal(G="#3d4a31", H="#556646", g="#2b3522", R="#b8302a", Y="#d6b23a", b="#8a6d2e", B="#c9a24a", d="#4a4e53",
           f="#4a4f45")),
    "rocket": ([
        "................",
        ".............KK.",
        "............KbbK",
        "...........KGGbK",
        "..........KGHGK.",
        ".........KGHGK..",
        "........KWWGK...",
        ".......KGHGK....",
        "......KGHGK.....",
        ".....KGHGK......",
        "..KKKGHGK.......",
        "..KffGGK........",
        "...KfnK.........",
        "..KfKnfK........",
        ".KffK.KK........",
        ".KKK............",
    ], pal(G="#59603a", H="#737b4c", W="#e0e0d8", b="#8a8f6a", f="#3f442c", n="#2a2a2a")),
    "rifle_rounds": ([
        "................",
        "................",
        "...K..K..K..K...",
        "..KcKKcKKcKKcK..",
        "..KcKKcKKcKKcK..",
        "..KbKKbKKbKKbK..",
        "..KBKKBKKBKKBK..",
        "..KBKKBKKBKKBK..",
        "..KBKKBKKBKKBK..",
        ".KKKKKKKKKKKKKK.",
        ".KllllllllllllK.",
        ".KlLLLLLLLLLLlK.",
        ".KlLwwLLLLLLLlK.",
        ".KlLLLLLLLLLLlK.",
        ".KllllllllllllK.",
        ".KKKKKKKKKKKKKK.",
    ], pal(c="#b87333", b="#a8862a", B="#d9b23a", l="#3d4524", L="#56632c", w="#e8e8e8")),
}

BLOCKS = {
    "rearm_station": ([
        "KKKKKKKKKKKKKKKK",
        "KmmmmmmmmmmmmmmK",
        "KmMMMMMMMMMMMMmK",
        "KmMddddddddddMmK",
        "KmMdddddYdddDMmK",
        "KmMddddYYdddDMmK",
        "KmMdddYYYYddDMmK",
        "KmMdddddYYddDMmK",
        "KmMddddYYdddDMmK",
        "KmMdddYdddddDMmK",
        "KmMDDDDDDDDDDMmK",
        "KmMMMMMMMMMMMMmK",
        "KyKyKyKyKyKyKyKK",
        "KKyKyKyKyKyKyKyK",
        "KmmmmmmmmmmmmmmK",
        "KKKKKKKKKKKKKKKK",
    ], pal(m="#5b6066", M="#73787e", d="#2a2d31", D="#3a3e43", Y="#e8c23a", y="#d9b02a")),
}


def draw(rows, palette, size=16):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == size, f"row {y} is {len(row)} wide: {row!r}"
        for x, char in enumerate(row):
            if char != ".":
                color = palette[char].lstrip("#")
                image.putpixel((x, y), tuple(int(color[i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    return image


def goggles_layer():
    """Armour layer 1 (64x32): only the head part is used. Strap all round, goggle box over the eyes."""
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    strap, box, rim, lens, glint = (42, 45, 49), (58, 62, 67), (31, 33, 36), (31, 58, 92), (122, 184, 240)
    # Head faces in the 64x32 layout: right 0-8, front 8-16, left 16-24, back 24-32, rows 8-16
    for x in range(0, 32):
        for y in (11, 12):
            image.putpixel((x, y), strap + (255,))
    for x in range(8, 16):
        for y in range(10, 14):
            image.putpixel((x, y), box + (255,))
    for x in range(8, 16):
        image.putpixel((x, 10), rim + (255,))
        image.putpixel((x, 13), rim + (255,))
    for x0 in (9, 13):
        for x in range(x0, x0 + 2):
            image.putpixel((x, 11), lens + (255,))
            image.putpixel((x, 12), lens + (255,))
        image.putpixel((x0, 11), glint + (255,))
    # Top of the head: the strap crossing over
    for y in range(0, 8):
        image.putpixel((12, y), strap + (255,))
        image.putpixel((11, y), strap + (255,))
    return image


def main(args):
    sheet = []
    for name, (rows, palette) in SPRITES.items():
        image = draw(rows, palette)
        image.save(ASSETS / "textures/items" / (name + ".png"))
        sheet.append(image)
    for name, (rows, palette) in BLOCKS.items():
        image = draw(rows, palette)
        image.save(ASSETS / "textures/blocks" / (name + ".png"))
        sheet.append(image)
    goggles_layer().save(ASSETS / "textures/models/armor/goggles_layer_1.png")
    if "--preview" in args:
        out = Path(args[args.index("--preview") + 1])
        preview = Image.new("RGBA", (len(sheet) * 18 * 6, 18 * 6), (90, 96, 104, 255))
        for index, image in enumerate(sheet):
            preview.alpha_composite(image.resize((96, 96), Image.NEAREST), (index * 108 + 6, 6))
        preview.save(out)
    print(f"{len(sheet)} sprites written")


if __name__ == "__main__":
    main(sys.argv[1:])
