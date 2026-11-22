"""Prepare append-only map pixels and an explicitly invoked native sign migration."""
from copy import deepcopy
import gzip
from io import BytesIO
from hashlib import sha256
import json
from pathlib import Path
import struct
from uuid import UUID
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

HERE = Path(__file__).resolve().parent
NS = 'ark_gate_ps5_261122'
DIM = 'minecraft:overworld'


def snbt(value, key=''):
    if key in {'shadow','see_through','bold','text_opacity','Facing','ItemRotation'}:
        return str(int(value))+'b'
    if isinstance(value, bool): return '1b' if value else '0b'
    if isinstance(value, dict): return '{'+','.join(json.dumps(k)+':'+snbt(v,k) for k,v in value.items())+'}'
    if isinstance(value, list):
        if key == 'UUID': return '[I;'+','.join(map(str,value))+']'
        if key == 'Pos': return '['+','.join(str(float(x))+'d' for x in value)+']'
        if key in {'scale','translation','left_rotation','right_rotation'}:
            return '['+','.join(format(x,'.9g')+'f' for x in value)+']'
        return '['+','.join(snbt(v) for v in value)+']'
    return json.dumps(value,ensure_ascii=False)


def map_file(colors):
    if len(colors) != 16384: raise ValueError('Expected 128 x 128 native colors')
    def utf(s):
        raw=s.encode('utf-8');return struct.pack('>H',len(raw))+raw
    def tag(kind,name,value): return bytes([kind])+utf(name)+value
    data = (tag(1,'scale',b'\0') + tag(1,'locked',b'\1') + tag(3,'xCenter',struct.pack('>i',0))
            + tag(3,'zCenter',struct.pack('>i',0)) + tag(8,'dimension',utf(DIM))
            + tag(1,'trackingPosition',b'\0') + tag(1,'unlimitedTracking',b'\0')
            + tag(7,'colors',struct.pack('>i',len(colors))+colors) + b'\0')
    root=b'\x0a\0\0'+tag(3,'DataVersion',struct.pack('>i',5023))+tag(10,'data',data)+b'\0'
    buffer=BytesIO()
    with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,compresslevel=6,mtime=0) as stream:
        stream.write(root)
    return buffer.getvalue()


def read_map(blob):
    stream=BytesIO(gzip.decompress(blob))
    def number(code):return struct.unpack('>'+code,stream.read(struct.calcsize('>'+code)))[0]
    def string():return stream.read(number('H')).decode('utf-8')
    def value(kind):
        if 1<=kind<=6:return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7:return stream.read(number('i'))
        if kind==8:return string()
        if kind==9:
            child=number('B');return [value(child) for _ in range(number('i'))]
        if kind==10:
            result={}
            while (child:=number('B')):
                name=string();result[name]=value(child)
            return result
        if kind in (11,12):return [number('i' if kind==11 else 'q') for _ in range(number('i'))]
        raise ValueError('Unknown native map NBT type')
    assert number('B')==10
    string();root=value(10)
    assert not stream.read(1)
    return root['data']


def frame_nbt(tile, tag):
    x,y,z=tile['position']
    return {'UUID':list(struct.unpack('>4i',UUID(tile['uuid']).bytes)), 'Tags':[tag],
            'Pos':[x+.5,y+.5,z+.03125], 'Facing':3, 'Fixed':True, 'Invisible':True,
            'Invulnerable':True, 'ItemRotation':0,
            'Item':{'id':'minecraft:filled_map','components':{'minecraft:map_id':tile['mapId']}}}


def exact(source, values, key):
    lines=[f'execute unless data {source} run return 0']
    for i,value in enumerate(values):
        lines += [f'data modify storage {NS}:tmp work.value set value '+snbt(value,key),
                  f'execute store success storage {NS}:tmp work.diff{i} byte 1 run data modify storage {NS}:tmp work.value set from {source}']
    predicate='{work:{'+','.join(f'diff{i}:1b' for i in range(len(values)))+'}}'
    return lines+[f'execute if data storage {NS}:tmp {predicate} run return 0']


def functions(spec, baseline):
    cells={tuple(p[:3]):p[3] for p in baseline['cells']}
    chunks=sorted({(x//16,z//16) for x,y,z in cells})
    assert len(cells)==5211 and len(baseline['displays'])==10
    assert spec['background']=='opaque' and spec['backgroundColor']==84
    assert len(spec['tiles'])==56
    result={}
    for stage in ('before','ready','after'):
        lines=[f'execute unless dimension {DIM} run return 0']
        lines += [f'execute unless loaded {x*16} 114 {z*16} run return 0' for x,z in chunks]
        lines += [f'execute unless block {x} {y} {z} {state} run return 0' for (x,y,z),state in sorted(cells.items())]
        base=list(lines)
        for uid,old in baseline['displays']:
            target=deepcopy(old);target['text']=''
            states=[old] if stage=='before' else [target] if stage=='after' else [old,target]
            lines += [f'execute unless entity {uid} run return 0',
                      f'execute as {uid} unless entity @s[type=minecraft:text_display,tag={spec["legacyTextDisplays"]["tag"]}] run return 0',
                      'execute '+' '.join(f'unless data entity {uid} '+snbt(state) for state in states)+' run return 0']
            for key in ('text','Pos'):
                lines += exact(f'entity {uid} {key}',[state[key] for state in states],key)
            lines += exact(f'entity {uid} transformation.scale',[old['transformation']['scale']],'scale')
        frame_start=len(lines)
        for tile in spec['tiles']:
            uid=tile['uuid'];nbt=frame_nbt(tile,spec['tag'])
            x,y,z=tile['position']
            assert cells[x,y,z]=='minecraft:air'
            # Bedrock has one hanging frame per voxel. Preserve any foreign frame in that cell.
            area=f'x={x},y={y},z={z},dx=0,dy=0,dz=0'
            own_uuid=snbt(nbt['UUID'],'UUID')
            lines += [f'execute if entity @e[type=minecraft:item_frame,{area}] run return 0',
                      f'execute as @e[type=minecraft:glow_item_frame,{area}] unless entity @s[nbt={{UUID:{own_uuid}}}] run return 0']
            # Byte-valued native frame fields must be bytes in exact predicates too.
            predicate=snbt(nbt)
            if stage=='before':
                lines.append(f'execute if entity {uid} run return 0')
            else:
                if stage=='after':lines.append(f'execute unless entity {uid} run return 0')
                lines += [f'execute if entity {uid} as {uid} unless entity @s[type={spec["frameType"]},tag={spec["tag"]}] run return 0',
                          f'execute if entity {uid} unless data entity {uid} {predicate} run return 0']
                # Pos has duplicate values in some frames; native predicates do not preserve list order.
                lines += [f'execute if entity {uid} run function {NS}:position_{tile["mapId"]}']
        lines.append('return 1');result[stage]=lines
        if stage=='after':result['frames']=base+lines[frame_start:]
    # A separately returned position failure must propagate to each caller.
    for tile in spec['tiles']:
        uid=tile['uuid']
        result[f'position_{tile["mapId"]}']=exact(f'entity {uid} Pos',[frame_nbt(tile,spec['tag'])['Pos']],'Pos')+['return 1']
        old=f'execute if entity {uid} run function {NS}:position_{tile["mapId"]}'
        replacement=f'execute if entity {uid} unless function {NS}:position_{tile["mapId"]} run return 0'
        for stage in ('ready','after','frames'):result[stage]=[replacement if line==old else line for line in result[stage]]
    apply=[f'execute unless function {NS}:ready run return 0']
    for tile in spec['tiles']:
        nbt=frame_nbt(tile,spec['tag']);nbt.pop('Pos')
        nbt['Item']['count']=1
        native=snbt(nbt)
        apply.append(f'execute unless entity {tile["uuid"]} run summon {spec["frameType"]} '+ ' '.join(map(str,tile['position']))+' '+native)
    apply += [f'execute unless function {NS}:frames run return 0']
    apply += [f'data modify entity {uid} text set value ""' for uid,old in baseline['displays']]
    apply.append(f'return run function {NS}:after');result['apply']=apply
    return result,chunks


def prepare():
    spec=json.loads((HERE/'generated/manifest.json').read_bytes())
    baseline=json.loads((HERE/'expected-v5.json').read_bytes())
    files={}
    out=HERE/'payload';out.mkdir(exist_ok=True)
    for tile in spec['tiles']:
        colors=(HERE/'generated'/tile['file']).read_bytes()
        assert sha256(colors).hexdigest()==tile['sha256']
        target=out/f'{tile["mapId"]}.dat';target.write_bytes(map_file(colors))
        files[target.name]=sha256(target.read_bytes()).hexdigest()
    funcs,chunks=functions(spec,baseline)
    archive=out/(NS+'.zip')
    content={'pack.mcmeta':json.dumps({'pack':{'description':'Crossplay Jurassic gate map lettering','min_format':[121,0],'max_format':[121,1]}})}
    content.update({f'data/{NS}/function/{name}.mcfunction':'\n'.join(lines)+'\n' for name,lines in funcs.items()})
    with ZipFile(archive,'w') as z:
        for name,value in sorted(content.items()):
            info=ZipInfo(name,date_time=(2026,11,22,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,value)
    manifest={'namespace':NS,'chunks':chunks,'tiles':spec['tiles'],'mapFiles':files,
              'pack_sha256':sha256(archive.read_bytes()).hexdigest(),
              'source_manifest_sha256':sha256((HERE/'generated/manifest.json').read_bytes()).hexdigest()}
    (out/'manifest.json').write_bytes((json.dumps(manifest,indent=2)+'\n').encode('utf-8'))
    print(json.dumps({'tiles':len(files),'pack':manifest['pack_sha256'],'functions':len(funcs)}))


if __name__=='__main__':prepare()
