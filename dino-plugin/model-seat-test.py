#!/usr/bin/env python3
"""Exercise the real model packet listener and the optional second-seat facade offline."""
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = Path(os.environ.get('DINO_MODEL_SEAT_OUTPUT', ROOT / 'output/model-seat'))
OUTPUT.mkdir(parents=True, exist_ok=True)
JDK = Path(os.environ.get('JAVA_HOME', ROOT / 'dino-plugin/.tools/jdk-25.0.5+8/Contents/Home')) / 'bin'
paths = sorted(p for p in (ROOT / 'dino-plugin/.deps').glob('*.jar') if not p.name.endswith('-sources.jar'))
cache = Path(os.environ.get('DINO_MODEL_TEST_LIBS', Path.home() / '.gradle/caches/modules-2/files-2.1'))
for pattern in ('**/mockito-core-*.jar', '**/byte-buddy-*.jar', '**/objenesis-*.jar'):
    matches = sorted(p for p in cache.glob(pattern) if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
    assert matches, f'Missing cached test dependency: {pattern}'
    paths.extend(matches)
paper_cache = Path(os.environ.get('DINO_MODEL_CACHE', os.environ.get('DINO_MODEL_STACK_CACHE', ROOT / 'output/live-dependency-stack-20261110/private/server')))
paths.extend(sorted(p for p in (paper_cache / 'libraries').rglob('*.jar') if not p.name.startswith('slf4j-simple-')))
classes = OUTPUT / 'classes'
classes.mkdir(exist_ok=True)
source = ROOT / 'dino-plugin/src/main/java/de/ark/dinos'
sources = [source / f'{name}.java' for name in ('DinoModels', 'BetterModelAdapter', 'BetterModelCleanup', 'DinoPassengerOrder', 'BedrockSeats', 'DinoGrowth', 'DinoSizes', 'EggAssets')]
sources += [ROOT / f'dino-plugin/src/runtime/java/de/ark/dinos/{name}.java' for name in ('ModelSeatProbe', 'ModelSeatOptionalProbe')]
classpath = ':'.join(map(str, paths))
agent = next(p for p in paths if p.name.startswith('byte-buddy-agent-'))
compiled = subprocess.run([str(JDK / 'javac'), '-sourcepath', '', '-cp', classpath, '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
if compiled.returncode:
    print(compiled.stderr, end='', file=sys.stderr)
    sys.exit(compiled.returncode)
checks = []
for optional, probe in ((False, 'ModelSeatProbe'), (True, 'ModelSeatOptionalProbe')):
    selected = [p for p in paths if not optional or not any(name in p.name.lower() for name in ('bettermodel', 'packetevents', 'geyser'))]
    result = subprocess.run([str(JDK / 'java'), f'-javaagent:{agent}', '-Dnet.bytebuddy.experimental=true', '-cp', ':'.join(map(str, selected)) + ':' + str(classes), f'de.ark.dinos.{probe}'], cwd=ROOT, capture_output=True, text=True)
    checks.append({'optionalJarsAbsent': optional, 'ok': result.returncode == 0, 'stdout': result.stdout, 'stderr': result.stderr})
    print(result.stdout, end='')
    print(result.stderr, end='', file=sys.stderr)
report = {'ok': all(check['ok'] for check in checks), 'checks': checks,
          'limit': 'Real ArkDinos listener and pinned PacketEvents wrappers; model engine, scheduler and network endpoints are controlled. No client rendering or PS5 visual QA.'}
(OUTPUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
sys.exit(0 if report['ok'] else 1)
