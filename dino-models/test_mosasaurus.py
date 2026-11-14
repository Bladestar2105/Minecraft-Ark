"""Aquatic motion stays cyclic while the bite leaves the carrier body fixed."""
import unittest

from build_models import mosasaurus, rotate
from test_model_poses import pose


class MosasaurusMotionTest(unittest.TestCase):
    def test_tail_cuboids_fit_java_model_limits_after_engine_rotation(self):
        model=mosasaurus()
        for bone,node in model.nodes.items():
            cubes=[c for c in model.elements if c['uuid'] in node['children']]
            if not cubes:continue
            origin=model.groups[bone]['origin']
            scale=max(1,max(abs(v-origin[i]) for c in cubes for field in ('from','to') for i,v in enumerate(c[field]))/16)
            for cube in cubes:
                if not cube['name'].startswith('tail_'):continue
                # BetterModel 3.6.0 Cube.buildJson rotates the local pivot before centralizing the cuboid.
                self.assertEqual(cube['rotation'][1:],[0,0])
                pivot=[(v-origin[i])/scale for i,v in enumerate(cube['origin'])]
                rotated=rotate(pivot,[-cube['rotation'][0],0,0])
                for field in ('from','to'):
                    values=[8+(v-origin[i])/scale+rotated[i]-pivot[i] for i,v in enumerate(cube[field])]
                    self.assertTrue(all(-16<=v<=32 for v in values),(cube['name'],field,values))

    def test_five_explicit_hitboxes_do_not_add_visible_geometry(self):
        model=mosasaurus()
        colliders=getattr(model,'colliders',[])
        self.assertEqual(len(colliders),5)
        self.assertTrue(all(c['type']=='bounding_box' and c['name'].startswith('b_mosa_') for c in colliders))
        self.assertFalse(any(c['name'].startswith('b_mosa_') for c in model.elements))
        centers=[]
        for collider in colliders:
            size=[(b-a)/16 for a,b in zip(collider['from'],collider['to'])]
            self.assertTrue(all(v>0 for v in size))
            self.assertLessEqual(max(size[0],size[2])/min(size[0],size[2]),2)
            self.assertLessEqual(max(size),5)
            centers.append(sum(collider[k][2] for k in ('from','to'))/32)
        self.assertLess(min(centers),-7)
        self.assertGreater(max(centers),8)

    def test_loop_tracks_have_exact_endpoints_and_unique_channel_times(self):
        model=mosasaurus()
        for animation in model.animations:
            for track in animation['animators'].values():
                frames=track['keyframes'];keys=[(frame['channel'],frame['time']) for frame in frames]
                self.assertEqual(len(keys),len(set(keys)),track['name']+' has duplicate channel keyframes')
                if animation['loop']=='loop':
                    for channel in {frame['channel'] for frame in frames}:
                        channel_frames=[frame for frame in frames if frame['channel']==channel]
                        self.assertEqual(channel_frames[0]['data_points'],channel_frames[-1]['data_points'])
        swim=next(animation for animation in model.animations if animation['name']=='swim')
        self.assertEqual(swim['anim_time_update'],'query.modified_distance_moved / (6.000000 * query.model_scale) + query.life_time * 0.15')
        for name,track in swim['animators'].items():
            if not track['name'].startswith('flipper_'):continue
            for axis in ['y','z']:
                self.assertGreater(len({frame['data_points'][0][axis] for frame in track['keyframes']}),3)

    def test_swim_loops_and_finite_bite_preserve_the_carrier_body(self):
        model=mosasaurus()
        body={cube['name'] for cube in model.elements if cube['uuid'] in model.nodes['body']['children']}
        for animation in model.animations:
            start=pose(model,animation['name'],0)
            end=pose(model,animation['name'],animation['length'])
            for name in start:
                for a,b in zip(start[name],end[name]):
                    for x,y in zip(a,b):self.assertAlmostEqual(x,y,places=10)
            for i in range(21):
                points=pose(model,animation['name'],animation['length']*i/20)
                for name in body:self.assertEqual(points[name],start[name])
        attack=next(a for a in model.animations if a['name']=='attack')
        self.assertEqual((attack['length'],attack['loop']),(.75,'once'))
        self.assertNotIn(model.groups['body']['uuid'],attack['animators'])
        start=pose(model,'attack',0);opened=pose(model,'attack',.14)
        front_y=lambda points,name: sum(points[name][i][1] for i in [0,2,4,6])/4
        gap=lambda points: front_y(points,'snout_tip')-front_y(points,'jaw_snout')
        self.assertGreater(gap(opened)-gap(start),20,'The lower jaw must open visibly below the snout')
        self.assertNotEqual(start['skull'],opened['skull'],'Bite needs a head lunge as well as jaw motion')


if __name__=='__main__':unittest.main()
