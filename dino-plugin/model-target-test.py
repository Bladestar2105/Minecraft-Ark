#!/usr/bin/env python3
"""Run the model target resolver with and without the optional model engines."""
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = Path(os.environ.get('DINO_TARGET_OUTPUT', ROOT / 'output/dino-round5-20261108/model-target'))
OUTPUT.mkdir(parents=True, exist_ok=True)
JDK = ROOT / 'dino-plugin/.tools/jdk-25.0.5+8/Contents/Home/bin'
paths = sorted(p for p in (ROOT / 'dino-plugin/.deps').glob('*.jar') if not p.name.endswith('-sources.jar'))
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
for pattern in ('**/mockito-core-*.jar', '**/byte-buddy-*.jar', '**/objenesis-*.jar'):
    matches = sorted(cache.glob(pattern))
    assert matches, f'Missing cached test dependency: {pattern}'
    paths.extend(matches)
paths.extend(sorted(p for p in (ROOT / 'output/dino-model-spike-20261107/private/server/libraries').rglob('*.jar') if not p.name.startswith('slf4j-simple-')))
classes = OUTPUT / 'classes'
classes.mkdir(exist_ok=True)
source = ROOT / 'dino-plugin/src/main/java/de/ark/dinos'
sources = [source / f'{name}.java' for name in ('DinoModels', 'BetterModelAdapter', 'BetterModelCleanup', 'DinoPassengerOrder', 'BedrockSeats', 'DinoGrowth', 'EggAssets')]
sources.append(ROOT / 'dino-plugin/src/runtime/java/de/ark/dinos/ModelTargetProbe.java')
classpath = ':'.join(map(str, paths))
compiled = subprocess.run([str(JDK / 'javac'), '-sourcepath', '', '-cp', classpath, '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
if compiled.returncode:
    print(compiled.stderr, end='', file=sys.stderr)
    sys.exit(compiled.returncode)
checks = []
for optional in (False, True):
    selected = [p for p in paths if not optional or not any(name in p.name.lower() for name in ('bettermodel', 'packetevents', 'geyser'))]
    result = subprocess.run([str(JDK / 'java'), '-Dnet.bytebuddy.experimental=true', '-XX:+EnableDynamicAgentLoading', '-cp', ':'.join(map(str, selected)) + ':' + str(classes), 'de.ark.dinos.ModelTargetProbe', *(['optional'] if optional else [])], capture_output=True, text=True)
    checks.append({'optionalJarsAbsent': optional, 'ok': result.returncode == 0, 'stdout': result.stdout, 'stderr': result.stderr})
    print(result.stdout, end='')
    print(result.stderr, end='', file=sys.stderr)
report = {'ok': all(check['ok'] for check in checks), 'checks': checks, 'limit': 'Target identity and absent-engine behavior only; native mounted hitbox attacks are checked by the combat runtime harness.'}
(OUTPUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
sys.exit(0 if report['ok'] else 1)
