"""Adult seats against the backs and animated geometry of the exported models."""
from itertools import product
import math
import unittest

from test_model_poses import boxes_overlap, exported_model, pose


# Adult scale, seat Z (+Z is rearward), seated hip height in blocks, seat count,
# and old back surfaces as (half-width, front Z, rear Z, height below hips) in pixels.
# The hip is 0.6 blocks above the measured mounted player's feet.
SEATS = {
    'trex': (2.10, .107950, 3.907772, 2, [(5.04, -8, 12, 0), (5.4, 5, 16, 1)]),
    'triceratops': (1.45, 0, 2.809375, 2, [(5.76, -10, 14, 0), (6.84, 9, 19, 3)]),
    'stegosaurus': (1.55, 0, 2.906250, 2, [(5.04, -11, 14, 0), (6.48, 8, 19, 1)]),
    'brachiosaurus': (2.15, 0, 5.375000, 2, [(7.92, -10, 16, 0), (7.2, -15, -3, -3)]),
    'velociraptor': (1.00, .041151, 1.489667, 1, [
        (1.8, -7, 8, 0), (3.024, -7, 8, 1), (3.24, 3, 11, 1),
        (4.2, -6.5, 7.5, 2.44), (4.5, 3.5, 10.5, 2.28)]),
    'pteranodon': (1.00, 0, 1.619054, 1, [(3.096, -6, 7, 0), (4.3, -5.5, 6.5, 1.6)]),
    'dilophosaurus': (1.15, .037847, 1.370070, 1, [
        (2.8, -4, 10, 0), (3.456, -5, 8, .2), (3.6, 4, 12, .2),
        # The arms cover the two outer front corners of the flank.
        (4, -4.5, 7.5, 1.64), (4.8, -3, 7.5, 1.64), (5, 4.5, 11.5, 1.48)]),
}


def adult_pose(model, clip, time):
    stage = 'adult' if any(a['name'] == 'growth_adult' for a in model.animations) else None
    return pose(model, clip, time, stage)


def surface_height(solids, x, z):
    """Intersect a vertical line with the six faces of every posed cuboid."""
    top = -math.inf
    for corners in solids:
        if not (min(p[0] for p in corners) <= x <= max(p[0] for p in corners)
                and min(p[2] for p in corners) <= z <= max(p[2] for p in corners)):
            continue
        # Each triple picks one face origin and its two neighboring vertices.
        for i, j, k in ((0, 1, 2), (4, 5, 6), (0, 1, 4), (2, 3, 6), (0, 2, 4), (1, 3, 5)):
            ox, oy, oz = corners[i]
            ux, uy, uz = (a-b for a, b in zip(corners[j], corners[i]))
            vx, vy, vz = (a-b for a, b in zip(corners[k], corners[i]))
            det = ux*vz - uz*vx
            if abs(det) < 1e-10:
                continue
            u = ((x-ox)*vz - (z-oz)*vx) / det
            v = (ux*(z-oz) - uz*(x-ox)) / det
            if -1e-9 <= u <= 1+1e-9 and -1e-9 <= v <= 1+1e-9:
                top = max(top, oy + u*uy + v*vy)
    return top


class RidingSurfaceTest(unittest.TestCase):
    def test_exported_backs_support_the_adult_rider_and_passenger(self):
        footprint = [(dx*.05, dz*.05) for dx, dz in product(range(-6, 7), repeat=2)]
        diagonal = [((x-z)/math.sqrt(2), (x+z)/math.sqrt(2)) for x, z in footprint]
        for species, (scale, driver_z, hips, seats, patches) in SEATS.items():
            solids = adult_pose(exported_model(species), 'idle', 0).values()
            pixel = scale / 16
            for seat in range(seats):
                with self.subTest(species=species, seat=seat):
                    # A world-aligned player box reaches further aft when the mount turns.
                    for dx, dz in footprint + (diagonal if seats == 2 else []):
                        x = dx / pixel
                        z = (driver_z + 1.2*seat + dz) / pixel
                        drops = [drop for width, front, rear, drop in patches
                                 if abs(x) <= width and front <= z <= rear]
                        if drops:  # Narrow backs leave only legs or wings under the outer edges.
                            self.assertAlmostEqual(surface_height(solids, x, z) * pixel,
                                                   hips - min(drops)*pixel, delta=.002,
                                                   msg=f'Back height at ({dx:.2f}, {dz:.2f}) blocks from seat')

    def test_animated_geometry_clears_each_adult_riders_upper_body(self):
        for species, (scale, driver_z, hips, seats, _) in SEATS.items():
            model = exported_model(species)
            pixel = scale / 16
            riders = []
            for seat in range(seats):
                z = driver_z + 1.2*seat
                # From just above the hip contact to the top of the measured 1.8-block player.
                low = (-.3/pixel, (hips+.02)/pixel, (z-.3)/pixel)
                high = (.3/pixel, (hips+1.2)/pixel, (z+.3)/pixel)
                riders.append((low, high))
            for clip in model.animations:
                if clip['name'].startswith('growth_'):
                    continue
                with self.subTest(species=species, clip=clip['name']):
                    ticks = math.ceil(clip['length'] * 20)
                    times = {clip['length'] * tick / ticks for tick in range(ticks+1)}
                    times.update(k['time'] for track in clip['animators'].values() for k in track['keyframes'])
                    for time in sorted(times):
                        for name, corners in adult_pose(model, clip['name'], time).items():
                            for seat, (low, high) in enumerate(riders):
                                if all(max(p[i] for p in corners) >= a and min(p[i] for p in corners) <= b
                                       for i, (a, b) in enumerate(zip(low, high))):
                                    self.assertFalse(boxes_overlap(corners, low, high),
                                                     f'{name} crosses seat {seat} at {time:g}s')


if __name__ == '__main__':
    unittest.main()
