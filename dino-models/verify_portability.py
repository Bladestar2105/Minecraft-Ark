"""Verify the public text-only asset source without checkout/private dependencies."""
from pathlib import Path
import shutil, subprocess, sys, tempfile

ROOT=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='dino-assets-portability-') as directory:
    target=Path(directory)/'dino-models';target.mkdir()
    for name in ['build_models.py','verify_exports.py','verify_animated_bounds.py','verify_gait.py','test_model_poses.py','test_growth_details.py','test_nametags.py','test_mosasaurus.py','LICENSE.txt','README.md']:
        shutil.copy2(ROOT/name,target/name)
    for folder in (ROOT/'models').iterdir():
        out=target/'models'/folder.name;out.mkdir(parents=True)
        for name in [folder.name+'.bbmodel',folder.name+'.geo.json',folder.name+'.animation.json','config.json','design.json']:
            shutil.copy2(folder/name,out/name)
    assert not list(target.rglob('*.png'))
    assert not (target/'original-spike-hashes.json').exists() and not (target/'previews').exists()
    for name in ['verify_exports.py','verify_animated_bounds.py','verify_gait.py','test_model_poses.py','test_growth_details.py','test_nametags.py','test_mosasaurus.py']:
        subprocess.run([sys.executable,str(target/name)],cwd=directory,check=True)
    assert not list(target.rglob('*.png'))
    external=next((target/'models').iterdir());image=external/(external.name+'.png');image.write_bytes(b'corrupt')
    rejected=subprocess.run([sys.executable,str(target/'verify_exports.py')],cwd=directory,capture_output=True,text=True)
    assert rejected.returncode!=0 and 'External texture mismatch' in rejected.stderr
print('PASS public text-only assets: export and animation bounds checks without external PNGs/previews/private spike files')
