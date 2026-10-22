import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
const manifest = JSON.parse(await readFile('dist/staff/manifest.webmanifest', 'utf8'));
assert.equal(manifest.start_url, '/staff/'); assert.equal(manifest.scope, '/staff/');
for (const icon of [...manifest.icons, {src:'/staff/icon-180.png'}]) await access(`dist${icon.src}`);
const worker = await readFile('dist/staff/sw.js', 'utf8');
const assets = JSON.parse(worker.match(/ASSETS=(\[[^;]+\]);/)[1]);
assert.ok(assets.length >= 2);
for (const asset of assets) { assert.match(asset, /^\/assets\/.+\.(js|css)$/); await access(`dist${asset}`); }
assert.ok(worker.includes("event.request.method!=='GET'"));
assert.ok(worker.includes('ASSETS.includes(url.pathname)'));
const staff = await readFile('dist/staff/index.html', 'utf8');
assert.ok(staff.includes('/staff/manifest.webmanifest')); assert.ok(staff.includes('viewport-fit=cover'));
const website = await readFile('dist/index.html', 'utf8');
assert.ok(!website.includes('/staff/manifest.webmanifest'));
console.log('PASS: production staff artifacts, hashed static cache allowlist, icons and separate website entry.');
