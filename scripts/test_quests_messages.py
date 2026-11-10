import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from configure_quests_messages import configure


class QuestMessagesTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        patch=self.root/'config/quests/messages-de-DE.json';patch.parent.mkdir(parents=True);patch.write_text(json.dumps({'questListTitle':'Take names','questNotFound':'No <input>; take a name','COMMAND_TAKE_HELP':'<command> <Name>'}))
        self.target=self.root/'data/plugins/Quests/lang/de-DE/strings.yml';self.target.parent.mkdir(parents=True)
        self.original=b'# custom locale\nquestListTitle: "Old"\nquestNotFound: "Missing"\nCOMMAND_TAKE_HELP: "Old help"\ncustom: "Keep this exactly"\n'
        self.target.write_bytes(self.original)

    def test_dry_run_preserves_full_locale_and_creates_no_tempfile(self):
        result=configure(self.root);self.assertTrue(result['changed']);self.assertEqual(self.target.read_bytes(),self.original);self.assertEqual(list(self.target.parent.iterdir()),[self.target])

    def test_exact_guarded_merge_is_idempotent_and_preserves_unrelated_text_and_mode(self):
        self.target.chmod(0o640);before=configure(self.root);configure(self.root,True,before['before_sha256']);content=self.target.read_text();self.assertIn('# custom locale\n',content);self.assertIn('custom: "Keep this exactly"\n',content);self.assertIn('questNotFound: "No <input>; take a name"',content);self.assertEqual(self.target.stat().st_mode&0o777,0o640);self.assertFalse(configure(self.root,True,before['before_sha256'])['changed'])

    def test_stale_or_missing_expected_hash_refuses_writes(self):
        for expected in [None,'0'*64]:
            with self.assertRaises(ValueError):configure(self.root,True,expected)
            self.assertEqual(self.target.read_bytes(),self.original)

    def seed_jar(self):
        self.target.unlink();jar=self.root/'data/plugins/Quests-5.3.2.jar'
        with zipfile.ZipFile(jar,'w')as archive:archive.writestr('lang/de-DE/strings.yml',self.original)
        item={'project':'quests','name':jar.name,'sha512':hashlib.sha512(jar.read_bytes()).hexdigest()};(self.root/'dependencies-runtime.json').write_text(json.dumps([item]));return jar

    def test_fresh_setup_seeds_complete_locale_from_verified_pinned_jar(self):
        self.seed_jar();result=configure(self.root);self.assertEqual(result['before_sha256'],'absent');self.assertFalse(self.target.exists());configure(self.root,True,'absent');self.assertIn('custom: "Keep this exactly"',self.target.read_text());self.assertIn('Take names',self.target.read_text())

    def test_fresh_setup_rejects_wrong_jar_without_creating_locale(self):
        jar=self.seed_jar();jar.write_bytes(b'tampered')
        with self.assertRaises(ValueError):configure(self.root)
        self.assertFalse(self.target.exists())

    def test_symlink_locale_is_rejected(self):
        other=self.root/'outside.yml';other.write_text('leave alone');self.target.unlink();self.target.symlink_to(other)
        with self.assertRaises(ValueError):configure(self.root)
        self.assertEqual(other.read_text(),'leave alone')

    def test_duplicate_or_multiline_target_key_is_rejected(self):
        for content in [self.original+b'questListTitle: "duplicate"\n',self.original.replace(b'questListTitle: "Old"',b'questListTitle: |\n  multiline'),self.original.replace(b'questListTitle: "Old"',b'questListTitle: "Old\n  continued"')]:
            self.target.write_bytes(content)
            with self.assertRaises(ValueError):configure(self.root)
            self.assertEqual(self.target.read_bytes(),content)


if __name__=='__main__':unittest.main()
