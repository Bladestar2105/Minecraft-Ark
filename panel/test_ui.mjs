import { template } from './test_templates.mjs';
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync } from 'node:fs';
import vm from 'node:vm';
const source = readFileSync(new URL('./static/app.js', import.meta.url), 'utf8');
const context = vm.createContext({});
vm.runInContext(source.slice(0, source.indexOf('(async () =>')), context);
const edit = (input, range, text) => JSON.parse(JSON.stringify(context.completionEdit(input, range, text)));
const choices = (data, input) => context.completionChoices(data, input);
assert.deepEqual(edit('give Al stone', { start: 5, end: 7 }, 'Alice'), { value: 'give Alice stone', caret: 10 });
assert.deepEqual(edit('say 😀pla suffix', { start: 6, end: 9 }, 'player'), { value: 'say 😀player suffix', caret: 12 });
assert.deepEqual(edit('time ', { start: 5, end: 5 }, 'day'), { value: 'time day', caret: 8 });
assert.deepEqual(edit('say old suffix', { start: 4, end: 7 }, '<img onerror=alert(1)>'), { value: 'say <img onerror=alert(1)> suffix', caret: 26 });
assert.throws(() => edit('abc', { start: -1, end: 2 }, 'x'));
assert.throws(() => edit('abc', { start: 2, end: 9 }, 'x'));
assert.equal(choices({ available: true, ready: false, start: 0, end: 3, suggestions: [{ text: 'list' }] }, 'lis'), null);
assert.equal(choices({ available: true, ready: true, start: 2, end: 1, suggestions: [{ text: 'x' }] }, 'abc'), null);
const bounded = choices({ available: true, ready: true, start: 0, end: 1, suggestions: Array.from({ length: 80 }, (_, i) => ({ text: `entry${i}` })) }, 'a');
assert.equal(bounded.suggestions.length, 50);
assert.equal(choices({ available: true, ready: true, start: 0, end: 1, suggestions: [{ text: 4 }, { text: 'safe' }] }, 'a').suggestions.length, 1);
const html = template();
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
assert.equal(new Set(ids).size, ids.length, 'Static controls must have unique IDs');
for (const [, id] of source.matchAll(/\$\("([^"]+)"\)/g)) assert.ok(ids.includes(id), `Missing control: ${id}`);
console.log('PASS completion ranges/UTF16/literal text/limits and all static control IDs');


if (process.argv.includes('--browser')) await liveSettingsBrowserTests();

async function liveSettingsBrowserTests() {
  const { createServer } = await import('node:http');
  const { pathToFileURL } = await import('node:url');
  const { readdirSync, existsSync } = await import('node:fs');
  const { homedir } = await import('node:os');
  const { join } = await import('node:path');
  let modulePath = process.env.PLAYWRIGHT_MODULE;
  if (!modulePath) {
    const cache = join(homedir(), '.npm', '_npx');
    modulePath = (existsSync(cache) ? readdirSync(cache) : []).map(name => join(cache, name, 'node_modules/playwright/index.mjs')).find(existsSync);
  }
  assert.ok(modulePath, 'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');
  const { chromium } = await import(pathToFileURL(modulePath));
  const clone = value => JSON.parse(JSON.stringify(value));
  const stamp = '2026-11-04T12:00:00Z';
  const fields = [
    { key: 'motd', label: 'Serverbeschreibung', description: 'Text in der Serverliste.', type: 'string' },
    { key: 'difficulty', label: 'Standard-Schwierigkeit', description: 'Startvorgabe; Welten haben eigene Werte.', type: 'choice', options: ['easy', 'normal', 'hard'] },
    { key: 'view-distance', label: 'Sichtweite', description: 'Entfernung in Chunks.', type: 'integer' },
    { key: 'allow-flight', label: 'Flugbewegungen erlauben', description: 'Verhindert Flug-Kicks.', type: 'boolean' },
    { key: 'player-idle-timeout', label: 'Leerlauf', description: '0 deaktiviert den Kick.', type: 'integer' }
  ];
  const minecraft = { settings: { motd: 'ARK original', difficulty: 'easy', 'view-distance': 6, 'allow-flight': false, 'player-idle-timeout': 0 }, fields,
    version: 'mc1', observed_at: stamp, sources: { motd: 'file', difficulty: 'file', 'view-distance': 'file', 'allow-flight': 'file', 'player-idle-timeout': 'default' },
    runtime: { available: true, observed_at: stamp, settings: { motd: 'ARK active', difficulty: 'normal', 'view-distance': 8, 'allow-flight': false, 'player-idle-timeout': 0 }, scopes: { difficulty: 'primary_world' } } };
  const pluginNames = [...html.matchAll(/<input[^>]+name="([^"]+)"[^>]*>/g)].map(m => m[1]);
  const geyser = { settings: {}, version: 'g1', observed_at: stamp, sources: {}, runtime: { available: true, observed_at: stamp, settings: {}, scopes: {} } };
  for (const key of pluginNames) {
    if (!key.includes('.') && key !== 'debug-mode') continue;
    geyser.settings[key] = key === 'gameplay.server-name' ? 'ARK Bedrock' : key === 'motd.max-players' ? 10 : ['motd.primary-motd', 'motd.secondary-motd'].includes(key) ? '' : false;
    geyser.runtime.settings[key] = geyser.settings[key]; geyser.sources[key] = 'file';
  }
  const makeWorld = (key, name, difficulty) => ({ key, name, difficulty, time: 0, weather: 'clear', spawn: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }, border: { size: 20480, centerX: 0, centerZ: 0 },
    rules: [{ name: 'minecraft:keep_inventory', label: 'Inventar beim Tod behalten', description: 'Aus: Gegenstände fallen beim Tod herunter.', type: 'boolean', value: false, default: false },
      { name: 'minecraft:raids', label: 'Dorfüberfälle erlauben', description: 'Erlaubt Plünderer-Überfälle auf Dörfer.', type: 'boolean', value: true, default: true }] });
  let worlds = { worlds: [makeWorld('minecraft:overworld', 'ark', 'normal'), makeWorld('minecraft:ark_creative', 'ark_creative', 'peaceful')], observed_at: stamp };
  const writes = []; const accountWrites = []; const mutationTokens = []; let rawContent = 'config-version: 12\n'; let sessionCsrf='test'; let gets = 0; let deferred = null; let deferWorld = false; let deferMinecraft = false; let deferredMinecraft = null; let deferWorldWrite = false; let deferredWorldWrite = null;
  const server = createServer(async (req, res) => {
    let raw = ''; for await (const piece of req) raw += piece;
    const body = raw ? JSON.parse(raw) : null;
    if (req.method !== 'GET') mutationTokens.push(req.headers['x-csrf-token']);
    const send = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)); };
    if (req.url.startsWith('/static/')) { res.writeHead(200, { 'Content-Type': req.url.endsWith('.js') ? 'text/javascript' : req.url.endsWith('.json') ? 'application/json' : 'text/css' }); res.end(readFileSync(new URL('.' + req.url.slice(7), new URL('./static/', import.meta.url)))); return; }
    if (!req.url.startsWith('/api/')) { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end(html.replace(/{{ url_for\('static', filename='([^']+)'\) }}/g, '/static/$1')); return; }
    if (req.url === '/api/modules') return send({accounts:true,wardrobe:true,support:true});
    if (req.url === '/api/session') return send({ authenticated: true, csrf: sessionCsrf, username: 'admin', auth_type:'local',is_admin:true,is_player:false });
    if (req.url === '/api/status') return send({ services: [], players: '0 players' });
    if (req.url === '/api/password') {
      accountWrites.push(body);
      if (body.current_password !== 'current-test-password') return send({ error: 'Das aktuelle Passwort stimmt nicht.' }, 403);
      sessionCsrf='changed-csrf'; return send({ ok: true, csrf: sessionCsrf, username: 'admin' });
    }
    if (req.url === '/api/settings/minecraft') {
      if (req.method === 'GET') { gets++; if (deferMinecraft) { deferMinecraft = false; const snapshot = clone(minecraft); deferredMinecraft = () => send(snapshot); return; } return send(minecraft); }
      writes.push(body);
      if (body.version !== minecraft.version) return send({ error: 'Die Datei wurde inzwischen geändert. Bitte neu laden.' }, 409);
      minecraft.settings = body.settings; minecraft.version = 'mc4'; return send({ ok: true, version: minecraft.version, restart_required: true });
    }
    if (req.url === '/api/settings/geyser') {
      if (req.method === 'GET') return send(geyser);
      writes.push(body);
      if (body.version !== geyser.version) return send({ error: 'Die Datei wurde inzwischen geändert. Bitte neu laden.' }, 409);
      geyser.settings = body.settings; return send({ ok: true, version: 'g3' });
    }
    if (req.url === '/api/configs') return send({ configs: [{ id: 'geyser', name: 'Geyser', available: true, description: 'Verbindet Bedrock mit Java.', reload_target: 'geyser' }] });
    if (req.url === '/api/configs/geyser') {
      if (req.method === 'GET') return send({ content: rawContent, version: geyser.version });
      writes.push(body); if (body.version !== geyser.version) return send({ error: 'Die Datei wurde inzwischen geändert. Bitte neu laden.' }, 409);
      rawContent = body.content; return send({ ok: true, version: 'g4' });
    }
    if (req.url === '/api/worlds') {
      if (deferWorld) { deferWorld = false; const snapshot = clone(worlds); deferred = () => send(snapshot); return; }
      return send(worlds);
    }
    if (req.url.startsWith('/api/worlds/') && req.method === 'PUT') {
      writes.push(body); worlds.worlds[0].rules[0].value = false; if (deferWorldWrite) { deferWorldWrite = false; deferredWorldWrite = () => send({ ok: true, output: 'Canonical false' }); return; } return send({ ok: true, output: 'Canonical false' });
    }
    return send({ error: 'Unknown test route: ' + req.url }, 404);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {}) });
  const page = await browser.newPage({ locale: 'de-DE' }); const errors = []; const dialogs = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('dialog', async dialog => { dialogs.push(dialog.message()); await dialog.accept(); });
  await page.clock.install();
  try {
    await page.goto(`http://127.0.0.1:${server.address().port}/#minecraft`);
    const motd = page.locator('[name="motd"]'); await motd.waitFor({ state: 'visible' });
    assert.equal(await page.locator('#account-button').count(), 1, 'Signed-in users can open account settings');
    await motd.fill('Mein ungespeicherter Entwurf');
    await page.locator('#account-button').click();
    assert.equal(await page.getByRole('dialog', { name: 'Passwort ändern' }).isVisible(), true);
    await page.getByLabel('Aktuelles Passwort', { exact: true }).fill('incorrect-password');
    await page.getByLabel('Neues Passwort', { exact: true }).fill('new-test-password');
    await page.getByLabel('Neues Passwort bestätigen', { exact: true }).fill('new-test-password');
    await page.getByRole('button', { name: 'Passwort speichern' }).click();
    await page.waitForFunction(() => document.getElementById('password-message').textContent.includes('stimmt nicht'));
    assert.equal(await page.locator('#app-view').isVisible(), true, 'Wrong current password does not log out the browser');
    await page.getByLabel('Aktuelles Passwort', { exact: true }).fill('current-test-password');
    await page.getByRole('button', { name: 'Passwort speichern' }).click();
    await page.waitForFunction(() => document.getElementById('password-message').textContent.includes('Passwort geändert'));
    assert.deepEqual(accountWrites.at(-1), { current_password: 'current-test-password', new_password: 'new-test-password', confirm_password: 'new-test-password' });
    for (const id of ['current-password', 'new-password', 'confirm-password']) assert.equal(await page.locator(`#${id}`).inputValue(), '');
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#password-dialog').isVisible(), false);
    assert.equal(await motd.inputValue(), 'Mein ungespeicherter Entwurf', 'Account changes preserve game form drafts');
    await page.locator('#minecraft-form button[type=submit]').click();
    await page.waitForFunction(() => document.getElementById('minecraft-message').textContent.includes('Gespeichert'));
    assert.equal(mutationTokens.at(-1), 'changed-csrf', 'Subsequent writes use the rotated CSRF token');
    minecraft.settings.motd = 'ARK original'; minecraft.version = 'mc1'; await page.locator('#refresh-settings').click();
    await page.waitForFunction(() => document.querySelector('[name="motd"]').value === 'ARK original');
    const difficulty = page.locator('#minecraft-form [name="difficulty"]');
    assert.equal(await difficulty.inputValue(), 'easy');
    assert.match(await difficulty.locator('..').innerText(), /Aktiv in der Hauptwelt:.*Normal/i);
    for (const input of await page.locator('#minecraft-dynamic-fields [name]').all()) {
      const described = await input.getAttribute('aria-describedby'); assert.ok(described, 'Every setting has accessible help/state');
      for (const id of described.split(/\s+/)) assert.equal(await page.locator(`[id="${id}"]`).count(), 1);
    }
    assert.match(await page.locator('[name="allow-flight"]').locator('..').innerText(), /Aktiv:.*Aus/);
    assert.match(await page.locator('[name="player-idle-timeout"]').locator('..').innerText(), /Aktiv:.*0/);
    mkdirSync(new URL('../output/playwright/', import.meta.url), { recursive: true });
    await page.screenshot({ path: new URL('../output/playwright/panel-live-help-minecraft.png', import.meta.url).pathname, fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: new URL('../output/playwright/panel-live-help-mobile.png', import.meta.url).pathname, fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    minecraft.settings.motd = 'Extern geändert'; minecraft.version = 'mc2';
    await page.locator('[data-view="worlds"]').click(); await page.locator('#world-select').waitFor({ state: 'visible' });
    await page.locator('[data-view="minecraft"]').click();
    await page.waitForFunction(() => document.querySelector('[name="motd"]').value === 'Extern geändert');
    minecraft.settings.motd = 'Weitere Änderung'; minecraft.version = 'mc3'; minecraft.runtime.settings.motd = 'Aktuell im Server';
    const oldGets = gets; deferMinecraft = true;
    await page.clock.runFor(15050);
    for (let i = 0; !deferredMinecraft && i < 100; i++) await new Promise(resolve => setTimeout(resolve, 5));
    assert.ok(deferredMinecraft); await motd.fill('Mein Entwurf'); await motd.evaluate(el => el.setSelectionRange(2, 5)); deferredMinecraft(); deferredMinecraft = null; await page.waitForFunction(() => document.querySelector('[name="motd"]').closest('.field').textContent.includes('Aktuell im Server'));
    assert.ok(gets > oldGets); assert.equal(await motd.inputValue(), 'Mein Entwurf'); assert.deepEqual(await motd.evaluate(el => [el.selectionStart, el.selectionEnd]), [2, 5]); assert.equal(dialogs.length, 0);
    await page.locator('#minecraft-form button[type=submit]').click();
    await page.waitForFunction(() => document.getElementById('minecraft-message').textContent.includes('inzwischen'));
    assert.equal(writes.at(-1).version, 'mc2'); assert.equal(await motd.inputValue(), 'Mein Entwurf');
    await page.locator('#refresh-settings').click(); await page.waitForFunction(() => document.querySelector('[name="motd"]').value === 'Weitere Änderung');
    await motd.fill('Gespeichert'); await page.locator('#minecraft-form button[type=submit]').click();
    await page.waitForFunction(() => document.getElementById('minecraft-message').textContent.includes('Gespeichert'));
    assert.equal(writes.at(-1).version, 'mc3');
    await page.locator('[data-view="geyser"]').click(); await page.locator('#gs-server-name').waitFor({ state: 'visible' });
    await page.locator('#view-geyser details summary').click(); await page.locator('#gs-server-name').fill('Bedrock Entwurf'); await page.locator('#geyser-content').fill('YAML Entwurf');
    geyser.version = 'g2'; geyser.runtime.settings['gameplay.server-name'] = 'Bedrock live';
    await page.clock.runFor(15050); assert.equal(await page.locator('#gs-server-name').inputValue(), 'Bedrock Entwurf'); assert.equal(await page.locator('#geyser-content').inputValue(), 'YAML Entwurf');
    await page.locator('#save-geyser-settings').click(); await page.waitForFunction(() => document.getElementById('geyser-settings-message').textContent.includes('inzwischen'));
    assert.equal(writes.at(-1).version, 'g1'); assert.equal(await page.locator('#geyser-content').inputValue(), 'YAML Entwurf');
    await page.locator('[data-view="worlds"]').click();
    const keep = page.locator('[name="minecraft:keep_inventory"]'); await keep.waitFor({ state: 'visible' });
    assert.match(await keep.locator('..').innerText(), /Gegenstände fallen/);
    await page.screenshot({ path: new URL('../output/playwright/panel-live-help-worlds.png', import.meta.url).pathname, fullPage: true });
    await page.locator('#world-controls details summary').first().click();
    await page.locator('#rule-search').fill('Inventar');
    await keep.check(); await page.locator('#world-controls [name="time"]').fill('1500');
    worlds.worlds[0].difficulty = 'hard'; await page.clock.runFor(15050);
    assert.equal(await page.locator('#world-controls [name="difficulty"]').inputValue(), 'hard'); assert.equal(await keep.isChecked(), true);
    assert.equal(await page.locator('#world-controls details').first().getAttribute('open'), '');
    assert.equal(await page.locator('#rule-search').inputValue(), 'Inventar');
    assert.equal(await page.locator('[name="minecraft:raids"]').locator('xpath=ancestor::form').isHidden(), true);
    await keep.locator('xpath=ancestor::form').locator('button[type=submit]').click();
    await page.waitForFunction(() => !document.querySelector('[name="minecraft:keep_inventory"]').checked);
    assert.equal(await page.locator('#world-controls [name="time"]').inputValue(), '1500', 'Another form draft survives readback');
    deferWorld = true; await page.clock.runFor(15050); await page.waitForFunction(() => document.getElementById('world-select').value === 'minecraft:overworld');
    await page.locator('#world-select').selectOption('minecraft:ark_creative');
    await page.waitForFunction(() => document.getElementById('world-select').value === 'minecraft:ark_creative' && document.querySelector('#world-controls [name="difficulty"]').value === 'peaceful');
    assert.ok(deferred); deferred(); deferred = null; await page.waitForTimeout(20);
    assert.equal(await page.locator('#world-select').inputValue(), 'minecraft:ark_creative'); assert.equal(await page.locator('#world-controls [name="difficulty"]').inputValue(), 'peaceful');
    await page.locator('#world-select').selectOption('minecraft:overworld');
    await page.waitForFunction(() => document.getElementById('world-controls').dataset.world === 'minecraft:overworld');
    await keep.check(); deferWorldWrite = true;
    await keep.locator('xpath=ancestor::form').locator('button[type=submit]').click();
    for (let i = 0; !deferredWorldWrite && i < 100; i++) await new Promise(resolve => setTimeout(resolve, 5));
    assert.ok(deferredWorldWrite); deferWorld = true;
    await page.locator('#world-select').selectOption('minecraft:ark_creative');
    for (let i = 0; !deferred && i < 100; i++) await new Promise(resolve => setTimeout(resolve, 5));
    assert.ok(deferred); deferredWorldWrite(); deferredWorldWrite = null;
    await page.waitForTimeout(20); deferred(); deferred = null;
    await page.waitForFunction(() => document.getElementById('world-controls').dataset.world === 'minecraft:ark_creative');
    assert.equal(await page.locator('#world-select').inputValue(), 'minecraft:ark_creative', 'Old save readback must not cancel a newer world selection');
    await page.locator('[data-view="configs"]').click();
    await page.locator('#view-configs details summary').click();
    await page.locator('#advanced-content').fill('Weiterer YAML-Entwurf');
    geyser.version = 'g3'; rawContent = 'config-version: 12\n# extern\n';
    await page.clock.runFor(15050);
    assert.equal(await page.locator('#advanced-content').inputValue(), 'Weiterer YAML-Entwurf');
    await page.locator('#save-advanced').click();
    await page.waitForFunction(() => document.getElementById('advanced-message').textContent.includes('inzwischen'));
    assert.equal(writes.at(-1).version, 'g2');
    assert.equal(await page.locator('#advanced-content').inputValue(), 'Weiterer YAML-Entwurf');
    await page.locator('[data-view="minecraft"]').click();
    await motd.fill('Entwurf ohne Live-Verbindung');
    minecraft.runtime = { available: false, observed_at: stamp, settings: {}, scopes: {} };
    await page.clock.runFor(15050);
    await page.waitForFunction(() => document.getElementById('minecraft-live-status').textContent.includes('nicht verfügbar'));
    assert.equal(await motd.inputValue(), 'Entwurf ohne Live-Verbindung');
    assert.match(await difficulty.locator('..').innerText(), /Aktiver Wert derzeit nicht verfügbar/);
    assert.doesNotMatch(await difficulty.locator('..').innerText(), /Aktiv in der Hauptwelt/);
    await motd.fill(minecraft.settings.motd);
    await page.locator('[name="player-idle-timeout"]').fill('');
    await page.locator('#settings-title').click();
    await page.clock.runFor(15050);
    assert.equal(await page.locator('[name="player-idle-timeout"]').inputValue(), '', 'An invalid empty numeric draft must not turn into zero');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('#account-button').click();
    const accountBounds = await page.locator('#password-dialog').evaluate(el => {
      const bounds = el.getBoundingClientRect();
      return { left: bounds.left, right: bounds.right, bottom: bounds.bottom, width: innerWidth, height: innerHeight };
    });
    assert.ok(accountBounds.left >= 0 && accountBounds.right <= accountBounds.width && accountBounds.bottom <= accountBounds.height, 'Account dialog fits a mobile viewport');
    await page.screenshot({ path: new URL('../output/playwright/panel-account-mobile.png', import.meta.url).pathname, fullPage: true });
    await page.keyboard.press('Escape');
    const draftBeforeLanguage=await motd.inputValue();
    await page.locator('#admin-language').selectOption('en');
    await page.getByRole('link',{name:'Overview',exact:true}).waitFor();
    assert.equal(await motd.inputValue(),draftBeforeLanguage,'Language switching preserves admin drafts');
    assert.equal(await page.locator('[name="player-idle-timeout"]').inputValue(),'');
    assert.equal(await page.locator('.language-control [data-wiki="Administration"]').getAttribute('href'),'https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration');
    await page.locator('#settings-search').fill('view distance');
    assert.equal(await page.locator('[name="view-distance"]').isVisible(),true,'English search includes translated setting labels');
    await page.locator('#settings-search').fill('');
    await page.locator('#admin-language').selectOption('de');
    await page.getByRole('link',{name:'Übersicht',exact:true}).waitFor();
    assert.deepEqual(errors, []);
    console.log('PASS browser account password/CSRF/draft/mobile flow, live/default values, accessible help, external refresh, dirty/version guards, canonical world readback and stale world responses');
  } finally { deferred?.(); deferredMinecraft?.(); deferredWorldWrite?.(); await page.close(); await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
