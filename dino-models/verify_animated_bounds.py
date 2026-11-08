"""Independent corner transforms at keyframes and intermediate linear rotations."""
from itertools import product
from pathlib import Path
import json, math

ROOT=Path(__file__).resolve().parent

def transform(point, pivot, angles):
    x,y,z=[a-b for a,b in zip(point,pivot)]
    rx,ry,rz=map(math.radians,angles)
    # Explicit matrix, independent of the exporter rotation helper.
    cx,sx,cy,sy,cz,sz=math.cos(rx),math.sin(rx),math.cos(ry),math.sin(ry),math.cos(rz),math.sin(rz)
    return [pivot[0]+cy*cz*x+(sx*sy*cz-cx*sz)*y+(cx*sy*cz+sx*sz)*z,
            pivot[1]+cy*sz*x+(sx*sy*sz+cx*cz)*y+(cx*sy*sz-sx*cz)*z,
            pivot[2]-sy*x+sx*cy*y+cx*cy*z]

def check(name):
    folder=ROOT/'models'/name
    model=json.loads((folder/(name+'.bbmodel')).read_text())
    desc=json.loads((folder/(name+'.geo.json')).read_text())['minecraft:geometry'][0]['description']
    groups={g['uuid']:g for g in model['groups']};owners={};parents={}
    def visit(node,parent=None):
        if isinstance(node,str):owners[node]=parent;return
        parents[node['uuid']]=parent
        for child in node['children']:visit(child,node['uuid'])
    for node in model['outliner']:visit(node)
    low=[float('inf')]*3;high=[-float('inf')]*3;count=0
    for animation in model['animations']:
        tracks=animation['animators'];times={0.,animation['length']}
        for track in tracks.values():
            for channel in {k['channel'] for k in track['keyframes']}:
                frames=[k for k in track['keyframes'] if k['channel']==channel]
                for a,b in zip(frames,frames[1:]):
                    span=max(abs(float(a['data_points'][0][k])-float(b['data_points'][0][k])) for k in 'xyz')
                    steps=max(1,math.ceil(span/.25))
                    times.update(a['time']+(b['time']-a['time'])*i/steps for i in range(steps+1))
        def sample(uid,t,channel):
            if uid not in tracks:return [1,1,1] if channel=='scale' else [0,0,0]
            frames=[k for k in tracks[uid]['keyframes'] if k['channel']==channel]
            if not frames:return [1,1,1] if channel=='scale' else [0,0,0]
            for a,b in zip(frames,frames[1:]):
                if a['time']<=t<=b['time']:
                    f=(t-a['time'])/(b['time']-a['time'])
                    return [float(a['data_points'][0][k])*(1-f)+float(b['data_points'][0][k])*f for k in 'xyz']
            raise AssertionError((uid,t))
        for t in sorted(times):
            for cube in model['elements']:
                for point in product(*zip(cube['from'],cube['to'])):
                    point=transform(point,cube['origin'],cube['rotation'])
                    uid=owners[cube['uuid']]
                    while uid:
                        point=[origin+(value-origin)*scale for origin,value,scale in zip(groups[uid]['origin'],point,sample(uid,t,'scale'))]
                        point=transform(point,groups[uid]['origin'],sample(uid,t,'rotation'));point=[a+b for a,b in zip(point,sample(uid,t,'position'))];uid=parents[uid]
                    for axis,value in enumerate(point):low[axis]=min(low[axis],value/16);high[axis]=max(high[axis],value/16)
                    count+=1
    offset=desc['visible_bounds_offset'];half=[desc['visible_bounds_width']/2,desc['visible_bounds_height']/2,desc['visible_bounds_width']/2]
    for axis in range(3):
        assert offset[axis]-half[axis]<=low[axis] and high[axis]<=offset[axis]+half[axis], (name,'animated geometry outside culling box',axis,low,high,desc)
    return dict(name=name,min=low,max=high,corner_samples=count,maximum_rotation_step_degrees=.25)

if __name__=='__main__':
    result=[check(p.name) for p in sorted((ROOT/'models').iterdir()) if p.is_dir()]
    (ROOT/'animated-bounds.json').write_text(json.dumps(result,indent=2)+'\n')
    print('PASS animated bounds:',len(result),'models',sum(r['corner_samples'] for r in result),'corner samples')
