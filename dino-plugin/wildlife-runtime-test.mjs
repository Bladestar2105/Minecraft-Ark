#!/usr/bin/env node
// Reuse the disposable native fixture and protocol clients for world-population checks.
process.env.DINO_WILDLIFE_ONLY = '1';
await import('./controls-runtime-test.mjs');
