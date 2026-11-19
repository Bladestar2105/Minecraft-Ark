"""Build seven opaque species shells around the unchanged native Sniffer Egg."""
import base64
from functools import lru_cache
import json
import math
from pathlib import Path
import uuid

from build_eggs import EGGS, mix, png, rgb

ROOT = Path(__file__).resolve().parent
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')


def identity(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'ark-placed-eggs/' + value))


# Crown rise, flank fullness and lean in Blockbench pixels. The blunt end rests on the native block.
PROFILES = {
    'trex': (3.9, .88, -.28), 'triceratops': (2.8, .96, .16),
    'stegosaurus': (3.55, .79, -.18), 'brachiosaurus': (3.12, 1.0, .08),
    'velociraptor': (5.3, .66, .60), 'pteranodon': (5.0, .71, -.54),
    'dilophosaurus': (4.45, .83, .32),
}


def shell_parts(species):
    # The central box alone conceals Mojang's native mesh [1,0,2]..[15,16,14].
    parts = [('species_shell', [-7.02, -.02, -6.02], [7.02, 16.02, 6.02], [0, 0, 0], [0, 0, 0])]
    rise, fullness, lean = PROFILES[species]
    flank_radius = 7.57 + .31*fullness
    rings = [
        # Height, two radii, 45-degree corner cut and sideways lean.
        (0, 7.40, 6.88, 1.06, 0),
        (2.15, 7.54, 7.17, 1.52, 0),
        (5.4, flank_radius, 7.32 + .48*fullness, 2.24, 0),
        (10.8, flank_radius, 7.31 + .44*fullness, 2.28, 0),
        (16.03, 7.44, 6.98, 1.28, 0),
    ]
    for fraction, radius in ((.38, .91), (.70, .74), (.90, .51), (1, .24)):
        rings.append((16.03 + fraction*rise, 7.44*radius, 6.98*radius, 1.88*radius, fraction*lean))

    def corners(ring):
        _, rx, rz, cut, drift = ring
        return [(x + drift, z) for x, z in (
            (rx-cut, -rz), (rx, -rz+cut), (rx, rz-cut), (rx-cut, rz),
            (-rx+cut, rz), (-rx, rz-cut), (-rx, -rz+cut), (-rx+cut, -rz))]

    for level, (lower, upper) in enumerate(zip(rings, rings[1:])):
        bottom, top = corners(lower), corners(upper)
        for side in range(8):
            a, b = bottom[side], bottom[(side+1) % 8]
            c, d = top[side], top[(side+1) % 8]
            dx, dz = b[0]-a[0], b[1]-a[1]
            length = math.hypot(dx, dz)
            tx, tz = dx/length, dz/length
            nx, nz = tz, -tx
            tangent = [x*tx + z*tz for x, z in (a, b, c, d)]
            mid = (min(tangent) + max(tangent))/2
            half = (max(tangent) - min(tangent))/2
            radius = a[0]*nx + a[1]*nz
            run = c[0]*nx + c[1]*nz - radius
            height = upper[0]-lower[0]
            x, y, z = mid*tx + radius*nx, lower[0], mid*tz + radius*nz
            # Opaque inclined facets meet along the ellipsoidal shell. Thickness faces inward.
            parts.append((f'shell_{level+1}_{side+1}', [x-half, y, z-.5], [x+half, y+math.hypot(height, run), z],
                          [x, y, z], [math.degrees(math.atan2(run, height)), math.degrees(math.atan2(nx, nz)), 0]))
    top, rx, rz, _, drift = rings[-1]
    parts.append(('crown', [drift-rx, top-.15, -rz], [drift+rx, top, rz], [0, 0, 0], [0, 0, 0]))
    return parts


@lru_cache(maxsize=32768)
def lattice(x, y, z):
    value = (x * 73856093 ^ y * 19349663 ^ z * 83492791) & 0xffffffff
    value = (value ^ (value >> 16)) * 0x45d9f3b & 0xffffffff
    return ((value ^ (value >> 16)) & 0xffff) / 65535


def mottle(x, y, z):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    dx, dy, dz = (f * f * (3 - 2 * f) for f in (x - ix, y - iy, z - iz))
    return sum(lattice(ix + i, iy + j, iz + k) * wx * wy * wz
               for i, wx in enumerate((1 - dx, dx)) for j, wy in enumerate((1 - dy, dy))
               for k, wz in enumerate((1 - dz, dz)))


def ramp(value, start, end):
    return min(1, max(0, (value - start) / (end - start)))


def shell_color(species, point, palette):
    x, y, z = point
    clay = mottle(.28*x + 8, .26*y + 2, .29*z + 7)
    grit = mottle(1.08*x - 5, 1.22*y + 9, 1.03*z)
    pores = mottle(2.46*x + 3, 2.63*y, 2.32*z - 9)
    if species == 'trex':
        # Dark, broken olive-brown thumbprints on the lower shoulders.
        field = mottle(.54*x + .24*y, .28*y, .47*z + 4)
        ink = ramp(.72*field + .28*clay - .035*y/16, .51, .74)
    elif species == 'triceratops':
        # Sandy mineral bloom and cinnamon stipple concentrated at the blunt end.
        ink = ramp(.69*clay + .31*grit - .11*y/16, .48, .79)
    elif species == 'stegosaurus':
        # Rust freckles with darker, scalloped margins.
        field = mottle(.38*x, .45*y, .42*z + 12)
        ink = .38*ramp(field, .50, .66) + .58*(1 - ramp(abs(field - .65), .01, .10))
    elif species == 'brachiosaurus':
        # A wide, washed celadon bloom; sparse dark centers remain visible at arm's length.
        ink = .52*ramp(clay, .39, .71) + .36*ramp(.69*clay + .31*grit, .58, .72)
    elif species == 'velociraptor':
        # Pigment dragged lengthwise through the warm ivory cuticle.
        field = mottle(.78*x + .31*y, .14*y, .71*z - .17*y)
        ink = ramp(.80*field + .20*grit, .55, .78)
    elif species == 'pteranodon':
        # Hairline slate marbling across a smooth, pale leathery surface.
        field = mottle(.26*x + .05*y, .29*y + 5, .38*z + .08*y)
        ink = .52*(1 - ramp(abs(field - .58) + .09*(grit - .5), .014, .065))
    else:  # Dilophosaurus: wine-colored pigment bleeds into paired clay streaks.
        field = mottle(.48*x - .18*y, .24*y, .58*z + .20*y)
        ink = .85*ramp(.70*field + .30*clay, .52, .68) + .15*ramp(grit, .62, .82)

    shell, pigment, chalk = palette
    cuticle = mix(shell, chalk, .08 + .25*clay + .07*y/22)
    color = mix(cuticle, pigment, min(.91, ink*.83 + .24*ramp(grit, .71, .84)))
    color = mix(color, chalk, .28*ramp(pores, .72, .88))
    # A little reflected warmth and contact shade round out the unavoidable native corners.
    contact = .16*(abs(x)/8 * abs(z)/8)**2 + .20*(1 - ramp(y, 0, 8))
    color = mix(color, pigment, contact)
    grain = round((pores - .5) * 11)
    return tuple(max(0, min(255, channel + grain)) for channel in color) + (255,)


def face_point(side, u, v, low, high, pivot, rotation):
    unit = {'north': (1-u, 1-v, 0), 'south': (u, 1-v, 1), 'east': (1, 1-v, 1-u),
            'west': (0, 1-v, u), 'up': (u, 1, v), 'down': (u, 0, 1-v)}[side]
    x, y, z = (a + t*(b-a) - p for t, a, b, p in zip(unit, low, high, pivot))
    cx, sx, cy, sy = rotation
    y, z = y*cx - z*sx, y*sx + z*cx
    x, z = x*cy + z*sy, -x*sy + z*cy
    return tuple(round(v + p, 6) for v, p in zip((x,y,z), pivot))


def textured_shell(species, name):
    parts = shell_parts(species)
    palette = tuple(map(rgb, EGGS[species][1:4]))
    width = 256
    x = y = shelf_height = 0
    elements, tiles = [], []
    for part, low, high, pivot, rotation in parts:
        pitch, yaw = map(math.radians, rotation[:2])
        trig = math.cos(pitch), math.sin(pitch), math.cos(yaw), math.sin(yaw)
        nx, ny, nz = (max(2, round((b - a) * 1.8) + 1) for a, b in zip(low, high))
        faces = {}
        for side, (w, h) in zip(SIDES, ((nx, ny), (nx, ny), (nz, ny), (nz, ny), (nx, nz), (nx, nz))):
            if x + w + 2 > width:
                x, y, shelf_height = 0, y + shelf_height, 0
            faces[side] = {'uv': [x+1, y+1, x+1+w, y+1+h], 'texture': 0}
            tiles.append((side, low, high, pivot, trig, x, y, w, h))
            shelf_height = max(shelf_height, h + 2)
            x += w + 2
        key = '/cube' if part == 'species_shell' else '/' + part
        elements.append({'name': part, 'type': 'cube', 'uuid': identity(name + key), 'box_uv': False,
                         'from': low, 'to': high, 'origin': pivot, 'rotation': rotation, 'faces': faces})
    height = 1 << (y + shelf_height - 1).bit_length()
    pixels = [[(*palette[0], 255)] * width for _ in range(height)]
    for side, low, high, pivot, trig, x, y, w, h in tiles:
        for row in range(h):
            colors = [shell_color(species, face_point(side, col/(w-1), row/(h-1), low, high, pivot, trig), palette) for col in range(w)]
            pixels[y+row+1][x:x+w+2] = [colors[0], *colors, colors[-1]]
        for gutter, edge in ((y, y+1), (y+h+1, y+h)):
            pixels[gutter][x:x+w+2] = pixels[edge][x:x+w+2]
    return elements, png(pixels), width, height


def bedrock_cube(cube):
    faces = {}
    for side, face in cube['faces'].items():
        u, v, u2, v2 = face['uv']
        faces[side] = ({'uv': [u2, v2], 'uv_size': [u-u2, v-v2]} if side in ('up', 'down')
                       else {'uv': [u, v], 'uv_size': [u2-u, v2-v]})
    result = {'origin': [-cube['to'][0], cube['from'][1], cube['from'][2]],
              'size': [b - a for a, b in zip(cube['from'], cube['to'])], 'uv': faces}
    if any(cube['rotation']):
        pitch, yaw, roll = cube['rotation']
        result.update(pivot=[-cube['origin'][0], *cube['origin'][1:]], rotation=[-pitch, -yaw, roll])
    return result


def build():
    for species in PROFILES:
        name = 'ark_egg_' + species
        folder = ROOT / 'models' / name
        folder.mkdir(parents=True, exist_ok=True)
        elements, texture, width, height = textured_shell(species, name)
        bone = identity(name + '/shell')
        animation = {'uuid': identity(name + '/idle'), 'name': 'idle', 'loop': 'loop', 'override': False,
                     'length': 1, 'snapping': 20, 'animators': {bone: {'name': 'shell', 'type': 'bone',
                     'keyframes': [{'channel': 'rotation', 'time': t, 'uuid': identity(name + '/idle/' + str(t)),
                                    'interpolation': 'linear', 'data_points': [{'x': '0', 'y': '0', 'z': '0'}]} for t in (0, 1)]}}}
        model = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
                 'name': name, 'model_identifier': name, 'resolution': {'width': width, 'height': height},
                 'elements': elements, 'groups': [{'name': 'shell', 'origin': [0, 0, 0], 'uuid': bone, 'rotation': [0, 0, 0], 'export': True}],
                 'outliner': [{'uuid': bone, 'isOpen': True, 'children': [cube['uuid'] for cube in elements]}],
                 'textures': [{'name': name + '.png', 'id': '0', 'uuid': identity(name + '/texture'), 'width': width,
                               'height': height, 'uv_width': width, 'uv_height': height, 'source': 'data:image/png;base64,' + base64.b64encode(texture).decode()}],
                 'animations': [animation]}
        geometry = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {
            'identifier': 'geometry.' + name, 'texture_width': width, 'texture_height': height,
            'visible_bounds_width': 2, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, .5, 0]},
            'bones': [{'name': 'shell', 'pivot': [0, 0, 0], 'cubes': [bedrock_cube(cube) for cube in elements]}]}]}
        outputs = {name + '.bbmodel': model, name + '.geo.json': geometry,
                   name + '.animation.json': {'format_version': '1.8.0', 'animations': {'idle': {'loop': True, 'animation_length': 1, 'bones': {'shell': {'rotation': {'0': [0, 0, 0], '1': [0, 0, 0]}}}}}},
                   'config.json': {'head_rotation': False, 'material': 'entity_alphatest_change_color_one_sided', 'blend_transition': True,
                                   'binding_bones': {name: ['*']}, 'anim_textures': {}, 'texture_materials': {}, 'disable_part_visibility': True}}
        for path, content in outputs.items():
            (folder / path).write_text(json.dumps(content, indent=2) + '\n')
        (folder / (name + '.png')).write_bytes(texture)
    print('Built seven distinct placed egg shells for Java and Bedrock.')


if __name__ == '__main__':
    build()
