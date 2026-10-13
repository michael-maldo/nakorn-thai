/** Synthetic data only. Every API request is intercepted before application startup. */
export function installStaffLayoutFixtures() {
  const role = new URLSearchParams(location.search).get('role') || 'ADMIN';
  const availability = { available: true, reason: 'AVAILABLE', evaluatedAt: '2026-10-13T01:00:00Z' };
  const rows = ['main', 'lunch'].map((id, index) => ({ collection: { id, version: 1, data: { name: index ? 'Lunch Menu' : 'Main Menu', slug: id, status: 'PUBLISHED', active: true, timezone: 'Australia/Melbourne', displayOrder: index } }, memberships: [{ id: 'dish', version: 1, data: { displayOrder: 0 } }], categories: [], schedules: [], availability, orderingAvailability: availability, restaurantTimezone: 'Australia/Melbourne', restaurantOpen: true }));
  const items = { items: [{ id: 'dish', name: 'Thai green curry', slug: 'dish', description: 'A synthetic dish', categoryId: 'curry', status: 'PUBLISHED', available: true, displayOrder: 0, collectionIds: ['main'], prices: [{ id: 'price', name: 'Standard', amount: '24.90' }], version: 1 }], categories: [{ id: 'curry', name: 'Curry' }], collections: rows.map(r => ({ id: r.collection.id, name: r.collection.data.name })) };
  const publicMenu = { id: 'main', slug: 'main', name: 'Main Menu', availability, categories: [{ id: 'curry', name: 'Curry' }], items: [{ id: 'dish', name: 'Thai green curry', description: 'Synthetic public dish', available: true, category: { id: 'curry' }, displayOrder: 0, variations: [], optionGroups: [], image: { url: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="100"><rect width="160" height="100" fill="#d99724"/></svg>'), alt: 'Synthetic dish' } }] };
  const fields = { SETTINGS: { businessWebsiteUrl: '', orderingEnabled: 'false', reservationsEnabled: 'true', orderPhoneRequired: 'true', reservationPhoneRequired: 'true', orderSms: 'true', orderEmail: 'false', staffOrderEmail: 'false', staffOrderEmailAddress: 'sample@example.test', reservationSms: 'true', reservationEmail: 'true', staffReservationEmail: 'false', staffReservationEmailAddress: 'sample@example.test', paypalEnabled: 'false', payidEnabled: 'false', payAtRestaurantEnabled: 'true' }, PAYPAL: { environment: 'sandbox', clientId: '' }, PAYID: { identifier: '', accountName: '' }, TWILIO: { accountSid: '', verifyServiceSid: '', smsFrom: '', verifySmsEnabled: 'false', verifyEmailEnabled: 'false' }, SMTP: { host: '', port: '587', username: '', from: '', starttls: 'true' } };
  const configuration = { encryptionAvailable: true };
  for (const [key, values] of Object.entries(fields)) configuration[key] = { fields: values, version: 1, sources: Object.fromEntries(Object.keys(values).map(k => [k, 'ENVIRONMENT_DEFAULT'])), secretsConfigured: key === 'PAYPAL' ? { clientSecret: false } : key === 'TWILIO' ? { authToken: false } : key === 'SMTP' ? { password: false } : {}, state: 'NOT_CONFIGURED', configured: false, enabled: false, validationStatus: 'NOT_TESTED', returnUrl: 'https://example.test/#/order-confirmation', pendingPayments: 0, smsVerificationConfigured: false, smsSendingConfigured: false };
  const longText = 'A'.repeat(160);
  const order = { id: '11111111-1111-4111-8111-111111111111', reference: 'NT-0042', version: 1, status: 'NEW', createdAt: '2026-10-13T01:00:00Z', customerName: 'Synthetic Guest', phone: '0412345678', items: [{ quantity: 2, dishName: 'Thai green curry', selectedOptions: [] }], notes: longText, totalMinor: 4980, paymentMethod: 'PAYID' };
  window.staffLayoutRequests = [];
  const realFetch = window.fetch;
  window.fetch = async (url, options = {}) => {
    const path = String(url);
    if (!path.startsWith('/api')) return realFetch(url, options);
    window.staffLayoutRequests.push(path);
    if (path.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'synthetic' });
    if (path === '/api/identity/refresh' && role === 'ANON') return Response.json({message:'Signed out'},{status:401});
    if (path === '/api/identity/refresh') return Response.json({ accessToken: 'synthetic', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: 'admin', username: 'Audit Staff', role } });
    if (options.method && options.method !== 'GET') throw new Error('Layout tests must not submit API writes: ' + path);
    if (path === '/api/staff/restaurant/configuration') return Response.json(configuration);
    if (path === '/api/staff/restaurant/configuration/audit') return Response.json([]);
    if (path === '/api/staff/menu/items') return Response.json(items);
    if (path === '/api/staff/menu/collections') return Response.json(rows);
    if (path.endsWith('/option-groups')) return Response.json([]);
    if (path === '/api/staff/menu/homepage') return Response.json({ version: 1, collectionIds: ['main'], collections: rows.map(r => ({ id: r.collection.id, name: r.collection.data.name, status: 'PUBLISHED' })) });
    if (path === '/api/restaurant/availability') return Response.json({ timezone: 'Australia/Melbourne', open: true });
    if (path === '/api/staff/restaurant/ordering') return Response.json({ version: 1, acceptingOrders: true, pauseMessage: '', configured: true, restaurantOpen: true, status: { enabled: true } });
    if (path === '/api/staff/restaurant/schedule') return Response.json({ settings: { timezone: 'Australia/Melbourne', version: 1 }, hours: [{ id: 'hour', dayOfWeek: 1, opensAt: '17:00:00', closesAt: '22:00:00', active: true, displayOrder: 0, version: 1 }], closedDates: [{ id: 'closure', closedDate: '2026-12-25', reason: longText, version: 1 }] });
    if (path.startsWith('/api/staff/foh/orders') || path.startsWith('/api/staff/kitchen/orders')) return Response.json([order, { ...order, id: 'second', reference: 'NT-0043', status: 'PREPARING', paymentMethod: 'PAY_AT_RESTAURANT' }]);
    if (path.startsWith('/api/staff/reservations')) return Response.json([1, 2].map(i => ({ id: 'reservation' + i, version: 1, requestedAt: '2026-10-13T19:30:00', customerName: 'Synthetic Guest', partySize: 6, status: 'REQUESTED', email: longText + '@example.test', staffNote: '', notes: longText })));
    if (path.startsWith('/api/staff/functions')) return Response.json({ items: [1, 2].map(i => ({ id: 'function' + i, version: 1, eventType: 'Birthday dinner', customerName: 'Synthetic Guest', guestCount: 30, status: 'NEW', phone: '0412345678', email: longText + '@example.test', message: longText, staffNote: '' })), hasNext: false });
    if (path === '/api/identity/users') return Response.json([{ id: 'admin', username: 'synthetic.admin', role: 'ADMIN', enabled: true, version: 1 }]);
    if (path === '/api/reservations/options') return Response.json({ enabled: true, phoneRequired: false });
    if (path === '/api/menu/homepage') return Response.json([publicMenu]);
    if (path === '/api/menu/collections') return Response.json([{ id: 'main', slug: 'main', name: 'Main Menu', availability }]);
    if (path === '/api/menu/collections/main/items') return Response.json(publicMenu);
    if (path === '/api/orders/options') return Response.json({ enabled: false });
    throw new Error('Unhandled layout fixture: ' + path);
  };
}
