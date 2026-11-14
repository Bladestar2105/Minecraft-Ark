import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { template } from './test_templates.mjs';

const cache = join(homedir(), '.npm', '_npx');
const modulePath = process.env.PLAYWRIGHT_MODULE || (existsSync(cache) ? readdirSync(cache) : [])
  .map(name => join(cache, name, 'node_modules/playwright/index.mjs')).find(existsSync);
assert.ok(modulePath, 'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');
const { chromium } = await import(pathToFileURL(modulePath));
const fixture = () => ({
  players: [{ name: 'Alice', world: 'ark', gamemode: 'survival', op: false, whitelisted: true }],
  whitelist: ['Alice'], banned: ['OfflineBan'],
  known_players: [
    { name: '.BedrockName', online: false, whitelisted: false, banned: false },
    { name: 'Alice', online: true, whitelisted: true, banned: false },
    { name: 'OfflineBan', online: false, whitelisted: false, banned: true },
    { name: 'Verwalten', online: false, whitelisted: false, banned: false }
  ]
});
let players = fixture(), authenticated = true, reads = 0, readError = false, actionError = false;
let delayRead = false, delayedRead = null, delayedAction = null;
const writes = [], requests = [];
const server = createServer(async (req, res) => {
  let raw = ''; for await (const chunk of req) raw += chunk;
  const body = raw ? JSON.parse(raw) : undefined;
  const send = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)); };
  if (req.url.startsWith('/static/')) {
    res.writeHead(200, { 'Content-Type': req.url.endsWith('.js') ? 'text/javascript' : req.url.endsWith('.json') ? 'application/json' : 'text/css' });
    return res.end(readFileSync(new URL('.' + req.url.slice(7), new URL('./static/', import.meta.url))));
  }
  if (!req.url.startsWith('/api/')) { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end(template()); }
  requests.push(req.url);
  if (req.url === '/api/session') return send({ authenticated, username: 'owner', csrf: 'history-csrf', auth_type: 'local', is_admin: authenticated, is_player: false });
  if (req.url === '/api/logout') { authenticated = false; return send({ ok: true }); }
  if (!authenticated) return send({ error: 'Sign in' }, 401);
  if (req.url === '/api/modules') return send({ accounts: true, wardrobe: false, support: false });
  if (req.url === '/api/status') return send({ services: [], players: '1 player' });
  if (req.url === '/api/worlds') return send({ worlds: [] });
  if (req.url === '/api/player-accounts') return send({ accounts: [] });
  if (req.url === '/api/players') {
    reads++;
    if (delayRead) { delayRead = false; delayedRead = () => send(players); return; }
    return readError ? send({ error: 'Spielerliste derzeit nicht erreichbar.' }, 503) : send(players);
  }
  if (req.url === '/api/players/action') {
    writes.push({ body, csrf: req.headers['x-csrf-token'] });
    if (actionError) return send({ error: 'Whitelist-Befehl fehlgeschlagen.' }, 503);
    delayedAction = () => {
      players.whitelist.push(body.player);
      players.known_players.find(player => player.name === body.player).whitelisted = true;
      send({ ok: true, output: 'Added to whitelist' });
    };
    return;
  }
  return send({ error: 'Unknown test route: ' + req.url }, 404);
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {}) });
const page = await browser.newPage({ locale: 'de-DE' }), errors = [];
page.on('pageerror', error => errors.push(error.message));
await page.clock.install();
const row = name => page.locator('#known-players .player-row').filter({ has: page.locator('strong', { hasText: name }) });
const refresh = async () => {
  await page.locator('#refresh-players').click();
  await page.waitForFunction(() => !document.getElementById('refresh-players').disabled);
};
try {
  await page.goto(`http://127.0.0.1:${server.address().port}/#players`);
  await page.waitForFunction(() => document.getElementById('players-admin-message').textContent.includes('geladen'));
  assert.equal(await page.getByRole('heading', { name: 'Bisher verbundene Spieler', exact: true }).count(), 1, 'Previously connected players have their own visible list');
  assert.deepEqual(await page.locator('#known-players strong').allTextContents(), ['.BedrockName', 'Alice', 'OfflineBan', 'Verwalten'], 'Offline and online history entries are listed with their literal names');
  assert.match(await row('.BedrockName').textContent(), /Offline/);
  assert.match(await row('Alice').textContent(), /Online/);
  assert.match(await row('Alice').textContent(), /Whitelist/);
  assert.equal(await row('Alice').getByRole('button', { name: 'Zur Whitelist hinzufügen' }).count(), 0, 'Whitelisted players cannot be added again');
  assert.match(await row('OfflineBan').textContent(), /Gesperrt/);
  assert.ok((await page.locator('#player-names option').evaluateAll(options => options.map(option => option.value))).includes('.BedrockName'), 'History names can be selected in the existing action form');
  assert.equal(writes.length, 0, 'Loading history never adds players automatically');
  await page.setViewportSize({ width: 375, height: 812 });
  assert.equal(await row('.BedrockName').evaluate(row => [...row.children].every(child => child.getBoundingClientRect().right <= row.getBoundingClientRect().right + 1)), true, 'Direct actions stay within the history card on narrow screens');
  await page.setViewportSize({ width: 1280, height: 720 });

  const beforeAdd = reads;
  const add = row('.BedrockName').getByRole('button', { name: 'Zur Whitelist hinzufügen' });
  await add.focus(); await page.keyboard.press('Enter');
  await page.waitForFunction(() => document.querySelector('#known-players button[aria-busy="true"]') !== null);
  assert.equal(await add.isDisabled(), true, 'A pending direct action cannot be submitted twice');
  assert.deepEqual(writes, [{ body: { action: 'whitelist-add', player: '.BedrockName' }, csrf: 'history-csrf' }], 'Direct addition uses the existing authenticated action API and preserves the Bedrock prefix');
  delayedAction(); delayedAction = null;
  await page.waitForFunction(() => document.getElementById('whitelist-players').textContent.includes('.BedrockName'));
  assert.ok(reads > beforeAdd, 'A successful addition reloads the native lists');
  assert.equal(await row('.BedrockName').getByRole('button', { name: 'Zur Whitelist hinzufügen' }).count(), 0, 'The refreshed row shows whitelist membership');

  actionError = true;
  await row('OfflineBan').getByRole('button', { name: 'Zur Whitelist hinzufügen' }).click();
  await page.waitForFunction(() => document.getElementById('player-history-message').textContent.includes('fehlgeschlagen'));
  assert.equal(await row('OfflineBan').getByRole('button', { name: 'Zur Whitelist hinzufügen' }).isEnabled(), true, 'A failed addition remains retryable and never shows membership');
  assert.equal(await page.locator('#whitelist-players').textContent(), 'Alice.BedrockName', 'An action failure leaves native membership unchanged');

  readError = true; await refresh();
  assert.equal(await page.locator('#known-players').textContent(), '', 'A failed history read removes stale names');
  assert.equal(await page.locator('#player-names option').count(), 0, 'A failed history read removes stale form suggestions');
  assert.match(await page.locator('#players-admin-message').textContent(), /nicht erreichbar/);
  readError = false; players.history_warning = 'Ein Teil der Spielerhistorie konnte nicht gelesen werden.';
  await refresh();
  assert.match(await page.locator('#player-history-message').textContent(), /nicht gelesen/);
  assert.match(await page.locator('#players-table').textContent(), /Alice/, 'Partial history warnings preserve the native online list');

  await page.locator('[data-language]').selectOption('en');
  assert.equal(await page.getByRole('heading', { name: 'Previously connected players' }).count(), 1, 'History controls translate with the panel');
  assert.equal(await row('Verwalten').locator('strong').textContent(), 'Verwalten', 'Player names matching UI text stay literal during language changes');
  assert.match(await row('OfflineBan').textContent(), /Banned/);
  assert.equal(await page.locator('#player-history-message').textContent(), 'Some player history could not be read.', 'Partial-history warnings translate with the panel');
  await page.locator('[data-language]').selectOption('de');

  players = { players: [], whitelist: [], banned: [], known_players: [] }; await refresh();
  assert.match(await page.locator('#known-players').textContent(), /Noch keine verbundenen Spieler bekannt/);
  players = fixture(); await refresh();
  delayRead = true; await page.locator('#refresh-players').click();
  await page.waitForFunction(() => document.getElementById('refresh-players').disabled);
  await page.locator('#player-logout').click();
  await page.locator('#pair-view').waitFor({ state: 'visible' });
  const lateRead = page.waitForResponse(response => response.url().endsWith('/api/players'));
  delayedRead(); delayedRead = null; await lateRead;
  await page.waitForFunction(() => !document.getElementById('refresh-players').disabled);
  assert.equal(await page.locator('#known-players').textContent(), '', 'Logout and late responses cannot retain or restore history names');
  assert.equal(await page.locator('#player-names').textContent(), '');
  assert.equal(await page.locator('#player-history-message').textContent(), '');
  assert.deepEqual(errors, []);
  assert.equal(requests.filter(path => path === '/api/players/action').length, 2);
  console.log('PASS player history: offline list, keyboard whitelist add/CSRF, native refresh, errors, partial warning, i18n, empty state and logout/late responses');
} finally {
  delayedAction?.(); delayedRead?.();
  await page.close(); await browser.close(); await new Promise(resolve => server.close(resolve));
}
