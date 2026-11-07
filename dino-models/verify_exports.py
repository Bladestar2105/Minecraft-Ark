"""Small asset checks: references, geometry parity, rest grounding and species features."""
import base64, hashlib, json, math, struct, zlib
from build_models import ROOT, vertices

expected={'ark_trex','ark_triceratops','ark_stegosaurus','ark_brachiosaurus','ark_velociraptor','ark_pteranodon'}
assert {p.name for p in (ROOT/'models').iterdir() if p.is_dir()}==expected
summary=[]
for name in sorted(expected):
    p=ROOT/'models'/name;m=json.loads((p/(name+'.bbmodel')).read_text());design=json.loads((p/'design.json').read_text())
    geo=json.loads((p/(name+'.geo.json')).read_text())['minecraft:geometry'][0];anims=json.loads((p/(name+'.animation.json')).read_text())['animations']
    cubes=m['elements'];groups={g['name']:g for g in m['groups']};bones={g['name']:g for g in geo['bones']}
    assert len(cubes)==design['cubes']==sum(len(b['cubes']) for b in bones.values())
    assert len(groups)==design['bones']==len(bones)
    assert len({c['name'] for c in cubes})==len(cubes) and len({c['uuid'] for c in cubes})==len(cubes)
    assert set(groups)==set(bones)
    ownership={};parents={}
    def visit(node,parent=None):
        if isinstance(node,str):
            assert node not in ownership;ownership[node]=parent;return
        assert node['uuid'] not in parents;parents[node['uuid']]=parent
        for child in node['children']:visit(child,node['uuid'])
    for node in m['outliner']:visit(node)
    assert set(ownership)=={c['uuid'] for c in cubes}
    assert set(parents)=={g['uuid'] for g in groups.values()}
    for g in groups.values():
        b=bones[g['name']];assert b['pivot']==[-g['origin'][0],*g['origin'][1:]]
        if b.get('parent'):assert groups[b['parent']]['uuid']==parents[g['uuid']]
        sources=[c for c in cubes if ownership[c['uuid']]==g['uuid']]
        assert len(sources)==len(b['cubes'])
        for c,q in zip(sources,b['cubes']):
            assert all(math.isfinite(v) for k in ['from','to','origin','rotation'] for v in c[k])
            assert all(b>a for a,b in zip(c['from'],c['to']))
            assert q['origin']==[-c['to'][0],c['from'][1],c['from'][2]]
            assert q['size']==[b-a for a,b in zip(c['from'],c['to'])]
            if any(c['rotation']):assert q['rotation']==[-c['rotation'][0],-c['rotation'][1],c['rotation'][2]] and q['pivot']==[-c['origin'][0],*c['origin'][1:]]
            assert set(c['faces'])=={'north','south','west','east','up','down'}
            assert all(f['texture']==0 for f in c['faces'].values())
    points=[v for c in cubes for v in vertices(c)];minimum=min(v[1] for v in points)
    assert abs(minimum)<1e-6,minimum
    foot=[c for c in cubes if c['name'] in design['ground_parts']]
    assert foot and abs(min(v[1] for c in foot for v in vertices(c)))<1e-6
    assert all(min(v[1] for v in vertices(c))>=-1e-6 for c in foot)
    names={c['name'] for c in cubes}
    expected_anims={'idle','flap','glide'} if name=='ark_pteranodon' else {'idle','walk'}
    assert {a['name'] for a in m['animations']}==expected_anims
    for a in m['animations']:
        assert a['loop']=='loop';ba=anims[a['name']];assert ba['loop'] and ba['animation_length']==a['length']
        for uid,track in a['animators'].items():
            assert uid in parents and track['name'] in groups
            k=track['keyframes'];assert k[0]['data_points']==k[-1]['data_points'] and k[0]['time']==0 and k[-1]['time']==a['length']
            for frame in k:
                assert 0<=frame['time']<=a['length']
                v=[float(frame['data_points'][0][axis]) for axis in 'xyz'];assert all(math.isfinite(n) for n in v)
                if frame['channel'] in ['rotation','position']:v[0]*=-1
                if frame['channel']=='rotation':v[1]*=-1
                assert ba['bones'][track['name']][frame['channel']][str(frame['time'])]==v
    source=m['textures'][0]['source'];assert source.startswith('data:image/png;base64,')
    tex=base64.b64decode(source.split(',',1)[1],validate=True)
    assert tex[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II5B',tex[16:29])==(64,64,8,6,0,0,0)
    offset=8;compressed=b'';chunks=[]
    while offset<len(tex):
        size=struct.unpack('>I',tex[offset:offset+4])[0];kind=tex[offset+4:offset+8];data=tex[offset+8:offset+8+size]
        assert struct.unpack('>I',tex[offset+8+size:offset+12+size])[0]==zlib.crc32(kind+data),'Invalid embedded PNG checksum'
        chunks.append(kind)
        if kind==b'IDAT':compressed+=data
        offset+=size+12
    assert offset==len(tex) and chunks==[b'IHDR',b'IDAT',b'IEND']
    row=b'\0'+b''.join(bytes.fromhex(design['palette'][x//8][1:])+b'\xff' for x in range(64))
    assert zlib.decompress(compressed)==row*64,'Embedded PNG pixels differ from design palette'
    external=p/(name+'.png')
    if external.exists():assert external.read_bytes()==tex,'External texture mismatch: '+name
    if name=='ark_trex':
        assert not any(n.startswith('foot_') for n in names)
        for side in ['left','right']:
            assert {f'toe_{side}_{i}_base' for i in range(3)}<=names
            heel=next(c for c in cubes if c['name']=='raised_heel_'+side)
            assert max(v[1] for v in vertices(heel))>4.5
            claws=[c for c in cubes if c['name'].startswith('claw_'+side+'_')];assert len(claws)==3
            centers=[]
            for c in claws:
                v=vertices(c);assert c['to'][0]-c['from'][0]<=.61
                centers.append(sum(p[0] for p in v)/8)
            assert min(abs(a-b) for i,a in enumerate(centers) for b in centers[i+1:])>2
    if name=='ark_triceratops':assert {'brow_horn_-10','brow_horn_10','nose_horn0','frill_top'}<=names
    if name=='ark_stegosaurus':assert len([n for n in names if n.startswith('plate_') and n.endswith('_top')])==8 and len([n for n in names if n.startswith('tail_spike_')])==12
    if name=='ark_brachiosaurus':assert max(v[1] for v in points)>85 and {'neck_lower','neck_upper'}<=names
    if name=='ark_velociraptor':assert len([n for n in names if n.startswith('sickle_')])==4 and len([n for n in names if n.startswith('feather_')])==6
    if name=='ark_pteranodon':assert len([n for n in names if n.startswith('wing_finger_')])==8 and len([n for n in names if n.startswith('membrane_') and not n.startswith('membrane_trailing')])==16 and max(v[0] for v in points)-min(v[0] for v in points)>125
    summary.append(dict(name=name,label=design['label'],cubes=len(cubes),bones=len(bones),animations=sorted(expected_anims),rest_ground_y=round(minimum,6),height=round(max(v[1] for v in points),2),sha256=hashlib.sha256((p/(name+'.bbmodel')).read_bytes()).hexdigest()))
report=dict(ok=True,models=summary,checks=['Six species with distinctive geometry','Java/Bedrock cube, bone, texture and animation parity','Valid finite dimensions and references','Rest pose grounded; no shape below floor','T-Rex: three separate splayed toes per foot, raised heel, small claws','Continuous looping animations','Embedded PNG dimensions, checksums and palette pixels; optional external texture parity'],limits=['Asset source checks; actual engine compilation is recorded separately in output/dino-assets-runtime-20261107/.','Walk cycles are simple hip rotations without inverse kinematics or planted-foot correction.','Pteranodon includes wing flap/glide asset animations, no flight controller, taming, saddle or passenger seat.','No console client, collision boxes, mounted riding or combat tested.'])
(ROOT/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS',len(summary),'species; geometry/animation parity, planted rest feet, distinctive features and embedded textures')
