# Prism container transaction fixes

This is a modified build of [Prism v4.5](https://github.com/prism/Prism/tree/57cbaa8ca9af946fa6c8a1d1b420a46e8a5f36d0), labeled `4.5+container-audit.1`. It fixes odd half-stack withdrawals rounding down and hotbar/offhand swaps omitting the item inserted into the container. Existing item validation and logging filters are retained. `provenance.json` records the official source, original binary, patch and modified binary fingerprints.

## Build

1. Download the [exact upstream tar.gz archive](https://codeload.github.com/prism/Prism/tar.gz/57cbaa8ca9af946fa6c8a1d1b420a46e8a5f36d0) and verify its SHA-256 from `provenance.json`.
2. Extract into a fresh directory and apply `patch -p1 -F0 < /path/to/container-transactions.patch` from that directory.
3. Use JDK 25 and run `./gradlew --no-daemon clean :prism-paper:test :prism-paper:checkstyleMain :prism-paper-loader:shadowJar -PskipWeb`.
4. The artifact is under `prism-paper-loader/build/libs/`. Its plugin descriptor must identify `4.5+container-audit.1`; verify the expected artifact SHA-256 before installation. Keep the embedded upstream MIT and third-party notices.

The optional web frontend is omitted and the deployed web listener is disabled. Rebuild with the frontend before enabling that feature. The application installer downloads the pinned, tested dependency artifact; it does not silently patch arbitrary upstream versions. Keep this pin until a replacement has passed the same real-container transaction regressions.

## Verification

Use a disposable Paper server with the pinned Prism build and NBTAPI 2.16.1. Verify full-stack insert/remove, taking half of an odd 17-item stack (nine removed, eight remaining), number-key and offhand swaps in both directions, empty-slot swaps, item metadata, persistence after a clean restart and absence of chat/command logging. Check recorded actor, time, world, coordinates, item and amount against actual inventory changes. Never run this fixture against a player's live containers.

The native acceptance run passed full-stack transfers, odd half-stacks, occupied/empty number-key swaps, offhand swaps, custom item metadata, privacy and restart persistence on Paper 26.3 build 249 with Java 25. The client fixture used a Java protocol client through a protocol proxy; physical Bedrock/console input was not part of this check. Two clean source builds produced the pinned binary hash.
