import assert from 'node:assert/strict';
import {readdir,readFile} from 'node:fs/promises';
for(const directory of (process.argv.length > 2 ? process.argv.slice(2) : ['dist/assets','dist-staff-pwa-proto/staff-pwa-proto/assets'])){
 const files=(await readdir(directory)).filter(file=>file.endsWith('.js'));
 const source=(await Promise.all(files.map(file=>readFile(`${directory}/${file}`,'utf8')))).join('\n');
 assert.ok(source.includes('Experimental browser printing enabled by build configuration'),directory);
 assert.ok(!source.includes('Browser printing is disabled in this build. Configuration can be saved'),directory);
 for(const marker of ['Enable printing on this device','deviceEnabled','/cgi-bin/epos/service.cgi','No automatic retry'])assert.ok(source.includes(marker),`${directory}: ${marker}`);
}
console.log('PASS: both production bundles contain enabled Epson capability and device safeguards.');
