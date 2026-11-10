"""Growth scales only display details; adult shapes and motion retain their baseline."""
import hashlib
import json
import unittest
import build_models
from test_model_poses import pose

BASELINE = {
    'triceratops': '26240c50ccec7fd1a6f57b2795f4937e8ce5bbee4940150bfc277a6deae29c80',
    'stegosaurus': 'e021fa349f51bc96ca5152ae915899567847d4abf4a9f859cbf94ecc16442f7f',
    'pteranodon': 'b192998cb08efbcb0e7968f754cdf7a07fb6a5dbfb951b465985454404428b5a',
    'dilophosaurus': '7d012313990b5099ea6b04c4f0649cea8506e1ec953a737d502bc9e329925298',
}
PREFIXES = {'triceratops': ('brow_horn_', 'nose_horn'),
            'stegosaurus': ('plate_', 'neck_plate_', 'tail_plate_', 'tail_spike_'),
            'pteranodon': ('head_crest',),
            'dilophosaurus': ('crest_', 'crest_rim_', 'frill_')}


def stable_numbers(value):
    # macOS/Linux libm differ by ~1e-14 in generated beam angles.
    if isinstance(value, float): return round(value, 12)
    if isinstance(value, list): return [stable_numbers(v) for v in value]
    if isinstance(value, dict): return {k: stable_numbers(v) for k, v in value.items()}
    return value


class GrowthDetailsTest(unittest.TestCase):
    def test_exact_adult_shapes_existing_bones_gaits_and_combat_are_preserved(self):
        for species, expected in BASELINE.items():
            model = getattr(build_models, species)()
            payload = {'elements': model.elements,
                       'groups': [g for n, g in model.groups.items() if not n.startswith(('growth_', 'name_anchor'))],
                       'animations': [a for a in model.animations if not a['name'].startswith('growth_')]}
            self.assertEqual(hashlib.sha256(json.dumps(stable_numbers(payload), sort_keys=True).encode()).hexdigest(), expected, species)

    def test_three_stage_clips_cover_every_requested_detail_and_only_detail_bones(self):
        for species, prefixes in PREFIXES.items():
            model = getattr(build_models, species)()
            parents = {child['uuid']: name for name, node in model.nodes.items() for child in node['children'] if isinstance(child, dict)}
            owners = {child: name for name, node in model.nodes.items() for child in node['children'] if isinstance(child, str)}
            growth = {name for name in model.groups if name.startswith('growth_')}
            self.assertTrue(growth, species)
            for cube in model.elements:
                ancestors = []
                name = owners[cube['uuid']]
                while name:
                    ancestors.append(name)
                    name = parents.get(model.groups[name]['uuid'])
                self.assertEqual(any(n in growth for n in ancestors), cube['name'].startswith(prefixes), (species, cube['name']))
            for stage, index in [('baby', 0), ('juvenile', 1), ('adult', 2)]:
                clip = next(a for a in model.animations if a['name'] == 'growth_' + stage)
                self.assertEqual((clip['loop'], clip['length']), ('loop', 1))
                self.assertEqual({a['name'] for a in clip['animators'].values()}, growth)
                for track in clip['animators'].values():
                    broad = 'plate' in track['name'] or 'frill' in track['name']
                    expected = ((.3, .65, 1) if broad else (.2, .55, 1))[index]
                    self.assertEqual({k['channel'] for k in track['keyframes']}, {'scale'})
                    self.assertEqual([k['time'] for k in track['keyframes']], [0, 1])
                    for frame in track['keyframes']:
                        self.assertEqual([float(frame['data_points'][0][axis]) for axis in 'xyz'], [expected] * 3)

    def test_dilo_growth_frill_is_between_attack_root_and_rib_rotations(self):
        model = build_models.dilophosaurus()
        for side in ('left', 'right'):
            growth = model.nodes['growth_frill_' + side]
            self.assertIn(growth, model.nodes['frill_' + side]['children'])
            self.assertEqual({n['uuid'] for n in growth['children']},
                             {model.groups[f'frill_{side}_rib_{i}']['uuid'] for i in range(11)})

    def test_growing_details_remain_attached_during_combat_and_do_not_move_other_shapes(self):
        for species, prefixes in PREFIXES.items():
            model = getattr(build_models, species)()
            animation = 'spit' if species == 'dilophosaurus' else 'attack'
            adult = pose(model, animation, .4, 'adult')
            for stage in ('baby', 'juvenile'):
                points = pose(model, animation, .4, stage)
                for cube in model.elements:
                    name = cube['name']
                    if not name.startswith(prefixes):
                        self.assertEqual(points[name], adult[name], (species, stage, name))
                    else:
                        width = lambda vertices: sum(sum((p[i]-vertices[0][i])**2 for i in range(3)) for p in vertices)
                        self.assertLess(width(points[name]), width(adult[name]), (species, stage, name))


if __name__ == '__main__':
    unittest.main()
