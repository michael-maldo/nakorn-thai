import assert from 'node:assert/strict';
import {readdir,readFile} from 'node:fs/promises';
for(const directory of (process.argv.length > 2 ? process.argv.slice(2) : ['dist/assets','dist-staff-pwa-proto/staff-pwa-proto/assets'])){
 const files=(await readdir(directory)).filter(file=>file.endsWith('.js'));
 const source=(await Promise.all(files.map(file=>readFile(`${directory}/${file}`,'utf8')))).join('\n');
 for(const marker of ['Enable printing on this device','deviceEnabled','/cgi-bin/epos/service.cgi','No automatic retry'])assert.ok(source.includes(marker),`${directory}: ${marker}`);
 if(directory.includes('dist-staff-pwa-proto/')){
  for(const marker of ['tmprintassistant://tmprintassistant.epson.com/print','data-type=eposprintxml','EPSON_TM_PRINT_ASSISTANT'])assert.ok(source.includes(marker),`${directory}: ${marker}`);
 }
}
console.log('PASS: built bundles contain their Epson transport and device/uncertain-outcome safeguards.');
