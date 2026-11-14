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
    rows=[]
    for y in range(64):
        row=bytearray(b'\0')
        for x in range(64):
            material=x//16+4*(y//32);u,v=x%16,y%32
            rgb=bytes.fromhex(colors[material][1:]);noise=((u*73+v*151+u*v*19)%17)-8
            # Staggered scale edges, broad mottling and fine pores share a material tile.
            edge=(v%4==0 or (u+(2 if (v//4)%2 else 0))%5==0)
            shade=noise*.7+7*math.sin(u*.48+v*.31)-11*edge
            if material==2:shade=noise*.45+5*(v%5)-9
            elif material==6:shade=noise*.4+9*math.sin(u*1.5)-v*.18
            elif material==4:shade=14 if (u-5)**2+(v-10)**2<12 else noise*.25
            elif material==5:shade=noise*.4
            elif material==7:shade=noise*.6-12*(u%6==v%6)+7*math.sin(v*.3)
            row.extend(max(0,min(255,round(c+shade))) for c in rgb);row.append(255)
        rows.append(row)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')

class Model:
    def __init__(self,name,label,colors,description):
        self.name=name;self.label=label;self.colors=colors;self.description=description
        self.groups={};self.nodes={};self.elements=[];self.colliders=[];self.animations=[];self.feet=[];self.legs=[];self.growth_details={}
    def bone(self,name,pivot,parent=None):
        self.groups[name]=dict(name=name,origin=list(pivot),uuid=ident(self.name+'/'+name),rotation=[0,0,0],export=True)
        self.nodes[name]=dict(uuid=self.groups[name]['uuid'],isOpen=True,children=[])
        if parent:self.nodes[parent]['children'].append(self.nodes[name])
    def cube(self,name,a,b,bone='body',color=0,rotation=(0,0,0),pivot=None,ground=False):
        assert name not in {c['name'] for c in self.elements},name
        sizes=[b[i]-a[i] for i in range(3)];faces={}
        for i,side in enumerate(SIDES):
            w,h=(sizes[0],sizes[1]) if side in ('north','south') else (sizes[2],sizes[1]) if side in ('east','west') else (sizes[0],sizes[2])
            w,h=min(14,max(4,math.ceil(w))),min(24,max(4,math.ceil(h)))
            seed=sum(name.encode())+i*7;u=(color%4)*16+1;v=(color//4)*32+1+seed%(30-h)
            faces[side]=dict(uv=[u,v,u+w,v+h],texture=0)
        c=dict(name=name,type='cube',uuid=ident(self.name+'/cube/'+name),box_uv=False,origin=list(pivot or self.groups[bone]['origin']),rotation=list(rotation),faces=faces)
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
    def mass(self,name,a,b,bone='body',color=0):
        # Overlapping chamfered cross-sections give a barrel rather than a single box.
        x,y,z=a;xx,yy,zz=b;inset=(xx-x)*.14;rise=(yy-y)*.16
        self.cube(name,[x+inset,y,z],[xx-inset,yy,zz],bone,color)
        self.cube(name+'_flanks',[x,y+rise,z+.5],[xx,yy-rise,zz-.5],bone,color)
    def tail_chain(self,points,widths):
        for i,(a,b,w) in enumerate(zip(points,points[1:],widths)):
            self.beam('tail_segment_'+str(i),a,b,w,w*.8,'tail',0 if i<3 else 1)
    def eyes(self,x,y,z,bone='head',size=1.5):
        for side in [-1,1]:
            a,b=sorted([side*x,side*(x+.3)])
            self.cube('eye_'+str(side),[a,y,z],[b,y+size,z+size*1.5],bone,4)
            a,b=sorted([side*(x+.31),side*(x+.45)])
            self.cube('pupil_'+str(side),[a,y+.15,z+.55*size],[b,y+size-.15,z+.85*size],bone,5)
    def tooth(self,name,start,end,bone,width,shape='point'):
        middle=[a+(b-a)*.55 for a,b in zip(start,end)]
        depth=width*.4 if shape=='spatulate' else width
        self.beam(name+'_root',start,middle,width*.65 if shape=='spatulate' else width,depth,bone,6)
        self.beam(name+'_tip',middle,end,width if shape=='spatulate' else width*(.85 if shape=='grinder' else .45),depth if shape!='point' else depth*.45,bone,6)
    def mouth_rows(self,x,upper_y,lower_y,upper_z,lower_z,width,length,shape='point'):
        for side,sign in [('left',-1),('right',1)]:
            for row,y,zs,direction,bone in [('upper',upper_y,upper_z,-1,'head'),('lower',lower_y,lower_z,1,'jaw')]:
                self.cube(f'gums_{row}_{side}',[sign*x-width*.65,y-.15,min(zs)-.4],[sign*x+width*.65,y+.15,max(zs)+.4],bone,3)
                for i,z in enumerate(zs):self.tooth(f'tooth_{row}_{side}_{i}',[sign*x,y,z],[sign*x,y+direction*length,z-.15],bone,width,shape)
    def animate(self,name,length,tracks,loop='loop'):
        animators={}
        for bone,channels in tracks.items():
            if isinstance(channels,tuple):channels=[channels]
            frames=[dict(channel=channel,time=t,uuid=ident(f'{self.name}/{name}/{bone}/{channel}/{t}'),interpolation='linear',data_points=[dict(zip('xyz',map(str,v)))]) for channel,values in channels for t,v in values]
            animators[self.groups[bone]['uuid']]=dict(name=bone,type='bone',keyframes=frames)
        self.animations.append(dict(uuid=ident(self.name+'/animation/'+name),name=name,loop=loop,override=False,length=length,snapping=20,animators=animators))
    def growth_detail(self,name,pivot,parent,prefixes,stages=(.2,.55)):
        self.bone(name,pivot,parent);self.growth_details[name]=stages
        for cube in self.elements:
            if cube['uuid'] in self.nodes[parent]['children'] and cube['name'].startswith(prefixes):
                self.nodes[parent]['children'].remove(cube['uuid']);self.nodes[name]['children'].append(cube['uuid'])
    def nametag(self):
        def shapes(node):
            return [child for child in node['children'] if isinstance(child,str)]+[uid for child in node['children'] if isinstance(child,dict) for uid in shapes(child)]
        head_shapes=set(shapes(self.nodes['head']))
        pivot=self.groups['head']['origin'][:]
        pivot[1]=max(v[1] for cube in self.elements if cube['uuid'] in head_shapes for v in vertices(cube))+8
        self.bone('name_anchor',pivot,'head')
    def collider(self,name,a,b,parent):
        element=dict(name=name,type='bounding_box',uuid=ident(self.name+'/'+name),**{'from':list(a),'to':list(b)})
        self.colliders.append(element);self.nodes[parent]['children'].append(element['uuid'])
    def finish(self):
        # Ground the lowest toe/sole exactly; all rest-pose shapes remain at or above the floor.
        low=min(v[1] for c in self.elements for v in vertices(c));shift=-low
        for c in self.elements:
            for field in ['from','to','origin']:c[field][1]+=shift
        for g in self.groups.values():g['origin'][1]+=shift
        self.nametag()
        self.animate('idle',2,{'head':('rotation',[(0,[0,-1,0]),(1,[1.5,1,0]),(2,[0,-1,0])]),'tail':('rotation',[(0,[0,-2,0]),(1,[0,2,0]),(2,[0,-2,0])])})
        tracks={};strides=[]
        for i,name in enumerate(self.legs):
            amplitude=16 if len(self.legs)==2 else 11;phase=i*.5 if len(self.legs)==2 else [0,.5,.25,.75][i]
            curve=[(0,amplitude,0),(.2,amplitude*.35,0),(.6,-amplitude,0),(.72,-amplitude*.55,1.8),(.86,amplitude*1.1,2.8),(1,amplitude,0)]
            times=sorted({0.,1.,*(round((t-phase)%1,10) for t,_,_ in curve)})
            foot=[v for c in self.elements if c['name'] in self.feet and c['uuid'] in self.nodes[name]['children'] for v in vertices(c)];pivot=self.groups[name]['origin'];rotation=[];position=[]
            stance_z=[sum(rotate([v[j]-pivot[j] for j in range(3)],[angle,0,0])[2]+pivot[2] for v in foot)/len(foot) for angle in [amplitude,-amplitude]]
            strides.append((stance_z[1]-stance_z[0])/16/.6)
            for t in times:
                local=(t+phase)%1
                a,b=next((a,b) for a,b in zip(curve,curve[1:]) if a[0]<=local<=b[0]);f=(local-a[0])/(b[0]-a[0]);angle=a[1]*(1-f)+b[1]*f;lift=a[2]*(1-f)+b[2]*f
                floor=min(rotate([v[j]-pivot[j] for j in range(3)],[angle,0,0])[1]+pivot[1] for v in foot)
                rotation.append((t,[angle,0,0]));position.append((t,[0,-floor+lift+.04,0]))
            tracks[name]=[('rotation',rotation),('position',position)]
            # Resample the same linear pose path to avoid whole-tick display interpolation snaps.
            for _,values in tracks[name]:
                original=values[:]
                for t in (i/20 for i in range(1,20)):
                    if t in times:continue
                    a,b=next((a,b) for a,b in zip(original,original[1:]) if a[0]<=t<=b[0]);f=(t-a[0])/(b[0]-a[0])
                    values.append((t,[x*(1-f)+y*f for x,y in zip(a[1],b[1])]))
                values.sort()
        tracks['tail']=('rotation',[(0,[0,-5,0]),(.5,[0,5,0]),(1,[0,-5,0])])
        self.animate('walk',1,tracks)
        self.walk_stride_blocks=round(sum(strides)/len(strides),6)
        # Short voxel legs need a readable cadence at the mount's 4.4/6.8 blocks/s.
        self.animations[-1]['anim_time_update']='query.anim_time + query.delta_time * math.clamp(query.ground_speed / 4, 0.35, 1.8)'
        self.combat()
        if self.growth_details:
            for index,stage in enumerate(['baby','juvenile','adult']):
                self.animate('growth_'+stage,1,{name:('scale',[(0,[(*values,1)[index]]*3),(1,[(*values,1)[index]]*3)]) for name,values in self.growth_details.items()})
        return self
    def combat(self):
        # Finite overlays leave the body/saddle and the underlying movement clock untouched.
        def rotate_clip(values):return ('rotation',[(0,[0,0,0]),(.16,values[0]),(.34,values[1]),(.52,values[2]),(.75,[0,0,0])])
        if self.name in ['ark_trex','ark_velociraptor','ark_dilophosaurus']:
            tracks={'head':rotate_clip([[-10,0,0],[12,0,0],[-4,0,0]]),'jaw':rotate_clip([[-27,0,0],[-3,0,0],[-16,0,0]])}
            for side,sign in [('left',-1),('right',1)]:
                tracks['arm_'+side]=rotate_clip([[-32,0,sign*18],[22,0,-sign*10],[-10,0,sign*5]])
        elif self.name=='ark_triceratops':
            tracks={'head':[rotate_clip([[-9,0,0],[10,0,0],[0,0,0]]),('position',[(0,[0,0,0]),(.16,[0,0,1]),(.34,[0,0,-3]),(.52,[0,0,-1]),(.75,[0,0,0])])]}
        elif self.name=='ark_stegosaurus':
            tracks={'tail':rotate_clip([[0,-20,0],[0,65,0],[0,-15,0]])}
            # Keep the same strike curve while shortening child-display arc/chord errors.
            values=tracks['tail'][1];original=values[:]
            for time in (i/20 for i in range(1,15)):
                if any(time==t for t,_ in original):continue
                a,b=next((a,b) for a,b in zip(original,original[1:]) if a[0]<=time<=b[0]);f=(time-a[0])/(b[0]-a[0])
                values.append((time,[x*(1-f)+y*f for x,y in zip(a[1],b[1])]))
            values.sort()
        elif self.name=='ark_brachiosaurus':
            tracks={'leg_front_left':('position',[(0,[0,0,0]),(.18,[0,5,0]),(.32,[0,5,0]),(.42,[0,0,0]),(.75,[0,0,0])])}
        elif self.name=='ark_pteranodon':
            tracks={'head':[rotate_clip([[-12,0,0],[18,0,0],[-3,0,0]]),('position',[(0,[0,0,0]),(.16,[0,0,1]),(.34,[0,0,-3]),(.52,[0,0,-1]),(.75,[0,0,0])])]}
        else:raise ValueError(self.name)
        if self.name in ['ark_triceratops','ark_stegosaurus','ark_brachiosaurus']:
            tracks['jaw']=rotate_clip([[-18,0,0],[-4,0,0],[-10,0,0]])
            # BetterModel resets overridden phase iterators per bone. Include the
            # head even when still so jaw/head resume idle on the same clock.
            tracks.setdefault('head',rotate_clip([[0,0,0],[0,0,0],[0,0,0]]))
        self.animate('attack',.75,tracks,loop='once')
    def export(self):
        out=ROOT/'models'/self.name;out.mkdir(parents=True,exist_ok=True);tex=png_texture(self.colors)
        m=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name=self.name,model_identifier=self.name,resolution=dict(width=64,height=64),elements=self.elements+self.colliders,groups=list(self.groups.values()),outliner=[self.nodes['body']],textures=[dict(name=self.name+'.png',id='0',uuid=ident(self.name+'/texture'),width=64,height=64,uv_width=64,uv_height=64,source='data:image/png;base64,'+base64.b64encode(tex).decode())],animations=self.animations)
        (out/(self.name+'.bbmodel')).write_text(json.dumps(m,indent=2)+'\n');(out/(self.name+'.png')).write_bytes(tex)
        # Mirror X and rotation X/Y like Blockbench's official Bedrock codec.
        bones=[]
        for name,g in self.groups.items():
            parent=next((p for p,n in self.nodes.items() if self.nodes[name] in n['children']),None)
            b=dict(name=name,pivot=[-g['origin'][0],*g['origin'][1:]],cubes=[])
            if parent:b['parent']=parent
            for c in self.elements:
                if c['uuid'] not in self.nodes[name]['children']:continue
                a,t=c['from'],c['to'];rot=c['rotation'];uv={}
                for s in SIDES:
                    u,v,u2,v2=c['faces'][s]['uv']
                    uv[s]=dict(uv=[u2,v2],uv_size=[u-u2,v-v2]) if s in ['up','down'] else dict(uv=[u,v],uv_size=[u2-u,v2-v])
                q=dict(origin=[-t[0],a[1],a[2]],size=[t[i]-a[i] for i in range(3)],uv=uv)
                if any(rot):q.update(pivot=[-c['origin'][0],*c['origin'][1:]],rotation=[-rot[0],-rot[1],rot[2]])
                b['cubes'].append(q)
            bones.append(b)
        points=[v for c in self.elements for v in vertices(c)];height=max(v[1] for v in points);width=max(max(abs(v[i]) for v in points) for i in [0,2])*2
        geo={'format_version':'1.12.0','minecraft:geometry':[dict(description=dict(identifier='geometry.'+self.name,texture_width=64,texture_height=64,visible_bounds_width=round(width/16+1,2),visible_bounds_height=round(height/16+1,2),visible_bounds_offset=[0,round(height/32,2),0]),bones=bones)]}
        if self.name=='ark_pteranodon':
            # Flapping wingtips extend below the rest-pose box.
            geo['minecraft:geometry'][0]['description'].update(visible_bounds_height=4,visible_bounds_offset=[0,1.3,0])
        if self.name=='ark_mosasaurus':
            # The aquatic fluke and opening jaw extend below the native body's feet.
            geo['minecraft:geometry'][0]['description'].update(visible_bounds_height=12,visible_bounds_offset=[0,1,0])
        (out/(self.name+'.geo.json')).write_text(json.dumps(geo,indent=2)+'\n')
        animations={}
        for a in self.animations:
            tracks={}
            for animator in a['animators'].values():
                channels={}
                for k in animator['keyframes']:
                    channel=k['channel']
                    v=[float(k['data_points'][0][c]) for c in 'xyz']
                    if channel in ['position','rotation']:v[0]*=-1
                    if channel=='rotation':v[1]*=-1
                    channels.setdefault(channel,{})[str(k['time'])]=v
                tracks[animator['name']]=channels
            animations[a['name']]=dict(loop=a['loop']=='loop',animation_length=a['length'],bones=tracks)
            if a.get('anim_time_update'):animations[a['name']]['anim_time_update']=a['anim_time_update']
        (out/(self.name+'.animation.json')).write_text(json.dumps(dict(format_version='1.8.0',animations=animations),indent=2)+'\n')
        (out/'config.json').write_text(json.dumps(dict(head_rotation=True,material='entity_alphatest_change_color_one_sided',blend_transition=True,binding_bones={self.name:['*']},anim_textures={},texture_materials={},disable_part_visibility=True),indent=2)+'\n')
        design=dict(name=self.name,label=self.label,palette=self.colors,description=self.description,ground_parts=self.feet,leg_bones=self.legs,walk_stride_blocks=self.walk_stride_blocks,cubes=len(self.elements),bones=len(self.groups))
        if self.name=='ark_mosasaurus':design.update(aquatic=True,swim_stride_blocks=self.walk_stride_blocks,native_carrier='DOLPHIN',native_collision_scale=4,rideable=False,mouth_anchor_blocks=[0,1.0625,-9.4375])
        (out/'design.json').write_text(json.dumps(design,indent=2)+'\n')
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
        m.mass('leg_'+side,[sx-width*.58,hip_y*.45,z-width*.64],[sx+width*.58,hip_y+1,z+width*.64],name,0)
        m.beam('ankle_'+side,[sx,hip_y*.55,z],[sx,2,z-.5],width*.7,width*.8,name,1)
        m.mass('sole_'+side,[sx-width*.5,0,z-width*.62],[sx+width*.5,3,z+width*.48],name,3)
        m.feet.append('sole_'+side)
        for dx in [-.28,0,.28]:m.cube('nail_'+side+str(dx),[sx+(dx-.09)*width,0,z-width*.6-.25],[sx+(dx+.09)*width,1.2,z-width*.6+.2],name,6,ground=True)

def trex():
    m=Model('ark_trex','T-Rex',['#68705a','#83896a','#b3ab87','#444c3e','#dbb358','#19241c','#eee1bc','#5a614b'],'Massiger Schädel · schuppige Olivhaut · muskulöse Hüfte · gebogene Elfenbeinkrallen')
    m.bone('body',[0,23,3]);m.mass('torso',[-7,16,-8],[7,30,12]);m.mass('chest',[-5.8,18,-15],[5.8,28,-4]);m.mass('hips',[-7.5,17,5],[7.5,29,16]);m.mass('belly',[-5.2,15.5,-7],[5.2,20,10],color=2)
    m.bone('neck',[0,27,-8],'body');m.beam('neck_lower',[0,25,-9],[0,30,-15],8,7,'neck');m.mass('neck',[-4.6,27,-19],[4.6,35,-11],'neck',1)
    m.bone('head',[0,33,-14],'neck');m.mass('skull',[-7,31,-28],[7,40,-12],'head',1);m.mass('snout',[-5.5,31,-35],[5.5,36.5,-23],'head')
    for side in [-1,1]:
        x=side*6.5;m.cube('brow_'+str(x),[x-.8,36.8,-25],[x+.8,39,-17],'head',3,(0,0,side*12))
        m.mass('cheek_'+str(side),[side*5.8-1,29.8,-21],[side*5.8+1,35.8,-13],'head')
        m.cube('nostril_'+str(side),[side*5.5-.2,33.5,-33],[side*5.5+.2,34.3,-31],'head',3)
    m.eyes(7.1,34,-24,size=2)
    m.bone('jaw',[0,31,-14],'head');m.mass('jaw',[-5.6,27.5,-34],[5.6,30.5,-13],'jaw');m.cube('mouth',[-5.7,30.4,-33],[5.7,31.05,-14],'head',3)
    m.mouth_rows(5,30.9,30.45,[-33+i*2.4 for i in range(8)],[-31.8+i*2.1 for i in range(7)],1.,2.1)
    m.cube('oral_cavity',[-4.8,30.65,-33],[4.8,31.1,-13],'head',5)
    m.bone('tail',[0,24,11],'body');m.tail_chain([[0,23,11],[0,23,22],[0,23,32],[0,23.5,41],[0,24,49],[0,24.5,56]],[9,7,5,3.2,1.5])
    for side,x in [('left',-7),('right',7)]:
        digitigrade(m,side,x,22,6)
        for i,spread in enumerate([-1,0,1]):
            end=next(c['origin'] for c in m.elements if c['name']==f'claw_{side}_{i}')
            root=[end[0],end[1]+.5,end[2]+.4];hook=[end[0]+spread*.2,end[1]+.8,end[2]-1.7];tip=[end[0]+spread*.4,end[1]+.15,end[2]-3.1]
            m.beam(f'toe_claw_{side}_{i}_root',root,hook,1.15,1.,'leg_'+side,6,True)
            m.beam(f'toe_claw_{side}_{i}_tip',hook,tip,.7,.6,'leg_'+side,6,True)
        m.mass('hip_muscle_'+side,[x-2.8,15,2],[x+2.8,25,10],'leg_'+side)
        m.bone('arm_'+side,[x*.85,26,-9],'body');m.beam('tiny_arm_'+side,[x*.85,26,-10],[x*.85,21,-14],1.8,bone='arm_'+side,color=1);m.beam('hand_'+side,[x*.85,21,-14],[x*.85,21,-17],1.1,bone='arm_'+side,color=3)
        for i,dx in enumerate([-.65,.65]):
            finger=[x*.85+dx,20.5,-18.8];hook=[finger[0],20.6,-20.3];tip=[finger[0],19.8,-21.4]
            m.beam(f'hand_finger_{side}_{i}',[x*.85+dx,21,-16.7],finger,.65,bone='arm_'+side,color=3)
            m.beam(f'hand_claw_{side}_{i}',finger,hook,.75,.7,'arm_'+side,6)
            m.beam(f'hand_claw_{side}_{i}_tip',hook,tip,.45,.4,'arm_'+side,6)
    return m.finish()

def triceratops():
    m=Model('ark_triceratops','Triceratops',['#87725a','#a28b6b','#b7a488','#554d3f','#d5ae67','#25271f','#ded1ad','#76634e'],'Fächerförmiger Nackenschild · drei Hörner · schwere Schuppenhaut')
    m.bone('body',[0,22,2]);m.mass('barrel',[-11,11,-10],[11,29,14]);m.mass('shoulder_mass',[-9,13,-15],[9,28,-4]);m.mass('rump',[-9.5,13,9],[9.5,28,19]);m.mass('back',[-8,27,-10],[8,31,14],color=1);m.mass('belly',[-8,10,-10],[8,15,13],color=2)
    quadruped(m,8,-9,12,16,5)
    m.bone('head',[0,21,-13],'body');m.mass('head',[-7,13,-29],[7,25,-15],'head');m.mass('snout',[-4.8,12,-37],[4.8,20,-26],'head',1);m.mass('beak',[-3.4,12,-40],[3.4,17,-35],'head',3)
    m.cube('frill_lower',[-9,19,-18],[9,28,-14],'head',7);m.cube('frill_top',[-4,27,-17],[4,36,-14],'head',1)
    for side in [-1,1]:
        for i,angle in enumerate([23,48,70]):
            m.beam(f'frill_fan_{side}_{i}',[side*3,23,-15.5],[side*(6+3*i),35-3*i,-15.5],5,3,'head',7)
        for i,(x,y) in enumerate([(5,36),(9,33.8),(12,30.8),(13.5,27)]):
            m.tapered(f'frill_knob_{side}_{i}',[side*x,y-1,-15.5],[side*(x+.7),y+1.2,-15.5],1.6,'head',6,2)
    for side in [-1,1]:
        m.tapered('brow_horn_'+str(side),[side*5,24,-26],[side*7.4,33,-40],3.1,'head',6,4)
        m.growth_detail('growth_brow_horn_'+('left' if side<0 else 'right'),[side*5,24,-26],'head','brow_horn_'+str(side))
    m.tapered('nose_horn',[0,20,-35],[0,31,-43],3.1,'head',6,4);m.growth_detail('growth_nose_horn',[0,20,-35],'head','nose_horn');m.eyes(7.05,20,-26,size=1.6)
    m.bone('jaw',[0,13,-16],'head');m.mass('lower_jaw',[-6,10.5,-26],[6,12.6,-16],'jaw',1)
    m.cube('jaw_hinge',[-4,12,-18],[4,13.6,-15.5],'jaw',1)
    m.cube('oral_cavity',[-6.5,12.7,-26],[6.5,13.05,-16],'head',5)
    m.mouth_rows(4.5,12.95,12.55,[-24.5,-22.5,-20.5],[-24.2,-22.2,-20.2],.36,.42,'grinder')
    m.bone('tail',[0,20,16],'body');m.tail_chain([[0,20,16],[0,18,25],[0,16,32],[0,15,39],[0,14.5,43]],[5.5,4,2.6,1.2])
    return m.finish()

def stegosaurus():
    m=Model('ark_stegosaurus','Stegosaurus',['#69735b','#89927a','#b4ad8f','#444e3c','#dfbb64','#242e21','#dacfad','#986b4d'],'Gewölbter Rücken · versetzte Rückenplatten · vier Schwanzstacheln')
    m.bone('body',[0,22,0]);m.mass('barrel',[-10,12,-11],[10,27,14]);m.mass('chest',[-8,12,-17],[8,25,-6]);m.mass('hips',[-9,13,8],[9,29,19]);m.mass('back',[-7,24,-11],[7,30,14],color=1);m.mass('belly',[-7,11,-11],[7,16,12],color=2)
    quadruped(m,7.5,-10,12,16,4.4)
    m.bone('head',[0,18,-14],'body');m.beam('neck',[0,20,-13],[0,14,-27],5,bone='head',color=1);m.cube('head',[-3.5,10,-36],[3.5,17,-26],'head',1);m.cube('beak',[-2.8,10,-39],[2.8,13,-34],'head',3);m.eyes(3.5,14,-31,size=1)
    m.bone('jaw',[0,10,-26],'head');m.mass('lower_jaw',[-3.1,7.5,-35],[3.1,9.5,-26],'jaw',1)
    m.cube('jaw_hinge',[-2.2,9,-26.8],[2.2,10.6,-25.5],'jaw',1)
    m.cube('oral_cavity',[-3.2,9.65,-34],[3.2,10.05,-26],'head',5)
    m.mouth_rows(1.8,9.85,9.45,[-30,-28.7,-27.4],[-29.8,-28.5,-27.2],.36,.42,'grinder')
    for side in [-1,1]:
        for i,z in enumerate([-10,-1,8,16]):
            z+=2.5 if side>0 else 0;high=[8,13,16,11][i];x=side*7.5;base=27 if i<2 else 28
            root=24.5 if i<2 or (side<0 and i==2) else 26.25
            m.cube(f'plate_{side}_{i}_base',[x-.65,root,z-1.5],[x+.65,base+high*.35,z+1.5],color=7)
            m.cube(f'plate_{side}_{i}_middle',[x-.7,base+high*.22,z-3.4],[x+.7,base+high*.76,z+3.4],color=7,rotation=(18 if i<2 else -12,0,0),pivot=[x,base+high*.5,z])
            m.beam(f'plate_{side}_{i}_top',[x,base+high*.65,z],[x,base+high,z-.8],1.1,3.3,color=7)
            m.growth_detail(f'growth_plate_{"left" if side<0 else "right"}_{i}',[x,root,z],'body',f'plate_{side}_{i}_',(.3,.65))
    m.bone('tail',[0,20,16],'body');m.tail_chain([[0,20,16],[0,17,27],[0,13,37],[0,10,46],[0,9,56]],[6,4.8,3.2,1.6])
    for prefix,bone,sections in [('neck_plate','head',[(-32,16,2.5),(-26,17,3.5),(-21,20,4.5),(-16,23,6)]),
                                 ('tail_plate','tail',[(23,21,8),(29,18,6),(35,15,4),(40,13,2.5)])]:
        for i,(z,base,high) in enumerate(sections):
            x=(-1 if i%2==0 else 1)*(1.3+high*.12)
            root=19.25 if prefix=='neck_plate' and i==2 else 20.5 if prefix=='tail_plate' and i==0 else base
            m.cube(f'{prefix}_{i}_base',[x-.45,root,z-1.2],[x+.45,base+high*.55,z+1.2],bone,7)
            m.beam(f'{prefix}_{i}_top',[x,base+high*.4,z],[x,base+high,z-.4],.75,2.8,bone,7)
            m.growth_detail(f'growth_{prefix}_{i}',[x,root,z],bone,f'{prefix}_{i}_',(.3,.65))
    for side in [-1,1]:
        for z in [42,49]:
            m.tapered(f'tail_spike_{side}_{z}',[side,11,z],[side*9,17,z+5],1.9,'tail',6,3)
            root=10.25 if z==49 else 11
            if z==49:next(c for c in m.elements if c['name']==f'tail_spike_{side}_{z}0')['from'][1]=root
            m.growth_detail(f'growth_tail_spike_{"left" if side<0 else "right"}_{z}',[side,root,z],'tail',f'tail_spike_{side}_{z}')
    return m.finish()

def brachiosaurus():
    m=Model('ark_brachiosaurus','Brachiosaurus',['#7b8071','#969b86','#bcb69b','#535c50','#d8bc77','#273128','#dfd5b3','#666e5d'],'Hoher Schulterbogen · verjüngter Langhals · gefaltete graugrüne Haut')
    m.bone('body',[0,31,2]);m.mass('barrel',[-11,21,-10],[11,40,16]);m.mass('rump',[-9.5,23,10],[9.5,38,23]);m.mass('shoulders',[-10,29,-15],[10,43,-3],color=1);m.mass('shoulder_hump',[-7,38,-13],[7,44,-5],color=1);m.mass('belly',[-8,20,-11],[8,27,16],color=2)
    quadruped(m,8,-9,14,26,5)
    m.bone('neck',[0,40,-10],'body')
    neck_points=[[0,39,-9],[0,48,-13],[0,58,-17],[0,68,-21],[0,77,-25],[0,85,-29]]
    for i,(a,b,w) in enumerate(zip(neck_points,neck_points[1:],[9.4,8.2,7.1,6.1,5.2])):
        m.beam('neck_lower' if i==0 else 'neck_upper' if i==4 else 'neck_segment_'+str(i),a,b,w,w*.86,'neck',1 if i>2 else 0)
        m.beam('throat_'+str(i),[a[0],a[1],a[2]-w*.36],[b[0],b[1],b[2]-w*.36],w*.6,w*.22,'neck',2)
    m.bone('head',[0,84,-29],'neck');m.mass('skull',[-4.6,82,-39],[4.6,90,-27],'head',1);m.mass('muzzle',[-3.8,81,-44],[3.8,86,-35],'head');m.mass('nasal_rise',[-2.7,88,-35],[2.7,93,-29],'head',1)
    m.bone('jaw',[0,81,-29],'head');m.mass('lower_jaw',[-3.5,78.8,-42],[3.5,80.5,-29],'jaw',2);m.eyes(4.6,86,-35,size=1.4)
    m.cube('jaw_hinge',[-2.5,80,-31],[2.5,82.6,-28],'jaw',2)
    m.cube('oral_cavity',[-3.5,80.6,-43.8],[3.5,81.05,-30],'head',5)
    for row,y,z,direction,bone in [('upper',80.9,-43.15,-1,'head'),('lower',80.45,-41.7,1,'jaw')]:
        m.cube('gums_'+row,[-3.4,y-.15,z-.25],[3.4,y+.15,z+.25],bone,3)
        for i,x in enumerate([-2.4,-.8,.8,2.4]):m.tooth(f'tooth_{row}_front_{i}',[x,y,z],[x,y+direction*.85,z-.1],bone,.28,'spatulate')
    for x in [-2,2]:m.cube('nostril_'+str(x),[x-.7,85,-44.2],[x+.7,86,-43.8],'head',3)
    m.bone('tail',[0,30,19],'body');m.tail_chain([[0,30,19],[0,23,30],[0,15,40],[0,8,50],[0,3.2,59],[0,.65,67]],[7.5,6,4.4,2.8,1.4])
    return m.finish()

def velociraptor():
    m=Model('ark_velociraptor','Velociraptor',['#8b8067','#aa9b7a','#c5b99c','#514b3d','#dabb60','#22291e','#d8cdae','#69614e'],'Waagerechter Körper · S-Hals · schmaler Fang · Sichelkrallen')
    m.bone('body',[0,18,1]);m.mass('torso',[-4.2,14,-7],[4.2,23,8]);m.mass('chest',[-3.5,15,-11],[3.5,21,-3]);m.mass('hips',[-4.5,15,3],[4.5,23,11]);m.mass('belly',[-3.4,13,-7],[3.4,17,6],color=2);m.cube('back_stripe',[-1.8,22,-7],[1.8,24,8],color=7)
    m.bone('neck',[0,21,-7],'body');m.beam('neck_lower',[0,20,-7],[0,25,-11],4.5,bone='neck');m.beam('neck',[0,25,-11],[0,29,-17],3.8,bone='neck',color=1)
    m.bone('head',[0,29,-14],'neck');m.mass('skull',[-3.9,27,-23],[3.9,33,-13],'head',1);m.mass('snout',[-2.5,27,-32],[2.5,30.3,-21],'head')
    m.bone('jaw',[0,27,-15],'head');m.mass('jaw',[-2.5,25.5,-31],[2.5,27,-15],'jaw',2);m.cube('mouth',[-2.65,26.9,-30],[2.65,27.3,-19],'head',3);m.eyes(3.95,29,-22,size=1.2)
    for side in [-1,1]:m.beam('brow_'+str(side),[side*3.6,31.4,-22],[side*3.6,32.1,-16],.85,bone='head',color=3)
    m.mouth_rows(2.2,26.95,26.9,[-30+i*1.9 for i in range(7)],[-29.2+i*1.8 for i in range(6)],.6,1.5)
    m.cube('oral_cavity',[-2.2,26.8,-30],[2.2,27.05,-15],'head',5)
    m.bone('tail',[0,19,7],'body');m.tail_chain([[0,19,7],[0,19.6,18],[0,20.1,29],[0,20.7,40],[0,21.3,49],[0,22,57]],[4.4,3.8,2.9,2,1])
    for side,x in [('left',-4.7),('right',4.7)]:
        digitigrade(m,side,x,18,4,.73,True)
        m.mass('thigh_muscle_'+side,[x-2.1,11.5,1],[x+2.1,20,7],'leg_'+side)
        # Start the shoulder at its rotation joint so the rake cannot pull it off the torso.
        m.bone('arm_'+side,[x,22,-6],'body');m.beam('upper_arm_'+side,[x,22,-6],[x*1.5,16,-9],1.7,bone='arm_'+side,color=1);m.beam('forearm_'+side,[x*1.5,16,-9],[x*1.8,17,-18],1.4,bone='arm_'+side)
        sign=-1 if x<0 else 1
        for i,z in enumerate([-11,-13.5,-16]):m.beam(f'feather_{side}_{i}',[x*1.55,16.5,z],[x*1.55+sign*(4-i*.4),15,z+4],1.1,.65,'arm_'+side,7)
        for dx in [-.5,.5]:m.tapered('hand_claw_'+side+str(dx),[x*1.8+dx,17,-18],[x*1.8+dx,16,-20],.55,'arm_'+side,6,2)
    return m.finish()

def pteranodon():
    m=Model('ark_pteranodon','Pteranodon',['#726e60','#938875','#b6ab91','#494a3e','#dab85c','#242a21','#c7b58c','#8b6d56'],'Verbundene ledrige Flughaut · langer Schnabel · schlanker Hinterkopfkamm')
    m.bone('body',[0,21,1]);m.mass('torso',[-4.3,16,-6],[4.3,26,7]);m.mass('breast',[-3.6,15,-5],[3.6,20,5],color=2)
    # The skull's three-unit beak jab still needs an overlapping neck connection.
    m.bone('neck',[0,23,-5],'body');m.beam('neck',[0,23,-5],[0,29,-15],4.2,bone='neck',color=1)
    m.bone('head',[0,29,-14],'neck');m.mass('skull',[-3.1,27,-23],[3.1,33,-12],'head',1)
    m.tapered('long_beak',[0,28.8,-23],[0,28,-49],4,'head',6,6);m.tapered('lower_beak',[0,27.1,-23],[0,26.9,-46],2.2,'head',2,5)
    m.tapered('head_crest',[0,32,-20],[0,42,-8],3.7,'head',7,6);m.growth_detail('growth_head_crest',[0,32,-20],'head','head_crest');m.eyes(3.15,29,-20,size=1.3)
    m.bone('tail',[0,20,7],'body');m.tapered('short_tail',[0,20,7],[0,19,13],2.8,'tail',0,2)
    for side,sign in [('left',-1),('right',1)]:
        wing='wing_'+side;m.bone(wing,[sign*4,23,0],'body')
        outer='wing_tip_'+side;m.bone(outer,[sign*16,23-12*.045,-1],wing)
        # The long outer leading edge is the extended fourth finger, not a bird feather fan.
        for i,(x1,x2,z1,z2) in enumerate([(4,16,0,-1),(16,31,-1,-8),(31,47,-8,-3),(47,65,-3,5)]):
            m.beam(f'wing_finger_{side}_{i}',[sign*x1,23-(x1-4)*.045,z1],[sign*x2,23-(x2-4)*.045,z2],1.8 if i<2 else 1.1,bone=wing if i==0 else outer,color=6)
        m.tapered('thumb_'+side,[sign*29,22,-7],[sign*27,23,-12],1.3,outer,6,2)
        profile=[(4,0,9),(16,-1,15),(31,-8,14),(47,-3,11),(65,5,5.4)]
        for i in range(30):
            x1=4+i*2.03;x2=min(65,x1+2.08);mid=(x1+x2)/2
            a,b=next((a,b) for a,b in zip(profile,profile[1:]) if a[0]<=mid<=b[0]);f=(mid-a[0])/(b[0]-a[0]);front=a[1]*(1-f)+b[1]*f;back=a[2]*(1-f)+b[2]*f;y=22.7-(x1-4)*.045
            lo,hi=sorted([sign*x1,sign*x2]);m.cube(f'membrane_{side}_{i}',[lo,y-.22,front],[hi,y+.1,max(front+.4,back)],wing if mid<16 else outer,7)
        m.beam('membrane_trailing_root_'+side,[sign*4,18,9],[sign*17,22,14],.7,bone=wing,color=1)
        # Stationary hip flesh covers the walking leg's vertical floor compensation.
        m.mass('hip_joint_'+side,[sign*3.8-1.1,14.5,5.5],[sign*3.8+1.1,18.5,8.5])
        digitigrade(m,side,sign*3.8,16,7,.42)
    m.finish()
    # Twist the outer membranes vertically beside the body, then fold the long fingers back.
    for animation in m.animations:
        if animation['name'] not in ['idle','walk']:continue
        for side,sign in [('left',-1),('right',1)]:
            for bone,rotation in [('wing_'+side,[0,sign*65,0]),('wing_tip_'+side,[90,-sign*155,0])]:
                animation['animators'][m.groups[bone]['uuid']]=dict(name=bone,type='bone',keyframes=[dict(channel='rotation',time=t,uuid=ident(f'{m.name}/{animation["name"]}/{bone}/rotation/{t}'),interpolation='linear',data_points=[dict(zip('xyz',map(str,rotation)))]) for t in [0,animation['length']]])
    opened={bone:('rotation',[(0,[0,0,0]),(1.2,[0,0,0])]) for bone in ['wing_tip_left','wing_tip_right']}
    opened.update({bone:('rotation',[(0,[-100,0,0]),(1.2,[-100,0,0])]) for bone in ['leg_left','leg_right']})
    m.animate('flap',1.2,dict(opened,wing_left=('rotation',[(0,[0,0,-28]),(.3,[0,0,18]),(.6,[0,0,28]),(.9,[0,0,-15]),(1.2,[0,0,-28])]),wing_right=('rotation',[(0,[0,0,28]),(.3,[0,0,-18]),(.6,[0,0,-28]),(.9,[0,0,15]),(1.2,[0,0,28])])))
    opened={bone:('rotation',[(0,[0,0,0]),(2,[0,0,0])]) for bone in ['wing_tip_left','wing_tip_right']}
    opened.update({bone:('rotation',[(0,[-100,0,0]),(2,[-100,0,0])]) for bone in ['leg_left','leg_right']})
    m.animate('glide',2,dict(opened,wing_left=('rotation',[(0,[0,0,-3]),(1,[0,0,-6]),(2,[0,0,-3])]),wing_right=('rotation',[(0,[0,0,3]),(1,[0,0,6]),(2,[0,0,3])])))
    return m

def dilophosaurus():
    m=Model('ark_dilophosaurus','Dilophosaurus',['#526b43','#89904e','#c4b16d','#293c29','#e6bc46','#17251d','#e5d6a4','#a8472d'],
            'Zwei Kopfkämme · faltbarer rotgelber Halsfächer · olivfarbene Streifenhaut · drei Fingerkrallen')
    m.bone('body',[0,15,3]);m.mass('torso',[-4.8,10,-5],[4.8,19,8]);m.mass('hips',[-5,11,4],[5,19,12]);m.mass('belly',[-3.4,9.5,-4],[3.4,13,8],color=2)
    m.cube('back_stripe',[-2.8,18.8,-4],[2.8,19.2,10],color=1)
    for side,sign in [('left',-1),('right',1)]:
        for i,z in enumerate([-3,1,5,9]):
            m.beam(f'flank_stripe_{side}_{i}',[sign*4.8,17.8,z],[sign*4.9,12.5,z+1.7],.55,1.5,color=7)
    m.bone('neck',[0,18,-5],'body');m.beam('neck_lower',[0,17,-5],[0,23,-11],4.8,4.2,'neck');m.mass('neck_upper',[-2.6,21,-15],[2.6,26,-9],'neck',1)
    m.bone('head',[0,25,-13],'neck');m.mass('skull',[-3.2,23,-22],[3.2,28.4,-11],'head',1);m.mass('snout',[-2.7,23,-31],[2.7,26.8,-19],'head')
    m.cube('mouth',[-2.75,22.8,-30],[2.75,23.2,-13],'head',3)
    m.bone('jaw',[0,23,-13],'head');m.mass('lower_jaw',[-2.5,21,-30],[2.5,23,-12],'jaw',2)
    m.eyes(3.25,25,-19,size=1.2)
    for side,sign in [('left',-1),('right',1)]:
        x=sign*2.2
        for i,(a,b) in enumerate([([x,27,-26],[x,31,-23]),([x,31,-23],[x,34,-18]),([x,34,-18],[x,29,-12])]):
            m.beam(f'crest_{side}_{i}',a,b,.85,3.2,'head',7)
            m.beam(f'crest_rim_{side}_{i}',[a[0],a[1]+.5,a[2]],[b[0],b[1]+.5,b[2]],.9,.8,'head',2)
        m.growth_detail('growth_crest_'+side,[x,26,-26],'head',('crest_'+side+'_','crest_rim_'+side+'_'))
        m.cube('nostril_'+side,[sign*2.7-.15,24.5,-29],[sign*2.7+.15,25.1,-27.7],'head',5)
        digitigrade(m,side,sign*3.9,15.5,6,.61)
        m.mass('thigh_muscle_'+side,[sign*3.9-1.5,10,3],[sign*3.9+1.5,17,9],'leg_'+side)
        arm='arm_'+side;m.bone(arm,[sign*4.2,17.5,-3],'body')
        m.beam('upper_arm_'+side,[sign*4.2,17.5,-3],[sign*6,13,-5],1.3,bone=arm,color=1)
        m.beam('forearm_'+side,[sign*6,13,-5],[sign*7,14,-12],1.1,bone=arm)
        for i,dx in enumerate([-.8,0,.8]):
            m.beam(f'finger_{side}_{i}',[sign*7+dx,14,-11],[sign*7+dx,13.7,-14],.55,bone=arm,color=3)
            m.tapered(f'hand_claw_{side}_{i}',[sign*7+dx,13.7,-14],[sign*7+dx,12.8,-16],.6,arm,6,3)
        # Each rib starts folded into the same narrow collar; unfolding spreads the fan.
        pivot=[sign*3.15,24,-17];root='frill_'+side;m.bone(root,pivot,'neck')
        growth='growth_frill_'+side;m.growth_detail(growth,pivot,root,(),(.3,.65))
        for i,angle in enumerate(range(-75,76,15)):
            rib=f'frill_{side}_rib_{i}';m.bone(rib,pivot,growth)
            theta=math.radians(angle*.12);end=[pivot[0],pivot[1]+math.sin(theta)*11,pivot[2]+math.cos(theta)*11]
            m.beam(f'frill_membrane_{side}_{i}',pivot,end,.38,3.2,rib,7 if i%2 else 1)
            m.beam(f'frill_vein_{side}_{i}',[pivot[0]+sign*.25,pivot[1],pivot[2]],[end[0]+sign*.25,end[1],end[2]],.28,.28,rib,6)
            edge=[pivot[j]+(end[j]-pivot[j])*.83 for j in range(3)]
            m.beam(f'frill_rim_{side}_{i}',edge,end,.48,3.25,rib,2)
    m.bone('tail',[0,16,10],'body');m.tail_chain([[0,16,10],[0,15,19],[0,15,29],[0,15.5,38],[0,16,45]],[4.8,3.4,2.2,1.2])
    m.mouth_rows(2.35,22.95,22.9,[-29.5+i*2.3 for i in range(7)],[-28.35+i*2.3 for i in range(6)],.6,1.5)
    m.cube('oral_cavity',[-2.2,22.8,-30],[2.2,23.05,-13],'head',5)
    m.finish()
    tracks={'head':('rotation',[(0,[0,0,0]),(.25,[-8,0,0]),(.45,[4,0,0]),(.65,[0,0,0]),(1,[0,0,0])]),
            'jaw':('rotation',[(0,[0,0,0]),(.25,[-24,0,0]),(.45,[-28,0,0]),(.65,[-10,0,0]),(1,[0,0,0])])}
    for side,sign in [('left',-1),('right',1)]:
        tracks['frill_'+side]=('rotation',[(0,[0,0,0]),(.25,[0,sign*80,0]),(.65,[0,sign*80,0]),(1,[0,0,0])])
        for i,angle in enumerate(range(-75,76,15)):
            tracks[f'frill_{side}_rib_{i}']=('rotation',[(0,[0,0,0]),(.25,[-angle*.88,0,0]),(.65,[-angle*.88,0,0]),(1,[0,0,0])])
    m.animate('spit',1,tracks,loop='once')
    return m

def mosasaurus():
    m=Model('ark_mosasaurus','Mosasaurus',['#344e60','#536d7a','#c5c9b6','#263a49','#b8a557','#111e27','#eee5c8','#657b83'],
            '24 Meter langer Meeresräuber · vier kräftige Ruderflossen · senkrechte Schwanzflosse · dunkler Fleckenrücken und heller Bauch')
    m.bone('body',[0,24,0]);m.mass('trunk',[-32,0,-36],[32,48,62]);m.mass('shoulders',[-28,2,-65],[28,45,-19]);m.mass('hips',[-28,3,40],[28,43,83])
    m.mass('pale_belly',[-23,0,-47],[23,14,63],color=2);m.mass('throat',[-18,5,-84],[18,19,-44],color=2)
    m.mass('back',[-23,39,-44],[23,49,61],color=1)
    for side,sign in [('left',-1),('right',1)]:
        for i,(z,width) in enumerate([(-47,25),(-27,29),(-6,31),(16,31),(38,30),(58,27),(73,23)]):
            x=sign*width
            m.cube(f'lateral_line_{side}_{i}',[x-1,24,z-8],[x+1,26,z+8],color=7)
            m.cube(f'flank_mottle_{side}_{i}',[x-1.2,30+(i%2)*3,z-5],[x+1.2,36+(i%2)*3,z+3],color=3)
        for i,z in enumerate([-38,-16,6,28,50]):
            m.cube(f'back_mottle_{side}_{i}',[sign*12-4,47,z-5],[sign*12+4,49.2,z+4],color=3 if i%2 else 7)
    m.bone('neck',[0,27,-57],'body');m.mass('neck',[-21,10,-95],[21,42,-57],'neck');m.mass('neck_belly',[-15,8,-92],[15,20,-59],'neck',2)
    m.bone('head',[0,29,-86],'neck');m.mass('skull',[-23,16,-113],[23,39,-78],'head',1)
    m.mass('snout',[-14,18,-143],[14,32,-105],'head');m.mass('snout_tip',[-11,18,-152],[11,29,-134],'head')
    m.cube('snout_ridge',[-8,31,-137],[8,34,-108],'head',1)
    for side,sign in [('left',-1),('right',1)]:
        m.mass('cheek_'+side,[sign*19-3,17,-106],[sign*19+3,32,-83],'head')
        m.cube('brow_'+side,[sign*22-1.5,32,-106],[sign*22+1.5,37,-93],'head',3)
        m.cube('nostril_'+side,[sign*10.6-.4,27.5,-146],[sign*10.6+.4,29.2,-142],'head',5)
        for i,z in enumerate([-119,-129,-139]):
            m.cube(f'snout_pore_{side}_{i}',[sign*13.8-.25,23,z-.7],[sign*13.8+.25,24,z+.7],'head',3)
    m.eyes(23.1,29,-102,size=3)
    m.bone('jaw',[0,16,-83],'head');m.mass('lower_jaw',[-16,8,-114],[16,16,-80],'jaw',1)
    m.mass('jaw_snout',[-10.5,9,-151],[10.5,16,-108],'jaw',2)
    m.cube('oral_cavity',[-13,16.4,-148],[13,17.7,-85],'head',5)
    m.cube('tongue',[-7,16.05,-143],[7,16.35,-91],'jaw',3)
    for side,sign in [('left',-1),('right',1)]:
        for row,y,first,count,direction,bone in [('upper',17.8,-148,14,-1,'head'),('lower',16.,-146.3,13,1,'jaw')]:
            for i in range(count):
                z=first+i*3.7;x=sign*(9.1+(z+148)*.095)
                m.cube(f'gums_{row}_{side}_{i}',[x-1,y-.2,z-1.2],[x+1,y+.2,z+1.2],bone,3)
                m.tooth(f'tooth_{row}_{side}_{i}',[x,y,z],[x,y+direction*(4.5 if i<10 else 3.8),z-.35],bone,1.55 if i<10 else 1.35)
    for side,sign in [('left',-1),('right',1)]:
        for location,z,length in [('front',-38,57),('back',58,43)]:
            bone=f'flipper_{location}_{side}';pivot=[sign*(25 if location=='front' else 24),16,z];m.bone(bone,pivot,'body')
            points=[pivot,[sign*(abs(pivot[0])+length*.38),10,z+7],[sign*(abs(pivot[0])+length*.75),7,z+18],[sign*(abs(pivot[0])+length),6,z+30]]
            for i,(a,b,w) in enumerate(zip(points,points[1:],[15,12,7])):
                m.beam(f'flipper_{location}_{side}{i}',a,b,w,4.2 if i==0 else 3,bone,0 if i==0 else 1)
                underside_a=[a[0],a[1]-1.5,a[2]+2];underside_b=[b[0],b[1]-1.5,b[2]+2]
                m.beam(f'flipper_belly_{location}_{side}_{i}',underside_a,underside_b,w*.72,1.5,bone,2)
            m.beam(f'flipper_rim_{location}_{side}',[points[1][0],points[1][1]+1,points[1][2]-5],[points[-1][0],points[-1][1]+1,points[-1][2]],1.5,1.2,bone,3)
    m.bone('tail',[0,24,70],'body');m.bone('tail_mid',[0,22,126],'tail');m.bone('tail_tip',[0,19,176],'tail_mid')
    points=[[0,24,68],[0,24,101],[0,22,133],[0,20,163],[0,18,192],[0,18,218]]
    for i,(a,b,w) in enumerate(zip(points,points[1:],[39,31,23,15,8])):
        bone='tail' if i<2 else 'tail_mid' if i<4 else 'tail_tip'
        m.beam('tail_segment_'+str(i),a,b,w,w*.7,bone,0 if i%2==0 else 1)
        m.beam('tail_belly_'+str(i),[a[0],a[1]-w*.3,a[2]],[b[0],b[1]-w*.3,b[2]],w*.65,2,bone,2)
        if i in (1,3):
            # Local mesh origins keep BetterModel's rotated cuboids within Java's [-16,32] bounds.
            for cube in m.elements[-2:]:
                mesh=cube['name']+'_mesh';m.bone(mesh,cube['origin'],bone)
                m.nodes[bone]['children'].remove(cube['uuid']);m.nodes[mesh]['children'].append(cube['uuid'])
            child='tail_mid' if i==1 else 'tail_tip'
            m.nodes[bone]['children'].remove(m.nodes[child]);m.nodes['tail_segment_'+str(i)+'_mesh']['children'].append(m.nodes[child])
    for name,a,b,color in [
            ('upper0',[-2.5,18,194],[2.5,36,219],0),('upper1',[-2,35,203],[2,49,226],1),('upper2',[-1.5,48,217],[1.5,56,232],7),
            ('lower0',[-2.5,0,194],[2.5,19,220],0),('lower1',[-2,-14,204],[2,1,228],1),('lower2',[-1.5,-24,219],[1.5,-13,232],7)]:
        m.cube('tail_fin_'+name,a,b,'tail_tip',color)
    m.beam('tail_fin_upper_rib',[0,18,196],[0,55,229],1.5,1.2,'tail_tip',3)
    m.beam('tail_fin_lower_rib',[0,18,196],[0,-23,229],1.5,1.2,'tail_tip',3)
    def wave(length,amplitude,phase=0,axis=1):
        return ('rotation',[(length*i/16,[amplitude*math.sin(2*math.pi*(i%16)/16+phase) if j==axis else 0 for j in range(3)]) for i in range(17)])
    idle={'tail':wave(3,1.5),'tail_mid':wave(3,2.5),'tail_tip':wave(3,3.5),'head':wave(3,.6)}
    for side,sign in [('left',-1),('right',1)]:
        for location in ['front','back']:idle[f'flipper_{location}_{side}']=wave(3,sign*2,axis=2)
    m.animate('idle',3,idle)
    swim={'tail':wave(1,5),'tail_mid':wave(1,9,-.6),'tail_tip':wave(1,14,-1.2),'neck':wave(1,-1.5)}
    for side,sign in [('left',-1),('right',1)]:
        for location in ['front','back']:
            phase=0 if location=='front' else math.pi
            rowing=wave(1,sign*11,phase,2)[1];yaw=wave(1,sign*8,phase+math.pi/2,1)[1]
            swim[f'flipper_{location}_{side}']=('rotation',[(time,[0,b[1],a[2]]) for (time,a),(_,b) in zip(rowing,yaw)])
    m.animate('swim',1,swim);m.walk_stride_blocks=6.
    # Horizontal distance omits vertical swimming, so keep a gentle time-based stroke.
    m.animations[-1]['anim_time_update']=f'query.modified_distance_moved / ({m.walk_stride_blocks:.6f} * query.model_scale) + query.life_time * 0.15'
    def bite(values):return ('rotation',[(0,[0,0,0]),(.14,values[0]),(.32,values[1]),(.5,values[2]),(.75,[0,0,0])])
    m.animate('attack',.75,{
        'neck':[bite([[-3,0,0],[4,0,0],[-1,0,0]]),('position',[(0,[0,0,0]),(.14,[0,0,2]),(.32,[0,0,-7]),(.5,[0,0,-2]),(.75,[0,0,0])])],
        'head':bite([[-7,0,0],[9,0,0],[-3,0,0]]),'jaw':bite([[-34,0,0],[-5,0,0],[-19,0,0]])},loop='once')
    m.nametag()
    for name,a,b,parent in [
            ('head',[-14,14,-150],[14,36,-96],'head'),
            ('shoulders',[-24,8,-92],[24,43,-30],'neck'),
            ('torso',[-30,1,-32],[30,47,48],'body'),
            ('rear',[-24,6,48],[24,40,113],'tail'),
            ('tail',[-16,10,112],[16,30,172],'tail_mid')]:
        m.collider('b_mosa_'+name,a,b,parent)
    return m

if __name__=='__main__':
    models=[trex(),triceratops(),stegosaurus(),brachiosaurus(),velociraptor(),pteranodon(),dilophosaurus(),mosasaurus()]
    for m in models:
        m.export();print(m.name,len(m.elements),'cubes',len(m.groups),'bones')
    (ROOT/'LICENSE.txt').write_text('Original dinosaur geometry, textures, animations and preview generators: CC0 1.0 Universal.\nhttps://creativecommons.org/publicdomain/zero/1.0/\nCreated for this project, 2026-11-07. No third-party dinosaur assets.\n')
