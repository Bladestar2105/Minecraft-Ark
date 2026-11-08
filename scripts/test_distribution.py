import hashlib
import base64
import json
from pathlib import Path
import tempfile
import unittest
import uuid
from unittest.mock import patch
import zipfile

import install_plugins
import package


PLUGINS = {"dino": ("ArkDinos", "1.1.0"), "atlas": ("ArkAtlas", "1.0.1"),
           "panel": ("ArkPanelBridge", "1.0.0"), "accounts": ("ArkAccounts", "1.0.2"),
           "wardrobe": ("ArkWardrobe", "1.0.0"), "support": ("ArkSupport", "1.0.1"),
           "access": ("ArkAccessBridge", "1.1.1")}


def plugins_fixture(root):
    entries = []
    for module, (name, version) in PLUGINS.items():
        path = f"{module}-plugin/build/{name}-{version}.jar"
        target = root / path
        target.parent.mkdir(parents=True)
        target.write_bytes((module + " jar fixture").encode())
        entries.append({"module": module, "name": name, "version": version, "path": path,
                        "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    (root / "own-plugins.json").write_text(json.dumps({"format": 1, "plugins": entries}))
    return entries


def install_fixture(root):
    entries = plugins_fixture(root)
    (root / "data/ark").mkdir(parents=True)
    (root / "data/ark/level.dat").write_bytes(b"world")
    (root / "config").mkdir()
    for name in ("geyser.yml", "floodgate.yml", "skinsrestorer.yml", "permissions.yml"):
        (root / "config" / name).write_text("fixture: true\n")
    (root / "config/floodgate.yml").write_text('metrics:\n  enabled: true\n  uuid: ${metrics.uuid}\n')
    (root / "dependencies-runtime.json").write_text(json.dumps([
        {"kind": "plugin", "project": "skinsrestorer", "name": "SkinsRestorer.jar", "url": "https://example.org/skins.jar", "sha256": "0" * 64},
        {"kind": "plugin", "project": "geyser", "name": "Geyser.jar", "url": "https://example.org/geyser.jar", "sha256": "0" * 64}]))
    return entries


def package_fixture(root):
    entries = plugins_fixture(root)
    (root / "README.md").write_text("Public fixture\n")
    (root / ".env.example").write_text("EULA=FALSE\n")
    (root / "check.mjs").write_text("export {};\n")
    allowlist = [".env.example", "README.md", "check.mjs", "distribution-files.json"]
    (root / "distribution-files.json").write_text(json.dumps(allowlist))
    return entries


def dino_models_fixture(root):
    source = next(parent / 'dino-items/egg-assets.json' for parent in Path(__file__).resolve().parents
                  if (parent / 'dino-items/egg-assets.json').is_file())
    (root / 'dino-items').mkdir()
    (root / 'dino-items/egg-assets.json').write_bytes(source.read_bytes())
    species = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus', 'mosasaurus')
    models = [('dino-models', 'ark_' + name) for name in species]
    models += [('dino-items', 'ark_egg_' + name) for name in species if name != 'mosasaurus']
    for directory, model in models:
        folder = root / directory / 'models' / model
        folder.mkdir(parents=True)
        texture = b'\x89PNG\r\n\x1a\nfixture'
        (folder / (model + '.bbmodel')).write_text(json.dumps({'textures': [{'source': 'data:image/png;base64,' + base64.b64encode(texture).decode()}]}))
        for suffix in ('.geo.json', '.animation.json'):
            (folder / (model + suffix)).write_text('{"fixture":true}\n')
        (folder / 'config.json').write_text('{"fixture":true}\n')


class InstallerTests(unittest.TestCase):
    def test_species_egg_pack_and_mappings_preserve_existing_model_pack_and_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            install_fixture(root)
            dino_models_fixture(root)
            geyser = root / 'data/plugins/Geyser-Spigot'
            preserved = [geyser / 'packs/other.mcpack', geyser / 'custom_mappings/operator.json',
                         geyser / 'extensions/geysermodelengineextension/generated_pack.zip',
                         root / 'data/plugins/BetterModel/.cache/model.json']
            for path in preserved:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'operator model cache')
            with patch.object(install_plugins, 'download'):
                install_plugins.install(root, ['dino'])
            pack = geyser / 'packs/ark-dino-eggs.mcpack'
            self.assertTrue(pack.is_file(), 'Dino installation must include the Bedrock egg graphics')
            with zipfile.ZipFile(pack) as archive:
                atlas = json.loads(archive.read('textures/item_texture.json'))['texture_data']
                self.assertEqual(len(atlas), 8)
                self.assertEqual(len({archive.read(value['textures'] + '.png') for value in atlas.values()}), 8)
            mappings = json.loads((geyser / 'custom_mappings/ark-dino-eggs.json').read_text())
            self.assertEqual(len(mappings['items']['minecraft:sniffer_egg']), 8)
            self.assertTrue(all(path.read_bytes() == b'operator model cache' for path in preserved))

    def test_model_dependencies_and_required_bedrock_pack_follow_module_selection(self):
        for modules in (['accounts'], ['dino']):
            with self.subTest(modules=modules), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                install_fixture(root)
                dino_models_fixture(root)
                (root / 'config/geyser.yml').write_text('gameplay:\n  force-resource-packs: false\n')
                (root / 'dependencies-runtime.json').write_text(json.dumps([
                    {'kind': 'plugin', 'project': 'bettermodel', 'name': 'BetterModel.jar'},
                    {'kind': 'geyser-extension', 'project': 'geyser-model-engine-extension', 'name': 'Bridge.jar'},
                    {'kind': 'plugin', 'project': 'packetevents', 'name': 'PacketEvents.jar'}]))
                fetched = []
                def download(item, folder):
                    fetched.append((item['name'], folder.relative_to(root.resolve()).as_posix()))
                with patch.object(install_plugins, 'download', side_effect=download):
                    install_plugins.install(root, modules)
                if 'dino' in modules:
                    self.assertEqual(fetched, [('BetterModel.jar', 'data/plugins'), ('Bridge.jar', 'data/plugins/Geyser-Spigot/extensions'), ('PacketEvents.jar', 'data/plugins')])
                    self.assertIn('force-resource-packs: true', (root / 'data/plugins/Geyser-Spigot/config.yml').read_text())
                else:
                    self.assertEqual(fetched, [])
                    self.assertFalse((root / 'data/plugins/BetterModel').exists())
                    self.assertIn('force-resource-packs: false', (root / 'data/plugins/Geyser-Spigot/config.yml').read_text())

    def test_missing_invalid_or_existing_model_rejects_before_installation_changes(self):
        for change in ('missing', 'texture', 'existing', 'egg_missing', 'egg_texture', 'egg_existing'):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                install_fixture(root)
                dino_models_fixture(root)
                model = 'ark_egg_pteranodon' if change.startswith('egg_') else 'ark_pteranodon'
                source = root / ('dino-items' if change.startswith('egg_') else 'dino-models') / 'models' / model
                if change.endswith('missing'):
                    (source / 'config.json').unlink()
                elif change.endswith('texture'):
                    (source / (model + '.bbmodel')).write_text('{"textures":[{"source":"data:image/png;base64,YmFk"}]}')
                else:
                    target = root / 'data/plugins/BetterModel/models' / (model + '.bbmodel')
                    target.parent.mkdir(parents=True)
                    target.write_text('existing operator model')
                with patch.object(install_plugins, 'download', side_effect=AssertionError('must preflight models')):
                    with self.assertRaises((ValueError, FileNotFoundError)):
                        install_plugins.install(root, ['dino'])
                self.assertFalse(list((root / 'data/plugins').glob('*.jar')) if (root / 'data/plugins').exists() else [])
                self.assertFalse((root / 'data/plugins/Geyser-Spigot/config.yml').exists())
                if change.endswith('existing'):
                    self.assertEqual(target.read_text(), 'existing operator model')

    def test_dino_selection_installs_eight_animals_and_seven_placed_egg_models(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            install_fixture(root)
            dino_models_fixture(root)
            with patch.object(install_plugins, 'download'):
                install_plugins.install(root, ['dino'])
            java = root / 'data/plugins/BetterModel/models'
            bedrock = root / 'data/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input'
            self.assertEqual(len(list(java.glob('ark_*.bbmodel'))), 15)
            self.assertEqual(len(list(bedrock.glob('ark_*'))), 15)
            self.assertEqual(len(list(java.glob('ark_egg_*.bbmodel'))), 7)
            self.assertFalse((java / 'ark_egg_mosasaurus.bbmodel').exists())
            for folder in bedrock.iterdir():
                self.assertEqual(sorted(p.name for p in folder.iterdir()),
                                 [folder.name + '.animation.json', folder.name + '.geo.json', folder.name + '.png', 'config.json'])
                self.assertEqual((folder / (folder.name + '.png')).read_bytes(), b'\x89PNG\r\n\x1a\nfixture')

    def test_local_linking_extension_installs_in_floodgate_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            install_fixture(root)
            provider = root / "publisher-provider.jar"
            provider.write_bytes(b"verified sqlite provider fixture")
            (root / "dependencies-runtime.json").write_text(json.dumps([{
                "kind": "floodgate-extension", "project": "floodgatedb",
                "name": "floodgate-sqlite-database.jar", "url": provider.as_uri(),
                "sha256": hashlib.sha256(provider.read_bytes()).hexdigest()}]))
            try:
                install_plugins.install(root, ["accounts"])
            except ValueError as error:
                self.fail("Local linking installation was rejected: " + str(error))
            floodgate = root / "data/plugins/floodgate"
            self.assertEqual((floodgate / "floodgate-sqlite-database.jar").read_bytes(), provider.read_bytes())
            self.assertFalse((root / "data/plugins/floodgate-sqlite-database.jar").exists())
            config = (floodgate / "config.yml").read_text()
            metrics_uuid = config.split('uuid: ')[1].strip()
            self.assertNotEqual(metrics_uuid, '${metrics.uuid}')
            self.assertEqual(str(uuid.UUID(metrics_uuid)), metrics_uuid)
            self.assertEqual(config.replace(metrics_uuid, '${metrics.uuid}'), (root / "config/floodgate.yml").read_text())
            self.assertEqual(sorted(p.name for p in floodgate.iterdir()), ["config.yml", "floodgate-sqlite-database.jar"])

    def test_accounts_and_support_install_without_skin_dependency_or_access(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            install_fixture(root)
            fetched = []
            def download(item, folder):
                fetched.append(item["name"])
                folder.mkdir(parents=True, exist_ok=True)
                (folder / item["name"]).write_bytes(b"dependency")
            with patch.object(install_plugins, "download", side_effect=download):
                install_plugins.install(root, ["accounts", "support"])
            self.assertEqual(fetched, ["Geyser.jar"])
            self.assertEqual(sorted(p.name for p in (root / "data/plugins").glob("*.jar")),
                             ["ArkAccounts-1.0.2.jar", "ArkSupport-1.0.1.jar", "Geyser.jar"])
            self.assertFalse((root / "data/plugins/SkinsRestorer").exists())

    def test_defaults_include_six_modules_and_optional_modules_require_accounts(self):
        self.assertEqual(install_plugins.selected_modules(None), ["dino", "atlas", "panel", "accounts", "wardrobe", "support"])
        for modules in (["support"], ["wardrobe"], ["unknown"], ["accounts", "accounts"]):
            with self.subTest(modules=modules), self.assertRaises(ValueError):
                install_plugins.selected_modules(modules)

    def test_all_selected_hashes_checked_before_world_changes_or_download(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            entries = install_fixture(root)
            support = next(item for item in entries if item["module"] == "support")
            (root / support["path"]).write_bytes(b"tampered")
            with patch.object(install_plugins, "download", side_effect=AssertionError("must preflight")):
                with self.assertRaises(ValueError):
                    install_plugins.install(root, ["accounts", "support"])
            self.assertEqual(list((root / "data").iterdir()), [root / "data/ark"])

    def test_native_jar_paths_symlinks_and_existing_configs_are_rejected(self):
        for change in ("symlink", "escape", "config", "floodgate-config", "data-link"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                entries = install_fixture(root)
                accounts = next(item for item in entries if item["module"] == "accounts")
                if change == "symlink":
                    path = root / accounts["path"]
                    path.unlink()
                    path.symlink_to(root / entries[0]["path"])
                elif change == "escape":
                    accounts["path"] = "../private.jar"
                    (root / "own-plugins.json").write_text(json.dumps({"format": 1, "plugins": entries}))
                elif change == "config":
                    path = root / "data/plugins/Geyser-Spigot/config.yml"
                    path.parent.mkdir(parents=True)
                    path.write_text("operator: settings")
                elif change == "floodgate-config":
                    path = root / "data/plugins/floodgate/config.yml"
                    path.parent.mkdir(parents=True)
                    path.write_text("operator: settings")
                    (path.parent / "linked-players.db").write_bytes(b"private links")
                else:
                    (root / "data/plugins").symlink_to(root / "config", target_is_directory=True)
                with patch.object(install_plugins, "download", side_effect=AssertionError("must preflight")):
                    with self.assertRaises(ValueError):
                        install_plugins.install(root, ["accounts"])
                if change == "floodgate-config":
                    self.assertEqual(path.read_text(), "operator: settings")
                    self.assertEqual((path.parent / "linked-players.db").read_bytes(), b"private links")


class DistributionTests(unittest.TestCase):
    def test_editable_dinosaur_models_are_allowed_as_text_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            package_fixture(root)
            name = 'dino-models/models/ark_trex/ark_trex.bbmodel'
            model = root / name
            model.parent.mkdir(parents=True)
            model.write_text('{"textures":[{"source":"data:image/png;base64,cHVibGlj"}]}\n')
            names = json.loads((root / 'distribution-files.json').read_text()) + [name]
            (root / 'distribution-files.json').write_text(json.dumps(sorted(names)))
            with zipfile.ZipFile(next(package.package(root, 'models').glob('*.zip'))) as bundle:
                self.assertEqual(bundle.read(name), model.read_bytes())

    def test_bundle_is_reproducible_contains_allowlisted_sources_and_seven_verified_jars(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            entries = package_fixture(root)
            (root / ".env").write_text("SECRET=private")
            (root / "unlisted.txt").write_text("private")
            first = package.package(root, "test-1")
            second = package.package(root, "test-2")
            archives = [next(path.glob("*.zip")) for path in (first, second)]
            self.assertEqual(archives[0].read_bytes(), archives[1].read_bytes())
            with zipfile.ZipFile(archives[0]) as bundle:
                self.assertEqual(set(bundle.namelist()), {".env.example", "README.md", "check.mjs", "distribution-files.json", "own-plugins.json", *[item["path"] for item in entries]})
                self.assertTrue(all(info.date_time == (2026, 11, 7, 0, 0, 0) for info in bundle.infolist()))
            self.assertEqual(len(list(first.glob("*.jar"))), 7)
            sums = (first / "SHA256SUMS").read_text().splitlines()
            self.assertEqual(len(sums), 8)
            for line in sums:
                digest, name = line.split("  ")
                self.assertEqual(hashlib.sha256((first / name).read_bytes()).hexdigest(), digest)
            with self.assertRaises((ValueError, FileExistsError)):
                package.package(root, "test-1")

    def test_package_rejects_tampered_binaries_private_paths_and_symlink_sources(self):
        for change in ("binary", "private", "escape", "symlink", "unlisted-build"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                entries = package_fixture(root)
                if change == "binary":
                    (root / entries[0]["path"]).write_bytes(b"tampered")
                else:
                    name = {"private": ".env", "escape": "../secret.txt", "symlink": "README.md", "unlisted-build": "dino-plugin/build/private.txt"}[change]
                    if change == "symlink":
                        (root / name).unlink()
                        (root / name).symlink_to(root / ".env.example")
                    elif change != "escape":
                        (root / name).write_text("private")
                    allowlist = [".env.example", "distribution-files.json", name]
                    (root / "distribution-files.json").write_text(json.dumps(sorted(allowlist)))
                with self.assertRaises(ValueError):
                    package.package(root, "test")
                self.assertFalse((root / "dist/test").exists())

    def test_package_rejects_unsafe_version_names(self):
        with tempfile.TemporaryDirectory() as tmp:
            for version in ("../escape", "", ".", "a/b"):
                with self.subTest(version=version), self.assertRaises(ValueError):
                    package.package(Path(tmp), version)
