# Source, build and tests

Custom components are `ArkDinos` (taming), `ArkAtlas` (map item), `ArkPanelBridge`
(read-only runtime data), `ArkAccessBridge` (separate offline/AuthMe migration)
and the Flask panel. Original code is MIT. Original world design/builds are
CC BY 4.0. Third-party dependencies retain their own licenses.

```sh
# Use JDK 25 through JAVA_HOME or PATH.
python3 scripts/build_plugins.py
python3 -m venv .venv
.venv/bin/pip install -r panel/requirements.txt
.venv/bin/python -m unittest discover -s panel -p 'test_*.py'
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```

The build uses published API dependencies with SHA-256 pins in
`dependencies-build.json`. It compiles with `--release 25`, runs Java rule tests
and writes JARs under `<module>/build/` with a fixed timestamp. Dependencies are
stored locally under `.deps/` and are not redistributed. Byte-identical builds
require the same JDK version. No private `artifacts/` directory is required.

The standard installer copies only ArkDinos, ArkAtlas and ArkPanelBridge.
ArkAccessBridge 1.1.0 is built but deliberately not installed. Its session checking
requires AuthMe 6.0.1, PacketEvents 2.14.0 and a separately reviewed offline server.
Keep `legacy-identities: {}` empty in public configuration. Never publish private
UUID/account mappings.

The world is a finished release snapshot. Private infrastructure build, deployment
and reset tools are not part of the public quick start. Include small reproduction
steps and relevant tests when reporting a problem. Documentation is English;
existing in-game content, source comments and the panel may remain German.
