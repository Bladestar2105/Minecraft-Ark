"""Keep generated runtime clearance tied to final transformed source geometry."""
import unittest
import verify_animated_bounds as bounds


class RuntimeBoundsTest(unittest.TestCase):
    def test_runtime_values_round_outward_and_keep_all_six_axes(self):
        renderer = getattr(bounds, "render_runtime_bounds", None)
        self.assertIsNotNone(renderer, "The final sampled model envelope must generate shared runtime values")
        source = renderer([{"name": "ark_test", "min": [-1.23456789, -.0000001, -3.25],
                            "max": [2.34567891, 4.0000001, 1.5]}])
        self.assertIn('case "ark_test" -> new Envelope(-1.234568, -0.000001, -3.250000, 2.345679, 4.000001, 1.500000)', source)


if __name__ == "__main__":
    unittest.main()
