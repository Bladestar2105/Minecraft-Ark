"""Sample the exported gait: a planted stance and a lifted return, without new rig bones."""
from itertools import product
import json
from build_models import ROOT
from verify_animated_bounds import transform


def sample(frames,time):
    for a,b in zip(frames,frames[1:]):
        if a['time']<=time<=b['time']:
            f=(time-a['time'])/(b['time']-a['time'])
            return [float(a['data_points'][0][axis])*(1-f)+float(b['data_points'][0][axis])*f for axis in 'xyz']
    raise AssertionError(time)


for folder in sorted((ROOT/'models').iterdir()):
    if not folder.is_dir():continue
    model=json.loads((folder/(folder.name+'.bbmodel')).read_text());design=json.loads((folder/'design.json').read_text())
    if design.get('aquatic'):
        assert not design['leg_bones'] and not design['ground_parts']
        swim=next(a for a in model['animations'] if a['name']=='swim')
        assert swim['loop']=='loop' and 'query.modified_distance_moved' in swim['anim_time_update']
        continue
    walk=next(a for a in model['animations'] if a['name']=='walk');groups={g['name']:g for g in model['groups']}
    for leg in design['leg_bones']:
        group=groups[leg];track=walk['animators'][group['uuid']]['keyframes']
        channels={channel:[f for f in track if f['channel']==channel] for channel in ['rotation','position']}
        assert all(channels.values()),folder.name+'/'+leg+': walk needs floor compensation and swing clearance'
        points=[transform(p,c['origin'],c['rotation']) for c in model['elements'] if c['name'] in design['ground_parts'] and (('_'+leg[4:]) in c['name']) for p in product(*zip(c['from'],c['to']))]
        heights=[]
        for i in range(101):
            t=i/100;rotation=sample(channels['rotation'],t);position=sample(channels['position'],t)
            heights.append(min(transform(p,group['origin'],rotation)[1]+position[1] for p in points))
        assert min(heights)>=-.2,(folder.name,leg,'foot penetrates floor',min(heights))
        assert sum(abs(y)<.22 for y in heights)>=50,(folder.name,leg,'stance never plants')
        assert max(heights)>1.1,(folder.name,leg,'swing foot never clears floor')
print('PASS eight-species locomotion (seven grounded gaits and one aquatic distance clock): sampled stance contact, swing clearance and floor penetration <=0.2 model units')
