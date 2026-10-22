export function installPwaFixtures(sessionFactory) {
  const params = new URLSearchParams(location.search), role = params.get('role') || 'ADMIN';
  const session = sessionFactory({ role });
  const makeOrder = (status, index) => ({ id: `order-${index}`, reference: `NT-${index}`, status, version: 1, createdAt: '2026-10-22T01:00:00Z', estimatedReadyAt: status === 'NEW' ? null : '2026-10-22T01:20:00Z', customerName: 'Synthetic guest', email: 'guest@example.test', fulfilment: 'PICKUP', currency: 'AUD', subtotalMinor: 2400, paymentStatus: 'PAYMENT_DUE', paymentReference: null, phone: '0412345678', paymentMethod: 'PAY_AT_RESTAURANT', totalMinor: 2400, items: [{ id: `item-${index}`, dishName: 'Thai green curry', variationName: 'Chicken', quantity: 1, unitPriceMinor: 2400, lineTotalMinor: 2400, selectedOptions: [] }], notes: 'No peanuts' });
  window.pwaFixture = { requests: [], queueError: false, statusError: false, signedOut: role === 'ANON', orders: ['NEW','ACCEPTED','PREPARING','READY'].map(makeOrder), loginError: false, expire: false };
  const state = window.pwaFixture;
  const realFetch = window.fetch;
  window.fetch = async (url, options = {}) => {
    const path = String(url);
    if (!path.startsWith('/api')) {
      if (/^https?:/.test(path) && !path.startsWith(location.origin)) throw new Error('External traffic forbidden in browser tests');
      return realFetch(url, options);
    }
    state.requests.push({ path, method: options.method || 'GET', body: options.body, headers: options.headers });
    if (path.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'synthetic' });
    if (path === '/api/identity/refresh') return state.signedOut || state.expire ? Response.json({message:'Session expired'},{status:401}) : Response.json(session);
    if (path === '/api/identity/login') { if (state.loginError) return Response.json({message:'Invalid credentials'},{status:401}); state.signedOut = false; return Response.json(sessionFactory({ role: 'ADMIN' })); }
    if (path === '/api/identity/logout') { state.signedOut = true; return new Response(null, {status:204}); }
    if (state.expire) return Response.json({message:'Session expired'},{status:401});
    if (path.startsWith('/api/staff/foh/orders') || path === '/api/staff/kitchen/orders') {
      if (state.queueError) throw new TypeError('Synthetic offline');
      return Response.json(state.orders.filter(order => role !== 'BOH' || order.status !== 'NEW').map(order => role !== 'BOH' ? order : { ...order, customerName:null,phone:null,email:null,paymentReference:null,totalMinor:0,paymentMethod:null }));
    }
    if (path.endsWith('/status')) {
      const id = path.split('/')[4], order = state.orders.find(order => order.id === id), command = JSON.parse(options.body);
      await new Promise(resolve => setTimeout(resolve, 80));
      if (state.statusError) return Response.json({message:'Version conflict'},{status:409});
      if (command.version !== order.version) return Response.json({message:'Version conflict'},{status:409});
      Object.assign(order, {status:command.status,version:order.version+1, estimatedReadyAt:'2026-10-22T01:20:00Z'});
      return new Response(null,{status:204});
    }
    if (path.startsWith('/api/staff/orders/')) {
      const order = state.orders.find(order => order.id === path.split('/')[4]);
      if (role === 'BOH') return Response.json({ ...order, customerName:null,phone:null,email:null,paymentReference:null,totalMinor:0,paymentMethod:null });
      return Response.json(order);
    }
    if (path.startsWith('/api/staff/reservations')) return Response.json([]);
    throw new Error('Unhandled PWA fixture: ' + path);
  };
}
