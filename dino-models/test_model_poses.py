"""Visible model features and actual animated poses, using independent corner transforms."""
from itertools import product
from types import SimpleNamespace
import unittest
import build_models

from build_models import brachiosaurus, pteranodon, stegosaurus, trex, triceratops, velociraptor
from verify_animated_bounds import transform


def pose(model, animation, time, detail_stage=None):
    tracks=dict(next(a for a in model.animations if a['name']==animation)['animators'])
    if detail_stage:tracks.update(next(a for a in model.animations if a['name']=='growth_'+detail_stage)['animators'])
    parents={child['uuid']:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,dict)}
    owners={child:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
    def channel(name,kind):
        frames=[k for k in tracks.get(model.groups[name]['uuid'],{}).get('keyframes',[]) if k['channel']==kind]
        if not frames:return [1,1,1] if kind=='scale' else [0,0,0]
        if len(frames)==1:return [float(frames[0]['data_points'][0][axis]) for axis in 'xyz']
        a,b=next((a,b) for a,b in zip(frames,frames[1:]) if a['time']<=time<=b['time'])
        f=(time-a['time'])/(b['time']-a['time'])
        return [float(a['data_points'][0][axis])*(1-f)+float(b['data_points'][0][axis])*f for axis in 'xyz']
    result={}
    for cube in model.elements:
        points=[transform(p,cube['origin'],cube['rotation']) for p in product(*zip(cube['from'],cube['to']))]
        name=owners[cube['uuid']]
        while name:
            group=model.groups[name]
            points=[[v+d for v,d in zip(transform([o+(v-o)*s for o,v,s in zip(group['origin'],p,channel(name,'scale'))],group['origin'],channel(name,'rotation')),channel(name,'position'))] for p in points]
            name=parents.get(group['uuid'])
        result[cube['name']]=points
    return result

def inside_box(point,corners):
    # Invert an oriented posed cube using its three perpendicular edge vectors.
    origin=corners[0];offset=[a-b for a,b in zip(point,origin)]
    for index in [4,2,1]:
        edge=[a-b for a,b in zip(corners[index],origin)]
        coordinate=sum(a*b for a,b in zip(offset,edge))/sum(v*v for v in edge)
        if coordinate < -1e-7 or coordinate > 1+1e-7:return False
    return True

def boxes_overlap(corners,low,high):
    edges=[[corners[i][j]-corners[0][j] for j in range(3)] for i in [4,2,1]]
    basis=[[1,0,0],[0,1,0],[0,0,1]]
    axes=basis+edges+[[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]] for a in edges for b in basis]
    other=list(product(*zip(low,high)))
    for axis in axes:
        if sum(v*v for v in axis)<1e-10:continue
        a=[sum(x*y for x,y in zip(p,axis)) for p in corners];b=[sum(x*y for x,y in zip(p,axis)) for p in other]
        if max(a)<min(b) or max(b)<min(a):return False
    return True


class ModelPoseTest(unittest.TestCase):
    def test_pteranodon_flight_tucks_its_feet_behind_the_hips(self):
        model=pteranodon();rest=pose(model,'idle',0)
        foot_names=[name for name in rest if name.startswith(('toe_','claw_'))]
        feet=lambda points:[p for name in foot_names for p in points[name]]
        self.assertAlmostEqual(min(p[1] for p in feet(rest)),0,delta=.01)
        for animation in ['flap','glide']:
            length=next(a['length'] for a in model.animations if a['name']==animation)
            for i in range(31):
                points=pose(model,animation,length*i/30);foot=feet(points)
                self.assertGreater(min(p[1] for p in foot),9,'Flight feet must lift clear of the standing pose')
                self.assertGreater(sum(p[2] for p in foot)/len(foot),18,'Flight feet must trail behind the hips')
                self.assertLess(max(p[1] for p in foot)-min(p[1] for p in foot),6,'Tucked toes must be streamlined')
                for p in foot:
                    for solid in ['torso','torso_flanks','breast','breast_flanks']:
                        self.assertFalse(inside_box(p,points[solid]),'Tucked feet cannot disappear inside '+solid)

    def test_stegosaurus_plates_continue_from_neck_to_tail_and_clear_the_rider(self):
        model=stegosaurus();owners={child:bone for bone,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
        neck=[c for c in model.elements if c['name'].startswith('neck_plate_')]
        tail=[c for c in model.elements if c['name'].startswith('tail_plate_')]
        self.assertGreaterEqual(len(neck),8,'Small plates must continue along the neck')
        self.assertGreaterEqual(len(tail),8,'Plates must taper along the moving tail')
        parents={child['uuid']:bone for bone,node in model.nodes.items() for child in node['children'] if isinstance(child,dict)}
        self.assertEqual({parents[model.groups[owners[c['uuid']]]['uuid']] for c in neck},{'head'})
        self.assertEqual({parents[model.groups[owners[c['uuid']]]['uuid']] for c in tail},{'tail'})
        rest=pose(model,'idle',0)
        self.assertLess(min(p[2] for c in neck for p in rest[c['name']]),-30)
        self.assertGreater(max(p[2] for c in tail for p in rest[c['name']]),39)
        feet=1.875-.6;margin=.08
        low=[(-.3-margin)*16,(feet-margin)*16,(-.3-margin)*16]
        high=[(.3+margin)*16,(feet+1.8+margin)*16,(.3+margin)*16]
        for animation in model.animations:
            for i in range(31):
                points=pose(model,animation['name'],animation['length']*i/30)
                for name,corners in points.items():
                    if name.startswith(('plate_','neck_plate_','tail_plate_')):
                        self.assertFalse(boxes_overlap(corners,low,high),(animation['name'],i,name,'Plate intersects fixed rider space'))

    def test_pteranodon_crest_clears_the_actual_seated_player_volume(self):
        model=pteranodon();feet=1.619054-.6;margin=.08
        low=[(-.3-margin)*16,(feet-margin)*16,(-.3-margin)*16]
        high=[(.3+margin)*16,(feet+1.8+margin)*16,(.3+margin)*16]
        for animation in model.animations:
            for i in range(41):
                points=pose(model,animation['name'],animation['length']*i/40)
                for name,corners in points.items():
                    if name.startswith('head_crest'):
                        self.assertFalse(boxes_overlap(corners,low,high),(animation['name'],i,name,'Crest intersects rider body/head with 8cm margin'))

    def test_mosasaurus_is_a_giant_four_flipper_swimmer_with_exposed_teeth(self):
        model=build_models.mosasaurus();rest=pose(model,'idle',0)
        points=[p for ps in rest.values() for p in ps]
        self.assertAlmostEqual((max(p[2] for p in points)-min(p[2] for p in points))/16,24,delta=.1)
        self.assertEqual(len([name for name in model.groups if name.startswith('flipper_')]),4)
        self.assertFalse(any('dorsal' in name for name in rest),'Mosasaurus has no shark dorsal fin')
        tail=[p for name,ps in rest.items() if name.startswith('tail_fin') for p in ps]
        self.assertGreater(max(p[1] for p in tail)-min(p[1] for p in tail),32)
        self.assertGreater(max(p[1] for p in tail)-min(p[1] for p in tail),max(p[0] for p in tail)-min(p[0] for p in tail))
        swimming=pose(model,'swim',.25)
        for name in ['tail_fin_upper0','flipper_front_left0','flipper_back_right0']:
            self.assertNotEqual(rest[name],swimming[name],name+' must propel the swimming pose')
        attacking=pose(model,'attack',.2)
        tips=[name for name in attacking if name.startswith('tooth_') and name.endswith('_tip')]
        self.assertGreaterEqual(len(tips),40)
        for name in tips:
            tip=[sum(attacking[name][i][axis] for i in [2,3,6,7])/4 for axis in range(3)]
            for solid,corners in attacking.items():
                if not solid.startswith('tooth_'):self.assertFalse(inside_box(tip,corners),name+' buried in '+solid)
        self.assertEqual({a['name'] for a in model.animations},{'idle','swim','attack'})
        swim=next(a for a in model.animations if a['name']=='swim')
        self.assertIn('query.modified_distance_moved',swim['anim_time_update'])
        self.assertFalse(model.legs);self.assertFalse(model.feet)

    def test_triceratops_nasal_horn_is_a_clear_third_silhouette(self):
        model=triceratops();points=pose(model,'idle',0)
        horn=sorted(n for n in points if n.startswith('nose_horn'))
        self.assertGreaterEqual(len(horn),3)
        tip=[sum(points[horn[-1]][i][axis] for i in [2,3,6,7])/4 for axis in range(3)]
        snout=[p for n,ps in points.items() if n.startswith('snout') for p in ps]
        self.assertGreater(tip[1]-max(p[1] for p in snout),7,'Central horn must stand clearly above the nasal bridge')
        self.assertGreater(min(p[2] for p in snout)-tip[2],4.5,'Side view needs a distinct forward nasal horn')
        front=lambda p:-p[1]-.25*p[2]
        self.assertGreater(min(front(p) for p in snout)-front(tip),6.5,'Front view must show the third horn separately above the snout')
        for n,solid in points.items():
            if not n.startswith('nose_horn'):self.assertFalse(inside_box(tip,solid),'Nasal horn tip must not be buried in '+n)

    def test_visible_upper_and_lower_teeth_are_outside_solid_mouth_geometry(self):
        for factory in [velociraptor,trex,build_models.dilophosaurus,triceratops,stegosaurus,brachiosaurus]:
            model=factory();points=pose(model,'attack',.16)
            tips=[c['name'] for c in model.elements if c['name'].startswith('tooth_') and c['name'].endswith('_tip')]
            self.assertTrue(tips,model.name+' needs real exposed tooth rows')
            for row in ['upper','lower']:
                teeth=[name for name in tips if name.startswith('tooth_'+row+'_')]
                self.assertGreaterEqual(len(teeth),4 if model.name=='ark_brachiosaurus' else 6,model.name+' needs an upper and lower tooth row')
                visible=0
                for name in teeth:
                    tip=[sum(points[name][i][axis] for i in [2,3,6,7])/4 for axis in range(3)]
                    solids=[ps for n,ps in points.items() if not n.startswith('tooth_')]
                    visible+=not any(inside_box(tip,solid) for solid in solids)
                self.assertEqual(visible,len(teeth),model.name+'/'+row+': ivory tips must protrude into open air, not be buried in jaws')
            if model.name=='ark_brachiosaurus':
                self.assertEqual(len(tips),8,'Brachiosaurus needs a few slender pegs, not a broad incisor fence')
                for name in tips:
                    ps=points[name];self.assertLess(max(p[0] for p in ps)-min(p[0] for p in ps),.35)
        self.assertFalse(any(c['name'].startswith('tooth_') for c in pteranodon().elements),'Pteranodon keeps its toothless beak')

    def test_combat_is_finite_visible_and_keeps_the_saddle_fixed(self):
        for factory in [trex,triceratops,stegosaurus,brachiosaurus,velociraptor,pteranodon,build_models.dilophosaurus]:
            model=factory();attacks=[a for a in model.animations if a['name']=='attack']
            self.assertEqual(len(attacks),1,model.name+' needs a visible attack')
            attack=attacks[0];self.assertEqual(attack['loop'],'once')
            self.assertNotIn(model.groups['body']['uuid'],attack['animators'])
            body={c['name'] for c in model.elements if c['uuid'] in model.nodes['body']['children']}
            start=pose(model,'attack',0);changed=False
            for i in range(21):
                points=pose(model,'attack',attack['length']*i/20)
                for name in body:self.assertEqual(points[name],start[name],'Attack must keep body and saddle fixed')
                self.assertGreaterEqual(min(p[1] for name,ps in points.items() if name in model.feet for p in ps),-1e-6)
                changed=changed or any(points[n]!=start[n] for n in points)
            self.assertTrue(changed,'A named clip must visibly move its attack bones')
            self.assertEqual(pose(model,'attack',attack['length']),start)

    def test_dilophosaurus_has_twin_crests_claws_and_folding_frill(self):
        factory=getattr(build_models,'dilophosaurus',None)
        self.assertIsNotNone(factory,'Seventh species needs editable source geometry')
        model=factory();names={c['name'] for c in model.elements}
        self.assertEqual(len(model.legs),2)
        for side in ['left','right']:
            self.assertTrue(any(n.startswith('crest_'+side) for n in names))
            for i in range(3):self.assertIn(f'hand_claw_{side}_{i}0',names)
        attack=next(a for a in model.animations if a['name']=='attack');self.assertEqual(attack['loop'],'once')
        spit=next(a for a in model.animations if a['name']=='spit');self.assertEqual(spit['loop'],'once')
        for animation,time in [('idle',0),('walk',.5),('spit',0),('spit',spit['length'])]:
            points=pose(model,animation,time);frill=[p for n,ps in points.items() if n.startswith('frill_') for p in ps]
            self.assertLess(max(p[0] for p in frill)-min(p[0] for p in frill),10,'Ground frill must fold against the neck')
        points=pose(model,'spit',.4);frill=[p for n,ps in points.items() if n.startswith('frill_') for p in ps]
        self.assertGreater(max(p[0] for p in frill)-min(p[0] for p in frill),25,'Spit must visibly unfold the frill')
        self.assertGreater(min(p[1] for p in frill),8,'Frill must clear the ground')
        self.assertTrue(.9<max(p[1] for p in points['back_stripe'])/16<1.4)

    def test_bites_open_the_lower_jaw_below_the_snout(self):
        for factory in [trex,velociraptor,build_models.dilophosaurus]:
            model=factory();jaw='lower_jaw' if model.name=='ark_dilophosaurus' else 'jaw'
            def gap(animation,time):
                points=pose(model,animation,time)
                front_y=lambda name:sum(points[name][i][1] for i in [0,2,4,6])/4
                return front_y('snout')-front_y(jaw)
            self.assertGreater(gap('attack',.16)-gap('attack',0),3,'Bite must open downwards, not hide the jaw inside the skull')
            if model.name=='ark_dilophosaurus':self.assertGreater(gap('spit',.4)-gap('spit',0),3)

    def test_walk_clock_keeps_a_readable_cadence_at_actual_mount_speeds(self):
        for factory in [trex,triceratops,stegosaurus,brachiosaurus,velociraptor,pteranodon,build_models.dilophosaurus]:
            model=factory();stride=getattr(model,'walk_stride_blocks',0)
            self.assertGreater(stride,0,'Walk needs a measured distance per full cycle')
            walk=next(a for a in model.animations if a['name']=='walk')
            expression=walk['anim_time_update']
            self.assertIn('query.delta_time',expression,'An absolute distance clock drives these short legs at 4–11 cycles/s')
            for fps in [30,60]:
                for speed,expected in [(4.4,1.1),(6.8,1.7),(20,1.8)]:
                    for size in [.45,.725,1]:
                        clock=0
                        for frame in range(fps*2):
                            query=SimpleNamespace(anim_time=clock,delta_time=1/fps,ground_speed=speed,model_scale=size,
                                                  modified_distance_moved=1000+speed*frame/fps)
                            next_clock=eval(expression,{'__builtins__':{},'query':query,'math':SimpleNamespace(clamp=lambda v,a,b:max(a,min(b,v)))})
                            self.assertAlmostEqual(next_clock-clock,expected/fps)
                            clock=next_clock
                        self.assertAlmostEqual(clock,expected*2)
            leg=model.legs[0];owned=set(c for c in model.nodes[leg]['children'] if isinstance(c,str))
            feet={c['name'] for c in model.elements if c['name'] in model.feet and c['uuid'] in owned}
            def foot_z(t):
                points=pose(model,'walk',t)
                values=[p[2] for name,ps in points.items() if name in feet for p in ps]
                return sum(values)/len(values)
            start,end=foot_z(0),foot_z(.6)
            self.assertAlmostEqual(stride,(end-start)/16/.6,places=5)

    def test_walk_targets_are_close_enough_for_java_display_interpolation(self):
        for factory in [trex,triceratops,stegosaurus,brachiosaurus,velociraptor,pteranodon,build_models.dilophosaurus]:
            model=factory();walk=next(a for a in model.animations if a['name']=='walk')
            for leg in model.legs:
                keys=[k for k in walk['animators'][model.groups[leg]['uuid']]['keyframes'] if k['channel']=='rotation']
                angles=[float(k['data_points'][0]['x']) for k in keys]
                self.assertLessEqual(max(abs(a-b) for a,b in zip(angles,angles[1:])),10,model.name+': sparse swing targets cause display interpolation snaps')
                self.assertGreater(min(b['time']-a['time'] for a,b in zip(keys,keys[1:])),.001,'Floating-point duplicate times must not create zero-tick segments')

    def test_beaked_herbivores_have_small_recessed_cheek_teeth(self):
        for factory,edge,front in [(stegosaurus,2.5,-31),(triceratops,5.3,-26)]:
            model=factory()
            for animation,time in [('idle',0),('attack',.16)]:
                points=pose(model,animation,time)
                for name,ps in points.items():
                    if not name.startswith('tooth_'):continue
                    self.assertLess(max(p[0] for p in ps)-min(p[0] for p in ps),.5,model.name+': cheek teeth must not look like broad human incisors')
                    self.assertLess(max(p[1] for p in ps)-min(p[1] for p in ps),.5,'Grinding crowns must be short')
                    self.assertLess(max(abs(p[0]) for p in ps),edge,'Teeth belong inside the jaw edges')
                    self.assertGreater(min(p[2] for p in ps),front,'Keep the forward beak toothless')

    def test_brachiosaurus_tail_reaches_ground_without_lowering_back(self):
        model=brachiosaurus()
        points=pose(model,'idle',0)
        self.assertAlmostEqual(max(p[1] for p in points['barrel']),40)
        tip=points['tail_segment_4']
        self.assertGreaterEqual(min(p[1] for p in tip),0)
        self.assertLess(min(p[1] for p in tip),.7,'Tail tip must visibly reach the floor')

    def test_trex_has_visible_curved_toe_and_two_finger_claws(self):
        model=trex();points=pose(model,'idle',0)
        for side in ['left','right']:
            for i in range(3):
                toe=[p for name,ps in points.items() if name==f'toe_{side}_{i}_tip' for p in ps]
                claw=[p for name,ps in points.items() if name.startswith(f'toe_claw_{side}_{i}_') for p in ps]
                self.assertTrue(claw,'Tiny straight toe caps are not visible curved claws')
                self.assertGreater(min(p[2] for p in toe)-min(p[2] for p in claw),2)
                self.assertGreater(max(p[1] for p in claw)-min(p[1] for p in claw),1)
                root=next(c for c in model.elements if c['name']==f'toe_claw_{side}_{i}_root')
                self.assertGreaterEqual(root['to'][0]-root['from'][0],1.1)
            for i in range(2):
                claw=[p for name,ps in points.items() if name.startswith(f'hand_claw_{side}_{i}') for p in ps]
                self.assertTrue(claw,'Each hand needs two visible ivory claws')
                self.assertGreater(max(p[2] for p in claw)-min(p[2] for p in claw),2)
                self.assertGreater(max(p[1] for p in claw)-min(p[1] for p in claw),1)

    def test_pteranodon_ground_wings_fold_beside_body_and_flight_opens(self):
        model=pteranodon()
        for animation in ['idle','walk','flap','glide']:
            length=next(a for a in model.animations if a['name']==animation)['length']
            for fraction in [0,.25,.5,.75,1]:
                points=pose(model,animation,length*fraction)
                wings=[p for name,ps in points.items() if name.startswith(('wing_finger_','membrane_','thumb_')) for p in ps]
                span=max(p[0] for p in wings)-min(p[0] for p in wings)
                if animation in ['idle','walk']:
                    self.assertLess(span,55,(animation,'Ground wing span must visibly fold'))
                    self.assertGreaterEqual(min(p[1] for p in wings),0,'Folded membranes must clear the floor')
                    for side,sign in [('left',-1),('right',1)]:
                        side_points=[p for name,ps in points.items() if name.startswith(('wing_finger_','membrane_','thumb_')) and '_'+side in name for p in ps]
                        self.assertGreater(min(sign*p[0] for p in side_points),3,'Fold must not cross the body')
                else:self.assertGreater(span,110,(animation,'Flight wings must open fully'))
            tracks=next(a for a in model.animations if a['name']==animation)['animators']
            for name in ['wing_left','wing_right','wing_tip_left','wing_tip_right']:
                self.assertIn(model.groups[name]['uuid'],tracks,'Every state must explicitly set both folding joints')


if __name__=='__main__':unittest.main()
