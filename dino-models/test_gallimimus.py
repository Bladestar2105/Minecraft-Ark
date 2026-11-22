"""Independent anatomy and posed movement checks for the new herbivore."""
import unittest
import build_models
from test_model_poses import pose


class GallimimusTest(unittest.TestCase):
    def model(self):
        factory = getattr(build_models, 'gallimimus', None)
        self.assertIsNotNone(factory, 'Gallimimus needs its own generated anatomy')
        return factory()

    def test_distinct_toothless_small_head_long_neck_and_hind_limbs(self):
        m = self.model()
        names = {c['name'] for c in m.elements}
        self.assertFalse(any(n.startswith(('tooth_', 'sickle_', 'hand_claw_')) for n in names))
        self.assertEqual(m.legs, ['leg_left', 'leg_right'])
        self.assertGreater(m.groups['head']['origin'][1] - m.groups['neck']['origin'][1], 15)
        self.assertGreater(m.groups['leg_left']['origin'][1], 25)
        head = [v for c in m.elements if c['name'].startswith(('skull', 'beak')) for v in build_models.vertices(c)]
        self.assertLess(max(v[0] for v in head) - min(v[0] for v in head), 6)
        self.assertGreater(max(v[1] for v in head), 45)
        self.assertLessEqual(len(m.elements), 180, 'Keep the eight-animal reference herd within the model budget')

    def test_curved_neck_high_hocks_and_three_grounded_toes(self):
        m = self.model()
        cubes = {c['name']: c for c in m.elements}
        centerline = [c['origin'] for c in m.elements
                      if c['name'].startswith('neck_core_') and c['name'].rsplit('_', 1)[-1].isdigit()]
        self.assertGreaterEqual(len(centerline), 5, 'The long neck needs both bends of the S-curve')
        forward_turn = min(range(len(centerline)), key=lambda i: centerline[i][2])
        self.assertGreater(forward_turn, 2, 'The upper neck turns back towards the beak')
        self.assertGreater(
            max(p[2] for p in centerline[3:]) - min(p[2] for p in centerline[:4]), .5,
            'The middle neck curves back before the nape bends forward',
        )
        for side in ('left', 'right'):
            hock = cubes[f'metatarsus_{side}']['origin']
            knee = cubes[f'shin_{side}']['origin']
            self.assertGreater(hock[1], 7)
            self.assertLess(hock[1], 11)
            self.assertGreater(hock[2], knee[2], 'The hock sits behind the knee')
            for digit in range(3):
                self.assertIn(f'toe_{side}_{digit}_claw', m.feet)
                self.assertIn(f'finger_{side}_{digit}_claw', cubes)
            self.assertEqual(sum(name.startswith(f'toe_{side}_') and name.endswith('_claw')
                                 for name in m.feet), 3)

    def test_plumage_texture_uses_a_species_specific_pattern(self):
        m = self.model()
        self.assertFalse(build_models.png_texture(m.colors, m.name) == build_models.png_texture(m.colors),
                         'The Gallimimus atlas must have its own plumage and tarsal patterns')

    def test_grazing_lowers_beak_and_run_uses_a_longer_grounded_stride(self):
        m = self.model()
        clips = {a['name']: a for a in m.animations}
        self.assertTrue({'idle', 'graze', 'walk', 'run', 'attack'} <= clips.keys())
        lower = min(v[1] for name, corners in pose(m, 'graze', 1).items() if name.startswith('beak') for v in corners)
        self.assertGreaterEqual(lower, -.2, 'The beak must stay above the ground while foraging')
        self.assertLess(lower / 16, .5, 'Foraging must reach the actual grass level')
        self.assertGreater(m.run_stride_blocks, m.walk_stride_blocks * 1.6)
        self.assertIn('query.ground_speed', clips['run']['anim_time_update'])
        for clip in ['walk', 'run']:
            heights = []
            for step in range(61):
                corners = pose(m, clip, step / 60)
                heights.append(min(v[1] for name in m.feet if name.startswith('toe_left_') for v in corners[name]))
            self.assertGreaterEqual(min(heights), -.2)
            self.assertGreater(max(heights), 1.2)


if __name__ == '__main__':
    unittest.main()
