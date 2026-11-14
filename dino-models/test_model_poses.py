"""Visible model features and actual animated poses, using independent corner transforms."""
from itertools import product
from types import SimpleNamespace
import json
import math
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
        if len(frames)==1 or time>=frames[-1]['time']:
            return [float(frames[-1]['data_points'][0][axis]) for axis in 'xyz']
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

def posed_boxes_overlap(corners,other):
    edges=[[corners[i][j]-corners[0][j] for j in range(3)] for i in [4,2,1]]
    basis=[[other[i][j]-other[0][j] for j in range(3)] for i in [4,2,1]]
    axes=basis+edges+[[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]] for a in edges for b in basis]
    for axis in axes:
        length=sum(v*v for v in axis)**.5
        if length<1e-5:continue
        a=[sum(x*y for x,y in zip(p,axis))/length for p in corners];b=[sum(x*y for x,y in zip(p,axis))/length for p in other]
        # Touching hinge faces must survive floating-point rotation.
        if max(a)<min(b)-1e-7 or max(b)<min(a)-1e-7:return False
    return True


def boxes_overlap(corners,low,high):
    return posed_boxes_overlap(corners,list(product(*zip(low,high))))


def exported_model(species):
    data=json.loads((build_models.ROOT/'models'/('ark_'+species)/('ark_'+species+'.bbmodel')).read_text())
    groups={g['uuid']:g for g in data['groups']};nodes={}
    def visit(node):
        nodes[groups[node['uuid']]['name']]=node
        for child in node['children']:
            if isinstance(child,dict):visit(child)
    for root in data['outliner']:visit(root)
    return SimpleNamespace(name=data['name'],groups={g['name']:g for g in groups.values()},nodes=nodes,
                           elements=[c for c in data['elements'] if c['type']=='cube'],animations=data['animations'])


class ModelPoseTest(unittest.TestCase):
    def test_attack_override_branches_keep_head_and_jaw_clocks_together(self):
        # BetterModel 3.6 resets a replaced bone's phase iterator. Replacing only
        # the jaw leaves it sampling a differently timed head after the attack.
        for species in ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','dilophosaurus','mosasaurus']:
            model=exported_model(species)
            for clip in model.animations:
                if clip['loop']!='once':continue
                affected=set()
                def visit(name,inherited=False):
                    active=inherited or model.groups[name]['uuid'] in clip['animators']
                    if active:affected.add(name)
                    for child in model.nodes[name]['children']:
                        if isinstance(child,dict):
                            child_name=next(n for n,g in model.groups.items() if g['uuid']==child['uuid'])
                            visit(child_name,active)
                visit('body')
                with self.subTest(species=species,clip=clip['name']):
                    self.assertEqual('head' in affected,'jaw' in affected,
                                     'A finite jaw overlay must switch and resume its head on the same animation clock')

    def test_exported_jaws_keep_flesh_contact_in_every_clip_and_growth_stage(self):
        heads={'trex':('skull','snout','cheek_'),'triceratops':('head','snout','beak','frill_'),
               'stegosaurus':('head','beak','neck'),'brachiosaurus':('skull','muzzle','nasal_rise'),
               'velociraptor':('skull','snout'),'dilophosaurus':('skull','snout'),
               'mosasaurus':('skull','snout','cheek_')}
        for species,prefixes in heads.items():
            model=exported_model(species)
            owners={child:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
            jaws=[c['name'] for c in model.elements if owners[c['uuid']]=='jaw' and c['name'].startswith(('jaw','lower_jaw'))]
            skull=[c['name'] for c in model.elements if owners[c['uuid']]=='head' and c['name'].startswith(prefixes)]
            stages=['baby','juvenile','adult'] if 'growth_adult' in {a['name'] for a in model.animations} else [None]
            for stage in stages:
                for clip in model.animations:
                    if clip['name'].startswith('growth_'):continue
                    for step in range(31):
                        with self.subTest(species=species,stage=stage,clip=clip['name'],step=step):
                            points=pose(model,clip['name'],clip['length']*step/30,stage)
                            self.assertTrue(any(posed_boxes_overlap(points[jaw],points[head]) for jaw in jaws for head in skull),
                                            'The lower jaw must remain joined to head flesh throughout every pose')

    def test_exported_anatomical_joints_stay_connected_in_every_clip(self):
        decorations=('eye','pupil','nostril','gums','tooth','oral','mouth','plate','feather','crest','brow','flank','back_mottle','lateral')
        for species in ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus']:
            model=exported_model(species)
            owners={child:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
            parents={child['uuid']:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,dict)}
            flesh={name:[c['name'] for c in model.elements if owners[c['uuid']]==name and not c['name'].startswith(decorations)] for name in model.groups}
            joints=[]
            for name in model.groups:
                if name not in {'head','neck','tail','tail_mid','tail_tip'} and not name.startswith(('arm_','leg_','wing_','flipper_')):continue
                parent=parents.get(model.groups[name]['uuid'])
                if parent and flesh[name] and flesh[parent]:joints.append((name,parent))
            self.assertTrue(joints,species+' needs actual anatomical connections')
            for clip in model.animations:
                if clip['name'].startswith('growth_'):continue
                times={clip['length']*step/30 for step in range(31)}
                times.update(k['time'] for track in clip['animators'].values() for k in track['keyframes'])
                for time in sorted(times):
                    points=pose(model,clip['name'],time)
                    for bone,parent in joints:
                        with self.subTest(species=species,clip=clip['name'],time=time,bone=bone,parent=parent):
                            self.assertTrue(any(posed_boxes_overlap(points[part],points[support]) for part in flesh[bone] for support in flesh[parent]),
                                            'Animated anatomy must remain joined to its supporting flesh')

    def test_exported_growing_crests_keep_contact_with_head_flesh(self):
        for species,bones in [('pteranodon',['growth_head_crest']),('dilophosaurus',['growth_crest_left','growth_crest_right'])]:
            model=exported_model(species)
            skull=[c['name'] for c in model.elements if c['name'].startswith(('skull','snout'))]
            for bone in bones:
                crest=[c['name'] for c in model.elements if c['uuid'] in model.nodes[bone]['children']]
                for stage in ['baby','juvenile','adult']:
                    for step in range(31):
                        with self.subTest(species=species,bone=bone,stage=stage,step=step):
                            points=pose(model,'attack',.75*step/30,stage)
                            self.assertTrue(any(posed_boxes_overlap(points[part],points[head]) for part in crest for head in skull),
                                            'A growing crest must remain joined to the skull or snout flesh')

    def test_exported_dilo_frill_roots_keep_contact_with_neck_or_head(self):
        model=exported_model('dilophosaurus')
        owners={child:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
        parents={child['uuid']:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,dict)}
        def under(name,root):
            while name:
                if name==root:return True
                name=parents.get(model.groups[name]['uuid'])
            return False
        flesh=[c['name'] for c in model.elements if owners[c['uuid']] in ['neck','head'] and c['name'].startswith(('neck_','skull','snout'))]
        details={side:[c['name'] for c in model.elements if under(owners[c['uuid']],'growth_frill_'+side)] for side in ['left','right']}
        for clip in model.animations:
            if clip['name'].startswith('growth_'):
                for track in clip['animators'].values():track['keyframes'][-1]['time']=20
        for stage in ['baby','juvenile','adult']:
            for clip in model.animations:
                if clip['name'].startswith('growth_'):continue
                for step in range(31):
                    points=pose(model,clip['name'],clip['length']*step/30,stage)
                    for side,parts in details.items():
                        with self.subTest(stage=stage,clip=clip['name'],step=step,side=side):
                            self.assertTrue(any(posed_boxes_overlap(points[part],points[body]) for part in parts for body in flesh),
                                            'The growing neck fan must remain joined to adjacent neck or head flesh')

    def test_stego_tail_bite_bounds_ideal_single_step_chord_error(self):
        model=exported_model('stegosaurus')
        track=next(a for a in model.animations if a['name']=='attack')['animators'][model.groups['tail']['uuid']]
        frames=[k for k in track['keyframes'] if k['channel']=='rotation']
        values=[(k['time'],float(k['data_points'][0]['y'])) for k in frames]
        anchors=[(0,0),(.16,-20),(.34,65),(.52,-15),(.75,0)]
        self.assertTrue(set(anchors).issubset(values),'Resampling must retain every original tail strike key')
        for time,value in values:
            a,b=next((a,b) for a,b in zip(anchors,anchors[1:]) if a[0]<=time<=b[0])
            expected=a[1]+(b[1]-a[1])*(time-a[0])/(b[0]-a[0])
            self.assertAlmostEqual(value,expected,places=9,msg='The original linear strike curve must be retained')
        pivot=model.groups['tail']['origin']
        radius=max(math.dist(pivot,g['origin'])/16 for name,g in model.groups.items() if name.startswith(('growth_tail_plate_','growth_tail_spike_')))
        # Display translations interpolate along a chord while parent rotations follow an arc.
        # This bounds one ideal interval; client interpolation restarts can accumulate
        # more transient error, which the separate actual-wire replay measures.
        error=max(radius*(1-math.cos(math.radians(abs(b[1]-a[1]))/2)) for a,b in zip(values,values[1:]))
        self.assertLess(error,.05,'Sparse strike keys slide child roots along the tail despite matching rotation clocks')

    def test_exported_stego_growth_roots_stay_joined_to_flesh(self):
        model=exported_model('stegosaurus')
        owners={child:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,str)}
        parents={child['uuid']:name for name,node in model.nodes.items() for child in node['children'] if isinstance(child,dict)}
        features={}
        for name,node in model.nodes.items():
            if not name.startswith('growth_'):continue
            ancestors=[];parent=parents.get(model.groups[name]['uuid'])
            while parent:
                ancestors.append(parent);parent=parents.get(model.groups[parent]['uuid'])
            details=[c['name'] for c in model.elements if owners[c['uuid']]==name]
            flesh=[c['name'] for c in model.elements if owners[c['uuid']] in ancestors and not c['name'].startswith(('plate_','neck_plate_','tail_plate_','tail_spike_','tooth_','gums_'))]
            features[name]=(details,flesh)
        # These stage poses are constant; extend their endpoint over the longer walk clip.
        for clip in model.animations:
            if clip['name'].startswith('growth_'):
                for track in clip['animators'].values():track['keyframes'][-1]['time']=20
        for stage in ['baby','juvenile','adult']:
            for clip in model.animations:
                if clip['name'].startswith('growth_'):continue
                for step in range(31):
                    points=pose(model,clip['name'],clip['length']*step/30,stage)
                    for bone,(details,flesh) in features.items():
                        with self.subTest(stage=stage,clip=clip['name'],step=step,bone=bone):
                            self.assertTrue(any(posed_boxes_overlap(points[detail],points[body]) for detail in details for body in flesh),
                                            'A growing plate or spike root must remain joined to the supporting flesh')

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
