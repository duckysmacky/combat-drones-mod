#!/usr/bin/env python3
"""Generates every geo model + texture under src/main/resources/assets/combat_drones/{geo,textures/entity}.

Run from anywhere: python3 tools/art/gen_models.py [name ...] [--preview DIR]
Without names it builds all models. --preview also writes a 4-view PNG sheet per model (iso, top, side, front).

Once a model has been hand-edited in Blockbench, drop it from MODELS here so it isn't overwritten.
Coordinates: Blockbench pixels, Y up, nose toward north (-Z), +X = right. See geo.py.
"""
import sys
from pathlib import Path

import math

from geo import (mix, rgb, Mat, Model, BlurDisc, band, carbon, dot, glass, grime, on_faces, panel_lines, rivets, vents, zband)

ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/combat_drones"

GLASS = Mat("#141820", noise=0.0, edge=0.0, patterns=(glass,))
BLACK = Mat("#1f2124", noise=0.05, edge=0.15)
RUBBER = Mat("#151617", noise=0.04, edge=0.1)
STEEL = Mat("#8a8f94", noise=0.05, edge=0.2, shine=0.15)
DARK_STEEL = Mat("#4a4e53", noise=0.05, edge=0.2)
BRASS = Mat("#b08d3c", noise=0.06, edge=0.2, shine=0.15)
PROP = Mat("#232527", noise=0.03, edge=0.0)
BLUR = BlurDisc()


def faces_except(mat, other, *names):
    """A per-face paint: `other` on the named faces, `mat` elsewhere."""
    return {face: (other if face in names else mat) for face in ("north", "south", "east", "west", "up", "down")}


def two_blade_prop(model, name, pivot, span, chord=1.2, mat=PROP, blur_parent=None):
    """Plane propeller spinning about Z at `pivot`: blades + spinner, and a blur disc sibling."""
    x, y, z = pivot
    group = model.bone("hub_" + name, pivot=pivot, parent=blur_parent)
    blades = model.bone(name, pivot=pivot, parent=group.name)
    blades.box((x, y, z), (span, chord, 0.4), mat, rotation=[0, 0, 0], pivot=list(pivot))
    blades.box((x, y, z), (span * 0.55, chord * 1.25, 0.45), mat, rotation=[0, 0, 12], pivot=list(pivot))
    blades.box((x, y, z - 0.5), (1.2, 1.2, 1.4), DARK_STEEL)
    blur = model.bone(name + "_blur", pivot=pivot, parent=group.name)
    blur.box((x, y, z), (span, span, 0), BLUR, faces=("north", "south"))
    blur.box((x, y, z - 0.5), (1.2, 1.2, 1.4), DARK_STEEL)


def rotor(model, name, pivot, span, blades=2, mat=PROP, blade_width=0.7):
    """Multirotor prop spinning about Y at `pivot`, with a blur disc sibling."""
    x, y, z = pivot
    group = model.bone("hub_" + name, pivot=pivot)
    bone = model.bone(name, pivot=pivot, parent=group.name)
    for index in range(blades):
        angle = index * 360 / blades
        # One blade from the hub outward, pitched a little, then swung round the hub
        bone.box((x + span / 4, y, z), (span / 2, 0.2, blade_width), mat, rotation=[8, angle, 0], pivot=list(pivot))
    bone.box((x, y + 0.2, z), (0.6, 0.5, 0.6), DARK_STEEL)
    blur = model.bone(name + "_blur", pivot=pivot, parent=group.name)
    blur.box((x, y, z), (span, 0, span), BlurDisc(density=0.3), faces=("up", "down"))
    blur.box((x, y + 0.2, z), (0.6, 0.5, 0.6), DARK_STEEL)


# ---- ramp-launched units ------------------------------------------------------------------------------------------

def loitering_light():
    """Lancet / Hero-style: slim tube, tandem X wings, seeker nose, pusher prop. ~1.4 x 1.7 blocks."""
    skin = Mat("#d6d9d3", noise=0.03, edge=0.1, shine=0.08, patterns=(panel_lines(10, "v", 0.12),))
    wing = Mat("#dcdfd9", noise=0.03, edge=0.22)
    model = Model("loitering_light", density=3)
    body = model.bone("body")
    body.cylinder_z((0, 0, 0), 3.4, 24, skin)
    body.cylinder_z((0, 0, -12.8), 2.8, 1.8, Mat("#c9ccc6", noise=0.03, edge=0.1))
    body.box((0, 0, -13.85), (2.2, 2.2, 0.3), faces_except(skin, GLASS, "north"))
    body.box((0, -1.4, -10.5), (1.2, 0.8, 1.6), GLASS)  # belly camera window
    body.cylinder_z((0, 0, 12.8), 2.6, 1.8, Mat("#9a9fa3", noise=0.04, edge=0.15, patterns=(rivets(2),)))
    body.box((0, 2.0, -8), (0.3, 1.4, 1.6), BLACK)  # datalink blade antenna
    body.box((0, 0, -4), (3.5, 3.5, 0.6), Mat("#b9bdb7", noise=0.02, edge=0.0))  # warhead section seam
    for angle in (45, -45):
        for z, span, chord in ((-5.5, 24, 3.6), (8.5, 22, 3.2)):
            body.box((0, 0, z), (span, 0.5, chord), wing, rotation=[0, 0, angle], pivot=[0, 0, z])
            body.box((0, 0, z + 0.4), (span + 1.6, 0.45, chord - 1.4), wing, rotation=[0, 0, angle], pivot=[0, 0, z])
    two_blade_prop(model, "prop", (0, 0, 14.4), 11)
    return model, (0.9, 0.5)


def loitering_heavy():
    """Shahed-style delta: long cylindrical body, swept delta, tip fins top and bottom, rear pusher. ~2.6 x 2.4."""
    skin = Mat("#e2e4e0", noise=0.035, edge=0.12, shine=0.06, patterns=(panel_lines(8, "v", 0.1), grime(0.12)))
    wing = Mat("#dfe1dd", noise=0.035, edge=0.18, patterns=(panel_lines(6, "u", 0.07),))
    model = Model("loitering_heavy", density=2)
    body = model.bone("body")
    body.cylinder_z((0, 0, 0), 4.6, 34, skin)
    body.cylinder_z((0, 0, -18), 3.8, 2.4, skin)
    body.cylinder_z((0, 0, -19.6), 2.6, 1.0, Mat("#3a3d40", noise=0.04, edge=0.1))
    body.box((0, 0, -15.5), (4.8, 4.8, 0.8), Mat("#9a9e98", noise=0.03, edge=0.0))  # warhead band
    body.box((0, 2.6, -2), (0.4, 1.4, 1.6), BLACK)  # GNSS antenna
    # Delta planform: stepped chord strips plus a swept leading-edge plank to smooth the staircase
    root_x, tip_x, le_root, le_tip, trailing = 2.0, 21.0, -9.0, 11.0, 15.0
    steps = 7
    for side in (1, -1):
        for index in range(steps):
            x0 = root_x + (tip_x - root_x) * index / steps
            x1 = root_x + (tip_x - root_x) * (index + 1) / steps
            leading = le_root + (le_tip - le_root) * (index + 0.5) / steps
            body.cube((min(side * x0, side * x1), -1.2, leading), (x1 - x0, 0.8, trailing - leading), wing)
        length = ((tip_x - root_x) ** 2 + (le_tip - le_root) ** 2) ** 0.5
        sweep = math.degrees(math.atan2(le_tip - le_root, tip_x - root_x))
        mid = (side * (root_x + tip_x) / 2, -0.8, (le_root + le_tip) / 2 + 0.6)
        body.box(mid, (length, 0.8, 2.2), wing, rotation=[0, -side * sweep, 0], pivot=list(mid))
        body.box((side * 21.2, -0.4, 13.0), (0.6, 10, 4.0), wing)  # tip fins, above and below
    body.cylinder_z((0, 0, 18.4), 3.6, 3.2, DARK_STEEL)
    body.box((1.6, -1.6, 18.6), (0.8, 0.8, 2.4), Mat("#3d3a36", noise=0.06, edge=0.1))  # exhaust
    two_blade_prop(model, "prop", (0, 0, 20.4), 14, chord=1.6, mat=Mat("#1d1e1f", noise=0.03, edge=0.0))
    return model, (1.2, 0.5)


def gunship(weapon):
    """Light armed fixed-wing: camera-nosed fuselage, straight high wing, twin tractor props, V-tail. ~3.6 x 2.6."""
    skin = Mat("#b8bdc2", noise=0.035, edge=0.12, shine=0.1, patterns=(panel_lines(9, "v", 0.1),))
    wing = Mat("#bfc4c8", noise=0.03, edge=0.2, patterns=(panel_lines(8, "u", 0.08),))
    accent = Mat("#4b5056", noise=0.04, edge=0.15)
    model = Model("gunship_" + weapon, density=2, scale=1.2)
    body = model.bone("body")
    body.cylinder_z((0, 0, -1), 5.6, 22, skin)
    body.cylinder_z((0, 0.4, 13), 3.2, 10, skin)
    body.cylinder_z((0, -0.4, -13.5), 5.0, 5, skin)
    body.box((0, -0.6, -16.3), (3.4, 3.4, 0.6), faces_except(skin, GLASS, "north"))
    body.box((0, -3.0, -11), (2.2, 1.4, 2.6), faces_except(accent, GLASS, "north", "down"))  # targeting ball
    body.box((0, 3.1, -6), (1.6, 0.8, 5), Mat("#9fa5ab", noise=0.03, edge=0.15, patterns=(vents(),)))
    body.box((0, -3.2, 2), (1.0, 0.8, 9), accent)  # belly skid
    body.box((0, 2.9, -2), (48, 0.9, 7), wing)
    for side in (1, -1):
        body.box((side * 23.6, 3.6, -1.6), (1.4, 0.6, 6.6), accent, rotation=[0, 0, -side * 25],
                 pivot=[side * 23.6, 3.6, -1.6])
        body.cylinder_z((side * 10, 1.9, -3.5), 3.0, 8, skin)  # engine nacelle
        body.box((side * 10, 1.2, -7.7), (2.0, 1.0, 0.6), accent)
        two_blade_prop(model, "prop_" + ("right" if side > 0 else "left"), (side * 10, 1.9, -8.2), 11, chord=1.1)
        # V-tail
        body.box((side * 0.4, 4.7, 15.5), (0.6, 7, 4.5), wing, rotation=[0, 0, -side * 40], pivot=[side * 0.4, 1.2, 15.5])
    if weapon == "rocket":
        pod = Mat("#4b5320", noise=0.05, edge=0.18, patterns=(on_faces(("north",), dot(0.5, 0.5, "#111111", 0.28)),))
        for side in (1, -1):
            body.cylinder_z((side * 16, 0.0, -2.5), 3.2, 8, pod)
            body.box((side * 16, 1.8, -2.5), (0.6, 1.6, 3), accent)
            body.box((side * 16, 0.0, -6.6), (2.8, 2.8, 0.3), Mat("#111214", noise=0.02, edge=0.0,
                                                                   patterns=(dot(0.3, 0.3, "#3a3a3a", 0.15),
                                                                             dot(0.7, 0.7, "#3a3a3a", 0.15))))
    else:
        gun = Mat("#3c4045", noise=0.05, edge=0.18, patterns=(rivets(3),))
        body.cylinder_z((0, -3.8, -3), 3.0, 11, gun)
        body.box((0, -3.8, -11.5), (0.9, 0.9, 6), Mat("#1b1c1e", noise=0.04, edge=0.1))
        body.box((0, -3.8, -14.6), (1.4, 1.4, 0.6), Mat("#1b1c1e", noise=0.04, edge=0.1))  # muzzle brake
        body.box((0, -2.2, -3), (0.8, 1.2, 4), accent)
    return model, (1.6, 0.7)


def recon_uav():
    """Twin-boom surveillance UAV: pod fuselage, long straight wing, H-tail, rear pusher, belly gimbal. ~3.4 x 2.0."""
    skin = Mat("#9ba1a6", noise=0.05, edge=0.15, shine=0.12, patterns=(panel_lines(7, "v", 0.14), rivets(4, 0.15)))
    wing = Mat("#a3a9ae", noise=0.04, edge=0.2, patterns=(panel_lines(9, "u", 0.08),))
    boom = Mat("#a3a9ae", noise=0.04, edge=0.1, patterns=(zband(0.55, 0.6, "#b8302a"), zband(0.68, 0.73, "#b8302a")))
    model = Model("recon_uav", density=2)
    body = model.bone("body")
    body.cylinder_z((0, 0, -4.5), 6.2, 19, skin)
    body.cylinder_z((0, -0.2, -14.6), 5.0, 2, skin)
    body.box((0, 3.2, -5), (3.6, 0.5, 5), Mat("#7f858a", noise=0.03, edge=0.1, patterns=(vents(),)))
    body.box((0, 3.4, -11), (2.2, 0.3, 2.2), Mat("#c8cbcd", noise=0.02, edge=0.2))  # GNSS puck
    body.box((0, 5.0, -8), (0.4, 3.8, 0.4), Mat("#c9ccce", noise=0.0, edge=0.0, patterns=(band(0.2, 0.35, "#b8302a", "v"),)))
    body.cylinder_y((0, -4.0, -10), 3.4, 2.6, Mat("#3a3e42", noise=0.04, edge=0.1))  # gimbal ball
    body.box((0, -4.0, -11.75), (1.8, 1.8, 0.2), GLASS)
    body.cylinder_z((0, 0, 6.5), 3.6, 3, DARK_STEEL)
    body.box((0, 2.2, -2), (54, 1.0, 7), wing)
    for side in (1, -1):
        body.box((side * 27.2, 2.2, -2), (1.0, 0.8, 6), Mat("#868c91", noise=0.03, edge=0.2))
        body.cylinder_z((side * 7, 1.6, 8), 1.8, 22, boom)
        body.box((side * 7, 4.2, 17.6), (0.6, 5.4, 3.8), wing)
    body.box((0, 1.8, 17.8), (15.4, 0.6, 3.6), wing)
    two_blade_prop(model, "prop", (0, 0, 8.6), 12)
    return model, (1.4, 0.6)


# ---- hand-flown ---------------------------------------------------------------------------------------------------

def fpv_quad(name, motor_offset, prop_span, blades, accent_color, body_size, extras):
    """X-frame FPV quad: carbon arms and plates, bell motors, coloured TPU, LiPo on top, tilted camera up front."""
    frame = Mat("#202224", noise=0.06, edge=0.1, patterns=(carbon,))
    accent = Mat(accent_color, noise=0.05, edge=0.15)
    bell = Mat("#b9bcc0", noise=0.05, edge=0.25, shine=0.2, patterns=(band(0.0, 0.3, accent_color, "v"),))
    lipo = Mat("#2b2e31", noise=0.04, edge=0.15, patterns=(band(0.3, 0.7, "#d9b23a", "u"),))
    model = Model(name, density=6)
    body = model.bone("body")
    width, height, length = body_size
    arm_length = motor_offset * 2 * 1.4142 + 1.6
    for angle in (45, -45):
        body.box((0, -0.2, 0), (1.0, 0.45, arm_length), frame, rotation=[0, angle, 0], pivot=[0, -0.2, 0])
    body.box((0, -0.2, 0), (width, 0.4, length), frame)  # bottom plate
    body.box((0, height / 2 + 0.1, 0), (width * 0.9, 0.3, length * 0.9), frame)  # top plate
    body.box((0, height / 4, 0), (width * 0.6, height / 2, length * 0.6), Mat("#2c6b3a", noise=0.08, edge=0.2))  # FC stack
    for x in (-1, 1):
        body.box((x * (width / 2 - 0.15), height / 4, -length * 0.3), (0.3, height / 2 + 0.2, length * 0.4), accent)
    body.box((0, height / 4 + 0.1, -length / 2 + 0.3), (1.2, 1.2, 0.9), faces_except(BLACK, GLASS, "north"),
             rotation=[-20, 0, 0], pivot=[0, height / 4, -length / 2 + 0.3])  # camera, tilted up
    body.box((0, height / 2 + 0.85, 0.2), (width * 0.75, 1.1, length * 0.7), lipo)
    body.box((0, height / 2 + 0.9, 0.2), (width * 0.8, 1.15, 0.35), Mat("#1a1a1a", noise=0.02, edge=0.0))  # strap
    for x in (-1, 1):
        for z in (-1, 1):
            motor = (x * motor_offset, 0.25, z * motor_offset)
            body.cylinder_y(motor, 1.3, 0.9, bell)
            corner = ("f" if z < 0 else "b") + ("r" if x > 0 else "l")
            rotor(model, "rotor_" + corner, (motor[0], 0.85, motor[2]), prop_span, blades=blades,
                  mat=Mat(mix(rgb(accent_color), (20, 20, 20), 0.35), noise=0.03, edge=0.0) if blades == 3 else PROP)
    extras(model, body, accent)
    return model


def racer_micro():
    """Cinewhoop: ducted 4-blade props around a small pod. ~0.45 blocks."""
    def extras(model, body, accent):
        duct = Mat("#2a2c2f", noise=0.04, edge=0.15)
        for x in (-1, 1):
            for z in (-1, 1):
                cx, cz = x * 2.2, z * 2.2
                for dx, dz, sx, sz in ((1.65, 0, 0.25, 3.5), (-1.65, 0, 0.25, 3.5), (0, 1.65, 3.5, 0.25), (0, -1.65, 3.5, 0.25)):
                    body.box((cx + dx, 0.7, cz + dz), (sx, 0.9, sz), duct)
    model = fpv_quad("racer_micro", 2.2, 2.9, 2, "#c8372d", (2.2, 1.0, 2.8), extras)
    return model, (0.4, 0.2)


def racer_standard():
    """5-inch freestyle quad, 3-blade props, antenna tail, grenade clip underneath. ~0.6 blocks."""
    def extras(model, body, accent):
        body.box((0, 0.7, 2.6), (0.25, 0.25, 1.6), accent, rotation=[35, 0, 0], pivot=[0, 0.4, 2.0])
        mount = model.bone("grenade_mount", pivot=(0, -0.4, 0))
        mount.box((0, -0.65, 0.2), (1.2, 0.3, 1.8), Mat("#3a3d40", noise=0.04, edge=0.2))
    model = fpv_quad("racer_standard", 3.2, 4.4, 3, "#e8641c", (2.6, 1.3, 4.0), extras)
    return model, (0.5, 0.25)


def racer_longrange():
    """7-inch long-range quad: big 2-blade props, Li-ion pack, twin antennas, GPS mast. ~0.8 blocks."""
    def extras(model, body, accent):
        for side in (1, -1):
            body.box((side * 0.6, 1.6, 2.9), (0.25, 0.25, 2.6), accent, rotation=[40, side * 20, 0], pivot=[side * 0.6, 0.8, 2.2])
        body.box((0, 2.0, -1.4), (0.2, 1.6, 0.2), BLACK)  # GPS mast
        body.box((0, 2.9, -1.4), (1.1, 0.3, 1.1), Mat("#d8d9da", noise=0.02, edge=0.2))
        mount = model.bone("grenade_mount", pivot=(0, -0.4, 0))
        mount.box((0, -0.65, 0.2), (1.2, 0.3, 1.8), Mat("#3a3d40", noise=0.04, edge=0.2))
    model = fpv_quad("racer_longrange", 4.3, 6.0, 2, "#2f7fd8", (3.2, 1.5, 5.6), extras)
    return model, (0.65, 0.3)


def dji_drone():
    """Mavic-style folded-arm camera quad: grey shell, gimbal under the nose, bomb release under the belly. ~0.85."""
    shell = Mat("#5d6166", noise=0.03, edge=0.12, shine=0.12, patterns=(panel_lines(5, "v", 0.1),))
    arm = Mat("#4e5257", noise=0.03, edge=0.15)
    model = Model("dji_drone", density=6)
    body = model.bone("body")
    body.box((0, 0, 0.3), (4.2, 2.8, 8.4), shell)
    body.box((0, -0.1, -4.4), (3.4, 2.2, 1.2), faces_except(shell, Mat("#202326", noise=0.02, edge=0.1), "north"))
    body.box((0, 0.2, -5.05), (2.0, 0.5, 0.1), GLASS)  # obstacle sensors
    body.box((0, 1.5, 1.0), (3.4, 0.3, 4.2), Mat("#55595e", noise=0.02, edge=0.2, patterns=(vents("#404347", 3),)))
    body.box((0, -1.5, -0.5), (2.4, 0.2, 3.0), Mat("#3c3f43", noise=0.02, edge=0.2))
    for side in (1, -1):
        # Front arms higher, swept forward; rear arms lower, swept back (the folded-out Mavic stance)
        for (corner_y, corner_z), (motor_y, motor_z) in (((0.7, -2.8), (1.0, -5.3)), ((-0.6, 3.0), (-0.3, 5.4))):
            # Arm from the body corner (x 2.1) to the motor (x 4.9), turned about Y to point along that line
            dx, dz = 4.9 - 2.1, motor_z - corner_z
            centre = (side * (2.1 + 4.9) / 2, (corner_y + motor_y) / 2, (corner_z + motor_z) / 2)
            angle = math.degrees(math.atan2(dx, dz))
            body.box(centre, (0.8, 0.6, math.hypot(dx, dz) + 0.8), arm, rotation=[0, side * angle, 0], pivot=list(centre))
        body.box((side * 4.8, -0.9, -5.2), (0.4, 1.2, 0.4), arm)  # front landing feet
    motors = {"fr": (4.9, 1.0, -5.3), "fl": (-4.9, 1.0, -5.3), "br": (4.9, -0.3, 5.4), "bl": (-4.9, -0.3, 5.4)}
    for corner, (x, y, z) in motors.items():
        body.cylinder_y((x, y, z), 1.3, 1.0, Mat("#2e3134", noise=0.03, edge=0.2))
        rotor(model, "rotor_" + corner, (x, y + 0.65, z), 5.6, blades=2, mat=Mat("#1e2022", noise=0.02, edge=0.0), blade_width=0.8)
    gimbal = model.bone("gimbal", pivot=(0, -1.6, -4.3))
    gimbal.box((0, -1.7, -4.6), (2.0, 1.6, 1.6), faces_except(Mat("#2b2e31", noise=0.03, edge=0.15), GLASS, "north"))
    gimbal.box((0, -0.9, -4.4), (0.6, 0.6, 0.6), Mat("#2b2e31", noise=0.03, edge=0.1))
    rack = model.bone("bomb_rack", pivot=(0, -1.6, 0.5))
    rack.box((0, -1.75, 0.5), (1.6, 0.5, 2.6), Mat("#26292b", noise=0.04, edge=0.2))
    rack.box((0, -2.15, 0.5), (0.3, 0.4, 0.3), STEEL)
    return model, (0.7, 0.3)


# ---- launch ramp --------------------------------------------------------------------------------------------------

def launch_ramp():
    """Pneumatic catapult rail, 4 blocks long, 15 degrees: skid base, A-frame legs, twin beam rail, carriage."""
    olive = Mat("#4f5a2c", noise=0.06, edge=0.2, patterns=(grime(0.3), rivets(4, 0.2)))
    rail_steel = Mat("#7a7f84", noise=0.05, edge=0.2, shine=0.2, patterns=(grime(0.15),))
    hazard = Mat("#d9b02a", noise=0.04, edge=0.1, patterns=(panel_lines(2, "u", 0.85),))
    model = Model("launch_ramp", density=2)
    base = model.bone("base")
    for side in (1, -1):
        base.box((side * 6, 0.75, 10), (1.5, 1.5, 44), olive)  # ground skids
        base.box((side * 5.2, 9.5, -20), (1.2, 19, 1.2), olive, rotation=[-10, 0, side * 10], pivot=[side * 5.2, 0, -20])
        base.box((side * 5.0, 6.0, -10), (1.0, 12, 1.0), olive, rotation=[-40, 0, 0], pivot=[side * 5.0, 1.5, -6])
        base.box((side * 6, 0.25, -21.5), (3, 0.5, 3), DARK_STEEL)  # leg feet
    for z in (28, 12, -4):
        base.box((0, 0.75, z), (13.5, 1.0, 1.4), olive)
    for side in (1, -1):
        base.box((side * 3.4, 3.0, 27), (1.2, 4.5, 1.2), olive)  # rear rail posts
    base.box((9.5, 2.5, 20), (5, 5, 7), Mat("#4a542a", noise=0.05, edge=0.2, patterns=(vents("#2a2f18", 2),)))  # compressor
    base.cylinder_z((9.5, 2.0, 11), 3, 8, Mat("#59652f", noise=0.05, edge=0.15))  # air tank
    rail = model.bone("rail", pivot=(0, 7, 30), rotation=[-15, 0, 0])
    for side in (1, -1):
        rail.box((side * 2.4, 6.0, -2), (1.2, 2.0, 64), rail_steel)
        rail.box((side * 3.2, 5.3, -2), (0.6, 0.6, 64), Mat("#62676c", noise=0.04, edge=0.1))
    for z in range(26, -34, -8):
        rail.box((0, 5.4, z), (6.0, 0.8, 0.8), olive)
    rail.cylinder_z((0, 3.6, 6), 2.2, 44, Mat("#3e4446", noise=0.04, edge=0.15, patterns=(zband(0.9, 0.97, "#c9a62a"),)))
    rail.box((0, 6.0, -33.6), (6.4, 2.6, 0.8), hazard)  # end stop
    carriage = model.bone("carriage", pivot=(0, 7, -4), parent="rail")
    carriage.box((0, 8.0, -4), (5.4, 2.0, 9), Mat("#3b3f43", noise=0.05, edge=0.2, patterns=(rivets(3),)))
    carriage.box((0, 9.3, 0.0), (3.0, 0.8, 0.8), RUBBER)
    carriage.box((0, 9.3, -8.0), (3.0, 0.8, 0.8), RUBBER)
    return model, None


# ---- projectiles --------------------------------------------------------------------------------------------------

def finned_round(name, body_diameter, body_length, nose_length, tail_length, fin_span, fin_chord, skin, bands=()):
    """Mortar-style round, nose -Z: ogive nose, cylindrical body, thin tail boom, cruciform fins."""
    model = Model(name, density=6)
    bone = model.bone("body")
    z = -(nose_length + body_length + tail_length) / 2
    bone.cylinder_z((0, 0, z + nose_length * 0.5), body_diameter * 0.55, nose_length, skin)
    bone.cylinder_z((0, 0, z + nose_length * 0.15), body_diameter * 0.3, nose_length * 0.3, BRASS)
    z += nose_length
    painted = Mat(skin.color, noise=skin.noise, edge=skin.edge, patterns=tuple(zband(a, b, c) for a, b, c in bands))
    bone.cylinder_z((0, 0, z + body_length / 2), body_diameter, body_length, painted)
    z += body_length
    bone.cylinder_z((0, 0, z + tail_length / 2), body_diameter * 0.4, tail_length, DARK_STEEL)
    fin = Mat("#4a4f45", noise=0.05, edge=0.15)
    bone.box((0, 0, z + tail_length - fin_chord / 2), (fin_span, 0.2, fin_chord), fin)
    bone.box((0, 0, z + tail_length - fin_chord / 2), (0.2, fin_span, fin_chord), fin)
    return model, None


def bomb_light():
    return finned_round("bomb_light", 1.8, 2.6, 1.4, 1.6, 2.6, 1.2, Mat("#5c6238", noise=0.05, edge=0.1),
                        bands=((0.1, 0.25, "#b08d3c"),))


def bomb():
    return finned_round("bomb", 2.8, 4.6, 2.2, 2.4, 3.8, 1.6, Mat("#4e5a35", noise=0.05, edge=0.1),
                        bands=((0.2, 0.32, "#d6b23a"),))


def bomb_heavy():
    return finned_round("bomb_heavy", 4.2, 6.4, 2.8, 2.6, 5.4, 2.2, Mat("#3d4a31", noise=0.05, edge=0.1),
                        bands=((0.15, 0.24, "#b8302a"), (0.28, 0.36, "#d6b23a")))


def rocket():
    """70 mm-style unguided rocket: olive body, ogive warhead, nozzle, small tail fins."""
    skin = Mat("#59603a", noise=0.04, edge=0.1, patterns=(zband(0.08, 0.14, "#e0e0d8"),))
    model = Model("rocket", density=6)
    bone = model.bone("body")
    bone.cylinder_z((0, 0, -5.4), 1.3, 2.2, Mat("#6a6f48", noise=0.04, edge=0.1))
    bone.cylinder_z((0, 0, -6.6), 0.7, 0.5, BRASS)
    bone.cylinder_z((0, 0, 0), 1.8, 9, skin)
    bone.cylinder_z((0, 0, 4.9), 1.3, 0.9, Mat("#2a2a2a", noise=0.04, edge=0.1))
    fin = Mat("#3f442c", noise=0.04, edge=0.1)
    bone.box((0, 0, 3.6), (3.2, 0.15, 1.4), fin)
    bone.box((0, 0, 3.6), (0.15, 3.2, 1.4), fin)
    return model, None


def frag_grenade():
    """RGD-5-style grenade on a drop clip, as it hangs under a racer (nose -Z)."""
    model = Model("frag_grenade", density=8)
    bone = model.bone("body")
    body = Mat("#566034", noise=0.06, edge=0.12, patterns=(zband(0.45, 0.55, "#d6b23a"),))
    bone.cylinder_z((0, 0, 0), 2.2, 2.4, body)
    bone.cylinder_z((0, 0, -1.4), 1.6, 0.6, body)
    bone.cylinder_z((0, 0, 1.6), 0.9, 1.0, Mat("#77786f", noise=0.05, edge=0.15))  # fuze
    bone.box((0, 0.9, 0.8), (0.4, 0.3, 2.2), Mat("#6c6d65", noise=0.05, edge=0.1))  # lever
    bone.box((0, 1.35, 0), (1.0, 0.5, 1.6), Mat("#26292b", noise=0.04, edge=0.2))  # clip
    return model, None


# ---- cruise missile -----------------------------------------------------------------------------------------------

def cruise_missile():
    """Tomahawk-style vertical-launch missile, nose -Z: ogive nose, long body, booster with nozzle and four tail fins.
    7 blocks long, 0.65 wide, centred on the origin (the entity renders it about its mid-height)."""
    skin = Mat("#c9ccc4", noise=0.03, edge=0.12, shine=0.08,
               patterns=(panel_lines(9, "v", 0.1), zband(0.06, 0.09, "#b8302a"), zband(0.12, 0.14, "#d6b23a"), grime(0.1)))
    booster = Mat("#3d4044", noise=0.05, edge=0.18, patterns=(zband(0.1, 0.18, "#d6b23a"), grime(0.25)))
    fin = Mat("#b9bcb5", noise=0.03, edge=0.2, patterns=(grime(0.15),))
    model = Model("cruise_missile", density=2)
    body = model.bone("body")
    # Ogive nose: tapering stack
    for z, diameter in ((-54.0, 3.4), (-50.0, 6.0), (-46.0, 8.3), (-42.0, 9.9)):
        body.cylinder_z((0, 0, z), diameter, 4.0, skin)
    body.cylinder_z((0, 0, -56.2), 1.8, 0.8, Mat("#2a2c2e", noise=0.03, edge=0.1))  # seeker window
    body.cylinder_z((0, 0, -2.0), 10.4, 76, skin)
    body.box((0, 5.35, -10), (1.3, 0.8, 28), Mat("#9a9e98", noise=0.03, edge=0.1))  # wiring tunnel
    # Booster section, its four fins (in line with the body axes, clear of the pad's corner clamps) and the nozzle
    body.cylinder_z((0, 0, 44.0), 11.2, 16, booster)
    body.box((0, 0, 46.5), (26, 0.65, 9), fin)
    body.box((0, 0, 46.5), (0.65, 26, 9), fin)
    body.cylinder_z((0, 0, 53.6), 7.3, 3.2, Mat("#24262a", noise=0.05, edge=0.25))
    body.cylinder_z((0, 0, 55.4), 4.7, 0.8, Mat("#ff9a3a", noise=0.1, edge=0.0))  # hot throat
    return model, (1.05, 7.0)


def missile_pad():
    """Vertical launch pad, 2.5 blocks square: steel deck on a skid frame, flame grate, four clamp posts round the
    missile, hazard-striped edges, control box with an antenna. Deck top at 6 px (EntityMissilePad.DECK_HEIGHT)."""
    deck = Mat("#6c7176", noise=0.06, edge=0.2, patterns=(rivets(4, 0.25), grime(0.3)))
    hazard = Mat("#d9b02a", noise=0.04, edge=0.1, patterns=(panel_lines(2, "u", 0.85),))
    olive = Mat("#4f5a2c", noise=0.06, edge=0.2, patterns=(grime(0.3),))
    model = Model("missile_pad", density=2)
    base = model.bone("base")
    for side in (1, -1):
        base.box((side * 16, 1.0, 0), (3, 2, 40), olive)  # skids
    base.box((0, 4.0, 0), (36, 4, 36), deck)
    for side in (1, -1):
        base.box((side * 18.5, 4.0, 0), (1, 4, 38), hazard)
        base.box((0, 4.0, side * 18.5), (36, 4, 1), hazard)
    base.box((0, 6.05, 0), (12, 0.1, 12), Mat("#1d1f21", noise=0.04, edge=0.0, patterns=(vents("#0d0e0f", 2),)))  # flame grate
    clamp = Mat("#3b3f43", noise=0.05, edge=0.2, patterns=(rivets(3),))
    for x, z in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        base.box((x * 8.5, 15.0, z * 8.5), (2, 18, 2), clamp)  # clamp posts, at the corners between the fins
        base.box((x * 6.4, 22.0, z * 6.4), (4.4, 1.6, 1.6), RUBBER, rotation=[0, -45 * x * z, 0], pivot=[x * 6.4, 22.0, z * 6.4])  # clamp jaws, touching the body
    base.box((13, 10, 13), (6, 8, 5), Mat("#4a542a", noise=0.05, edge=0.2, patterns=(vents("#2a2f18", 2),)))  # control box
    base.box((13, 11, 10.4), (3, 2, 0.2), Mat("#55ff55", noise=0.02, edge=0.0))  # status light panel
    base.box((15, 18, 14.5), (0.4, 8, 0.4), BLACK)  # antenna
    return model, None


MODELS = {
    "loitering_light": loitering_light,
    "loitering_heavy": loitering_heavy,
    "gunship_rocket": lambda: gunship("rocket"),
    "gunship_gun": lambda: gunship("gun"),
    "recon_uav": recon_uav,
    "racer_micro": racer_micro,
    "racer_standard": racer_standard,
    "racer_longrange": racer_longrange,
    "dji_drone": dji_drone,
    "launch_ramp": launch_ramp,
    "bomb_light": bomb_light,
    "bomb": bomb,
    "bomb_heavy": bomb_heavy,
    "rocket": rocket,
    "frag_grenade": frag_grenade,
    "cruise_missile": cruise_missile,
    "missile_pad": missile_pad,
}


def main(args):
    preview = None
    if "--preview" in args:
        index = args.index("--preview")
        preview = Path(args[index + 1])
        args = args[:index] + args[index + 2:]
        preview.mkdir(parents=True, exist_ok=True)
    names = args or list(MODELS)
    from geo import render_views
    for name in names:
        model, hitbox = MODELS[name]()
        geo, texture = model.save(ASSETS)
        print(f"{name}: {geo.relative_to(ASSETS)} ({model.texture_size}px)" + (f" hitbox {hitbox}" if hitbox else ""))
        if preview:
            render_views(geo, texture, preview / f"{name}.png", title=name)


if __name__ == "__main__":
    main(sys.argv[1:])
