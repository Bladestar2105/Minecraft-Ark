"""Verify the compiled fifteen-model Bridge pack against its installed source exports."""
import base64
import hashlib
import json
from pathlib import Path
import sys
import uuid
import zipfile

root=Path(__file__).resolve().parents[1]
species=('trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus')
expected=['ark_'+name for name in species]+['ark_egg_'+name for name in species if name!='mosasaurus']
path=Path(sys.argv[1])
with zipfile.ZipFile(path) as pack:
    names=pack.namelist()
    assert len(names)==len(set(names)), 'Duplicate Bedrock resource paths'
    manifest=json.loads(pack.read('manifest.json'))
    assert manifest['format_version']==2
    header=uuid.UUID(manifest['header']['uuid'])
    assert manifest['header']['version'] and manifest['header']['min_engine_version']
    modules=manifest['modules']
    assert modules and all(module['type']=='resources' for module in modules)
    assert all(uuid.UUID(module['uuid'])!=header for module in modules)
    geometries={}
    for name in names:
        if name.startswith('models/') and name.endswith('.json'):
            for geometry in json.loads(pack.read(name))['minecraft:geometry']:
                identifier=geometry['description']['identifier']
                assert identifier not in geometries, f'Duplicate geometry {identifier}'
                geometries[identifier]=geometry
    entities=[name for name in names if name.startswith('entity/') and name.endswith('.json')]
    assert set(entities)=={f'entity/{name}.json' for name in expected}, 'Expected exactly eight dinosaurs and seven placed eggs'
    checked=0
    for name in expected:
        directory=root/('dino-items' if name.startswith('ark_egg_') else 'dino-models')/'models'/name
        source=json.loads((directory/f'{name}.geo.json').read_text())['minecraft:geometry'][0]
        entity=json.loads(pack.read(f'entity/{name}.json'))['minecraft:client_entity']['description']
        assert entity['identifier']==f'modelengine:{name}'
        normal=[identifier for key,identifier in entity['geometry'].items() if not key.endswith('_tint')]
        assert len(normal)==1
        for identifier in entity['geometry'].values():
            assert identifier in geometries, f'{name}: missing geometry {identifier}'
        assert geometries[normal[0]]['bones']==source['bones'], f'{name}: compiled geometry differs from source'
        for texture in entity['textures'].values():
            assert texture+'.png' in names, f'{name}: missing texture {texture}'
        bbmodel=json.loads((directory/f'{name}.bbmodel').read_text())
        source_png=base64.b64decode(bbmodel['textures'][0]['source'].split(',',1)[1],validate=True)
        assert pack.read(entity['textures']['default']+'.png')==source_png, f'{name}: compiled texture differs from source'
        animations=json.loads(pack.read(f'animations/{name}.json'))['animations']
        original=json.loads((directory/f'{name}.animation.json').read_text())['animations']
        for clip,animation in original.items():
            assert animations[f'animation.{name}.{clip}']==animation, f'{name}/{clip}: compiled animation differs from source'
        assert json.loads(pack.read(f'animation_controllers/{name}.json'))['animation_controllers']
        assert json.loads(pack.read(f'render_controllers/{name}.json'))['render_controllers']
        checked+=1
    assert checked==15
print(json.dumps(dict(ok=True,models=checked,geometries=len(geometries),files=len(names),sha256=hashlib.sha256(path.read_bytes()).hexdigest())))
