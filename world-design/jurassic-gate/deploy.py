"""Publish new maps, replace only the gate lettering, then remove temporary native tools."""
import fcntl
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

from prepare import read_map

ROOT=Path('/srv/minecraft-ark')
STAGE=ROOT/'staging/gate-ps5-20261122'
MAPS=ROOT/'data/ark/data/minecraft/maps'
NS='ark_gate_ps5_261122'
PACK=ROOT/f'data/ark/datapacks/{NS}.zip'
DIM='minecraft:overworld'


def regular(path):
    assert not any(p.is_symlink() for p in (path,*path.parents)), 'Symlink refused'
    assert path.is_file(), 'Expected regular file: '+str(path)
    return path


def digest(path):return sha256(regular(path).read_bytes()).hexdigest()


def command(args):
    return subprocess.run(args,cwd=ROOT,check=True,capture_output=True,text=True,timeout=120).stdout.strip()


def rcon(text):
    return re.sub(r'\x1b\[[0-9;]*m','',command(['docker','exec','minecraft-ark','rcon-cli',text]))


def native(name):
    rcon(f'minecraft:data modify storage {NS}:tmp proof set value -7')
    rcon(f'minecraft:execute in {DIM} store result storage {NS}:tmp proof int 1 run function {NS}:{name}')
    response=rcon(f'minecraft:data get storage {NS}:tmp proof')
    match=re.search(r'following contents: ([01])$',response)
    assert match,response
    return match[1]=='1'


def publish(source,target,expected):
    blob=regular(source).read_bytes();assert sha256(blob).hexdigest()==expected
    assert not any(p.is_symlink() for p in (target,*target.parents))
    descriptor,name=tempfile.mkstemp(prefix='.'+target.name+'.gate-new-',dir=target.parent)
    temporary=Path(name)
    try:
        with os.fdopen(descriptor,'wb') as stream:
            stream.write(blob);stream.flush();os.fsync(stream.fileno())
        os.chown(temporary,1000,1000);temporary.chmod(0o644)
        os.link(temporary,target)  # Existing native player files are never overwritten.
    finally:
        temporary.unlink(missing_ok=True)


def deploy(mode,pin):
    assert digest(STAGE/'manifest.json')==pin,'Reviewed manifest changed'
    manifest=json.loads((STAGE/'manifest.json').read_bytes())
    assert manifest['namespace']==NS and len(manifest['mapFiles'])==len(manifest['tiles'])==56
    assert {f'{tile["mapId"]}.dat' for tile in manifest['tiles']}==set(manifest['mapFiles'])
    assert all(9221100<=tile['mapId']<=9221174 for tile in manifest['tiles'])
    for name,want in manifest['mapFiles'].items():assert digest(STAGE/name)==want
    assert digest(STAGE/PACK.name)==manifest['pack_sha256']
    receipt=STAGE/'live-receipt.json'
    journal=json.loads(receipt.read_bytes()) if receipt.exists() else {
        'manifest_sha256':pin,'ownedChunks':[],'ownedMapFiles':[],'ownsPack':False,'ownsStorage':False,
        'complete':False,'preflight':False,'newBackups':False}
    assert journal['manifest_sha256']==pin,'Another deployment owns this receipt'
    def save():
        temporary=receipt.with_suffix('.json.new')
        with temporary.open('w') as stream:
            stream.write(json.dumps(journal,indent=2)+'\n');stream.flush();os.fsync(stream.fileno())
        temporary.replace(receipt)
    def check_map(name):
        target=MAPS/name
        assert name in journal['ownedMapFiles'],'Existing map belongs to another owner: '+name
        data=read_map(regular(target).read_bytes())
        expected=next(t for t in manifest['tiles'] if f'{t["mapId"]}.dat'==name)
        assert sha256(data['colors']).hexdigest()==expected['sha256'],'Native map pixels changed'
        assert data.get('scale',0)==0 and data.get('locked')==1 and data['dimension']==DIM
        assert not data.get('trackingPosition',False)
    # Never ask Bukkit for a future map ID: even a failed lookup is cached.
    for name in manifest['mapFiles']:
        if (MAPS/name).exists():check_map(name)
    if (MAPS/'last_id.dat').exists():
        assert read_map(regular(MAPS/'last_id.dat').read_bytes())['map']<9221100,'Map allocator reached reserved range'
    info=json.loads(command(['docker','inspect','minecraft-ark']))[0]
    assert info['State']['Running'] and info['State']['Health']['Status']=='healthy'
    assert [m['Source'] for m in info['Mounts'] if m['Destination']=='/data']==[str(ROOT/'data')]
    if 'containerId' in journal:assert journal['containerId']==info['Id'],'Live container replaced'
    journal['containerId']=info['Id'];save()
    def forced(x,z):
        text=rcon(f'minecraft:execute in {DIM} run forceload query {16*x} {16*z}')
        if 'not marked for force loading' in text:return False
        assert 'is marked for force loading' in text,text
        return True
    def wait(check,message):
        for _ in range(75):
            if check():return
            time.sleep(1)
        raise RuntimeError(message)
    def cleanup():
        if journal['ownsPack'] and PACK.exists():
            assert digest(PACK)==manifest['pack_sha256'],'Operation pack was changed'
            rcon(f'minecraft:datapack disable "file/{NS}.zip"')
            wait(lambda:'Unknown function' in rcon(f'minecraft:function {NS}:after'),'Pack still enabled')
            regular(PACK).unlink();journal['ownsPack']=False;save()
        for x,z in list(journal['ownedChunks']):
            if forced(x,z):rcon(f'minecraft:execute in {DIM} run forceload remove {16*x} {16*z}')
            assert not forced(x,z)
            journal['ownedChunks'].remove([x,z]);save()
        if journal['ownsStorage']:
            rcon(f'minecraft:data remove storage {NS}:tmp work');rcon(f'minecraft:data remove storage {NS}:tmp proof')
            assert rcon(f'minecraft:data get storage {NS}:tmp').endswith('{}')
        assert not PACK.exists()
    try:
        if not journal['ownsStorage']:
            assert rcon(f'minecraft:data get storage {NS}:tmp').endswith('{}')
            journal['ownsStorage']=True;save()
        if PACK.exists():
            assert journal['ownsPack'] and digest(PACK)==manifest['pack_sha256']
        else:
            journal['ownsPack']=True;save();publish(STAGE/PACK.name,PACK,manifest['pack_sha256'])
        rcon('minecraft:datapack list available')
        answer=rcon(f'minecraft:datapack enable "file/{NS}.zip"')
        assert any(word in answer.lower() for word in ('enabl','already')),answer
        wait(lambda:'Unknown function' not in rcon(f'minecraft:function {NS}:before'),'Native pack did not load')
        for x,z in manifest['chunks']:
            if not forced(x,z):
                if [x,z] not in journal['ownedChunks']:journal['ownedChunks'].append([x,z]);save()
                rcon(f'minecraft:execute in {DIM} run forceload add {16*x} {16*z}')
        wait(lambda:all('Test passed' in rcon(f'minecraft:execute in {DIM} if loaded {16*x} 114 {16*z}')
                        for x,z in manifest['chunks']),'Gate chunks did not load')
        if not journal['preflight']:
            assert native('before'),'Original gate/labels or reserved frame UUIDs differ'
            journal['preflight']=True;save()
        assert native('ready'),'Gate or partial migration differs from approved state'
        if mode=='--check':
            print('Native preflight passed. Map files, blocks and labels unchanged.',flush=True)
            return
        assert mode=='--apply'
        assert not any(p.is_symlink() for p in (MAPS,*MAPS.parents))
        missing=[];parent=MAPS
        while not parent.exists():missing.append(parent);parent=parent.parent
        for directory in reversed(missing):
            directory.mkdir();os.chown(directory,1000,1000);directory.chmod(0o755)
        for name,want in manifest['mapFiles'].items():
            if not (MAPS/name).exists():
                if name not in journal['ownedMapFiles']:journal['ownedMapFiles'].append(name);save()
                publish(STAGE/name,MAPS/name,want)
            check_map(name)
        journal['mapsPublished']=True;save()
        assert native('apply'),'Native map frames or text migration failed'
        time.sleep(2)
        assert native('after'),'Settled sign failed verification'
        assert 'Saved the game' in rcon('minecraft:save-all flush')
        for name in manifest['mapFiles']:check_map(name)
        journal.update(complete=True,frames=56,clearedLegacyTexts=10,verifiedCells=5211,
                       finishedAt=time.time(),players=rcon('list'));save()
        print('56 map frames and 10 cleared texts verified; 5211 gate cells preserved.',flush=True)
    except BaseException as error:
        journal['error']=str(error);save();raise
    finally:
        cleanup()


if __name__=='__main__':
    assert len(sys.argv)==3 and sys.argv[1] in ('--check','--apply')
    with (ROOT/'maintenance/update.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        deploy(*sys.argv[1:])
