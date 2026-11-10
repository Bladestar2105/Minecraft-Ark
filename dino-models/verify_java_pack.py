"""Check the compiled BetterModel Java pack against Minecraft's cuboid limits."""
import json
import math
import sys
import zipfile


with zipfile.ZipFile(sys.argv[1]) as pack:
    names=pack.namelist()
    assert len(names)==len(set(names)), 'Duplicate resource-pack paths'
    models=cuboids=0
    for name in names:
        if not name.startswith('assets/') or '/models/' not in name or not name.endswith('.json'):
            continue
        model=json.loads(pack.read(name));models+=1
        for index,element in enumerate(model.get('elements',[])):
            cuboids+=1
            for field in ('from','to'):
                values=element[field]
                assert len(values)==3 and all(math.isfinite(v) and -16<=v<=32 for v in values), \
                    f'{name} element {index} {field} exceeds Minecraft [-16,32]: {values}'
        for texture in model.get('textures',{}).values():
            if texture.startswith('#'):
                continue
            namespace,path=texture.split(':',1) if ':' in texture else ('minecraft',texture)
            if namespace!='minecraft':
                assert f'assets/{namespace}/textures/{path}.png' in names, f'{name}: missing texture {texture}'
    assert models and cuboids, 'No compiled Java cuboids were checked'
    print(json.dumps(dict(ok=True,models=models,cuboids=cuboids)))
