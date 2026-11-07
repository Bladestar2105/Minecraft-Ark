"""Original CC0 voxel dinosaurs; editable Blockbench and explicit Bedrock exports."""
from pathlib import Path
import base64, hashlib, json, math, struct, uuid, zlib

ROOT=Path(__file__).resolve().parent
SIDES=('north','south','east','west','up','down')
def ident(value): return str(uuid.uuid5(uuid.NAMESPACE_URL,'ark-dino-designs/'+value))
def rotate(v,rotation):
    x,y,z=v
    for axis,degrees in enumerate(rotation):
        c,s=math.cos(math.radians(degrees)),math.sin(math.radians(degrees))
        if axis==0:y,z=y*c-z*s,y*s+z*c
        elif axis==1:x,z=x*c+z*s,-x*s+z*c
        else:x,y=x*c-y*s,x*s+y*c
    return [x,y,z]
def vertices(c):
    p=c['origin'];a,b=c['from'],c['to']
    return [[r[i]+p[i] for i in range(3)] for r in (rotate([x-p[0],y-p[1],z-p[2]],c.get('rotation',[0,0,0])) for x,y,z in [(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])])]
def png_texture(colors):
    def chunk(kind,b):return struct.pack('>I',len(b))+kind+b+struct.pack('>I',zlib.crc32(kind+b))
    row=b'\0'+b''.join(bytes.fromhex(colors[x//8][1:])+b'\xff' for x in range(64))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(row*64))+chunk(b'IEND',b'')

class Model:
    def __init__(self,name,label,colors,description):
        self.name=name;self.label=label;self.colors=colors;self.description=description
        self.groups={};self.nodes={};self.elements=[];self.animations=[];self.feet=[];self.legs=[]
    def bone(self,name,pivot,parent=None):
        self.groups[name]=dict(name=name,origin=list(pivot),uuid=ident(self.name+'/'+name),rotation=[0,0,0],export=True)
        self.nodes[name]=dict(uuid=self.groups[name]['uuid'],isOpen=True,children=[])
        if parent:self.nodes[parent]['children'].append(self.nodes[name])
    def cube(self,name,a,b,bone='body',color=0,rotation=(0,0,0),pivot=None,ground=False):
        assert name not in {c['name'] for c in self.elements},name
        c=dict(name=name,type='cube',uuid=ident(self.name+'/cube/'+name),box_uv=False,origin=list(pivot or self.groups[bone]['origin']),rotation=list(rotation),faces={s:dict(uv=[color*8+1,1,color*8+7,7],texture=0) for s in SIDES})
        c['from']=list(a);c['to']=list(b);self.elements.append(c);self.nodes[bone]['children'].append(c['uuid'])
        if ground:self.feet.append(name)
        return c
    def beam(self,name,start,end,width,depth=None,bone='body',color=0,ground=False):
        depth=depth or width;dx,dy,dz=[b-a for a,b in zip(start,end)];length=math.sqrt(dx*dx+dy*dy+dz*dz)
        rotation=[math.degrees(math.acos(dy/length)),math.degrees(math.atan2(dx,dz)),0]
        return self.cube(name,[start[0]-width/2,start[1],start[2]-depth/2],[start[0]+width/2,start[1]+length,start[2]+depth/2],bone,color,rotation,start,ground)
    def tapered(self,name,start,end,width,bone='body',color=6,steps=3):
        for i in range(steps):
            a=[p+(q-p)*i/steps for p,q in zip(start,end)];b=[p+(q-p)*(i+1)/steps for p,q in zip(start,end)]
            self.beam(name+str(i),a,b,width*(1-.7*i/steps),bone=bone,color=color)
    def eyes(self,x,y,z,bone='head',size=1.5):
        for side in [-1,1]:
            a,b=sorted([side*x,side*(x+.3)])
            self.cube('eye_'+str(side),[a,y,z],[b,y+size,z+size*1.5],bone,4)
            a,b=sorted([side*(x+.31),side*(x+.45)])
            self.cube('pupil_'+str(side),[a,y+.15,z+.55*size],[b,y+size-.15,z+.85*size],bone,5)
    def animate(self,name,length,tracks):
        self.animations.append(dict(uuid=ident(self.name+'/animation/'+name),name=name,loop='loop',override=False,length=length,snapping=20,animators={self.groups[b]['uuid']:dict(name=b,type='bone',keyframes=[dict(channel=channel,time=t,uuid=ident(f'{self.name}/{name}/{b}/{t}'),interpolation='linear',data_points=[dict(zip('xyz',map(str,v)))]) for t,v in frames]) for b,(channel,frames) in tracks.items()}))
    def finish(self):
        # Ground the lowest toe/sole exactly; all rest-pose shapes remain at or above the floor.
        low=min(v[1] for c in self.elements for v in vertices(c));shift=-low
        for c in self.elements:
            for field in ['from','to','origin']:c[field][1]+=shift
        for g in self.groups.values():g['origin'][1]+=shift
        self.animate('idle',2,{'head':('rotation',[(0,[0,-1,0]),(1,[1.5,1,0]),(2,[0,-1,0])]),'tail':('rotation',[(0,[0,-2,0]),(1,[0,2,0]),(2,[0,-2,0])])})
        tracks={}
        for i,name in enumerate(self.legs):
            amplitude=17 if len(self.legs)==2 else 11;phase=1 if i%2==0 else -1
            tracks[name]=('rotation',[(0,[amplitude*phase,0,0]),(.5,[-amplitude*phase,0,0]),(1,[amplitude*phase,0,0])])
        tracks['tail']=('rotation',[(0,[0,-5,0]),(.5,[0,5,0]),(1,[0,-5,0])])
        self.animate('walk',1,tracks)
        return self
    def export(self):
        out=ROOT/'models'/self.name;out.mkdir(parents=True,exist_ok=True);tex=png_texture(self.colors)
        m=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name=self.name,model_identifier=self.name,resolution=dict(width=64,height=64),elements=self.elements,groups=list(self.groups.values()),outliner=[self.nodes['body']],textures=[dict(name=self.name+'.png',id='0',uuid=ident(self.name+'/texture'),width=64,height=64,uv_width=64,uv_height=64,source='data:image/png;base64,'+base64.b64encode(tex).decode())],animations=self.animations)
        (out/(self.name+'.bbmodel')).write_text(json.dumps(m,indent=2)+'\n');(out/(self.name+'.png')).write_bytes(tex)
        # Mirror X and rotation X/Y like Blockbench's official Bedrock codec.
        bones=[]
        for name,g in self.groups.items():
            parent=next((p for p,n in self.nodes.items() if self.nodes[name] in n['children']),None)
            b=dict(name=name,pivot=[-g['origin'][0],*g['origin'][1:]],cubes=[])
            if parent:b['parent']=parent
            for c in self.elements:
                if c['uuid'] not in self.nodes[name]['children']:continue
                a,t=c['from'],c['to'];rot=c['rotation'];uv={s:dict(uv=c['faces'][s]['uv'][:2],uv_size=[6,6]) for s in SIDES}
                for s in ['up','down']:uv[s]=dict(uv=[uv[s]['uv'][0]+6,uv[s]['uv'][1]+6],uv_size=[-6,-6])
                q=dict(origin=[-t[0],a[1],a[2]],size=[t[i]-a[i] for i in range(3)],uv=uv)
                if any(rot):q.update(pivot=[-c['origin'][0],*c['origin'][1:]],rotation=[-rot[0],-rot[1],rot[2]])
                b['cubes'].append(q)
            bones.append(b)
        points=[v for c in self.elements for v in vertices(c)];height=max(v[1] for v in points);width=max(max(abs(v[i]) for v in points) for i in [0,2])*2
        geo={'format_version':'1.12.0','minecraft:geometry':[dict(description=dict(identifier='geometry.'+self.name,texture_width=64,texture_height=64,visible_bounds_width=round(width/16+1,2),visible_bounds_height=round(height/16+1,2),visible_bounds_offset=[0,round(height/32,2),0]),bones=bones)]}
        if self.name=='ark_pteranodon':
            # Flapping wingtips extend below the rest-pose box.
            geo['minecraft:geometry'][0]['description'].update(visible_bounds_height=4,visible_bounds_offset=[0,1.3,0])
        (out/(self.name+'.geo.json')).write_text(json.dumps(geo,indent=2)+'\n')
        animations={}
        for a in self.animations:
            tracks={}
            for animator in a['animators'].values():
                channel=animator['keyframes'][0]['channel'];values={}
                for k in animator['keyframes']:
                    v=[float(k['data_points'][0][c]) for c in 'xyz']
                    if channel in ['position','rotation']:v[0]*=-1
                    if channel=='rotation':v[1]*=-1
                    values[str(k['time'])]=v
                tracks[animator['name']]={channel:values}
            animations[a['name']]=dict(loop=True,animation_length=a['length'],bones=tracks)
        (out/(self.name+'.animation.json')).write_text(json.dumps(dict(format_version='1.8.0',animations=animations),indent=2)+'\n')
        (out/'config.json').write_text(json.dumps(dict(head_rotation=True,material='entity_alphatest_change_color_one_sided',blend_transition=True,binding_bones={self.name:['*']},anim_textures={},texture_materials={},disable_part_visibility=True),indent=2)+'\n')
        (out/'design.json').write_text(json.dumps(dict(name=self.name,label=self.label,palette=self.colors,description=self.description,ground_parts=self.feet,leg_bones=self.legs,cubes=len(self.elements),bones=len(self.groups)),indent=2)+'\n')
        return m

def digitigrade(m,side,x,hip_y,hip_z,scale=1,sickle=False):
    leg='leg_'+side;m.bone(leg,[x,hip_y,hip_z],'body');m.legs.append(leg)
    m.beam('thigh_'+side,[x,hip_y,hip_z],[x,hip_y*.56,hip_z-3*scale],4.8*scale,6*scale,leg)
    ankle=[x,4.8*scale,hip_z+1.2*scale]
    m.beam('shin_'+side,[x,hip_y*.56,hip_z-3*scale],ankle,2.5*scale,2.6*scale,leg,1)
    base=[x,1.25*scale,hip_z-2*scale]
    m.beam('raised_heel_'+side,ankle,base,1.8*scale,1.8*scale,leg,1)
    for i,spread in enumerate([-1,0,1]):
        start=[x+spread*.8*scale,1.15*scale,base[2]-.3*scale]
        end=[x+spread*3.3*scale,.65*scale,base[2]-(7 if spread==0 else 5.8)*scale]
        if sickle and i==0:
            start[1]+=2.2*scale;end[1]+=2.2*scale;end[2]+=2*scale
        middle=[(start[j]+end[j])*.5 for j in range(3)]
        m.beam(f'toe_{side}_{i}_base',start,middle,1.2*scale,1.05*scale,leg,3,True)
        m.beam(f'toe_{side}_{i}_tip',middle,end,.9*scale,.8*scale,leg,3,True)
        claw=[end[0]+spread*.3*scale,end[1]-.15*scale,end[2]-1.4*scale]
        m.beam(f'claw_{side}_{i}',end,claw,.6*scale,.55*scale,leg,6,True)
        if sickle and i==0:m.tapered('sickle_'+side,claw,[claw[0],claw[1]-2*scale,claw[2]-.35*scale],.55*scale,leg,5,2)

def quadruped(m,x,front_z,back_z,hip_y,width):
    for side,sx,z in [('front_left',-x,front_z),('back_right',x,back_z),('front_right',x,front_z),('back_left',-x,back_z)]:
        name='leg_'+side;m.bone(name,[sx,hip_y,z],'body');m.legs.append(name)
        m.cube('leg_'+side,[sx-width/2,2,z-width/2],[sx+width/2,hip_y+1,z+width/2],name,0)
        m.cube('sole_'+side,[sx-width*.56,0,z-width*.58],[sx+width*.56,3,z+width*.58],name,3,ground=True)
        for dx in [-.28,0,.28]:m.cube('nail_'+side+str(dx),[sx+(dx-.09)*width,0,z-width*.6-.25],[sx+(dx+.09)*width,1.2,z-width*.6+.2],name,6,ground=True)

def trex():
    m=Model('ark_trex','T-Rex',['#4d7c48','#6e9957','#b8bd83','#304c35','#e8be58','#14251a','#ddd5b0','#934b43'],'Großer Kopf · kräftiger Schwanz · angehobene Ferse · drei gespreizte Zehen')
    m.bone('body',[0,23,3]);m.cube('torso',[-7,16,-9],[7,30,13]);m.cube('belly',[-5.6,15,-8],[5.6,20,10],color=2)
    m.bone('neck',[0,27,-8],'body');m.cube('neck',[-5,24,-17],[5,35,-7],'neck',1)
    m.bone('head',[0,33,-14],'neck');m.cube('skull',[-7,31,-29],[7,40,-12],'head',1);m.cube('snout',[-6.5,31,-35],[6.5,37,-23],'head')
    for x in [-6.5,6.5]:m.cube('brow_'+str(x),[x-1,36,-24],[x+1,40,-17],'head',3)
    m.eyes(7.1,34,-24,size=2)
    m.bone('jaw',[0,31,-14],'head');m.cube('jaw',[-6,27.5,-34],[6,30.5,-13],'jaw');m.cube('mouth',[-5.5,30.5,-33],[5.5,31,-14],'head',7)
    for x in [-5,5]:
        for z in [-32,-27,-22]:m.tapered(f'tooth_{x}_{z}',[x,31,z],[x,29.6,z],.65,'head',6,2)
    m.bone('tail',[0,24,11],'body');m.cube('tail_base',[-5,19,11],[5,27,28],'tail');m.cube('tail_tip',[-2.5,21,27],[2.5,25,48],'tail',1)
    for side,x in [('left',-7),('right',7)]:
        digitigrade(m,side,x,22,6)
        m.bone('arm_'+side,[x*.85,26,-9],'body');m.beam('tiny_arm_'+side,[x*.85,26,-10],[x*.85,21,-14],1.8,bone='arm_'+side,color=1);m.beam('hand_'+side,[x*.85,21,-14],[x*.85,21,-17],1.1,bone='arm_'+side,color=3)
    return m.finish()

def triceratops():
    m=Model('ark_triceratops','Triceratops',['#b97546','#d9a05f','#d4bd80','#6b5039','#ffe18b','#26251c','#eee0b8','#a55336'],'Drei Hörner · breiter Nackenschild · vier schwere Beine')
    m.bone('body',[0,22,2]);m.cube('barrel',[-11,11,-13],[11,29,17]);m.cube('back',[-8,28,-10],[8,31,14],color=1);m.cube('belly',[-8,10,-10],[8,15,13],color=2)
    quadruped(m,8,-9,12,16,5)
    m.bone('head',[0,21,-13],'body');m.cube('head',[-7,13,-30],[7,25,-15],'head');m.cube('snout',[-5.5,12,-38],[5.5,20,-26],'head',1);m.cube('beak',[-5,12,-40],[5,17,-36],'head',3)
    m.cube('frill_lower',[-11,19,-18],[11,28,-14],'head',7);m.cube('frill_middle',[-12,26,-17],[12,32,-14],'head',7);m.cube('frill_top',[-8,31,-16.5],[8,35,-13.5],'head',1)
    for x in [-10,-5,0,5,10]:m.cube('frill_knob_'+str(x),[x-1.2,31.5 if abs(x)>8 else 34,-17],[x+1.2,34 if abs(x)>8 else 36.5,-13.5],'head',6)
    for side in [-1,1]:m.tapered('brow_horn_'+str(side),[side*5,24,-26],[side*7.4,33,-40],3.1,'head',6,4)
    m.tapered('nose_horn',[0,19,-35],[0,25,-40],2.3,'head',6,3);m.eyes(7.05,20,-26,size=1.6)
    m.bone('tail',[0,20,16],'body');m.beam('tail_base',[0,20,16],[0,17,30],5,bone='tail');m.beam('tail_tip',[0,17,29],[0,14,39],2.7,bone='tail',color=1)
    return m.finish()

def stegosaurus():
    m=Model('ark_stegosaurus','Stegosaurus',['#497c78','#76a199','#b9b99a','#31554f','#f7ce68','#183633','#e7ddba','#d47c43'],'Zwei versetzte Plattenreihen · kleiner Kopf · vier Schwanzstacheln')
    m.bone('body',[0,22,0]);m.cube('barrel',[-10,12,-15],[10,27,17]);m.cube('back',[-7,25,-11],[7,30,14],color=1);m.cube('belly',[-7,11,-11],[7,16,12],color=2)
    quadruped(m,7.5,-10,12,16,4.4)
    m.bone('head',[0,18,-14],'body');m.beam('neck',[0,20,-13],[0,14,-27],5,bone='head',color=1);m.cube('head',[-3.5,10,-36],[3.5,17,-26],'head',1);m.cube('beak',[-2.8,10,-39],[2.8,13,-34],'head',3);m.eyes(3.5,14,-31,size=1)
    for side in [-1,1]:
        for i,z in enumerate([-10,-1,8,16]):
            z+=2.5 if side>0 else 0;high=[9,14,16,11][i];x=side*2.5
            m.cube(f'plate_{side}_{i}_base',[x-1.1,27,z-1.6],[x+1.1,30,z+1.6],color=7)
            m.cube(f'plate_{side}_{i}_middle',[x-1.1,29,z-3.8],[x+1.1,30+high*.55,z+3.8],color=7 if side<0 else 1)
            m.cube(f'plate_{side}_{i}_top',[x-1,29+high*.55,z-2],[x+1,29+high,z+2],color=7)
    m.bone('tail',[0,20,16],'body');m.beam('tail_base',[0,20,15],[0,14,32],5.8,bone='tail');m.beam('tail_middle',[0,14,31],[0,10,45],3.8,bone='tail');m.beam('tail_tip',[0,10,44],[0,9,56],2,bone='tail',color=1)
    for side in [-1,1]:
        for z in [42,49]:m.tapered(f'tail_spike_{side}_{z}',[side,11,z],[side*9,17,z+5],1.9,'tail',6,3)
    return m.finish()

def brachiosaurus():
    m=Model('ark_brachiosaurus','Brachiosaurus',['#668d9b','#8fb6b7','#d1d0af','#446575','#f0d481','#1b3339','#e7dfbd','#527f8d'],'Sehr langer Hals · hohe Schultern · säulenartige Beine')
    m.bone('body',[0,31,2]);m.cube('barrel',[-11,21,-14],[11,40,20]);m.cube('shoulders',[-10,35,-14],[10,44,-3],color=1);m.cube('belly',[-8,20,-11],[8,27,16],color=2)
    quadruped(m,8,-9,14,26,5)
    m.bone('neck',[0,40,-10],'body');m.beam('neck_lower',[0,39,-9],[0,63,-20],9,8,'neck');m.beam('neck_upper',[0,61,-19],[0,85,-29],6.5,6,'neck',1)
    m.bone('head',[0,84,-29],'neck');m.cube('skull',[-4.6,82,-41],[4.6,90,-27],'head',1);m.cube('muzzle',[-4,81,-44],[4,86,-36],'head');m.cube('nasal_rise',[-3,88,-36],[3,93,-29],'head',1);m.cube('lower_jaw',[-3.5,80,-42],[3.5,82,-29],'head',2);m.eyes(4.6,86,-35,size=1.4)
    for x in [-2,2]:m.cube('nostril_'+str(x),[x-.7,85,-44.2],[x+.7,86,-43.8],'head',3)
    m.bone('tail',[0,30,19],'body');m.beam('tail_base',[0,30,18],[0,23,35],7,bone='tail');m.beam('tail_middle',[0,23,34],[0,19,49],4.4,bone='tail');m.beam('tail_tip',[0,19,48],[0,17,65],2.2,bone='tail',color=1)
    return m.finish()

def velociraptor():
    m=Model('ark_velociraptor','Velociraptor',['#b78049','#d5ad69','#d6c49b','#624935','#f2d967','#24281e','#ddd2a7','#8c4936'],'Schlanker Körper · lange Arme · Sichelkrallen · Federakzente')
    m.bone('body',[0,18,1]);m.cube('torso',[-4.2,14,-8],[4.2,23,8]);m.cube('belly',[-3.4,13,-7],[3.4,17,6],color=2);m.cube('back_stripe',[-2,22,-7],[2,24,8],color=7)
    m.bone('neck',[0,21,-7],'body');m.beam('neck',[0,21,-6],[0,29,-15],4.5,bone='neck',color=1)
    m.bone('head',[0,29,-14],'neck');m.cube('skull',[-3.9,27,-24],[3.9,33,-13],'head',1);m.cube('snout',[-3.1,27,-32],[3.1,30.5,-21],'head');m.cube('jaw',[-2.8,25.5,-31],[2.8,27,-15],'head',2);m.cube('mouth',[-3.2,26.9,-30],[3.2,27.3,-19],'head',3);m.eyes(3.95,29,-22,size=1.2)
    for z in [-16,-13,-10]:m.cube('crest_'+str(z),[-.7,32,z],[.7,35,z+1.5],'head',7)
    m.bone('tail',[0,19,7],'body');m.beam('tail_base',[0,19,7],[0,20,25],4,bone='tail');m.beam('tail_middle',[0,20,24],[0,21,41],2.8,bone='tail',color=1);m.beam('tail_tip',[0,21,40],[0,22,53],1.3,bone='tail',color=3)
    for side,x in [('left',-4.7),('right',4.7)]:
        digitigrade(m,side,x,18,4,.73,True)
        m.bone('arm_'+side,[x,22,-6],'body');m.beam('upper_arm_'+side,[x,22,-7],[x*1.5,16,-9],1.7,bone='arm_'+side,color=1);m.beam('forearm_'+side,[x*1.5,16,-9],[x*1.8,17,-18],1.4,bone='arm_'+side)
        sign=-1 if x<0 else 1
        for i,z in enumerate([-11,-13.5,-16]):m.beam(f'feather_{side}_{i}',[x*1.55,16.5,z],[x*1.55+sign*(4-i*.4),15,z+4],1.1,.65,'arm_'+side,7)
        for dx in [-.5,.5]:m.tapered('hand_claw_'+side+str(dx),[x*1.8+dx,17,-18],[x*1.8+dx,16,-20],.55,'arm_'+side,6,2)
    return m.finish()

def pteranodon():
    m=Model('ark_pteranodon','Pteranodon',['#536d7b','#91acb4','#c5c3ae','#344854','#f0d377','#1e3039','#e2d7b3','#b56c4e'],'Langer Schnabel und Kopfkamm · gespannte Flughaut · verlängerter Flügelfinger')
    m.bone('body',[0,21,1]);m.cube('torso',[-4.3,16,-6],[4.3,26,8]);m.cube('breast',[-3.6,15,-5],[3.6,20,5],color=2)
    m.bone('neck',[0,23,-5],'body');m.beam('neck',[0,23,-5],[0,29,-14],4.2,bone='neck',color=1)
    m.bone('head',[0,29,-14],'neck');m.cube('skull',[-3.1,27,-24],[3.1,33,-12],'head',1)
    m.tapered('long_beak',[0,28.8,-23],[0,28,-49],4,'head',6,6);m.tapered('lower_beak',[0,27.1,-23],[0,26.9,-46],2.2,'head',2,5)
    m.tapered('head_crest',[0,32,-13],[0,43,7],4.3,'head',7,5);m.eyes(3.15,29,-20,size=1.3)
    m.bone('tail',[0,20,7],'body');m.tapered('short_tail',[0,20,7],[0,19,13],2.8,'tail',0,2)
    for side,sign in [('left',-1),('right',1)]:
        wing='wing_'+side;m.bone(wing,[sign*4,23,0],'body')
        # The long outer leading edge is the extended fourth finger, not a bird feather fan.
        for i,(x1,x2,z1,z2) in enumerate([(4,16,0,-1),(16,31,-1,-8),(31,47,-8,-3),(47,65,-3,5)]):
            m.beam(f'wing_finger_{side}_{i}',[sign*x1,23-(x1-4)*.045,z1],[sign*x2,23-(x2-4)*.045,z2],1.8 if i<2 else 1.1,bone=wing,color=6)
        m.tapered('thumb_'+side,[sign*29,22,-7],[sign*27,23,-12],1.3,wing,6,2)
        front=[0,-1,-3,-5,-7,-5,-2,1];back=[14,15,15,14,12,10,8,6]
        for i,(z1,z2) in enumerate(zip(front,back)):
            x1=5+i*7.1;x2=x1+7.25;y=22.7-(x1-4)*.045
            a,b=sorted([sign*x1,sign*x2]);m.cube(f'membrane_{side}_{i}',[a,y-.28,z1],[b,y+.12,z2],wing,7 if i%3 else 1)
        m.beam('membrane_trailing_root_'+side,[sign*4,18,9],[sign*17,22,14],.7,bone=wing,color=1)
        digitigrade(m,side,sign*3.8,16,7,.42)
    m.finish();m.animations=[a for a in m.animations if a['name']!='walk']
    m.animate('flap',1.2,{'wing_left':('rotation',[(0,[0,0,-28]),(.3,[0,0,18]),(.6,[0,0,28]),(.9,[0,0,-15]),(1.2,[0,0,-28])]),'wing_right':('rotation',[(0,[0,0,28]),(.3,[0,0,-18]),(.6,[0,0,-28]),(.9,[0,0,15]),(1.2,[0,0,28])])})
    m.animate('glide',2,{'wing_left':('rotation',[(0,[0,0,-3]),(1,[0,0,-6]),(2,[0,0,-3])]),'wing_right':('rotation',[(0,[0,0,3]),(1,[0,0,6]),(2,[0,0,3])])})
    return m

if __name__=='__main__':
    models=[trex(),triceratops(),stegosaurus(),brachiosaurus(),velociraptor(),pteranodon()]
    for m in models:
        m.export();print(m.name,len(m.elements),'cubes',len(m.groups),'bones')
    (ROOT/'LICENSE.txt').write_text('Original dinosaur geometry, textures, animations and preview generators: CC0 1.0 Universal.\nhttps://creativecommons.org/publicdomain/zero/1.0/\nCreated for this project, 2026-11-07. No third-party dinosaur assets.\n')
