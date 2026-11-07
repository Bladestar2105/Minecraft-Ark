import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import install_plugins
import package


PLUGINS = {"dino": ("ArkDinos", "1.0.0"), "atlas": ("ArkAtlas", "1.0.0"),
           "panel": ("ArkPanelBridge", "1.0.0"), "accounts": ("ArkAccounts", "1.0.1"),
           "wardrobe": ("ArkWardrobe", "1.0.0"), "support": ("ArkSupport", "1.0.0"),
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
    for name in ("geyser.yml", "skinsrestorer.yml", "permissions.yml"):
        (root / "config" / name).write_text("fixture: true\n")
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


class InstallerTests(unittest.TestCase):
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
                             ["ArkAccounts-1.0.1.jar", "ArkSupport-1.0.0.jar", "Geyser.jar"])
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
        for change in ("symlink", "escape", "config", "data-link"):
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
                else:
                    (root / "data/plugins").symlink_to(root / "config", target_is_directory=True)
                with patch.object(install_plugins, "download", side_effect=AssertionError("must preflight")):
                    with self.assertRaises(ValueError):
                        install_plugins.install(root, ["accounts"])


class DistributionTests(unittest.TestCase):
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
