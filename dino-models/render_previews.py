"""Render actual exported cube geometry to SVG and PNG; no generated raster art."""
from pathlib import Path
import base64, html, json, math, struct, subprocess, zlib
from build_models import rotate, vertices

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'previews';OUT.mkdir(exist_ok=True)
CAMERA=(.6,.5,-.8)
FACES=dict(north=[0,3,2,1],south=[4,5,6,7],west=[0,4,7,3],east=[1,2,6,5],up=[3,7,6,2],down=[0,1,5,4])
def projection(v):x,y,z=v;return .8*x+.6*z,.3*x-.4*z-y
def animated_vertices(model,animation='idle',time=0,base_animation=None,base_time=0):
    poses={c['uuid']:vertices(c) for c in model['elements']}
    selected=next((a for a in model.get('animations',[]) if a['name']==animation),None)
    if selected is None:return poses
    groups={g['uuid']:g for g in model['groups']};parents={};owners={}
    def visit(node,parent=None):
        if isinstance(node,str):owners[node]=parent;return
        parents[node['uuid']]=parent
        for child in node['children']:visit(child,node['uuid'])
    for node in model['outliner']:visit(node)
    channels={}
    base=next((a for a in model.get('animations',[]) if a['name']==base_animation),None)
    for layer,clock in ([(base,base_time)] if base else [])+[(selected,time)]:
        for uid,track in layer['animators'].items():
            channels[uid]={}
            for channel in ['rotation','position','scale']:
                frames=[k for k in track['keyframes'] if k['channel']==channel]
                if not frames:continue
                a,b=next(((a,b) for a,b in zip(frames,frames[1:]) if a['time']<=clock<=b['time']),(frames[-1],frames[-1]))
                f=(clock-a['time'])/(b['time']-a['time']) if a is not b else 0
                channels[uid][channel]=[float(a['data_points'][0][axis])*(1-f)+float(b['data_points'][0][axis])*f for axis in 'xyz']
    for uid,points in poses.items():
        parent=owners[uid]
        while parent:
            pivot=groups[parent]['origin'];track=channels.get(parent,{})
            points=[[v+p+d for v,p,d in zip(rotate([(v-p)*s for v,p,s in zip(point,pivot,track.get('scale',[1,1,1]))],track.get('rotation',[0,0,0])),pivot,track.get('position',[0,0,0]))] for point in points]
            parent=parents[parent]
        poses[uid]=points
    return poses

def model_svg(model,design,box,view='perspective',animation='idle',time=0,ground=False,base_animation=None,reference_boxes=(),framing_points=()):
    camera=(0,.25,-1) if view=='front' else (1,.25,0) if view=='side' else (.6,.12,-.8) if view=='mouth' else CAMERA
    project=(lambda v:(v[0],-v[1]-.25*v[2])) if view=='front' else (lambda v:(v[2],.25*v[0]-v[1])) if view=='side' else (lambda v:(.8*v[0]+.6*v[2],.072*v[0]-.096*v[2]-v[1])) if view=='mouth' else projection
    poses=animated_vertices(model,animation,time,base_animation)
    references=[]
    for low,high in reference_boxes:
        references.append([[x,y,z] for x,y,z in [(low[0],low[1],low[2]),(high[0],low[1],low[2]),(high[0],high[1],low[2]),(low[0],high[1],low[2]),(low[0],low[1],high[2]),(high[0],low[1],high[2]),(high[0],high[1],high[2]),(low[0],high[1],high[2])]])
    all_points=[project(v) for c in model['elements'] for v in poses[c['uuid']]]+[project(v) for points in references for v in points]+[project(v) for v in framing_points]
    left,right=min(v[0] for v in all_points),max(v[0] for v in all_points)
    top,bottom=min(v[1] for v in all_points),max(v[1] for v in all_points)
    x,y,width,height=box;scale=min(width/(right-left),height/(bottom-top))
    offx=x+(width-(right-left)*scale)/2-left*scale;offy=y+(height-(bottom-top)*scale)/2-top*scale
    def screen(v):u,w=project(v);return u*scale+offx-x,w*scale+offy-y
    # Per-pixel depth keeps small eyes visible against a larger intersecting skull.
    image_width,image_height=math.ceil(width),math.ceil(height)
    rgba=bytearray(image_width*image_height*4);depths=[-math.inf]*(image_width*image_height)
    tex=base64.b64decode(model['textures'][0]['source'].split(',',1)[1]);compressed=b'';offset=8
    while offset<len(tex):
        size=struct.unpack('>I',tex[offset:offset+4])[0]
        if tex[offset+4:offset+8]==b'IDAT':compressed+=tex[offset+8:offset+8+size]
        offset+=size+12
    texture_width,texture_height=struct.unpack('>II',tex[16:24]);pixels=zlib.decompress(compressed);stride=texture_width*4+1
    assert all(pixels[j*stride]==0 for j in range(texture_height)),'Generated textures must use PNG filter zero'
    for c in model['elements']:
        pts=poses[c['uuid']]
        for side,inds in FACES.items():
            face=[pts[i] for i in inds];a=[face[1][i]-face[0][i] for i in range(3)];b=[face[2][i]-face[0][i] for i in range(3)]
            normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];length=math.sqrt(sum(v*v for v in normal));normal=[v/length for v in normal]
            if sum(a*b for a,b in zip(normal,camera))<1e-8:continue
            shade=.7+.33*max(0,sum(a*b for a,b in zip(normal,[.1,.85,-.5])))
            points=[(*screen(v),sum(a*b for a,b in zip(v,camera))) for v in face]
            assert all(-1e-6<=a<=width+1e-6 and -1e-6<=b<=height+1e-6 for a,b,_ in points),'Clipped preview'
            u,v,u2,v2=c['faces'][side]['uv']
            if side in ('north','east'):uv=[(u2,v2),(u2,v),(u,v),(u,v2)]
            elif side=='up':uv=[(u,v),(u,v2),(u2,v2),(u2,v)]
            else:uv=[(u,v2),(u2,v2),(u2,v),(u,v)]
            for tri in [(0,1,2),(0,2,3)]:
                (ax,ay,az),(bx,by,bz),(cx,cy,cz)=[points[i] for i in tri];(au,av),(bu,bv),(cu,cv)=[uv[i] for i in tri]
                denominator=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
                if abs(denominator)<1e-9:continue
                for py in range(max(0,math.floor(min(ay,by,cy))),min(image_height,math.ceil(max(ay,by,cy)))):
                    for px in range(max(0,math.floor(min(ax,bx,cx))),min(image_width,math.ceil(max(ax,bx,cx)))):
                        wa=((by-cy)*(px+.5-cx)+(cx-bx)*(py+.5-cy))/denominator
                        wb=((cy-ay)*(px+.5-cx)+(ax-cx)*(py+.5-cy))/denominator;wc=1-wa-wb
                        if min(wa,wb,wc)<-1e-8:continue
                        depth=wa*az+wb*bz+wc*cz;index=py*image_width+px
                        if depth<=depths[index]:continue
                        tx=min(texture_width-1,max(0,int(wa*au+wb*bu+wc*cu)));ty=min(texture_height-1,max(0,int(wa*av+wb*bv+wc*cv)));sample=ty*stride+1+tx*4
                        if pixels[sample+3]==0:continue
                        depths[index]=depth;start=index*4
                        rgba[start:start+4]=bytes([min(255,round(pixels[sample+i]*shade)) for i in range(3)]+[pixels[sample+3]])
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
    rows=b''.join(b'\0'+rgba[j*image_width*4:(j+1)*image_width*4] for j in range(image_height))
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',image_width,image_height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')
    actualbottom=bottom*scale+offy;actualwidth=(right-left)*scale
    shadow=f'<ellipse cx="{x+width/2}" cy="{actualbottom-4}" rx="{actualwidth*.4}" ry="{max(6,actualwidth*.055)}" fill="#071315" opacity=".65"/>'
    floor=''
    if ground:
        z=[v[2] for points in poses.values() for v in points]
        a,b=[screen([0,0,value]) for value in [min(z),max(z)]]
        floor=f'<path d="M {a[0]+x},{a[1]+y} L {b[0]+x},{b[1]+y}" stroke="#a3c3ad" stroke-width="2" stroke-dasharray="8 6"/>'
    reference=''
    for points in references:
        for a,b in [(0,1),(1,2),(2,3),(3,0),(4,5),(5,6),(6,7),(7,4),(0,4),(1,5),(2,6),(3,7)]:
            ax,ay=screen(points[a]);bx,by=screen(points[b])
            reference+=f'<path d="M {x+ax},{y+ay} L {x+bx},{y+by}" stroke="#f1ce6c" stroke-width="2" fill="none"/>'
    return floor+shadow+f'<image x="{x}" y="{y}" width="{width}" height="{height}" href="data:image/png;base64,{base64.b64encode(png).decode()}"/>'+reference
def svg(content,width,height):return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}"><rect width="100%" height="100%" fill="#102226"/>{content}</svg>'
def text(value,x,y,size=18,color='#dcebd8',weight='normal'):
    return f'<text x="{x}" y="{y}" fill="{color}" font-family="DejaVu Sans, sans-serif" font-size="{size}" font-weight="{weight}">{html.escape(value)}</text>'
def save(name,content,width,height):
    path=OUT/(name+'.svg');path.write_text(svg(content,width,height));subprocess.run(['rtk','proxy','rsvg-convert','-o',str(path.with_suffix('.png')),str(path)],check=True,capture_output=True)

def main():
    models=[]
    for dirname in ['ark_trex','ark_triceratops','ark_stegosaurus','ark_brachiosaurus','ark_velociraptor','ark_pteranodon','ark_dilophosaurus','ark_mosasaurus']:
        p=ROOT/'models'/dirname;m=json.loads((p/(dirname+'.bbmodel')).read_text());d=json.loads((p/'design.json').read_text());models.append((m,d))
        subtitle=d['description'].split(' · ')
        content=text('ARK / '+d['label'],55,65,32,weight='bold')+text(' · '.join(subtitle[:2]),55,99,17,'#9dbbb4')
        content+=model_svg(m,d,(70,145,1060,550))
        content+=text('Modellvorschau · '+str(d['cubes'])+' Würfel · '+str(d['bones'])+' Knochen',55,760,18)
        animations=' / '.join(a['name'].capitalize() for a in m['animations'])
        content+=text('Editierbare CC0-Entwürfe · '+animations+' · Java- und Bedrock-Quelldateien',55,791,15,'#85a69d')
        save(dirname,content,1200,830)

    content=text('ARK / Dinosaurier und Mosasaurus',42,62,32,weight='bold')+text('Acht CC0-Modelle · echte Geometrie, Hauttexturen und Angriffe',42,94,16,'#9dbbb4')
    for i,(m,d) in enumerate(models):
        col,row=i%3,i//3;x=28+col*488;y=125+row*450
        content+=f'<rect x="{x}" y="{y}" width="472" height="432" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(d['label'],x+22,y+39,24,weight='bold')
        content+=model_svg(m,d,(x+22,y+70,428,285))
        content+=text(d['description'].split(' · ')[0],x+22,y+385,14,'#a7c2b4')
        special='Drei Einzelzehen, erhöhte Ferse' if d['name']=='ark_trex' else 'Idle / Walk / Flap / Glide' if d['name']=='ark_pteranodon' else '24 Meter · Idle / Swim / Attack' if d['name']=='ark_mosasaurus' else 'Texturierte Haut · Idle / Walk'
        content+=text(special,x+22,y+411,13,'#80a79d')
    content+=text('Lokale Modellvorschau · echte PNG-UVs und Tiefentest · PS5-Darstellung noch nicht geprüft',42,1514,15,'#8bada3')
    save('dinosaurier-uebersicht',content,1500,1550)
    content=text('Alle acht / Ruhe und Angriff',40,55,33,weight='bold')+text('Tatsächliche Geometrie, Texturen und Knochenposen · Modellansichten, keine Konsolen-Screenshots',40,92,17,'#9dbbb4')
    moments={'ark_trex':.16,'ark_velociraptor':.16,'ark_dilophosaurus':.16,'ark_triceratops':.34,'ark_stegosaurus':.34,'ark_brachiosaurus':.25,'ark_pteranodon':.34,'ark_mosasaurus':.2}
    for i,(model,design) in enumerate(models):
        x=25+(i%2)*1000;y=125+(i//2)*535
        content+=f'<rect x="{x}" y="{y}" width="975" height="510" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(design['label'],x+22,y+38,27,weight='bold')+text('Ruhe',x+22,y+72,16,'#a7c2b4')+text('Angriff',x+510,y+72,16,'#a7c2b4')
        content+=model_svg(model,design,(x+20,y+100,455,380))
        content+=model_svg(model,design,(x+505,y+100,450,380),animation='attack',time=moments[design['name']],base_animation='idle')
    save('dinosaurier-ruhe-angriff',content,2050,2300)
    content=text('Zähne / tatsächliche offene Mäuler',40,55,32,weight='bold')+text('Elfenbeinfarbene Ober- und Unterreihen · Eckpunkte gegen feste Maulgeometrie geprüft',40,92,17,'#9dbbb4')
    for i,(model,design) in enumerate(models[:5]+[models[6]]):
        x=25+(i%3)*600;y=125+(i//3)*470
        prefixes=('tooth_','gums_','jaw','lower_jaw','oral_cavity','mouth','skull','snout','muzzle','head','beak','eye_','pupil_','brow_','cheek_','nostril_','nasal_rise','nose_horn')
        head=dict(model,elements=[cube for cube in model['elements'] if cube['name'].startswith(prefixes)])
        content+=f'<rect x="{x}" y="{y}" width="575" height="445" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(design['label'],x+20,y+38,24,weight='bold')+model_svg(head,design,(x+20,y+75,535,340),view='mouth',animation='attack',time=.16,base_animation='idle')
    content+=text('Pteranodon behält seinen zahnlosen Schnabel.',40,1110,18,'#9dbbb4')
    save('zaehne-mauldetails',content,1850,1150)
    trice,trice_design=models[1]
    head=dict(trice,elements=[c for c in trice['elements'] if c['name'].startswith(('head','snout','beak','eye_','pupil_','frill_','brow_horn','nose_horn','jaw','lower_jaw','tooth_','gums_','oral_cavity'))])
    content=text('Triceratops / drei deutliche Hörner',40,55,32,weight='bold')+text('Zwei unveränderte Stirnhörner · größeres zentrales Nasenhorn · tatsächliche Quellgeometrie',40,92,17,'#9dbbb4')
    for i,(view,label) in enumerate([('front','Vorne: drei getrennte Hornspitzen'),('side','Seite: Nasenhorn vor der Stirn')]):
        x=25+i*750
        content+=f'<rect x="{x}" y="125" width="725" height="550" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+20,165,24,weight='bold')+model_svg(head,trice_design,(x+30,200,665,430),view=view)
    save('triceratops-drei-hoerner',content,1550,725)
    trex,design=models[0]
    foot_model=dict(trex,elements=[c for c in trex['elements'] if c['name'].startswith(('shin_','raised_heel_','toe_','claw_','toe_claw_'))])
    right_foot=dict(trex,elements=[c for c in foot_model['elements'] if '_right' in c['name']])
    content=text('T-Rex / Füße mit gebogenen Krallen',45,61,31,weight='bold')+text('Drei gespreizte Einzelzehen · angehobene Ferse · große Elfenbeinkrallen',45,98,17,'#9dbbb4')
    for x,label,model,view in [(25,'Vorne',foot_model,'front'),(620,'Seite',right_foot,'side')]:
        content+=f'<rect x="{x}" y="130" width="555" height="430" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+24,169,22,weight='bold')+model_svg(model,design,(x+35,205,485,315),view)
    content+=text('Modell-Detail zur Ansicht · keine blockigen Schuhe',45,605,17)
    save('trex-fuesse-vorne-seite',content,1200,650)
    hands=dict(trex,elements=[c for c in trex['elements'] if c['name'].startswith(('tiny_arm_','hand_'))])
    content=text('T-Rex / zwei Fingerkrallen pro Hand',45,61,31,weight='bold')+text('Tatsächliche Quellgeometrie und UV-Texturen · vergrößerte Detailansicht',45,98,17,'#9dbbb4')
    content+=model_svg(hands,design,(80,145,1040,440))
    save('trex-haende-krallen',content,1200,650)
    brachio,brachio_design=models[3]
    content=text('Brachiosaurus / Schwanz bis zum Boden',45,61,31,weight='bold')+text('Seitenansicht · gestrichelt: Bodenhöhe y=0 · unveränderte Rücken- und Fußhöhe',45,98,17,'#9dbbb4')
    content+=model_svg(brachio,brachio_design,(80,145,1040,490),view='side',ground=True)
    save('brachiosaurus-schwanz-boden',content,1200,700)
    ptera,ptera_design=models[5]
    content=text('Pteranodon / Boden und Flug',40,55,31,weight='bold')+text('Exportierte Knochenanimationen · lokale Modellansicht, kein Konsolen-Screenshot',40,89,17,'#9dbbb4')
    for i,(animation,time,label) in enumerate([('idle',0,'Boden: Flügel gefaltet'),('walk',.72,'Gehen: gefaltet, Fuß angehoben'),('flap',.6,'Flug: offener Flügelschlag'),('glide',0,'Gleiten: Flügel offen')]):
        x=25+(i%2)*750;y=120+(i//2)*470
        content+=f'<rect x="{x}" y="{y}" width="725" height="445" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+20,y+35,23,weight='bold')+model_svg(ptera,ptera_design,(x+25,y+65,675,350),animation=animation,time=time)
    save('pteranodon-boden-flug',content,1550,1080)
    dilo,dilo_design=models[6]
    content=text('Dilophosaurus / Faltkragen und Angriff',40,55,31,weight='bold')+text('Originaler JP-inspirierter Entwurf · animierte Quellgeometrie · keine PS5-Aufnahme',40,90,17,'#9dbbb4')
    for i,(animation,time,label) in enumerate([('idle',0,'Ruhe: Halsfächer gefaltet'),('spit',.4,'Spucken: Halsfächer geöffnet'),('attack',.34,'Biss und Krallenhieb')]):
        x=25+i*590
        content+=f'<rect x="{x}" y="125" width="565" height="550" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+18,165,21,weight='bold')+model_svg(dilo,dilo_design,(x+20,195,525,430),animation=animation,time=time)
    save('dilophosaurus-frill-angriff',content,1800,725)
    content=text('Pteranodon / freier Sitz und angezogene Flugfüße',40,55,30,weight='bold')+text('Gelb: gemessene native Spieler-Hüllbox 0,6 × 1,8 m · feste Sitzhöhe · Seitenansichten',40,90,17,'#9dbbb4')
    feet=(1.619054-.6)*16
    rider=([-.3*16,feet,-.3*16],[.3*16,feet+1.8*16,.3*16])
    for i,(animation,time,label) in enumerate([('idle',0,'Boden: Kamm vor dem Sitz'),('flap',.6,'Flügelschlag: Füße nach hinten'),('glide',0,'Gleiten: Füße angezogen')]):
        x=25+i*590
        content+=f'<rect x="{x}" y="125" width="565" height="550" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+18,165,21,weight='bold')+model_svg(ptera,ptera_design,(x+20,195,525,430),view='side',animation=animation,time=time,reference_boxes=[rider])
    save('pteranodon-sitz-flugfuesse',content,1800,725)
    stego,stego_design=models[2];feet=(1.875-.6)*16
    rider=([-.3*16,feet,-.3*16],[.3*16,feet+1.8*16,.3*16])
    content=text('Stegosaurus / Platten vom Hals bis zum Schwanz',40,55,30,weight='bold')+text('Kleine Hals- und Schwanzplatten bewegen sich mit · große Rückenplatten neben dem festen Sitz',40,90,17,'#9dbbb4')
    for i,(animation,time,label) in enumerate([('idle',0,'Ruhe: durchgehende Platten'),('attack',.34,'Schwanzschlag: Platten folgen')]):
        x=25+i*850
        content+=f'<rect x="{x}" y="125" width="825" height="550" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,x+18,165,23,weight='bold')+model_svg(stego,stego_design,(x+20,195,785,430),view='side',animation=animation,time=time,reference_boxes=[rider])
    save('stegosaurus-hals-schwanzplatten',content,1750,725)
    mosa,mosa_design=models[7]
    content=text('Mosasaurus / 24 Meter im Wasser',40,55,32,weight='bold')+text('Vier Flossen · senkrechte Schwanzfluke · Ober- und Unterzahnreihen · wildes Meerestier',40,90,17,'#9dbbb4')
    for i,(animation,time,label) in enumerate([('swim',.25,'Schwimmen'),('attack',.2,'Biss: Maul geöffnet')]):
        y=125+i*430
        content+=f'<rect x="25" y="{y}" width="1750" height="410" rx="18" fill="#183034" stroke="#2c4749"/>'
        content+=text(label,45,y+38,25,weight='bold')+model_svg(mosa,mosa_design,(55,y+65,1690,320),view='side',animation=animation,time=time)
    save('mosasaurus-schwimmen-angriff',content,1800,1030)
    # Both models and the player reference share one physical coordinate system.
    import copy
    comparison=copy.deepcopy(trex)
    trex_shift=[0,-min(v[1] for c in trex['elements'] for v in vertices(c))-90,-105]
    for g in comparison['groups']:g['origin']=[a+b for a,b in zip(g['origin'],trex_shift)]
    for cube in comparison['elements']:
        for key in ['from','to','origin']:cube[key]=[a+b for a,b in zip(cube[key],trex_shift)]
    comparison['animations']=[]
    player=([-.3*16,-90,20],[.3*16,-90+1.8*16,20+.6*16])
    content=text('Mosasaurus / echter Größenvergleich',40,55,32,weight='bold')+text('Gleicher Maßstab: 24-m-Mosasaurus, T-Rex und gelbe 1,8-m-Spieler-Hüllbox · Seitenansicht',40,90,17,'#9dbbb4')
    frame=[[-100,-95,-160],[100,64,245]]
    content+=model_svg(mosa,mosa_design,(55,140,1690,780),view='side',animation='none',framing_points=frame)
    content+=model_svg(comparison,design,(55,140,1690,780),view='side',reference_boxes=[player],framing_points=frame)
    save('mosasaurus-groessenvergleich',content,1800,1000)
    baseline=ROOT.parent/'output/dino-visual-quality-20261108/baseline/dino-models/models'
    if baseline.is_dir():
        comparison=text('Dinosaurier / tatsächliche Modelle im Vergleich',40,50,30,weight='bold')
        comparison+=text('Bisher',80,91,22)+text('Überarbeitet: Anatomie und Haut',970,91,22)
        for i,(model,design) in enumerate(models):
            if not (baseline/design['name']/(design['name']+'.bbmodel')).is_file():continue
            old=json.loads((baseline/design['name']/(design['name']+'.bbmodel')).read_text());top=110+i*315
            comparison+=text(design['label'],40,top+30,22,weight='bold')
            comparison+=model_svg(old,design,(60,top+50,800,235))+model_svg(model,design,(940,top+50,800,235))
        comparison+=text('Gleiche orthografische Ansicht · Quellgeometrie mit echten UV-Texturen · kein Minecraft-/PS5-Screenshot',40,2045,18,'#9dbbb4')
        save(str(ROOT.parent/'output/dino-visual-quality-20261108/before-after'),comparison,1800,2080)
    print('Rendered eight models, combat overview, aquatic scale comparison, rider clearance and species details')

if __name__=="__main__":main()
