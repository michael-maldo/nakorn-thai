/** Synthetic in-memory API only. Unknown API requests fail closed. */
export function installPrototypeFixtures(staffSessionFixture, installStaffLayoutFixtures){
 installStaffLayoutFixtures();
 const role=new URLSearchParams(location.search).get('role')||'ADMIN';
 const seedSession=()=>staffSessionFixture({role:role==='ANON'?'ADMIN':role});
 window.fixture={requests:[],writes:[],failure:null,readFailure:null,session:role==='ANON'?null:seedSession(),empty:false};
 window.confirm=()=>true;window.alert=()=>{};
 const availability={available:true,reason:'AVAILABLE',evaluatedAt:new Date().toISOString()};
 const item={id:'dish',name:'Green curry',slug:'green-curry',description:'Synthetic curry',categoryId:'curry',status:'PUBLISHED',available:true,displayOrder:0,collectionIds:['main'],prices:[{id:'standard',name:'Standard',amount:'24.90'}],version:1};
 const collection={collection:{id:'main',version:1,data:{name:'Main menu',slug:'main',description:null,status:'PUBLISHED',active:true,displayOrder:0,timezone:'Australia/Melbourne',startsAt:null,endsAt:null,dailyCutoffTime:null}},memberships:[{id:'dish',version:1,data:{collectionCategoryId:null,priceOverrideMinor:null,displayOrder:0}}],categories:[],schedules:[],availability,orderingAvailability:availability,restaurantTimezone:'Australia/Melbourne',restaurantOpen:true};
 const rows=fixture.rows=[collection];
 const menu=fixture.menu={items:[item],categories:[{id:'curry',name:'Curry'},{id:'side',name:'Sides'}],collections:[{id:'main',name:'Main menu'},{id:'lunch',name:'Lunch menu'}]};
 fixture.groups=[{group:{id:'protein',version:1,data:{name:'Protein',code:'protein',active:true,selectionType:'SINGLE',displayOrder:0}},options:[{id:'tofu',version:1,data:{name:'Tofu',code:'tofu',active:true,displayOrder:0}}]}];
 fixture.assignments=[];
 fixture.homepage={version:1,collectionIds:['main'],collections:menu.collections.map(c=>({...c,status:'PUBLISHED'}))};
 fixture.schedule={settings:{version:1,timezone:'Australia/Melbourne'},hours:[],closedDates:[]};
 fixture.ordering={version:1,acceptingOrders:true,pauseMessage:'',configured:true,restaurantOpen:true,status:{enabled:true}};
 fixture.users=[{id:'owner',username:'owner',role:'ADMIN',enabled:true,version:1}];
 fixture.reservations=[{id:'booking',version:1,requestedAt:'2026-10-26T19:00:00',customerName:'Test guest',partySize:4,status:'REQUESTED',phone:'0412345678',phoneVerified:true,email:'test@example.test',staffNote:'',notes:'Window table'}];
 fixture.functions=[{id:'event',version:1,eventType:'Birthday',customerName:'Test host',guestCount:12,status:'NEW',phone:'0412345678',email:'host@example.test',message:'Private dinner',staffNote:'',arrangedDate:null}];
 fixture.orders=[{id:'11111111-1111-4111-8111-111111111111',version:1,reference:'NT-PROTO',status:'NEW',createdAt:new Date().toISOString(),customerName:'Test guest',phone:'0412345678',email:'test@example.test',items:[{quantity:1,dishName:'Green curry',selectedOptions:[]}],totalMinor:2490,paymentMethod:'PAY_AT_RESTAURANT'}];
 const base=window.fetch;
 const ok=data=>Response.json(structuredClone(data));const no=()=>new Response(null,{status:204});
 const error=(status,message)=>Response.json({message},{status});
 window.fetch=async(url,options={})=>{
  const path=String(url);if(!path.startsWith('/api'))return base(url,options);
  const method=options.method||'GET',write=!['GET','HEAD'].includes(method);
  const body=options.body instanceof FormData?Object.fromEntries(options.body.entries()):options.body?JSON.parse(options.body):null;
  const request={url:path,method,body,headers:options.headers};fixture.requests.push(request);
  if(path.endsWith('/csrf'))return ok({headerName:'X-CSRF-TOKEN',token:'prototype-csrf'});
  if(path==='/api/identity/refresh')return fixture.session?ok(fixture.session):error(401,'Signed out');
  if(path==='/api/identity/login'){fixture.session=seedSession();return ok(fixture.session);}
  if(path==='/api/identity/logout'){fixture.session=null;return no();}
  if(write){
   fixture.writes.push(request);
   if(fixture.holdWrites)await new Promise(resolve=>fixture.releaseWrite=resolve);
   if(fixture.failure){const failure=fixture.failure;fixture.failure=null;return error(failure.status,failure.message);}
   if(!options.headers?.['X-CSRF-TOKEN'])return error(403,'CSRF required');
   if(path==='/api/staff/restaurant/ordering'){Object.assign(fixture.ordering,body,{version:body.version+1,status:{enabled:body.acceptingOrders}});return ok(fixture.ordering);}
   if(path==='/api/staff/restaurant/settings'){Object.assign(fixture.schedule.settings,body,{version:body.version+1});return ok(fixture.schedule.settings);}
   if(path.startsWith('/api/staff/restaurant/hours')||path.startsWith('/api/staff/restaurant/closed-dates')){
    const kind=path.includes('/hours')?'hours':'closedDates',id=path.split('?')[0].split('/')[5];
    if(method==='DELETE'){fixture.schedule[kind]=fixture.schedule[kind].filter(r=>r.id!==id);return no();}
    const row={...body,id:id||kind+'-new',version:(body.version||0)+1},index=fixture.schedule[kind].findIndex(r=>r.id===id);
    if(index<0)fixture.schedule[kind].push(row);else fixture.schedule[kind][index]=row;return ok(row);
   }
   if(path.startsWith('/api/staff/reservations/')){const row=fixture.reservations[0];if(body.version!==row.version)return error(409,'Booking changed; refresh');Object.assign(row,body,{version:row.version+1});return no();}
   if(path.startsWith('/api/staff/functions/')){
    const row=fixture.functions[0];if(body.version!==row.version)return error(409,'Enquiry changed; refresh before saving');
    if(['CONFIRMED','COMPLETED'].includes(body.status)&&!body.arrangedDate)return error(400,'Enter the agreed event date before confirming');
    Object.assign(row,body,{version:row.version+1,updatedBy:'owner'});return no();
   }
   if(path==='/api/identity/users'){const user={...body,id:'new-user',enabled:true,version:1};delete user.password;fixture.users.push(user);return ok(user);}
   if(path.startsWith('/api/identity/users/')){const row=fixture.users.find(r=>r.id===path.split('/').at(-1));Object.assign(row,{role:body.role,enabled:body.enabled,version:row.version+1});return no();}
   if(path==='/api/staff/menu/homepage'){Object.assign(fixture.homepage,body,{version:body.version+1});return ok(fixture.homepage);}
   if(path.endsWith('/image')){item.image={url:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=',alt:body.alt,focusX:Number(body.focusX),focusY:Number(body.focusY),zoom:Number(body.zoom),rotation:Number(body.rotation)};item.version++;return no();}
   if(path.includes('/option-groups')){
    const parts=path.split('?')[0].split('/'),itemScope=path.includes('/items/'),groupId=parts[itemScope?7:5];
    if(itemScope){
     if(method==='DELETE'){fixture.assignments=fixture.assignments.filter(a=>a.id!==groupId);return no();}
     if(method==='POST'){
      const id='created-group';fixture.groups.push({group:{id,version:1,data:{...body,active:true}},options:body.options.map((o,i)=>({id:'choice-'+i,version:1,data:{...o,active:true,displayOrder:i}}))});fixture.assignments.push({id,version:1,data:{minSelections:body.selectionType==='SINGLE'?1:0,maxSelections:body.maxSelections,displayOrder:body.displayOrder,prices:body.options.map((o,i)=>({optionId:'choice-'+i,priceDeltaMinor:o.priceDeltaMinor}))}});return ok({id});
     }
     const resource={id:groupId,version:(body.version||0)+1,data:body},index=fixture.assignments.findIndex(a=>a.id===groupId);if(index<0)fixture.assignments.push(resource);else fixture.assignments[index]=resource;return ok(resource);
    }
    const group=fixture.groups.find(g=>g.group.id===groupId);const optionId=parts[7],resource={id:optionId||'shared-new',version:(body.version||0)+1,data:body};const index=group.options.findIndex(o=>o.id===optionId);if(index<0)group.options.push(resource);else group.options[index]=resource;return ok(resource);
   }
   if(path.startsWith('/api/staff/menu/items')){if(method==='DELETE'){item.status='ARCHIVED';item.version++;return no();}if(method==='POST'){const value={...body,id:'created-item',version:1};menu.items.push(value);return ok({id:value.id});}Object.assign(item,body,{version:item.version+1});return no();}
   if(path.startsWith('/api/staff/orders/')&&path.endsWith('/status')){Object.assign(fixture.orders[0],body,{version:fixture.orders[0].version+1});return no();}
   if(path.startsWith('/api/staff/payments/')){fixture.orders[0].paidAt=new Date().toISOString();return ok({paid:true});}
   throw Error('Unhandled prototype mutation '+method+' '+path);
  }
  if(fixture.readFailure&&path.includes(fixture.readFailure)){return error(503,'Synthetic service unavailable');}
  if(path==='/api/staff/menu/items')return ok(fixture.empty?{...menu,items:[]}:menu);
  if(path==='/api/staff/menu/collections')return ok(rows);
  if(path==='/api/staff/menu/option-groups')return ok(fixture.groups);
  if(/\/items\/[^/]+\/option-groups$/.test(path))return ok(fixture.assignments);
  if(path==='/api/staff/menu/homepage')return ok(fixture.homepage);
  if(path==='/api/staff/restaurant/schedule')return ok(fixture.schedule);
  if(path==='/api/staff/restaurant/ordering')return ok(fixture.ordering);
  if(path.startsWith('/api/staff/reservations'))return ok(fixture.empty?[]:fixture.reservations);
  if(path.startsWith('/api/staff/functions')){const params=new URL(path,location.origin).searchParams;return ok({items:fixture.empty?[]:fixture.functions.filter(r=>params.get('status')==='ALL'||params.get('status')===r.status),hasNext:params.get('page')==='0'});}
  if(path==='/api/identity/users')return ok(fixture.users);
  if(path==='/api/staff/payments/review')return ok(fixture.review||{orders:[],webhookEvents:[]});
  if(path.startsWith('/api/staff/foh/orders'))return ok(fixture.orders.filter(o=>path.includes('history=true')?['COMPLETED','CANCELLED'].includes(o.status):!['COMPLETED','CANCELLED'].includes(o.status)));
  if(path.startsWith('/api/staff/kitchen/orders'))return ok(fixture.orders.filter(o=>['ACCEPTED','PREPARING','READY'].includes(o.status)));
  if(path.startsWith('/api/staff/orders/'))return ok(fixture.orders.find(o=>o.id===path.split('/').at(-1)));
  return base(url,options);
 };
}
