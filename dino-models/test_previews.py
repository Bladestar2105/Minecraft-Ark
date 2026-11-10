"""A preview must show embedded pixels and the selected face UVs."""
import base64
import copy
import re
import struct
import unittest
import zlib
from build_models import Model, png_texture, pteranodon, dilophosaurus
from render_previews import animated_vertices, model_svg
from test_model_poses import pose


class TexturedPreviewTest(unittest.TestCase):
    def test_detail_growth_overlays_match_independent_scale_and_attack_math(self):
        model=dilophosaurus();source={'elements':model.elements,'groups':list(model.groups.values()),'outliner':[model.nodes['body']],'animations':model.animations}
        normalized=lambda points:sorted(tuple(round(v,6) for v in p) for p in points)
        for stage in ['baby','juvenile','adult']:
            actual=animated_vertices(source,'spit',.4,base_animation='growth_'+stage);expected=pose(model,'spit',.4,stage)
            for cube in model.elements:self.assertEqual(normalized(actual[cube['uuid']]),normalized(expected[cube['name']]),(stage,cube['name']))

    def test_ground_attack_overlay_preserves_folded_wings(self):
        model=pteranodon();source={'elements':model.elements,'groups':list(model.groups.values()),'outliner':[model.nodes['body']],'animations':model.animations}
        idle=animated_vertices(source);attack=animated_vertices(source,'attack',.34,base_animation='idle')
        for cube in model.elements:
            if cube['name'].startswith(('wing_','membrane_','thumb_')):self.assertEqual(idle[cube['uuid']],attack[cube['uuid']])
        self.assertNotEqual(idle[next(c['uuid'] for c in model.elements if c['name']=='skull')],attack[next(c['uuid'] for c in model.elements if c['name']=='skull')])

    def test_nested_fold_and_flight_vertices_match_independent_pose_math(self):
        model=pteranodon()
        source={'elements':model.elements,'groups':list(model.groups.values()),'outliner':[model.nodes['body']],'animations':model.animations}
        for animation,time in [('idle',0),('walk',.72),('flap',.6),('glide',1)]:
            actual=animated_vertices(source,animation,time);expected=pose(model,animation,time)
            for cube in model.elements:
                normalized=lambda points:sorted(tuple(round(v,6) for v in p) for p in points)
                self.assertEqual(normalized(actual[cube['uuid']]),normalized(expected[cube['name']]),(animation,cube['name']))

    def test_default_preview_uses_actual_idle_bone_pose(self):
        model=Model('preview_pose','test',['#ef2020','#2040ef']*4,'test')
        model.bone('body',[0,0,0]);model.cube('cube',[2,0,0],[10,3,4])
        source={'elements':model.elements,'groups':list(model.groups.values()),'outliner':[model.nodes['body']],'textures':[{'source':'data:image/png;base64,'+base64.b64encode(png_texture(model.colors)).decode()}]}
        rest=model_svg(source,{},(0,0,100,100))
        model.animate('idle',1,{'body':('rotation',[(0,[0,0,90]),(1,[0,0,90])])})
        source['animations']=model.animations
        self.assertNotEqual(rest,model_svg(source,{},(0,0,100,100)),'The preview must show the idle pose, including folded wings')

    def test_dilophosaurus_folding_fan_preview_matches_independent_pose_math(self):
        model=dilophosaurus()
        source={'elements':model.elements,'groups':list(model.groups.values()),'outliner':[model.nodes['body']],'animations':model.animations}
        for animation,time in [('idle',0),('spit',.4),('spit',1),('attack',.34)]:
            actual=animated_vertices(source,animation,time);expected=pose(model,animation,time)
            normalized=lambda points:sorted(tuple(round(v,6) for v in p) for p in points)
            for cube in model.elements:self.assertEqual(normalized(actual[cube['uuid']]),normalized(expected[cube['name']]))

    def test_north_face_preserves_blockbench_uv_direction(self):
        model=Model('preview_orientation','test',['#ff0000','#ff0000','#0000ff','#0000ff']*2,'test')
        model.bone('body',[0,0,0]);model.cube('cube',[0,0,0],[8,8,8])
        for face in model.elements[0]['faces'].values():face['uv']=[0,0,64,64]
        source={'elements':model.elements,'textures':[{'source':'data:image/png;base64,'+base64.b64encode(png_texture(model.colors)).decode()}]}
        rendered=model_svg(source,{},(0,0,100,100),view='front')
        png=base64.b64decode(re.search(r'data:image/png;base64,([^\"]+)',rendered)[1])
        offset=8;compressed=b''
        while offset<len(png):
            size=struct.unpack('>I',png[offset:offset+4])[0]
            if png[offset+4:offset+8]==b'IDAT':compressed+=png[offset+8:offset+8+size]
            offset+=size+12
        pixels=zlib.decompress(compressed)
        def pixel(x,y):return pixels[y*401+1+x*4:y*401+1+x*4+3]
        left,right=pixel(30,60),pixel(70,60)
        self.assertGreater(left[2],left[0],'North face low world X samples high U (blue)')
        self.assertGreater(right[0],right[2],'North face high world X samples low U (red)')

    def test_embedded_pixels_and_uvs_determine_the_render(self):
        model=Model('preview_test','test',['#ef2020','#2040ef']*4,'test')
        model.bone('body',[0,0,0]);model.cube('cube',[0,0,0],[8,8,8])
        source={'elements':model.elements,'textures':[{'source':'data:image/png;base64,'+base64.b64encode(png_texture(model.colors)).decode()}]}
        design={'palette':['#000000']*8}
        red=model_svg(source,design,(0,0,100,100))
        blue=copy.deepcopy(source)
        for face in blue['elements'][0]['faces'].values():face['uv']=[17,1,31,15]
        blue_pixels=model_svg(blue,design,(0,0,100,100))
        self.assertNotEqual(red,blue_pixels,'Changing the sampled UV must change preview pixels')
        blue['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(png_texture(['#10d010']*8)).decode()
        self.assertNotEqual(blue_pixels,model_svg(blue,design,(0,0,100,100)),'Changing embedded pixels must change the preview')
        self.assertIn('data:image/png;base64,',red,'Preview must embed its actual rasterized texture')


if __name__=='__main__':unittest.main()
