#!/usr/bin/env python3
"""Run the actual pinned Geyser passenger translator without a Minecraft server.

Pass marker-baseline to reproduce the uncorrected ArmorStand seat (expected failure).
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = Path(os.environ.get('DINO_SEAT_OUTPUT', ROOT / 'output/dino-visual-quality-20261108'))
OUTPUT.mkdir(parents=True, exist_ok=True)
JDK = Path(os.environ.get('JAVA_HOME', ROOT / 'dino-plugin/.tools/jdk-25.0.5+8/Contents/Home')) / 'bin'
GEYSER = ROOT / 'dino-plugin/.deps/geyser-spigot-2.12.1-b1295.jar'
assert hashlib.sha256(GEYSER.read_bytes()).hexdigest() == 'b60ff8f9a007f30823cf40173532a0390e6150cdfc577551f6f37bdd5ccd1528'
paths = [GEYSER, *sorted(p for p in (ROOT / 'dino-plugin/.deps').glob('*.jar') if not p.name.endswith('-sources.jar'))]
cache = Path(os.environ.get('DINO_MODEL_TEST_LIBS', Path.home() / '.gradle/caches/modules-2/files-2.1'))
for pattern in ('**/mockito-core-*.jar', '**/byte-buddy-*.jar', '**/objenesis-*.jar'):
    matches = sorted(p for p in cache.glob(pattern) if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
    assert matches, f'Missing cached test dependency: {pattern}'
    paths.extend(matches)
paper_cache = Path(os.environ.get('DINO_MODEL_CACHE', os.environ.get('DINO_MODEL_STACK_CACHE', ROOT / 'output/dino-model-spike-20261107/private/server')))
paths.extend(sorted(p for p in (paper_cache / 'libraries').rglob('*.jar') if not p.name.startswith('slf4j-simple-')))
classes = OUTPUT / 'seat-classes'
classes.mkdir(exist_ok=True)
sources = [ROOT / 'dino-plugin/src/runtime/java/de/ark/dinos/BedrockSeatProbe.java']
seats = ROOT / 'dino-plugin/src/main/java/de/ark/dinos/BedrockSeats.java'
if seats.exists():
    sources.extend(sorted((ROOT / 'dino-plugin/src/main/java/de/ark/dinos').glob('*.java')))
    sources.append(ROOT / 'dino-plugin/src/runtime/java/de/ark/dinos/BedrockSeatsOptionalProbe.java')
classpath = ':'.join(map(str, paths))
agent = next(p for p in paths if p.name.startswith('byte-buddy-agent-'))
compiled = subprocess.run([str(JDK / 'javac'), '-sourcepath', '', '-cp', classpath, '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
if compiled.returncode:
    print(compiled.stderr, end='', file=sys.stderr)
    sys.exit(compiled.returncode)
result = subprocess.run([str(JDK / 'java'), f'-javaagent:{agent}', '-Dnet.bytebuddy.experimental=true',
                         '-cp', classpath + ':' + str(classes), 'de.ark.dinos.BedrockSeatProbe', *sys.argv[1:]], capture_output=True, text=True)
optional = None
if result.returncode == 0 and seats.exists():
    optional_paths = [p for p in paths if 'geyser' not in p.name.lower()]
    optional = subprocess.run([str(JDK / 'java'), f'-javaagent:{agent}', '-Dnet.bytebuddy.experimental=true',
                              '-cp', ':'.join(map(str, optional_paths)) + ':' + str(classes), 'de.ark.dinos.BedrockSeatsOptionalProbe'], capture_output=True, text=True)
report = {'ok': result.returncode == 0 and (optional is None or optional.returncode == 0), 'baseline': 'baseline' in sys.argv or 'marker-baseline' in sys.argv, 'stdout': result.stdout, 'stderr': result.stderr,
          'limit': 'Real translator, flags, links and SEAT_OFFSET metadata. No Bedrock renderer or PS5; visual contact still needs device QA.'}
if optional is not None:
    report['optional'] = {'ok': optional.returncode == 0, 'stdout': optional.stdout, 'stderr': optional.stderr}
report_name = 'seat-marker-red.json' if 'marker-baseline' in sys.argv else 'seat-red.json' if 'baseline' in sys.argv else 'seat-green.json'
(OUTPUT / report_name).write_text(json.dumps(report, indent=2) + '\n')
print(result.stdout, end='')
print(result.stderr, end='', file=sys.stderr)
if optional is not None:
    print(optional.stdout, end='')
    print(optional.stderr, end='', file=sys.stderr)
sys.exit(0 if report['ok'] else 1)
