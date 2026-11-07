# Original dinosaur assets

Six original CC0 models approved on 2026-11-07: T-Rex, Triceratops, Stegosaurus, Brachiosaurus, Velociraptor and Pteranodon. Editable Blockbench 5 free-format models include embedded 64×64 PNG palettes. No third-party dinosaur geometry or textures.

Run from the repository root with Python 3 and only its standard library:

```sh
rtk proxy python3 dino-models/build_models.py
rtk proxy python3 dino-models/verify_exports.py
rtk proxy python3 dino-models/verify_animated_bounds.py
rtk proxy python3 dino-models/verify_portability.py
```

The public source is text-only: `models/<id>/` contains `<id>.bbmodel`, `<id>.geo.json`, `<id>.animation.json`, `config.json` and `design.json`, alongside the generation/verification scripts, this README and the CC0 license. Textures are embedded in each BBModel. `build_models.py` deterministically regenerates the model exports and standalone PNGs when needed. `verify_exports.py` validates the embedded PNG dimensions, chunk checksums and palette pixels directly; an optional external PNG is compared when present. Neither verifier depends on previews, private spike files, absolute paths or separately published textures.

The public installer decodes the embedded textures, prepares both engines and serves the generated Java pack automatically at `/resources/dinos.zip` through the player portal. Configure the public portal URL with `python3 scripts/setup.py --portal-url https://portal.example.org/` and enable required resource packs; the installer sets the Java pack URL and handles the Bedrock pack path. The all-in-one installation needs no separate manual HTTP service. BetterModel 3.6.0 consumes BBModels from `plugins/BetterModel/models/` and generates `plugins/BetterModel/build.zip` for that route.

GeyserModelEngine 1.0.9 input: copy each model's geometry, animation, texture and `config.json` into `plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/<id>/`. For a text-only distribution, decode the model's `textures[0].source` data URI into `<id>.png`; the installer extracts the exact embedded bytes. The config binds that texture basename to all bones. **Animation input keys are short names** (`idle`, `walk`, `flap`, `glide`): this bridge adds `animation.<id>.` itself and uses the short keys for BetterModel synchronization. Fully prefixed input keys silently produce incompatible runtime aliases.

`verify_portability.py` copies only the public text asset files to a fresh temporary directory and runs both export and animated-bounds verification there, without generating external PNGs. It also proves a mismatched optional PNG is rejected.

All land models have idle/walk loops. Pteranodon has idle/flap/glide, and a culling box height 4 / offsetY 1.3 covering full wing travel. Independent corner transforms sample every keyframe and intermediate rotations at no more than 0.25 degrees; grounding is exact in rest pose. Walking uses simple hip rotation without inverse kinematics or planted-foot correction.

The actual BetterModel 3.6.0/GeyserModelEngine 1.0.9 packs and runtime evidence are local under `output/dino-assets-runtime-20261107/`. `verify_compiled.py` reconstructs every compiled Java corner through vanilla item-display transforms and compares it with the editable source (tolerance 0.001 model units); it also checks UVs, exact textures and all generated Bedrock references. This catches arbitrary-angle errors that an axis-aligned spike or comparing two exporters cannot catch.

No real Minecraft renderer, Bedrock login, PS5 or Switch was available. Headless protocol/resource-pack acknowledgments and HTTP checks do not prove visual rendering or client resource-pack application. Mount controls, collision, combat and server deployment belong to the gameplay/adapter integration.
