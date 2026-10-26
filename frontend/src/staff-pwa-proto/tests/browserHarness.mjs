import {mkdir,writeFile} from 'node:fs/promises';
export async function browser(){
 const debug=process.env.CHROME_DEBUG_URL||'http://127.0.0.1:9225';
 const page=await(await fetch(`${debug}/json/new?about:blank`,{method:'PUT'})).json();
 const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(resolve=>ws.addEventListener('open',resolve,{once:true}));
 let sequence=0;const pending=new Map(),errors=[];
 ws.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id){const waiter=pending.get(message.id);if(!waiter)return;pending.delete(message.id);clearTimeout(waiter.timeout);message.error?waiter.reject(Error(JSON.stringify(message.error))):waiter.resolve(message.result);}else if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails);});
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence,timeout=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout '+method));},20000);pending.set(id,{resolve,reject,timeout});ws.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const value=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true,userGesture:true});if(value.exceptionDetails)throw Error(JSON.stringify(value.exceptionDetails));return value.result.value;};
 const waitFor=async expression=>{for(let i=0;i<180;i++){if(await evaluate(`(async()=>Boolean(await (${expression})))()`))return;await new Promise(resolve=>setTimeout(resolve,40));}throw Error('Timed out: '+expression+'\n'+await evaluate('document.body.innerText'));};
 const frame=()=>evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 const click=async(text,selector='button')=>{const target=`[...document.querySelectorAll(${JSON.stringify(selector)})].find(e=>(e.textContent.trim()===${JSON.stringify(text)}||e.getAttribute('aria-label')===${JSON.stringify(text)})&&!e.disabled&&!e.closest('fieldset:disabled')&&!e.closest('[hidden]'))`;await waitFor(target);await evaluate(`${target}.click()`);await frame();};
 const field=async(label,value)=>{await evaluate(`(()=>{const label=[...document.querySelectorAll('label')].find(l=>l.textContent.trim().startsWith(${JSON.stringify(label)}));if(!label)throw Error('Missing label');const el=label.querySelector('input,select,textarea');Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`);await frame();};
 const check=async label=>{await evaluate(`(()=>{const label=[...document.querySelectorAll('label')].find(l=>l.textContent.trim().startsWith(${JSON.stringify(label)}));label.querySelector('input[type=checkbox]').click();})()`);await frame();};
 const screenshot=async name=>{const dir=process.env.PROTO_ARTIFACTS||'/tmp/nakorn-staff-proto';await mkdir(dir,{recursive:true});const result=await send('Page.captureScreenshot',{format:'png'});await writeFile(`${dir}/${name}.png`,Buffer.from(result.data,'base64'));};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 // Browser-level fail-safe: JS fixture bugs cannot fall through to a live API.
 await send('Network.setBlockedURLs',{urls:['http://127.0.0.1:5175/api/*','http://localhost:5175/api/*','http://127.0.0.1:5176/api/*']});
 return {send,evaluate,waitFor,frame,click,field,check,screenshot,errors,close:async()=>{await send('Page.close');ws.close();}};
}
