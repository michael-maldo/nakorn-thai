import { test } from 'node:test';
import assert from 'node:assert/strict';
import { observeOptions } from './observeOptions.js';
const flush = () => new Promise(resolve => setImmediate(resolve));
test('options refresh on timer and focus, fail closed, recover and clean up', async () => {
 let tick, focus, cleared=false, removed=false, state={}, failure=false;
 const target={setInterval(fn,ms){tick=fn;assert.equal(ms,15000);return 1;},clearInterval(){cleared=true;},addEventListener(event,fn){assert.equal(event,'focus');focus=fn;},removeEventListener(){removed=true;}};
 let response={paypal:true,payid:true,payAtRestaurant:true};
 const stop=observeOptions(async()=>{if(failure)throw Error('offline');return response;},data=>{state=data;},()=>{state={};},target);
 await flush();assert.equal(state.paypal,true);
 response={paypal:false,payid:true,payAtRestaurant:true};await tick();assert.equal(state.paypal,false);assert.equal(state.payid,true);
 failure=true;await focus();assert.deepEqual(state,{});
 failure=false;response={sms:false,email:true};await tick();assert.deepEqual(state,{sms:false,email:true});
 stop();assert.ok(cleared&&removed);response={sms:true};await tick();assert.equal(state.sms,false);
});
test('late responses after unmount are ignored and overlapping refreshes are skipped',async()=>{
 let resolve,tick,calls=0,received=0;
 const target={setInterval(fn){tick=fn;},clearInterval(){},addEventListener(){},removeEventListener(){}};
 const stop=observeOptions(()=>{calls++;return new Promise(r=>{resolve=r;});},()=>received++,()=>{},target);
 await flush();await tick();assert.equal(calls,1);stop();resolve({paypal:true});await flush();assert.equal(received,0);
});
test('verification rendering disables send and check until backend availability is known',async()=>{
 const {createServer}=await import('vite');const {createElement}=await import('react');const {renderToStaticMarkup}=await import('react-dom/server');
 const server=await createServer({server:{middlewareMode:true,hmr:false,watch:null},appType:'custom',logLevel:'error'});
 try{
  const Component=(await server.ssrLoadModule('/src/domains/notification/components/ContactVerification.jsx')).default;
  const html=renderToStaticMarkup(createElement(Component,{channel:'SMS',state:{destination:'0412345678',status:'codeSent',id:'challenge',code:'123456'},now:Date.now(),onChange(){},startVerification(){throw Error('must not send');},verifyCode(){throw Error('must not check');}}));
  assert.match(html,/disabled="">Send verification code/);assert.match(html,/disabled="">Verify code/);assert.match(html,/SMS verification is currently unavailable/);
 }finally{await server.close();}
});

test('StrictMode cleanup avoids duplicate initial requests and hidden tabs skip polling',async()=>{
 let calls=0,tick,focus;const target={document:{hidden:false},setInterval(fn){tick=fn;},clearInterval(){},addEventListener(event,fn){focus=fn;},removeEventListener(){}};
 const load=async()=>{calls++;return {};};
 observeOptions(load,()=>{},()=>{},target)();
 const stop=observeOptions(load,()=>{},()=>{},target);await flush();assert.equal(calls,1);
 target.document.hidden=true;await tick();assert.equal(calls,1);
 target.document.hidden=false;await focus();assert.equal(calls,2);stop();
});
test('malformed options fail closed and the next valid response recovers',async()=>{
 let tick,value=null,state={paypal:true};const target={setInterval(fn){tick=fn;},clearInterval(){},addEventListener(){},removeEventListener(){}};
 const stop=observeOptions(async()=>value,data=>{state=data;},()=>{state={};},target);
 await flush();assert.deepEqual(state,{});value={paypal:true};await tick();assert.equal(state.paypal,true);stop();
});

function timeoutTarget() {
 let tick, focus, nextId=0;
 const timers=new Map();
 const target={
  setInterval(fn){tick=fn;},clearInterval(){},
  addEventListener(event,fn){focus=fn;},removeEventListener(){},
  setTimeout(fn,ms){assert.equal(ms,5000);timers.set(++nextId,fn);return nextId;},
  clearTimeout(id){timers.delete(id);},
 };
 return {target,tick:()=>tick(),focus:()=>focus(),expire(){const [id,fn]=timers.entries().next().value;timers.delete(id);fn();},timers};
}
test('hung requests fail closed after five seconds, abort and recover on the next poll',async()=>{
 const clock=timeoutTarget();let signal,calls=0,state={paypal:true},error='';
 const stop=observeOptions(s=>{signal=s;calls++;return calls===1?new Promise(()=>{}):Promise.resolve({paypal:false,payid:true});},data=>{state=data;error='';},e=>{state={};error=e.message;},clock.target);
 await flush();await clock.focus();assert.equal(calls,1);
 clock.expire();await flush();assert.equal(signal.aborted,true);assert.deepEqual(state,{});assert.match(error,/timed out/);
 await clock.tick();assert.equal(calls,2);assert.deepEqual(state,{paypal:false,payid:true});assert.equal(error,'');assert.equal(clock.timers.size,0);stop();
});
test('late timed-out results cannot update state or unlock a newer request',async()=>{
 const clock=timeoutTarget();let calls=0,firstResolve,secondResolve,state={},failures=0;
 const stop=observeOptions(()=>{calls++;return new Promise(resolve=>{if(calls===1)firstResolve=resolve;else secondResolve=resolve;});},data=>{state=data;},()=>{state={};failures++;},clock.target);
 await flush();clock.expire();await flush();assert.equal(failures,1);
 const next=clock.tick();await flush();assert.equal(calls,2);
 firstResolve({paypal:true});await flush();assert.deepEqual(state,{});await clock.focus();assert.equal(calls,2);
 secondResolve({paypal:false,email:true});await next;assert.deepEqual(state,{paypal:false,email:true});stop();
});
test('timeout works without AbortController and late rejection cannot affect recovery',async()=>{
 const original=globalThis.AbortController;globalThis.AbortController=undefined;
 const clock=timeoutTarget();let rejectFirst,calls=0,state={},failures=0;
 try {
  const stop=observeOptions(signal=>{assert.equal(signal,undefined);calls++;return calls===1?new Promise((_,reject)=>{rejectFirst=reject;}):Promise.resolve({sms:true});},data=>{state=data;},()=>{state={};failures++;},clock.target);
  await flush();clock.expire();await flush();await clock.tick();assert.deepEqual(state,{sms:true});
  rejectFirst(Error('late provider failure'));await flush();assert.equal(failures,1);assert.deepEqual(state,{sms:true});stop();
 }finally{globalThis.AbortController=original;}
});
test('cleanup aborts a hung request and clears its timeout without reporting failure',async()=>{
 const clock=timeoutTarget();let signal,failures=0;
 const stop=observeOptions(s=>{signal=s;return new Promise(()=>{});},()=>assert.fail('late update'),()=>failures++,clock.target);
 await flush();assert.equal(clock.timers.size,1);stop();await flush();assert.equal(signal.aborted,true);assert.equal(clock.timers.size,0);assert.equal(failures,0);
});
test('late success after a recovered response cannot overwrite newer availability',async()=>{
 const clock=timeoutTarget();let resolveFirst,calls=0,state={};
 const stop=observeOptions(()=>++calls===1?new Promise(resolve=>{resolveFirst=resolve;}):Promise.resolve({paypal:false,payid:true}),data=>{state=data;},()=>{state={};},clock.target);
 await flush();clock.expire();await flush();await clock.tick();assert.deepEqual(state,{paypal:false,payid:true});
 resolveFirst({paypal:true,payid:false});await flush();assert.deepEqual(state,{paypal:false,payid:true});stop();
});
