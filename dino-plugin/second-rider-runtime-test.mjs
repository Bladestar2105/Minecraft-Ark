#!/usr/bin/env node
// Two real Java protocol clients in a disposable loopback Paper world.
process.env.DINO_SECOND_RIDER_ONLY='1';
await import('./controls-runtime-test.mjs');
