"""Tiny Bedrock geo.json authoring kit: cube models with procedurally painted per-face textures, plus a software
preview renderer that reproduces GeckoLib 1.12's geometry (and the mod's Z mirror) so models can be checked as PNGs.

Coordinates are Blockbench's: pixels, Y up, nose toward north (-Z), +X = the model's right. Models are centred on
the hitbox centre (vehicles) or stand on y = 0 (the launch ramp).
"""
import json
import math
import random
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

FACES = ("north", "south", "east", "west", "up", "down")


def rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


class Mat:
    """A paint: base colour, texel noise, darker panel edges and optional pattern layers."""

    def __init__(self, color, noise=0.05, edge=0.18, patterns=(), shine=0.0):
        self.color = rgb(color) if isinstance(color, str) else color
        self.noise = noise
        self.edge = edge
        self.patterns = patterns
        self.shine = shine

    def paint(self, width, height, seed, face):
        rng = np.random.default_rng(seed)
        tile = np.empty((height, width, 3), dtype=np.float32)
        tile[:] = self.color
        if self.noise:
            tile *= 1 + rng.normal(0, self.noise, (height, width, 1)).astype(np.float32)
        if self.shine and face == "up":
            gradient = np.linspace(1 + self.shine, 1, height, dtype=np.float32)[:, None, None]
            tile *= gradient
        for pattern in self.patterns:
            pattern(tile, rng, face)
        if self.edge and width > 2 and height > 2:
            tile[0, :] *= 1 - self.edge
            tile[-1, :] *= 1 - self.edge
            tile[:, 0] *= 1 - self.edge
            tile[:, -1] *= 1 - self.edge
        return np.clip(tile, 0, 255).astype(np.uint8)


class BlurDisc:
    """Spinning-prop disc: a dithered ring of dark texels on a transparent face (alpha-tested in game)."""

    def __init__(self, color="#3a3d40", density=0.28):
        self.color = rgb(color)
        self.density = density

    def paint(self, width, height, seed, face):
        rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:height, 0:width]
        radius = np.hypot((xs + 0.5) / width - 0.5, (ys + 0.5) / height - 0.5) * 2
        keep = (radius <= 1.0) & (radius >= 0.15) & (rng.random((height, width)) < self.density * (0.5 + 0.5 * radius))
        tile = np.zeros((height, width, 4), dtype=np.uint8)
        tile[..., :3] = self.color
        tile[..., 3] = np.where(keep | (radius < 0.15), 255, 0)
        return tile


# ---- pattern layers: (tile, rng, face) -> None, modify tile in place ------------------------------------------------

def carbon(tile, rng, face):
    """Woven carbon: alternating 2x2 light/dark texels."""
    height, width, _ = tile.shape
    ys, xs = np.mgrid[0:height, 0:width]
    tile *= np.where(((xs // 2) + (ys // 2)) % 2 == 0, 1.18, 0.85)[..., None]


def band(start, end, color, axis="u"):
    """A painted band across the face, from fraction start to end along u (width) or v (height)."""
    paint = rgb(color)

    def apply(tile, rng, face):
        height, width, _ = tile.shape
        size = width if axis == "u" else height
        lo, hi = int(round(start * size)), max(int(round(start * size)) + 1, int(round(end * size)))
        if axis == "u":
            tile[:, lo:hi] = paint
        else:
            tile[lo:hi, :] = paint
        tile *= 1 + rng.normal(0, 0.03, tile.shape[:2] + (1,)).astype(np.float32)
    return apply


def zband(start, end, color):
    """A ring painted round a body that runs along Z: fractions from the nose (min Z) end. End caps stay clear."""
    paint = rgb(color)

    def apply(tile, rng, face):
        height, width, _ = tile.shape
        if face in ("north", "south"):
            return
        size = width if face in ("east", "west") else height
        lo = int(round(start * size))
        hi = max(lo + 1, int(round(end * size)))
        if face in ("east", "up"):  # these faces run tail-to-nose in texture space
            lo, hi = size - hi, size - lo
        if face in ("east", "west"):
            tile[:, lo:hi] = paint
        else:
            tile[lo:hi, :] = paint
    return apply


def on_faces(faces, pattern):
    def apply(tile, rng, face):
        if face in faces:
            pattern(tile, rng, face)
    return apply


def panel_lines(every=6, axis="u", darkness=0.25):
    def apply(tile, rng, face):
        height, width, _ = tile.shape
        if axis == "u":
            for x in range(every, width - 1, every):
                tile[:, x] *= 1 - darkness
        else:
            for y in range(every, height - 1, every):
                tile[y, :] *= 1 - darkness
    return apply


def vents(color="#1c1e20", spacing=2):
    paint = rgb(color)

    def apply(tile, rng, face):
        height, width, _ = tile.shape
        for y in range(1, height - 1, spacing):
            tile[y, 1:width - 1] = paint
    return apply


def glass(tile, rng, face):
    """Dark glass with a soft highlight in one corner."""
    height, width, _ = tile.shape
    ys, xs = np.mgrid[0:height, 0:width]
    tile[:] = (18, 22, 30)
    glow = np.clip(1 - np.hypot(xs / max(width, 1) - 0.3, ys / max(height, 1) - 0.3) * 2.2, 0, 1)
    tile += (glow * 90)[..., None] * np.array([0.7, 0.85, 1.0], dtype=np.float32)


def rivets(spacing=4, darkness=0.3):
    def apply(tile, rng, face):
        height, width, _ = tile.shape
        tile[1:height - 1:spacing, 1:width - 1:spacing] *= 1 - darkness
    return apply


def grime(amount=0.25):
    """Dirt streaks running down (v)."""
    def apply(tile, rng, face):
        height, width, _ = tile.shape
        streaks = rng.random(width) < amount
        fade = np.linspace(0.75, 1.0, height, dtype=np.float32)
        tile[:, streaks] *= fade[:, None, None]
    return apply


def camo(colors, scale=3):
    paints = [rgb(color) for color in colors]

    def apply(tile, rng, face):
        height, width, _ = tile.shape
        cells = rng.integers(0, len(paints), ((height + scale - 1) // scale + 1, (width + scale - 1) // scale + 1))
        for y in range(height):
            for x in range(width):
                pick = cells[y // scale, x // scale]
                if pick:
                    tile[y, x] = tile[y, x] * 0.4 + np.array(paints[pick], dtype=np.float32) * 0.6
    return apply


def dot(u, v, color, radius=0.5):
    """A round marking centred at (u, v) as fractions of the face."""
    paint = rgb(color)

    def apply(tile, rng, face):
        height, width, _ = tile.shape
        ys, xs = np.mgrid[0:height, 0:width]
        mask = np.hypot((xs + 0.5) - u * width, (ys + 0.5) - v * height) <= radius * min(width, height)
        tile[mask] = paint
    return apply


# ---- model --------------------------------------------------------------------------------------------------------

class Bone:
    def __init__(self, model, name, pivot, rotation, parent):
        self.model = model
        self.name = name
        self.pivot = list(pivot)
        self.rotation = list(rotation) if rotation else None
        self.parent = parent
        self.cubes = []

    def cube(self, origin, size, mat, rotation=None, pivot=None, faces=None, mirror_paint=None):
        """origin = min corner (Blockbench), size in pixels. mat: Mat or {face: Mat}. faces: subset to keep."""
        self.cubes.append(dict(origin=list(origin), size=list(size), mat=mat, rotation=rotation, pivot=pivot,
                               faces=faces))
        return self

    def box(self, center, size, mat, **kwargs):
        """Cube by centre instead of min corner."""
        origin = [center[i] - size[i] / 2 for i in range(3)]
        return self.cube(origin, size, mat, **kwargs)

    def cylinder_z(self, center, diameter, length, mat, sides=2, **kwargs):
        """A round-ish body along Z: one box plus copies turned about Z (sides=2 -> octagon)."""
        size = [diameter, diameter, length]
        for index in range(sides):
            angle = index * 90 / sides
            self.box(center, size, mat, rotation=[0, 0, angle] if angle else None,
                     pivot=list(center) if angle else None, **kwargs)
        return self

    def cylinder_y(self, center, diameter, height, mat, sides=2, **kwargs):
        size = [diameter, height, diameter]
        for index in range(sides):
            angle = index * 90 / sides
            self.box(center, size, mat, rotation=[0, angle, 0] if angle else None,
                     pivot=list(center) if angle else None, **kwargs)
        return self

    def cylinder_x(self, center, diameter, length, mat, sides=2, **kwargs):
        size = [length, diameter, diameter]
        for index in range(sides):
            angle = index * 90 / sides
            self.box(center, size, mat, rotation=[angle, 0, 0] if angle else None,
                     pivot=list(center) if angle else None, **kwargs)
        return self


class Model:
    def __init__(self, name, density=2, scale=1.0):
        """scale grows the whole model (geometry, not texel size) without rewriting its cube numbers."""
        self.name = name
        self.density = density
        self.scale = scale
        self.bones = []

    def bone(self, name, pivot=(0, 0, 0), rotation=None, parent=None):
        bone = Bone(self, name, pivot, rotation, parent)
        self.bones.append(bone)
        return bone

    # -- texture packing --
    def _face_size(self, cube, face):
        sx, sy, sz = (value * self.scale for value in cube["size"])
        return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                "up": (sx, sz), "down": (sx, sz)}[face]

    def build(self):
        tiles = []
        for bone_index, bone in enumerate(self.bones):
            for cube_index, cube in enumerate(bone.cubes):
                cube["uv"] = {}
                for face in FACES:
                    if cube["faces"] is not None and face not in cube["faces"]:
                        continue
                    width, height = self._face_size(cube, face)
                    if width <= 0 or height <= 0:
                        continue
                    texels = (max(1, int(math.ceil(width * self.density - 1e-6))),
                              max(1, int(math.ceil(height * self.density - 1e-6))))
                    mat = cube["mat"][face] if isinstance(cube["mat"], dict) else cube["mat"]
                    if mat is None:
                        continue
                    seed = zlib.crc32(f"{self.name}/{bone.name}/{cube_index}/{face}".encode())
                    tiles.append((cube, face, texels, mat, seed))
        # Shelf packing, tallest first
        tiles.sort(key=lambda tile: (-tile[2][1], -tile[2][0]))
        size = 32
        while True:
            placements = self._pack([tile[2] for tile in tiles], size)
            if placements is not None:
                break
            size *= 2
        image = np.zeros((size, size, 4), dtype=np.uint8)
        for (cube, face, (width, height), mat, seed), (x, y) in zip(tiles, placements):
            tile = mat.paint(width, height, seed, face)
            image[y:y + height, x:x + width, :3] = tile[..., :3]
            image[y:y + height, x:x + width, 3] = tile[..., 3] if tile.shape[2] == 4 else 255
            cube["uv"][face] = {"uv": [x, y], "uv_size": [width, height]}
        self.texture = Image.fromarray(image, "RGBA")
        self.texture_size = size

    @staticmethod
    def _pack(sizes, atlas):
        x = y = shelf = 0
        placements = []
        for width, height in sizes:
            if width > atlas:
                return None
            if x + width > atlas:
                x, y, shelf = 0, y + shelf, 0
            if y + height > atlas:
                return None
            placements.append((x, y))
            x += width
            shelf = max(shelf, height)
        return placements

    def geo_json(self):
        bones = []
        for bone in self.bones:
            scaled = lambda values: [round(value * self.scale, 4) for value in values]
            entry = {"name": bone.name, "pivot": scaled(bone.pivot)}
            if bone.parent:
                entry["parent"] = bone.parent
            if bone.rotation:
                entry["rotation"] = bone.rotation
            cubes = []
            for cube in bone.cubes:
                if not cube["uv"]:
                    continue
                item = {"origin": scaled(cube["origin"]), "size": scaled(cube["size"]), "uv": cube["uv"]}
                if cube["rotation"]:
                    item["pivot"] = scaled(cube["pivot"])
                    item["rotation"] = cube["rotation"]
                cubes.append(item)
            if cubes:
                entry["cubes"] = cubes
            bones.append(entry)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.name,
                    "texture_width": self.texture_size,
                    "texture_height": self.texture_size,
                    "visible_bounds_width": 6,
                    "visible_bounds_height": 3,
                    "visible_bounds_offset": [0, 0.5, 0],
                },
                "bones": bones,
            }],
        }

    def save(self, assets):
        self.build()
        geo = Path(assets) / "geo" / (self.name + ".geo.json")
        geo.parent.mkdir(parents=True, exist_ok=True)
        geo.write_text(json.dumps(self.geo_json(), indent=1))
        texture = Path(assets) / "textures" / "entity" / (self.name + ".png")
        texture.parent.mkdir(parents=True, exist_ok=True)
        self.texture.save(texture)
        return geo, texture


# ---- preview: GeckoLib 1.12 geometry, then the mod's Z mirror -----------------------------------------------------

def _rot(axis, radians):
    c, s = math.cos(radians), math.sin(radians)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _affine(matrix=None, translation=(0, 0, 0)):
    result = np.eye(4)
    if matrix is not None:
        result[:3, :3] = matrix
    result[:3, 3] = translation
    return result


def load_quads(geo_path, texture_path, bone_rotations=None):
    """Quads in render space (blocks, nose +Z after the mod's mirror), each with UVs, as GeckoLib builds them."""
    data = json.loads(Path(geo_path).read_text())["minecraft:geometry"][0]
    tex_w = data["description"]["texture_width"]
    tex_h = data["description"]["texture_height"]
    texture = np.asarray(Image.open(texture_path).convert("RGBA"), dtype=np.float32)
    by_name = {bone["name"]: bone for bone in data["bones"]}
    children = {}
    for bone in data["bones"]:
        children.setdefault(bone.get("parent"), []).append(bone)
    quads = []
    bone_rotations = bone_rotations or {}

    def bone_matrix(bone):
        pivot = bone.get("pivot", [0, 0, 0])
        rotation = bone_rotations.get(bone["name"], bone.get("rotation", [0, 0, 0]))
        rx, ry, rz = math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])
        point = np.array([-pivot[0], pivot[1], pivot[2]]) / 16
        rot = _rot("z", rz) @ _rot("y", ry) @ _rot("x", rx)
        return _affine(None, point) @ _affine(rot) @ _affine(None, -point)

    def visit(bone, parent_matrix):
        if bone["name"].endswith("_blur"):
            return  # the game shows these only while spinning fast
        matrix = parent_matrix @ bone_matrix(bone)
        for cube in bone.get("cubes", []):
            size = np.array(cube["size"], dtype=float)
            origin = np.array(cube["origin"], dtype=float)
            ox, oy, oz = -(origin[0] + size[0]) / 16, origin[1] / 16, origin[2] / 16
            sx, sy, sz = size / 16
            p = {1: (ox, oy, oz), 2: (ox, oy, oz + sz), 3: (ox, oy + sy, oz), 4: (ox, oy + sy, oz + sz),
                 5: (ox + sx, oy, oz), 6: (ox + sx, oy, oz + sz), 7: (ox + sx, oy + sy, oz),
                 8: (ox + sx, oy + sy, oz + sz)}
            cube_matrix = np.eye(4)
            if "rotation" in cube:
                rotation = cube["rotation"]
                rx, ry, rz = math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])
                pivot = np.array([-cube["pivot"][0], cube["pivot"][1], cube["pivot"][2]]) / 16
                rot = _rot("z", rz) @ _rot("y", ry) @ _rot("x", rx)
                cube_matrix = _affine(None, pivot) @ _affine(rot) @ _affine(None, -pivot)
            order = {"west": (4, 3, 1, 2), "east": (7, 8, 6, 5), "north": (3, 7, 5, 1), "south": (8, 4, 2, 6),
                     "up": (4, 8, 7, 3), "down": (1, 5, 6, 2)}
            for face, uv in cube["uv"].items():
                u1, v1 = uv["uv"]
                u2, v2 = u1 + uv["uv_size"][0], v1 + uv["uv_size"][1]
                corners = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
                points = []
                for index in order[face]:
                    vertex = matrix @ cube_matrix @ np.array([*p[index], 1.0])
                    points.append([vertex[0], vertex[1], -vertex[2]])  # the mod's Z mirror
                quads.append((np.array(points), np.array(corners, dtype=float)))
        for child in children.get(bone["name"], []):
            visit(child, matrix)

    for bone in children.get(None, []):
        visit(bone, np.eye(4))
    return quads, texture


def render_views(geo_path, texture_path, out_path, size=360, bone_rotations=None, title=""):
    """Sheet of four views: iso, top (nose up), side (nose right), front. Flat-lit, nearest-texel sampling."""
    quads, texture = load_quads(geo_path, texture_path, bone_rotations)
    every = np.concatenate([quad[0] for quad in quads])
    centre = (every.min(0) + every.max(0)) / 2
    extent = np.max(every.max(0) - every.min(0))
    views = [
        ("iso", _rot("x", math.radians(30)) @ _rot("y", math.radians(-135))),
        ("top", _rot("x", math.radians(90)) @ _rot("y", math.radians(180))),
        ("side", _rot("y", math.radians(-90))),
        ("front", _rot("y", math.radians(180))),
    ]
    sheet = Image.new("RGB", (size * 2, size * 2), (60, 64, 70))
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    for view_index, (name, view) in enumerate(views):
        colour = np.zeros((size, size, 3), dtype=np.float32)
        colour[:] = (84, 92, 104) if view_index % 3 == 0 else (74, 80, 90)
        depth = np.full((size, size), -np.inf)
        scale = size * 0.85 / extent
        for points, uvs in quads:
            # view space: x right, y up, z toward the viewer (view rotates the model)
            local = (points - centre) @ view.T
            normal = np.cross(points[1] - points[0], points[2] - points[0])
            if np.linalg.norm(normal) < 1e-12:
                normal = np.cross(points[2] - points[0], points[3] - points[0])
            normal = normal / (np.linalg.norm(normal) + 1e-12)
            shade = 0.45 + 0.55 * abs(float(np.dot(normal, light)))
            screen = np.stack([size / 2 + local[:, 0] * scale, size / 2 - local[:, 1] * scale, local[:, 2]], 1)
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster(colour, depth, screen[list(tri)], uvs[list(tri)], texture, shade)
        tile = Image.fromarray(np.clip(colour, 0, 255).astype(np.uint8))
        sheet.paste(tile, ((view_index % 2) * size, (view_index // 2) * size))
    sheet.save(out_path)


def _raster(colour, depth, tri, uvs, texture, shade):
    height, width = depth.shape
    xs, ys = tri[:, 0], tri[:, 1]
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(width - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(height - 1, math.ceil(ys.max())))
    if x0 > x1 or y0 > y1:
        return
    area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
    if abs(area) < 1e-9:
        return
    py, px = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
    w0 = ((xs[1] - px) * (ys[2] - py) - (xs[2] - px) * (ys[1] - py)) / area
    w1 = ((xs[2] - px) * (ys[0] - py) - (xs[0] - px) * (ys[2] - py)) / area
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    if not inside.any():
        return
    z = w0 * tri[0, 2] + w1 * tri[1, 2] + w2 * tri[2, 2]
    region = depth[y0:y1 + 1, x0:x1 + 1]
    u = w0 * uvs[0, 0] + w1 * uvs[1, 0] + w2 * uvs[2, 0]
    v = w0 * uvs[0, 1] + w1 * uvs[1, 1] + w2 * uvs[2, 1]
    tu = np.clip(u.astype(int), 0, texture.shape[1] - 1)
    tv = np.clip(v.astype(int), 0, texture.shape[0] - 1)
    texel = texture[tv, tu]
    visible = inside & (z > region) & (texel[..., 3] > 0)
    region[visible] = z[visible]
    colour[y0:y1 + 1, x0:x1 + 1][visible] = texel[..., :3][visible] * shade
