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
def png_texture(colors,species=None):
    def chunk(kind,b):return struct.pack('>I',len(b))+kind+b+struct.pack('>I',zlib.crc32(kind+b))
    rows=[]
    for y in range(64):
        row=bytearray(b'\0')
        for x in range(64):
            material=x//16+4*(y//32);u,v=x%16,y%32
            rgb=bytes.fromhex(colors[material][1:]);noise=((u*73+v*151+u*v*19)%17)-8
            # Broken markings and granular scales read at both mount and close-up distances.
            mottling=math.sin(u*.55+math.sin(v*.37)*1.5)+math.cos(v*.51+u*.21)
            edge=(v+(u//4)%2)%4==0 and (u+v)%5!=0
            shade=noise*.7+5*mottling-5*edge
            if material in (0,1):
                if species in ('ark_trex','ark_velociraptor','ark_dilophosaurus'):
                    stripe=math.cos(u*.70+v*.21+math.sin(v*.32)*1.7)
                    shade-=(48 if species=='ark_velociraptor' else 32)*max(0,stripe-.12)
                elif species=='ark_mosasaurus':shade-=20*max(0,mottling-.4)
                elif species=='ark_triceratops':shade+=7*math.cos(u*1.7+math.sin(v)*1.2)
                elif species=='ark_brachiosaurus':shade-=8*max(0,math.cos(v*.8+math.sin(u*.4)))
            elif material==2:shade=noise*.45+4*math.sin(v*1.15+math.sin(u*.45)*1.3)
            elif material==6:shade=noise*.4+9*math.sin(u*1.5)-v*.18
            elif material==4:shade=14 if (u-5)**2+(v-10)**2<12 else noise*.25
            elif material==5:shade=noise*.4
            elif material==7:
                shade=noise*.5+7*mottling
                if species in ('ark_stegosaurus','ark_pteranodon','ark_dilophosaurus'):
                    shade-=15*max(0,math.cos(u*.95+math.sin(v*.22)+v*.23))
            if species=='ark_gallimimus':
                if material in (0,1,2,7):
                    barb=math.cos(u*1.55+math.sin(v*.52)*.75+v*.23)
                    shade=noise*.45+6*mottling+9*barb
                elif material==3:
                    shade=noise*.45+5*math.cos(v*1.45+(u//4)*.6)
                elif material==6:
                    shade=noise*.28+5*math.sin(u*.55+v*.16)
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
    def profile(self,name,sections,bone='body',color=0,belly=None):
        """Build three overlapping, pitched bands between successive (Z, half-width, floor, crown) sections."""
        for i,(start,end) in enumerate(zip(sections,sections[1:])):
            za,wa,low_a,high_a=start;zb,wb,low_b,high_b=end
            height_a,height_b=high_a-low_a,high_b-low_b
            stem=name if i==0 else f'{name}_{i}'
            for suffix,fraction,thickness,breadth,material in (
                    ('',1,.43,.77,color),('_flanks',.80,.60,1,color),
                    ('_underside',.35,.35,.73,color if belly is None else belly)):
                ya=low_a+height_a*fraction;yb=low_b+height_b*fraction
                pitch=-math.atan2(yb-ya,zb-za);length=math.hypot(yb-ya,zb-za)
                width=(wa+wb)*.5*breadth;depth=(height_a+height_b)*.5*thickness*math.cos(pitch)
                self.cube(stem+suffix,[-width,ya-depth,za],[width,ya,za+length],bone,material,
                          (math.degrees(pitch),0,0),[0,ya,za])
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
        elif self.name=='ark_gallimimus':
            tracks={'head':rotate_clip([[-8,0,0],[10,0,0],[-3,0,0]])}
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
        out=ROOT/'models'/self.name;out.mkdir(parents=True,exist_ok=True);tex=png_texture(self.colors,self.name)
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
        if self.name=='ark_gallimimus':design.update(run_stride_blocks=self.run_stride_blocks,native_carrier='SNIFFER',rideable=True,diet='herbivore',ecology='loose_herd')
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
    for i,spread in enumerate([1,0,-1] if sickle and x<0 else [-1,0,1]):
        start=[x+spread*.8*scale,1.15*scale,base[2]-.3*scale]
        end=[x+spread*3.3*scale,.65*scale,base[2]-(7 if spread==0 else 5.8)*scale]
        if sickle and i==0:
            start[1]+=2.2*scale;end[1]+=2.2*scale;end[2]+=2*scale
        middle=[(start[j]+end[j])*.5 for j in range(3)]
        m.beam(f'toe_{side}_{i}_base',start,middle,1.2*scale,1.05*scale,leg,3,True)
        m.beam(f'toe_{side}_{i}_tip',middle,end,.9*scale,.8*scale,leg,3,True)
        claw=[end[0]+spread*.3*scale,end[1]-.15*scale,end[2]-1.4*scale]
        m.beam(f'claw_{side}_{i}',end,claw,.6*scale,.55*scale,leg,6,True)
        if sickle and i==0:
            hook=[claw[0],claw[1]+2.0*scale,claw[2]-1.45*scale]
            m.beam('sickle_'+side+'0',claw,hook,1.1*scale,.95*scale,leg,5)
            m.beam('sickle_'+side+'1',hook,[hook[0],claw[1]-.8*scale,hook[2]-1.5*scale],.65*scale,.55*scale,leg,5)

def quadruped(m,x,front_z,back_z,hip_y,width):
    for side,sx,z in [('front_left',-x,front_z),('back_right',x,back_z),('front_right',x,front_z),('back_left',-x,back_z)]:
        name='leg_'+side;m.bone(name,[sx,hip_y,z],'body');m.legs.append(name)
        m.mass('leg_'+side,[sx-width*.58,hip_y*.45,z-width*.64],[sx+width*.58,hip_y+1,z+width*.64],name,0)
        m.beam('ankle_'+side,[sx,hip_y*.55,z],[sx,2,z-.5],width*.7,width*.8,name,1)
        m.mass('sole_'+side,[sx-width*.5,0,z-width*.62],[sx+width*.5,3,z+width*.48],name,3)
        m.feet.append('sole_'+side)
        for dx in [-.28,0,.28]:m.cube('nail_'+side+str(dx),[sx+(dx-.09)*width,0,z-width*.6-.25],[sx+(dx+.09)*width,1.2,z-width*.6+.2],name,6,ground=True)

def trex():
    m=Model('ark_trex','T-Rex',['#997653','#705940','#c6aa7f','#534536','#e0ac52','#251f1a','#e9d9b1','#68513e'],'Breiter Raubtierschädel mit schweren Brauen · umberbraune Querbänder · kräftige Hüfte und kurze Zweifingerarme')
    m.bone('body',[0,23,3]);m.mass('torso',[-7,16,-8],[7,30,12])
    m.mass('hips',[-7.5,17,5],[7.5,29,16])
    m.profile('ribcage',[(-15,4.2,20.3,26.8),(-9,6.7,15.5,29.1),(-5.4,7.2,15.1,27.7),(-2,7.4,14.7,27.7),(6,7.3,16.1,27.7),(12,6.9,18,29),(17,4.6,20.5,26.8)],belly=2)
    m.bone('neck',[0,27,-8],'body');m.beam('neck_lower',[0,25,-8],[0,29.2,-15.8],8.8,7,'neck')
    m.profile('neck',[(-21,4.2,30,36),(-16,5.1,26.6,35.8),(-10,4.5,23.2,30.5)],'neck',0,2)
    m.bone('head',[0,33,-14],'neck')
    m.profile('skull',[(-27,5.4,31,36.7),(-23,7,30.8,39.5),(-18,7.4,29.8,40),(-12.3,5.8,30.1,37)],'head')
    m.profile('snout',[(-36.5,3.7,31,35),(-33.5,5.0,30.9,36.9),(-28.5,5.7,31,36.4),(-24.2,5.6,31.1,37.7)],'head')
    for side in [-1,1]:
        m.beam('brow_'+str(side),[side*6.55,37.1,-26.8],[side*7.28,38.5,-20],2.0,2.2,'head',1)
        m.beam('cheek_'+str(side),[side*6.8,34.1,-22.7],[side*5.8,30.9,-14.4],2.1,2.6,'head',1)
        m.beam('neck_tendon_'+str(side),[side*3.9,32,-16],[side*4.0,25.5,-8.8],1.6,2.6,'neck',1)
        m.cube('nostril_'+str(side),[side*4.42-.16,33.7,-35.2],[side*4.42+.16,34.55,-33.9],'head',5)
    m.eyes(7.25,35.0,-23.1,size=1.45)
    m.bone('jaw',[0,31,-14],'head')
    m.profile('jaw',[(-35.4,3.65,28.8,30.4),(-31.5,5.1,27.4,30.55),(-23,5.4,26.7,30.6),(-17.8,4.9,27.3,31.5),(-13,4.5,29.2,32.4)],'jaw',0,2)
    m.cube('mouth',[-5.2,30.4,-33],[5.2,31.05,-14],'head',3)
    m.mouth_rows(5,30.9,30.45,[-33+i*2.4 for i in range(8)],[-31.8+i*2.1 for i in range(7)],1.,2.1)
    m.cube('oral_cavity',[-4.8,30.65,-33],[4.8,31.1,-13],'head',5)
    m.bone('tail',[0,24,11],'body');m.tail_chain([[0,23.8,11],[0,23.7,22],[0,22.3,32],[0,21.5,41],[0,22.5,49],[0,24.5,56]],[10.1,7.4,5.1,3,1.3])
    for side,x in [('left',-7),('right',7)]:
        digitigrade(m,side,x,22,6)
        for i,spread in enumerate([-1,0,1]):
            end=next(c['origin'] for c in m.elements if c['name']==f'claw_{side}_{i}')
            root=[end[0],end[1]+.5,end[2]+.4];hook=[end[0]+spread*.2,end[1]+.8,end[2]-1.7];tip=[end[0]+spread*.4,end[1]+.15,end[2]-3.1]
            m.beam(f'toe_claw_{side}_{i}_root',root,hook,1.15,1.,'leg_'+side,6,True)
            m.beam(f'toe_claw_{side}_{i}_tip',hook,tip,.7,.6,'leg_'+side,6,True)
        m.mass('hip_muscle_'+side,[x-2.8,15,2],[x+2.8,25,10],'leg_'+side)
        m.beam('calf_'+side,[x,13,2.8],[x,7.5,6],3.1,3.4,'leg_'+side,1)
        m.bone('arm_'+side,[x*.85,26,-9],'body');m.beam('tiny_arm_'+side,[x*.85,26,-10],[x*.85,21,-14],1.8,bone='arm_'+side,color=1);m.beam('hand_'+side,[x*.85,21,-14],[x*.85,21,-17],1.1,bone='arm_'+side,color=3)
        for i,dx in enumerate([-.65,.65]):
            finger=[x*.85+dx,20.5,-18.8];hook=[finger[0],20.6,-20.3];tip=[finger[0],19.8,-21.4]
            m.beam(f'hand_finger_{side}_{i}',[x*.85+dx,21,-16.7],finger,.65,bone='arm_'+side,color=3)
            m.beam(f'hand_claw_{side}_{i}',finger,hook,.75,.7,'arm_'+side,6)
            m.beam(f'hand_claw_{side}_{i}_tip',hook,tip,.45,.4,'arm_'+side,6)
    return m.finish()

def triceratops():
    m=Model('ark_triceratops','Triceratops',['#986e4f','#775640','#b5936d','#4b3b30','#c4995c','#251f1c','#d1bd99','#875f47'],'Breiter Nackenschild mit gekerbtem Rand · zwei lange gebogene Brauenhörner und ein Nasenhorn · erdbraune Schuppenhaut')
    m.bone('body',[0,22,2])
    m.profile('barrel',[(-16,6.4,14.9,24.5),(-10,10.1,10.4,29),(-3,11.3,9.3,30.3),(6,11.5,9.5,30.2),(14,9.5,12.3,28),(18.5,7.4,14.5,28),(20.5,5.8,15.4,25.3)],belly=2)
    m.mass('back',[-8,27,-10],[8,31,14],color=1)
    m.profile('neck_base',[(-21,4.6,17.3,22.9),(-15,6.5,15.6,25.8),(-10,7.4,14.9,27.1)])
    quadruped(m,8,-9,12,16,5)
    m.bone('head',[0,21,-13],'body')
    m.profile('head',[(-30,4.6,13,20.4),(-25,7,12.8,24),(-19,7.5,12.7,26),(-14,6.4,13,25.7)],'head')
    m.profile('snout',[(-37,3.25,12.1,17.6),(-33,4.2,12.3,20),(-27,5.3,13,20)],'head')
    m.profile('beak',[(-40.5,1.9,11.2,14.6),(-38.3,3,12.1,16.4),(-35.4,3.3,13.2,17.2)],'head',3)
    m.cube('frill_lower',[-8.6,18.8,-18.5],[8.6,28.2,-14.2],'head',7)
    m.cube('frill_top',[-5.9,25,-17.9],[5.9,36,-14.7],'head',7,(24,0,0),[0,23,-16])
    for side in [-1,1]:
        for i,(x,y,top,width) in enumerate([(6.4,23.5,35.2,5.5),(9.7,22.1,32.5,4.6),(12.1,22.7,29.6,3.0)]):
            m.cube(f'frill_fan_{side}_{i}',[side*x-width/2,y,-18],[side*x+width/2,top,-14.9],'head',7,(24,0,-side*9),[side*x,23,-16])
        for i,(x,y) in enumerate([(4.3,35.5),(8.2,34.5),(11.2,32.3),(13.1,29.1)]):
            z=-16+(y-23)*.44;y=23+(y-23)*.91
            m.beam(f'frill_knob_{side}_{i}',[side*x,y-.8,z],[side*(x+.75),y+.65,z+.3],1.85,1.95,'head',1)
        m.beam('cheek_fold_'+str(side),[side*6.9,18.5,-23.5],[side*7.3,21,-15.3],1.6,2,'head',1)
    for side in [-1,1]:
        arc=[[side*5,24,-26],[side*5.7,27.4,-29.8],[side*6.7,30.1,-34.7],[side*7.5,32,-40],[side*8,32.5,-45.8]]
        widths=[3.85,3.0,2.0,1.05,.28]
        for i,(a,b,w,tip) in enumerate(zip(arc,arc[1:],widths,widths[1:])):
            mid=[(v+u)*.5 for v,u in zip(a,b)]
            m.beam(f'brow_horn_{side}{i}',a,mid,w,bone='head',color=6)
            m.beam(f'brow_horn_{side}{i}_tip',mid,b,(w+tip)*.5,bone='head',color=6)
        m.growth_detail('growth_brow_horn_'+('left' if side<0 else 'right'),[side*5,24,-26],'head','brow_horn_'+str(side))
    arc=[[0,19.5,-35],[0,23.6,-35.9],[0,26.9,-37.7],[0,29.4,-40.4],[0,30.7,-43.4]]
    for i,(a,b,w) in enumerate(zip(arc,arc[1:],[3.65,2.9,1.9,.9])):m.beam('nose_horn'+str(i),a,b,w,bone='head',color=6)
    m.growth_detail('growth_nose_horn',[0,20,-35],'head','nose_horn');m.eyes(7.28,20.1,-24.7,size=1.3)
    m.bone('jaw',[0,13,-16],'head');m.mass('lower_jaw',[-6,10.5,-26],[6,12.6,-16],'jaw',1)
    m.profile('mandible',[(-39.5,1.6,10.7,12.35),(-36,2.6,9.8,12.3),(-31,3.2,9.5,12.3),(-25.3,4.1,10.2,12.5)],'jaw',0,2)
    for side in [-1,1]:m.beam('jaw_fold_'+str(side),[side*5.5,12.2,-25.6],[side*5.7,14,-17.3],1.45,1.65,'jaw',0)
    m.cube('jaw_hinge',[-4,12,-18],[4,13.6,-15.5],'jaw',1)
    m.cube('oral_cavity',[-6.5,12.7,-26],[6.5,13.05,-16],'head',5)
    m.mouth_rows(4.5,12.95,12.55,[-24.5,-22.5,-20.5],[-24.2,-22.2,-20.2],.36,.42,'grinder')
    m.bone('tail',[0,20,16],'body');m.tail_chain([[0,20,16],[0,18,25],[0,16,32],[0,15,39],[0,14.5,43]],[5.5,4,2.6,1.2])
    return m.finish()

def stegosaurus():
    m=Model('ark_stegosaurus','Stegosaurus',['#788363','#596748','#b6ac86','#4a4e3c','#cda957','#26291e','#d2c3a1','#aa7953'],'Tiefer Kopf unter einem hohen Hüftbogen · versetzte kupferbraune Rückenplatten · vier ausladende Schwanzstacheln')
    m.bone('body',[0,22,0])
    m.profile('barrel',[(-18,4.6,14.7,22.6),(-12,8.1,11.1,25.1),(-5,10,10,27.6),(3,10.6,10.7,28.95),(11,10.1,12.3,29),(17,8.9,15.3,29),(21,6.8,17.5,23.4)],belly=2)
    m.mass('back',[-7,24,-11],[7,30,14],color=1)
    quadruped(m,7.5,-10,12,16,4.4)
    for side in [-1,1]:
        m.beam('haunch_'+str(side),[side*7.5,24.1,13],[side*7.5,14,10.7],5,6,'leg_back_'+('left' if side<0 else 'right'))
    m.bone('head',[0,18,-14],'body');m.beam('neck',[0,20,-13],[0,14,-27],5.5,5.8,'head',0)
    m.beam('nuchal_arch',[0,21.8,-14],[0,17.2,-24],4.9,4.45,'head')
    m.profile('head',[(-36,2.2,10,13.6),(-32,3,10,15.8),(-29,3.5,9.9,17.1),(-25.2,3.2,10,16.9)],'head')
    m.profile('beak',[(-39.5,1.8,9.5,11.7),(-37.7,2.6,10,13),(-34,2.7,10.3,13.5)],'head',3);m.eyes(3.32,13.8,-30.9,size=1)
    m.bone('jaw',[0,10,-26],'head');m.mass('lower_jaw',[-3.1,7.5,-35],[3.1,9.5,-26],'jaw',1)
    m.profile('beak_lower',[(-38.8,1.45,8.7,10),(-35,2.2,8.45,10),(-32,2.4,8.55,9.7)],'jaw',3)
    m.cube('jaw_hinge',[-2.2,9,-26.8],[2.2,10.6,-25.5],'jaw',1)
    m.cube('oral_cavity',[-3.2,9.65,-34],[3.2,10.05,-26],'head',5)
    m.mouth_rows(1.8,9.85,9.45,[-30,-28.7,-27.4],[-29.8,-28.5,-27.2],.36,.42,'grinder')
    for side in [-1,1]:
        for i,z in enumerate([-10,-1,8,16]):
            z+=3.8 if side>0 else 0;high=[7.7,12.4,15.8,10.7][i];x=side*7.5;base=27 if i<2 else 28
            root=([22.5,24.5,24.5,26.25] if side<0 else [23.3,24.5,26.25,22.4])[i]
            half=2.55+high*.14
            # Successive laminae fill an asymmetric leaf; the highest point leans toward the tail.
            bottom=root
            for layer,level,front,rear,thickness in [
                    ('base',.12,-.38,.39,.69),('lower',.32,-.66,.73,.68),('middle',.5,-.96,1,.68),
                    ('upper',.68,-.83,.88,.61),('crown',.85,-.41,.67,.53),('top',1,.02,.43,.42)]:
                top=base+high*level
                m.cube(f'plate_{side}_{i}_{layer}',[x-thickness,bottom,z+front*half],[x+thickness,top,z+rear*half],color=7)
                bottom=top
            m.growth_detail(f'growth_plate_{"left" if side<0 else "right"}_{i}',[x,root,z],'body',f'plate_{side}_{i}_',(.3,.65))
    m.bone('tail',[0,20,16],'body');m.tail_chain([[0,20,16],[0,17,27],[0,13,37],[0,10,46],[0,9,56]],[6,4.8,3.2,1.6])
    for prefix,bone,sections in [('neck_plate','head',[(-32,16,2.5),(-26,17,3.5),(-21,20,4.5),(-16,23,6)]),
                                 ('tail_plate','tail',[(23,21,8),(29,18,6),(35,15,4),(40,13,2.5)])]:
        for i,(z,base,high) in enumerate(sections):
            x=(-1 if i%2==0 else 1)*(1.3+high*.12)
            root=15.3 if prefix=='neck_plate' and i==0 else 19.25 if prefix=='neck_plate' and i==2 else 20.5 if prefix=='tail_plate' and i==0 else base
            m.cube(f'{prefix}_{i}_base',[x-.45,root,z-1.2],[x+.45,base+high*.55,z+1.2],bone,7)
            m.beam(f'{prefix}_{i}_top',[x,base+high*.4,z],[x,base+high,z-.4],.75,2.8,bone,7)
            m.growth_detail(f'growth_{prefix}_{i}',[x,root,z],bone,f'{prefix}_{i}_',(.3,.65))
    for side in [-1,1]:
        for z in [42,49]:
            m.tapered(f'tail_spike_{side}_{z}',[side,11,z],[side*(10.3 if z==42 else 11.4),15.5 if z==42 else 18.2,z+7],2.25,'tail',6,3)
            root=10.25 if z==49 else 11
            if z==49:next(c for c in m.elements if c['name']==f'tail_spike_{side}_{z}0')['from'][1]=root
            m.growth_detail(f'growth_tail_spike_{"left" if side<0 else "right"}_{z}',[side,root,z],'tail',f'tail_spike_{side}_{z}')
    return m.finish()

def brachiosaurus():
    m=Model('ark_brachiosaurus','Brachiosaurus',['#868276','#706e62','#b8a892','#555247','#c6a46a','#2d2921','#d9cfb4','#756f60'],'Hohe Schultern über den Vorderbeinen · gebogener Langhals mit Nasenkuppel · graubraune Hautfalten')
    m.bone('body',[0,31,2]);m.mass('barrel',[-9.65,22,-7],[9.65,40,16])
    m.profile('ribcage',[(-16,6.2,27.3,38.3),(-10,10.4,21.8,43.7),(-5,11.1,19.5,39.9),(4,11.8,18.5,39.7),(12,11,20.4,38.5),(19,8.2,24,36.4),(24,4.9,26.5,32.6)],belly=2)
    m.mass('shoulders',[-10,29,-10],[10,43,-3],color=1)
    m.mass('shoulder_hump',[-7,38,-11],[7,44,-5],color=1)
    quadruped(m,8,-9,14,26,5)
    for side in [-1,1]:
        fore='leg_front_'+('left' if side<0 else 'right')
        m.beam('shoulder_column_'+str(side),[side*7.5,36.7,-8],[side*8,19,-9.5],5.3,5.9,fore)
        m.beam('hind_haunch_'+str(side),[side*8,30.5,15.2],[side*8,18.3,11.7],5.6,6.7,'leg_back_'+('left' if side<0 else 'right'))
    m.bone('neck',[0,40,-10],'body')
    neck_points=[[0,39,-9],[0,48,-13],[0,58.4,-15.5],[0,68.3,-15.9],[0,77.2,-19.9],[0,85.2,-29.2]]
    for i,(a,b,w) in enumerate(zip(neck_points,neck_points[1:],[9.4,8.8,7.1,5.9,4.9])):
        m.beam('neck_lower' if i==0 else 'neck_upper' if i==4 else 'neck_segment_'+str(i),a,b,w,w*(.86 if i==0 else .91),'neck')
        m.beam('throat_'+str(i),[a[0],a[1],a[2]-w*.36],[b[0],b[1],b[2]-w*.36],w*.6,w*.22,'neck',2)
    m.bone('head',[0,84,-29],'neck')
    m.profile('skull',[(-38.5,3.3,82,86.5),(-34.7,4.6,81.5,89.2),(-30.4,4.8,81.3,90),(-26.4,3.1,83,87.5)],'head')
    m.profile('muzzle',[(-44,2.9,81,84.5),(-41.2,3.7,80.9,85.7),(-35,3.6,81.8,87.1)],'head')
    m.profile('nasal_rise',[(-37,1.7,85.8,87),(-34.3,2.9,86.8,91.8),(-31.7,2.95,87,93),(-28,1.8,85,88.4)],'head',1)
    m.bone('jaw',[0,81,-29],'head');m.mass('lower_jaw',[-3.5,78.8,-42],[3.5,80.5,-29],'jaw',2);m.eyes(4.6,86,-35,size=1.4)
    m.cube('jaw_hinge',[-2.5,80,-31],[2.5,82.6,-28],'jaw',2)
    m.cube('oral_cavity',[-3.5,80.6,-43.8],[3.5,81.05,-30],'head',5)
    for row,y,z,direction,bone in [('upper',80.9,-43.15,-1,'head'),('lower',80.45,-41.7,1,'jaw')]:
        m.cube('gums_'+row,[-3.4,y-.15,z-.25],[3.4,y+.15,z+.25],bone,3)
        for i,x in enumerate([-2.4,-.8,.8,2.4]):m.tooth(f'tooth_{row}_front_{i}',[x,y,z],[x,y+direction*.85,z-.1],bone,.28,'spatulate')
    for side in [-1,1]:
        x=side*2.65;m.cube('nostril_'+str(side),[x-.22,89.7,-34.6],[x+.22,90.7,-32.9],'head',5)
    m.bone('tail',[0,30,19],'body');m.tail_chain([[0,30,19],[0,23,30],[0,15,40],[0,8,50],[0,3.2,59],[0,.65,67]],[7.5,6,4.4,2.8,1.4])
    return m.finish()

def velociraptor():
    m=Model('ark_velociraptor','Velociraptor',['#b08758','#826645','#d3b98d','#584534','#daa74b','#252017','#e0d0a6','#705237'],'Sandbraune Schuppen mit dunklen Bändern · schmaler Fang und dreifingrige Greifhände · hochgezogene Sichelkrallen')
    m.bone('body',[0,18,1])
    m.mass('torso',[-4.2,14,-7],[4.2,23,8]);m.mass('hips',[-4.5,15,3],[4.5,23,11])
    m.profile('chest',[(-11.6,2.1,17.5,21.4),(-8.5,3.4,14.4,22.4),(-6.4,3.8,14.3,22.7)],belly=2)
    m.mass('belly',[-3.4,13,-7],[3.4,17,6],color=2)
    m.cube('back_stripe',[-1.8,22,-7],[1.8,24,8],color=7)
    m.bone('neck',[0,21,-7],'body');m.beam('neck_lower',[0,19.7,-7],[0,24.3,-10],4.8,4.4,'neck');m.beam('neck',[0,24.3,-10],[0,29,-16.7],3.7,3.5,'neck')
    m.bone('head',[0,29,-14],'neck')
    m.profile('skull',[(-24.3,2.6,27.2,30.4),(-21.2,3.8,27,32.6),(-17.6,4.05,26.6,33.4),(-13.4,3.35,26.4,31.7)],'head')
    m.profile('snout',[(-32.9,1.7,27.0,29.25),(-29.7,2.45,26.95,30),(-25.6,2.55,26.95,29.8),(-22,2.65,27.1,30.7)],'head')
    m.bone('jaw',[0,27,-15],'head');m.profile('jaw',[(-31.9,1.75,25.8,27),(-27,2.45,25.2,27),(-21,2.65,24.8,26.95),(-15,2.65,25.6,27.7)],'jaw',0,2)
    m.cube('mouth',[-2.45,26.9,-30],[2.45,27.24,-19],'head',3);m.eyes(3.95,29.35,-20.8,size=1.15)
    for side in [-1,1]:
        m.beam('brow_'+str(side),[side*3.4,31.2,-23.1],[side*3.8,32.3,-18.2],1.0,1.3,'head',1)
        m.beam('eye_mask_'+str(side),[side*3.77,30.4,-19.5],[side*3.48,30.6,-14.9],.72,1.35,'head',7)
        m.cube('nostril_'+str(side),[side*2.13-.12,28.4,-31.1],[side*2.13+.12,28.9,-30.1],'head',5)
    m.mouth_rows(2.2,26.95,26.9,[-30+i*1.9 for i in range(7)],[-29.2+i*1.8 for i in range(6)],.6,1.5)
    m.cube('oral_cavity',[-2.2,26.8,-30],[2.2,27.05,-15],'head',5)
    m.bone('tail',[0,19,7],'body');m.tail_chain([[0,19,7],[0,19.8,18],[0,20.5,29],[0,20.7,40],[0,20.8,49],[0,20.7,57]],[5.2,3.7,2.4,1.55,.85])
    for side,x in [('left',-4.7),('right',4.7)]:
        digitigrade(m,side,x,18,4,.73,True)
        m.mass('thigh_muscle_'+side,[x-2.1,11.5,1],[x+2.1,20,7],'leg_'+side)
        # Start the shoulder at its rotation joint so the rake cannot pull it off the torso.
        arm='arm_'+side;m.bone(arm,[x,22,-6],'body')
        elbow=[x*1.5,16,-9];m.beam('upper_arm_'+side,[x,22,-6],elbow,1.7,bone=arm,color=1)
        wrist=[x*1.64,17.8,-15.8];m.beam('forearm_'+side,elbow,wrist,1.45,1.55,arm)
        m.beam('palm_'+side,wrist,[x*1.64,16.7,-18.3],1.55,1.7,arm)
        sign=-1 if x<0 else 1
        for i,(spread,reach) in enumerate([(-1,2.8),(0,4.3),(1,3.3)]):
            root=[wrist[0]+sign*spread*.55,16.9,-17.8]
            knuckle=[wrist[0]+sign*spread*1.35,16.1,-18-reach*.65]
            hook=[knuckle[0],15.7,-18-reach]
            m.beam(f'finger_{side}_{i}',root,knuckle,.76,.74,arm)
            m.beam(f'hand_claw_{side}_{i}_root',knuckle,hook,.65,.64,arm,5)
            m.beam(f'hand_claw_{side}_{i}_tip',hook,[hook[0]-sign*.2,14.45,hook[2]-.6],.40,.4,arm,5)
    return m.finish()


def gallimimus():
    m=Model('ark_gallimimus','Gallimimus',['#bc8e68','#90694e','#e1c6a0','#635c50','#dbaf5b','#292620','#7a7161','#77533e'],
            'S-förmiger Hals und zahnloser Schnabel · lange Laufbeine mit drei Zehen · feines Federkleid rekonstruiert')

    def axial_segment(name,start,end,width,depth,bone,back,belly):
        rise,run=end[1]-start[1],end[2]-start[2]
        length=math.hypot(rise,run);rotation=(math.degrees(math.atan2(run,rise)),0,0)
        join=min(length*.22,depth*.36)
        x,y,z=start
        for suffix,breadth,low,high,color in (
                ('',1,-.29,.29,0),('_back',.74,.16,.50,back),('_belly',.73,-.50,-.16,belly)):
            half=width*breadth/2
            m.cube(name+suffix,[x-half,y-join,z+low*depth],[x+half,y+length+join,z+high*depth],
                   bone,color,rotation,start)

    m.bone('body',[0,29,2])
    m.profile('trunk',[(-11.5,2.4,26,30.7),(-7,4.3,23.3,32.7),(1.5,5.5,22.5,33.4),
                      (6,6.8,23.2,33.3),(10,5.6,24.6,32.5),(13,3.0,26.3,31.1)],belly=2)
    m.cube('torso',[-3.25,31,-6],[3.25,33.5,9],color=1)
    m.bone('neck',[0,31,-6],'body')
    neck=[(0,29.8,-7.0),(0,33.2,-10.0),(0,36.9,-13.2),(0,40.0,-14.25),
          (0,43.05,-13.45),(0,46.7,-13.75),(0,49.45,-15.3),(0,50.8,-17.35)]
    m.bone('neck_upper',neck[4],'neck')
    for i,(start,end,width,depth) in enumerate(zip(neck,neck[1:],
                                                  [4.7,4.0,3.15,2.65,2.3,2.05,2.3],
                                                  [5.45,4.2,3.3,2.7,2.45,2.3,2.4])):
        axial_segment(f'neck_core_{i}',start,end,width,depth,'neck' if i<4 else 'neck_upper',1,2)
    m.bone('head',[0,50.55,-17.3],'neck_upper')
    m.profile('skull',[(-20.6,1.0,50.0,51.65),(-19.15,1.68,49.75,52.4),
                      (-17.5,1.73,49.75,52.65),(-16.1,1.37,50.0,51.95)],'head',belly=2)
    m.profile('beak',[(-23.9,.68,49.82,50.20),(-22.65,1.02,49.88,50.88),
                      (-20.75,1.18,49.92,51.4),(-19.9,1.27,49.93,51.46)],'head',6)
    m.bone('jaw',[0,49.82,-17.95],'head')
    m.profile('beak_lower',[(-23.8,.65,49.62,49.76),(-22.55,.94,49.48,49.81),
                            (-20.25,1.13,49.35,49.86)],'jaw',6)
    m.profile('mandible',[(-20.4,1.12,49.35,49.83),(-18.3,1.48,49.30,49.84),
                          (-16.65,1.16,49.65,50.2)],'jaw',belly=2)
    m.eyes(1.65,50.95,-19.31,size=.72)
    for sign in (-1,1):
        m.beam(f'brow_{sign}',[sign*1.43,51.75,-20.15],[sign*1.54,52.05,-17.9],
               .48,.38,'head',1)
        m.beam(f'cheek_{sign}',[sign*1.31,50.35,-20.15],[sign*1.49,50.8,-17.25],
               .46,.50,'head',2)
        lo,hi=sorted([sign*1.093,sign*1.21])
        m.cube(f'nostril_{sign}',[lo,50.38,-21.62],[hi,50.56,-21.16],'head',5)

    m.bone('tail',[0,28,8],'body')
    tail=[(0,28.3,8),(0,28.85,13),(0,29.35,19),(0,29.65,25),(0,29.75,31),
          (0,29.5,36.5),(0,29.0,42),(0,28.2,47),(0,27.25,52)]
    m.bone('tail_tip',tail[4],'tail')
    for i,(start,end,width,depth) in enumerate(zip(tail,tail[1:],
                                                  [5.3,4.0,3.15,2.45,1.85,1.38,.98,.6],
                                                  [5.7,4.65,3.45,2.7,1.95,1.42,1.05,.6])):
        axial_segment(f'tail_segment_{i}',start,end,width,depth,'tail' if i<4 else 'tail_tip',
                      2 if i<4 else 0,7 if i in (2,4,6) else 1)

    for side,x in [('left',-6.7),('right',6.7)]:
        sign=1 if x>0 else -1
        for i,(start,end,width,depth,color) in enumerate([
                ((4.0,31.2,-8.8),(5.15,30.4,-2.4),1.9,2.15,0),
                ((4.35,28.4,-7.4),(5.45,28.6,-1.0),1.55,2.1,2),
                ((5.25,30.45,-2.9),(6.42,31.0,5.7),1.55,2.55,7),
                ((5.65,28.1,-.95),(6.65,28.9,6.8),1.6,2.9,0)]):
            a=(sign*start[0],*start[1:]);b=(sign*end[0],*end[1:])
            m.beam(f'flank_{side}_{i}',a,b,width,depth,'body',color)

        leg='leg_'+side;m.bone(leg,[x,29,5],'body');m.legs.append(leg)
        knee=[sign*7.45,19.8,.55];hock=[sign*7.48,8.9,6.6];foot=[sign*7.5,1.4,1.45]
        m.beam('thigh_'+side,[x,29,5],knee,3.35,3.4,leg)
        m.beam('haunch_'+side,[sign*7.1,29.8,5.25],[sign*7.85,24.0,3.35],3.25,4.70,leg)
        m.beam('thigh_flank_'+side,[sign*8.25,28.3,4.6],[sign*8.25,22.15,1.5],1.35,2.85,leg,1)
        m.beam('thigh_taper_'+side,[sign*7.5,24.5,3.4],knee,2.65,3,leg,0)
        m.beam('knee_'+side,[sign*7.42,20.6,.95],[sign*7.46,18.8,1.15],2.4,2.5,leg,1)
        m.beam('shin_'+side,knee,hock,1.48,1.58,leg,3)
        m.beam('calf_'+side,[sign*7.46,19.65,.85],[sign*7.47,14.2,3.95],1.94,2.15,leg,1)
        m.beam('hock_'+side,[sign*7.48,10.0,6.12],[sign*7.48,8.23,6.13],1.62,1.65,leg,3)
        m.beam('metatarsus_'+side,hock,foot,1.24,1.30,leg,3)
        m.beam('tarsus_ridge_'+side,[sign*7.50,8.45,5.88],[sign*7.5,2.25,1.18],.69,.68,leg,6)
        m.cube('foot_pad_'+side,[foot[0]-1,0.52,.2],[foot[0]+1,1.63,2.2],leg,3)
        for i,offset in enumerate((-1,0,1)):
            px=foot[0]+offset*.61;pivot=[px,0,1.45];rotation=(0,-offset*14,0)
            length=4.25 if offset==0 else 3.35-.25*sign*offset
            for part,wide,low,high,front,rear,color in (
                    ('',.49,0,1.22,.53,-.05,3),
                    ('_tip',.37,0,1.04,.85,.48,3),
                    ('_claw',.25,.13,1.21,1,.77,6)):
                m.cube(f'toe_{side}_{i}{part}',[px-wide,low,1.45-front*length],
                       [px+wide,high,1.45-rear*length],leg,color,rotation,pivot,ground=True)

        arm='arm_'+side
        shoulder=[sign*4.55,29.05,-7.0];elbow=[sign*5.45,22.5,-3.65]
        wrist=[sign*5.7,19.9,-8.1];palm=[sign*5.62,19.12,-9.33]
        m.bone(arm,shoulder,'body')
        m.beam('upper_arm_'+side,shoulder,elbow,1.53,1.72,arm)
        m.beam('forearm_'+side,elbow,wrist,1.13,1.32,arm,1)
        m.beam('palm_'+side,wrist,palm,.84,1.03,arm,3)
        m.beam('shoulder_'+side,[sign*4.65,29.3,-7.15],[sign*5.58,24.1,-4.2],1.94,2.05,arm,0)
        for i,(ay,az,by,bz,width,material) in enumerate([
                (27.7,-6.1,22.2,-4.0,1.08,7),(24.3,-4.1,20.55,-6.85,.74,1)]):
            m.beam(f'arm_covert_{side}_{i}',[sign*5.68,ay,az],[sign*6.0,by,bz],
                   width,width*.95,arm,material)
        for i,(y,z,length) in enumerate([(19.55,-9.02,1.9),(19.05,-9.21,2.35),(18.65,-8.92,1.62)]):
            start=[palm[0],y,z];knuckle=[sign*5.45,y-.42,z-length*.64]
            end=[sign*5.41,y-.75,z-length]
            m.beam(f'finger_{side}_{i}',start,knuckle,.44,.40,arm,3)
            m.beam(f'finger_{side}_{i}_tip',knuckle,end,.36,.34,arm,3)
            m.beam(f'finger_{side}_{i}_claw',end,[sign*5.15,y-1.05,z-length-.43],.27,.27,arm,6)
    m.finish()
    walk=next(a for a in m.animations if a['name']=='walk')
    walk['anim_time_update']=f'query.anim_time + query.delta_time * math.clamp(query.ground_speed / ({m.walk_stride_blocks:.6f} * query.model_scale), 0.35, 2.4)'
    # The splayed toes need extra clearance between the walk's stance keyframes.
    walk_floor_margin=.09
    for track in walk['animators'].values():
        for key in track['keyframes']:
            if key['channel']=='position':key['data_points'][0]['y']=str(float(key['data_points'][0]['y'])+walk_floor_margin)
    tracks={};strides=[]
    for leg in m.legs:
        source=walk['animators'][m.groups[leg]['uuid']]['keyframes']
        rotations=[k for k in source if k['channel']=='rotation']
        positions={k['time']:float(k['data_points'][0]['y']) for k in source if k['channel']=='position'}
        foot=[v for c in m.elements if c['name'] in m.feet and c['uuid'] in m.nodes[leg]['children'] for v in vertices(c)]
        pivot=m.groups[leg]['origin'];rotation=[];position=[]
        def turned(point,angle):
            return [a+b for a,b in zip(rotate([a-b for a,b in zip(point,pivot)],[angle,0,0]),pivot)]
        for k in rotations:
            time=k['time'];old=float(k['data_points'][0]['x']);angle=old*2
            lift=positions[time]+min(turned(v,old)[1] for v in foot)-.04-walk_floor_margin
            rotation.append((time,[angle,0,0]))
            position.append((time,[0,-min(turned(v,angle)[1] for v in foot)+max(0,lift)*1.8+.12,0]))
        tracks[leg]=[('rotation',rotation),('position',position)]
        z=[sum(turned(v,a)[2] for v in foot)/len(foot) for a in [32,-32]]
        strides.append((z[1]-z[0])/16/.6)
    m.run_stride_blocks=round(sum(strides)/len(strides),6)
    tracks['tail']=('rotation',[(0,[0,-7,0]),(.5,[0,7,0]),(1,[0,-7,0])])
    tracks['tail_tip']=('rotation',[(0,[1,2,0]),(.25,[-1,-3,0]),(.5,[1,-2,0]),(.75,[-1,3,0]),(1,[1,2,0])])
    tracks['neck']=('rotation',[(0,[-10,0,0]),(.25,[-15,0,0]),(.5,[-10,0,0]),(.75,[-15,0,0]),(1,[-10,0,0])])
    tracks['head']=('rotation',[(0,[8,0,0]),(.25,[11,0,0]),(.5,[8,0,0]),(.75,[11,0,0]),(1,[8,0,0])])
    m.animate('run',1,tracks)
    m.animations[-1]['anim_time_update']=f'query.anim_time + query.delta_time * math.clamp(query.ground_speed / ({m.run_stride_blocks:.6f} * query.model_scale), 0.35, 2.4)'
    m.animate('graze',4,{'neck':('rotation',[(0,[0,0,0]),(.85,[-104,0,0]),(2.85,[-104,0,0]),(4,[0,0,0])]),
                         'neck_upper':('rotation',[(0,[0,0,0]),(.85,[-34,0,0]),(2.85,[-34,0,0]),(4,[0,0,0])]),
                         'head':('rotation',[(0,[0,0,0]),(.85,[43,-3,0]),(2,[48,3,0]),(2.85,[43,0,0]),(4,[0,0,0])])})
    return m

def pteranodon():
    m=Model('ark_pteranodon','Pteranodon',['#827669','#675f58','#c8b192','#554332','#c9a054','#2a2826','#b69168','#996e59'],'Lang ausgestrichener Schnabel und keilförmiger Kamm · geknickte Flügel mit kupferbrauner Flughaut')
    m.bone('body',[0,21,1]);m.mass('torso',[-4.3,16,-6],[4.3,26,7])
    m.mass('breast',[-3.6,15,-5],[3.6,20,5],color=2)
    # The skull's three-unit beak jab still needs an overlapping neck connection.
    m.bone('neck',[0,23,-5],'body');m.beam('neck',[0,23,-5],[0,29,-15],4.2,bone='neck',color=1)
    m.bone('head',[0,29,-14],'neck');m.profile('skull',[(-24.4,2.05,27.1,30.6),(-20.5,3.1,26.9,33),(-16,3.25,27.3,33.6),(-12.4,2.25,28,31.5)],'head')
    m.profile('long_beak',[(-49,.32,27.85,28.32),(-42,1,27.7,29),(-34,1.65,27.65,29.8),(-26,2.15,27.72,30.5),(-22,2.4,27.85,30.9)],'head',6)
    m.tapered('lower_beak',[0,27.35,-22],[0,27.65,-47.7],2.12,'head',2,5)
    m.profile('head_crest',[(-22.1,1.1,30.4,32),(-19,1.6,31.1,35.2),(-15.6,1.4,32.8,37.8),(-12.8,1.1,35.6,40.1),(-9.6,.5,39.2,41.35)],'head',7)
    m.growth_detail('growth_head_crest',[0,32,-20],'head','head_crest');m.eyes(3.18,29.6,-19.9,size=1.15)
    m.bone('tail',[0,20,7],'body');m.tapered('short_tail',[0,20,7],[0,19,13],2.8,'tail',0,2)
    for side,sign in [('left',-1),('right',1)]:
        wing='wing_'+side;m.bone(wing,[sign*4,23,0],'body')
        outer='wing_tip_'+side;m.bone(outer,[sign*16,23-12*.045,-1],wing)
        # The long outer leading edge is the extended fourth finger, not a bird feather fan.
        profile=[(4,0,9),(16,-1,15),(31,-9.4,15.3),(47,-5.4,10.8),(65,3,3.45)]
        for i,((x1,z1,_),(x2,z2,_)) in enumerate(zip(profile,profile[1:])):
            m.beam(f'wing_finger_{side}_{i}',[sign*x1,23-(x1-4)*.045,z1],[sign*x2,23-(x2-4)*.045,z2],[1.8,1.65,1.18,.8][i],bone=wing if i==0 else outer,color=0 if i<2 else 1)
        m.tapered('thumb_'+side,[sign*29,22,-7],[sign*27,23,-12],1.3,outer,6,2)
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
    m=Model('ark_dilophosaurus','Dilophosaurus',['#73814a','#566442','#d7bd78','#453b2a','#ecc044','#28251d','#e5d6a4','#ac482f'],
            'Zwei gewölbte Kopfkämme · scharlachroter Halsfächer mit gelber Zeichnung · olivgrüne Bänderhaut')
    m.bone('body',[0,15,3])
    m.mass('torso',[-4.8,10,-5],[4.8,19,8]);m.mass('hips',[-5,11,4],[5,19,12])
    m.mass('belly',[-3.4,9.5,-4],[3.4,13,8],color=2)
    m.cube('back_stripe',[-2.8,18.8,-4],[2.8,19.2,10],color=1)
    for side,sign in [('left',-1),('right',1)]:
        for i,z in enumerate([-3,1,5,9]):
            m.beam(f'flank_stripe_{side}_{i}',[sign*4.8,17.8,z],[sign*4.9,12.5,z+1.7],.55,1.5,color=1)
    m.bone('neck',[0,18,-5],'body');m.beam('neck_lower',[0,17,-5],[0,23,-11],4.8,4.2,'neck');m.mass('neck_upper',[-2.6,21,-15],[2.6,26,-9],'neck',1)
    m.bone('head',[0,25,-13],'neck')
    m.profile('skull',[(-23,2.35,23,27.05),(-20.4,3.15,22.9,28),(-16.5,3.5,22.7,28.8),(-11.2,2.8,22.7,27.35)],'head')
    m.profile('snout',[(-31.3,1.95,23,25.8),(-28.5,2.55,23,26.9),(-25.5,2.45,23.2,26.1),(-21,2.65,23,27.4)],'head')
    m.cube('mouth',[-2.75,22.8,-30],[2.75,23.2,-13],'head',3)
    m.bone('jaw',[0,23,-13],'head');m.mass('lower_jaw',[-2.5,21,-30],[2.5,23,-12],'jaw',2)
    m.eyes(3.35,25.1,-19.9,size=1.15)
    for side,sign in [('left',-1),('right',1)]:
        x=sign*2.2
        m.beam('snout_crest_pedestal_'+side,[x,25.6,-26.5],[x,27.5,-21.2],1.3,1.7,'head',1)
        for i,(a,b) in enumerate([([x,26.45,-26],[x,30.5,-23]),([x,30.5,-23],[x,33.3,-18.9]),([x,33.3,-18.9],[x,32.1,-16]),([x,32.1,-16],[x,28.4,-12.8])]):
            m.beam(f'crest_{side}_{i}',a,b,1.03,3.15,'head',7)
            m.beam(f'crest_rim_{side}_{i}',[a[0],a[1]+.65,a[2]],[b[0],b[1]+.65,b[2]],1.08,.82,'head',2)
        m.cube('crest_'+side+'_web',[x-.42,27.9,-22.6],[x+.42,31.55,-16],'head',7)
        m.growth_detail('growth_crest_'+side,[x,26,-26],'head',('crest_'+side+'_','crest_rim_'+side+'_'))
        m.cube('nostril_'+side,[sign*2.43-.14,24.8,-29.6],[sign*2.43+.14,25.3,-28.4],'head',5)
        digitigrade(m,side,sign*3.9,15.5,6,.61)
        m.mass('thigh_muscle_'+side,[sign*3.9-1.5,10,3],[sign*3.9+1.5,17,9],'leg_'+side)
        arm='arm_'+side;m.bone(arm,[sign*4.2,17.5,-3],'body')
        m.beam('upper_arm_'+side,[sign*4.2,17.5,-3],[sign*6,13,-5],1.3,bone=arm,color=1)
        m.beam('forearm_'+side,[sign*6,13,-5],[sign*7,14,-12],1.1,bone=arm)
        for i,dx in enumerate([-.8,0,.8]):
            m.beam(f'finger_{side}_{i}',[sign*7+dx,14,-11],[sign*7+dx,13.7,-14],.55,bone=arm,color=3)
            m.tapered(f'hand_claw_{side}_{i}',[sign*7+dx,13.7,-14],[sign*7+dx,12.8,-16],.6,arm,6,3)
        # Each rib starts folded into the same narrow collar; unfolding spreads the fan.
        pivot=[sign*3.15,24,-19.5];root='frill_'+side;m.bone(root,pivot,'neck')
        growth='growth_frill_'+side;m.growth_detail(growth,pivot,root,(),(.3,.65))
        for i,angle in enumerate(range(-75,76,15)):
            rib=f'frill_{side}_rib_{i}';m.bone(rib,pivot,growth)
            theta=math.radians(angle*.12);radius=10.6+2.45*math.cos(math.radians(angle))+.6*(i%2)
            def fan_point(distance,face=0):
                return [pivot[0]+sign*face,pivot[1]+math.sin(theta)*radius*distance,pivot[2]+math.cos(theta)*radius*distance]
            m.beam(f'frill_membrane_{side}_{i}',pivot,fan_point(1),.42,3.78,rib,4)
            m.beam(f'frill_vein_{side}_{i}',fan_point(.08,.24),fan_point(.89,.24),.27,.42,rib,7)
            m.beam(f'frill_rim_{side}_{i}',fan_point(.79),fan_point(1),.56,3.83,rib,7)
            if i%3!=0:
                m.beam(f'frill_spot_{side}_{i}',fan_point(.52+(i%2)*.07,.28),fan_point(.71+(i%2)*.06,.28),.24,1.42,rib,3)
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
    m=Model('ark_mosasaurus','Mosasaurus',['#53686a','#43585a','#afbaab','#3a4543','#b8a468','#172322','#e6d9b7','#6b7772'],
            '24 Meter langer Meeresräuber · keilförmiger Krokodilschädel · niedrige Rückenschilde und kräftige Ruderflossen')
    m.bone('body',[0,24,0])
    m.profile('trunk',[(-68,19.8,9,37.5),(-50,26,3.5,43),(-26,31.5,.7,48.2),(0,33.2,-.2,49.1),(25,32.8,.6,48.3),(48,30.2,2.4,45.9),(68,25,6.7,40.8),(85,17.7,11,34.8)],belly=2)
    for i,(z,top) in enumerate([(-47,43.7),(-33,46.5),(-18,48.6),(-2,49.2),(14,49),(30,47.9),(45,46.3),(59,43.2),(71,40)]):
        for sign in [-1,1]:
            x=sign*(7.3 if i<7 else 5.4)
            m.beam(f'back_scute_{sign}_{i}',[x,top-.2,z-4],[x,top+1.7,z+4.3],4.5 if i<7 else 3.4,2.8,color=1)
    m.bone('neck',[0,27,-57],'body');m.profile('neck',[(-95,16.4,12.4,34.8),(-80,21.5,8.8,38.7),(-64,22.2,8.1,40.5),(-55,18.9,10.2,38)],'neck',0,2)
    m.bone('head',[0,29,-86],'neck')
    m.profile('skull',[(-115,14.4,17.8,32.7),(-105,19,16,36.3),(-95,23.2,15.4,39.1),(-83,22.6,15.3,38.1),(-77,17.1,17.3,34.8)],'head')
    m.profile('snout',[(-139,10.8,17.9,28),(-128,12.6,17.8,29.9),(-116,14.7,17.8,32.8),(-106,17.7,17.8,34.7)],'head')
    m.profile('snout_tip',[(-152,7.7,18,24.5),(-147,10.2,17.9,27.3),(-137,11.2,17.9,28.5)],'head')
    for side,sign in [('left',-1),('right',1)]:
        m.beam('cheek_'+side,[sign*19.5,28,-104],[sign*20.1,18.8,-83],5.8,6.6,'head',1)
        m.beam('brow_'+side,[sign*19.5,33.5,-105],[sign*21.5,36.1,-94],4,4.8,'head',1)
        m.cube('nostril_'+side,[sign*9.7-.6,26.1,-132.5],[sign*9.7+.6,28.15,-128.4],'head',5)
    m.eyes(21.25,29.6,-102.1,size=2.6)
    m.bone('jaw',[0,16,-83],'head')
    m.profile('lower_jaw',[(-114,12.5,8.8,16),(-103,16.7,7,16.1),(-91,18.5,7.3,16.7),(-79.7,14.2,10.3,18.3)],'jaw',0,2)
    m.profile('jaw_snout',[(-151,7.7,10.4,16),(-142,10.1,9,16),(-127,11.5,8,16),(-109,13.7,8.7,16)],'jaw',0,2)
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
            points=[pivot,[sign*(abs(pivot[0])+length*.37),10.7,z+5],[sign*(abs(pivot[0])+length*.76),7.7,z+16],[sign*(abs(pivot[0])+length),6,z+33]]
            for i,(a,b,w) in enumerate(zip(points,points[1:],[16,15.5,7.2])):
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
    for lobe,points in [('upper',[(18,200),(31,207),(43,217),(54.7,228.2)]),
                        ('lower',[(18,200),(3,207),(-12,217),(-24,228.6)])]:
        for i,((y,z),(ey,ez),w,d) in enumerate(zip(points,points[1:],[5,3.8,2.5],[22,16,10])):
            m.beam(f'tail_fin_{lobe}{i}',[0,y,z],[0,ey,ez],w,d,'tail_tip',0 if i==0 else 1)
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
    models=[trex(),triceratops(),stegosaurus(),brachiosaurus(),velociraptor(),gallimimus(),pteranodon(),dilophosaurus(),mosasaurus()]
    for m in models:
        m.export();print(m.name,len(m.elements),'cubes',len(m.groups),'bones')
    (ROOT/'LICENSE.txt').write_text('Original dinosaur geometry, textures, animations and preview generators: CC0 1.0 Universal.\nhttps://creativecommons.org/publicdomain/zero/1.0/\nCreated for this project, 2026-11-07. No third-party dinosaur assets.\n')
