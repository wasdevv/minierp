// Opens every page in a real Chrome, logged in, and fails on Content-Security-Policy violations, JavaScript
// errors or failed requests: the part of the security headers that unit tests cannot see.
//
//   node scripts/browser-check.mjs <base-url> <user> <password>   (CHROME=/path/to/chrome, default google-chrome)
//
// Talks to Chrome over the DevTools protocol with Node's built-in WebSocket (Node 22+); no dependencies.
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const [base = 'http://localhost:8080', user = 'admin', password] = process.argv.slice(2);
if (!password) {
  console.error('usage: node scripts/browser-check.mjs <base-url> <user> <password>');
  process.exit(2);
}
const pages = ['/', '/products', '/products/new', '/customers', '/customers/new', '/stock', '/sales', '/sales/new',
  '/reports/sales'];

const profile = mkdtempSync(join(tmpdir(), 'browser-check-'));
const port = 9300 + Math.floor(Math.random() * 500);
const chrome = spawn(process.env.CHROME || 'google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu',
  `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let target;
for (let i = 0; i < 50 && !target; i++) {
  try {
    target = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((t) => t.type === 'page');
  } catch { await sleep(200); }
}
if (!target) { console.error('Chrome did not start'); chrome.kill(); process.exit(2); }

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));
let nextId = 0;
const pending = new Map();
const problems = [];
let current = '(login)';
ws.addEventListener('message', ({ data }) => {
  const msg = JSON.parse(data);
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg.result); pending.delete(msg.id); return; }
  if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
    problems.push(`${current}: ${msg.params.entry.text}`);
  }
  if (msg.method === 'Runtime.exceptionThrown') {
    problems.push(`${current}: JS error ${msg.params.exceptionDetails.exception?.description ?? msg.params.exceptionDetails.text}`);
  }
  if (msg.method === 'Network.loadingFailed' && !msg.params.canceled) {
    problems.push(`${current}: request failed (${msg.params.errorText}, ${msg.params.blockedReason ?? 'not blocked'})`);
  }
});
const send = (method, params = {}) => new Promise((resolve) => {
  const id = ++nextId;
  pending.set(id, resolve);
  ws.send(JSON.stringify({ id, method, params }));
});
const loaded = async (url) => {
  await send('Page.navigate', { url });
  for (let i = 0; i < 50; i++) {
    const { result } = await send('Runtime.evaluate', { expression: 'document.readyState' });
    if (result.value === 'complete') break;
    await sleep(100);
  }
  await sleep(500);
};
const evaluate = async (expression) => (await send('Runtime.evaluate', { expression, awaitPromise: true })).result.value;

await send('Page.enable');
await send('Runtime.enable');
await send('Log.enable');
await send('Network.enable');

await loaded(`${base}/login`);
await evaluate(`document.querySelector('#username').value = ${JSON.stringify(user)};
  document.querySelector('#password').value = ${JSON.stringify(password)};
  document.querySelector('form').submit(); true`);
await sleep(1500);
if ((await evaluate('location.pathname')) === '/login') {
  console.error('login failed'); chrome.kill(); process.exit(1);
}

for (const path of pages) {
  current = path;
  await loaded(base + path);
  const title = await evaluate('document.title');
  const styled = await evaluate(`getComputedStyle(document.body).backgroundColor !== 'rgba(0, 0, 0, 0)'`);
  console.log(`${styled ? 'ok ' : 'NO CSS'}  ${path}  (${title})`);
  if (!styled) problems.push(`${path}: stylesheet not applied`);
}

ws.close();
await new Promise((resolve) => { chrome.once('exit', resolve); chrome.kill(); });
try { rmSync(profile, { recursive: true, force: true }); } catch { /* Chrome may still flush its profile */ }
if (problems.length) {
  console.error(`\n${problems.length} problem(s):\n` + problems.map((p) => `  - ${p}`).join('\n'));
  process.exit(1);
}
console.log(`\n${pages.length} pages, no CSP violations, JS errors or failed requests.`);
