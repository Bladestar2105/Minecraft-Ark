# Quest names and command hints

Quests 5.3.2 numbers `/quests list` entries for display. `/quests take` accepts the **display name**, for example `/quests take Nachtwache`, followed by the normal confirmation. List numbers are not quest IDs and may change when the list is filtered. Nachtwache still requires Material fürs Lager; the hints do not alter prerequisites, progress or rewards.

The supplied Quests config selects German (`language: de-DE`, `language-override-client: true`). `messages-de-DE.json` contains three German wording overrides; it is a merge input, **not** a replacement for the complete plugin language file.

After installing the pinned Quests JAR, an operator can preview the message merge from the installation directory:

```sh
python3 scripts/configure_quests_messages.py
```

Review `target`, `changed`, and `before_sha256`. To write exactly that previewed language file:

```sh
python3 scripts/configure_quests_messages.py --apply --expected-sha256 HASH_FROM_PREVIEW
```

For a fresh locale the preview reports `before_sha256: absent`; use `--expected-sha256 absent`. The utility reads the full German locale from the checksum-verified installed Quests JAR and changes only the three hints. For existing locales it preserves unrelated text, ownership and permissions. Repeating an already-applied merge is harmless. Symlink targets, duplicate/multiline target keys and stale previews are refused.

The utility changes only `data/plugins/Quests/lang/de-DE/strings.yml`. It never edits player profiles, accepts quests, grants rewards, reloads Quests or restarts the server. Operators activate updated wording with their normal Quests reload/start procedure. Do not overwrite the full locale with the three-key merge input.
