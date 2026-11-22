"""Run the isolated gate generator and migration tests in the regular world-design CI."""
from pathlib import Path
import subprocess
import sys
import unittest


class GateSignTests(unittest.TestCase):
    def test_crossplay_lettering_and_guarded_native_migration(self):
        folder=Path(__file__).resolve().parent/'jurassic-gate'
        result=subprocess.run([sys.executable,'-B','-m','unittest','discover',
                               '-s',str(folder),'-p','test_*.py'],
                              capture_output=True,text=True,timeout=120)
        self.assertEqual(result.returncode,0,result.stdout+'\n'+result.stderr)


if __name__=='__main__':unittest.main()
