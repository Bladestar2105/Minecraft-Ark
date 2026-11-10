"""Name anchors have no geometry and stay above the moving skull."""
import copy
import unittest
import build_models
from test_model_poses import inside_box, pose


SPECIES = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus',
           'velociraptor', 'pteranodon', 'dilophosaurus', 'mosasaurus')


class NametagTest(unittest.TestCase):
    def test_all_species_have_one_empty_anchor_above_the_animated_head(self):
        for species in SPECIES:
            model = getattr(build_models, species)()
            self.assertEqual([name for name in model.groups if name == 'name_anchor'], ['name_anchor'], species)
            self.assertEqual(model.nodes['name_anchor']['children'], [], species)
            self.assertIn(model.nodes['name_anchor'], model.nodes['head']['children'], species)
            model = copy.deepcopy(model)
            anchor = model.groups['name_anchor']['origin']
            model.cube('_name_probe', anchor, anchor, 'name_anchor')
            for clip in model.animations:
                for fraction in (0, .25, .5, .75, 1):
                    points = pose(model, clip['name'], clip['length'] * fraction)
                    point = points.pop('_name_probe')[0]
                    for name, corners in points.items():
                        self.assertFalse(inside_box(point, corners), (species, clip['name'], fraction, name))


if __name__ == '__main__':
    unittest.main()
