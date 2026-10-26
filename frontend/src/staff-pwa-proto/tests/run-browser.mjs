import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
for(const name of ['prototype','menuAdmin','ordersDrafts','printingSettings','adminSettings']){
 const path=fileURLToPath(new URL(`./${name}.browser.mjs`,import.meta.url));
 const result=spawnSync(process.execPath,[path],{stdio:'inherit',env:process.env});
 if(result.status!==0)process.exit(result.status||1);
}
