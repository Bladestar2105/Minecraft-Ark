# Source, builds and tests

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development-DE) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One)

The project has seven plugin sources: `accounts-plugin`, `wardrobe-plugin`, `support-plugin`, `dino-plugin`, `atlas-plugin`, `panel-plugin` and optional `access-plugin` (ArkAccessBridge 1.1.1). `panel` contains the isolated Flask player app and administration app. [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules) defines their responsibilities; account/session authority is ArkAccounts, not Wardrobe.

## Build from a clean public checkout

```sh
# JAVA_HOME/PATH must use JDK 25.
python3 scripts/build_plugins.py
python3 -m venv .venv
.venv/bin/pip install -r panel/requirements.txt
.venv/bin/python -m unittest discover -s panel -p 'test_*.py'
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
node panel/test_skin_editor.mjs
node panel/test_ui.mjs
```

The builder verifies pinned API dependencies from `dependencies-build.json`, compiles with `--release 25`, runs dependency-free Java checks and creates all seven JARs plus `own-plugins.json`. Local dependency caches are not redistributed. Reproducible JAR bytes require the same JDK version; a binary-package user does not need a host JDK.

Browser fixture checks are `panel/test_ui.mjs --browser`, `panel/test_player_ui.mjs`, `panel/test_signing_ui.mjs` and `panel/test_support_ui.mjs`. They require an installed Playwright module/Chromium; set `PLAYWRIGHT_MODULE` to its `index.mjs` and optionally `PLAYWRIGHT_CHROMIUM` to the browser executable. They exercise production UI with local APIs, including DE/EN drafts, account recovery, OP separation, support revisions and missing optional modules. Screenshots are local test evidence.

## Package application artifacts

After the verified build:

```sh
python3 scripts/package.py --version app-YOUR_VERSION
```

This writes `dist/app-YOUR_VERSION/`, containing `Minecraft-Ark-app-YOUR_VERSION-all-in-one.zip`, seven individual project JARs and `SHA256SUMS`. The ZIP uses the explicit source allowlist, verified JAR hashes and fixed archive metadata; private worlds/secrets/state are excluded. It references the fixed world release instead of embedding it.

CI can produce `app-<commit SHA>` build artifacts with a 14-day Actions retention. An Actions artifact is not a world release or a deployment. Application release assets are available only after publication; use the artifacts actually attached to the selected build/release. The package workflow does not automatically accept the EULA or start a server.

## Changes and acceptance

Keep player and admin APIs explicitly separated; a player cookie is never an admin login. Preserve UUID ownership, optimistic revisions and readable existing state on failed writes. Keep OP status checks authoritative, Accounts core independent of Support/Wardrobe, and frontend capability queries honest. Update both language catalogs and this wiki's paired feature pages when behavior changes.

The default installer selects six modules and omits ArkAccessBridge. The Access 1.1.1 integration is for separately reviewed existing offline/AuthMe installations; do not infer existing account access from a global linking record alone. Never publish private UUID/account mappings.

Report real Microsoft/Minecraft signing and Java/Bedrock/device acceptance separately from fixtures. Tests cannot demonstrate an operator-created OAuth application's real Minecraft access or console steering. The world snapshot remains fixed; private reset/deployment machinery is not a public quick-start requirement.

Original code/docs are MIT; original world content is CC BY 4.0. Third-party APIs/plugins retain their own licenses.
