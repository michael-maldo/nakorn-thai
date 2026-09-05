export async function menuRequest(path, { authorization, csrf, ...options } = {}) {
  const response = await fetch(`/api${path}`, {
    ...options,
    credentials: 'same-origin',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(authorization ? { Authorization: authorization } : {}),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
    },
  });
  if (!response.ok) {
    const messages = {
      400: 'Check the required fields, category and collections.',
      401: 'Sign-in failed or expired. Check your admin credentials.',
      403: 'Access denied or security token expired. Sign out and sign in again.',
      404: 'The requested menu could not be found.',
      409: 'This dish has changed or its slug already exists. Reload the menu before saving.',
    };
    throw new Error(messages[response.status] || 'The menu service is unavailable. Please try again.');
  }
  if (response.status === 204) return null;
  try { return await response.json(); }
  catch { throw new Error('The menu service returned an invalid response. Please try again.'); }
}

export const getSignatureDishes = (signal) =>
  menuRequest('/menu/collections/signature-dishes/items', { signal });
export const getStaffMenu = (authorization) => menuRequest('/staff/menu/items', { authorization });
export const getStaffCsrf = (authorization) => menuRequest('/staff/menu/csrf', { authorization });
export const saveMenuItem = (item, authorization, csrf) => menuRequest(
  `/staff/menu/items${item.id ? `/${item.id}` : ''}`,
  { method: item.id ? 'PUT' : 'POST', body: JSON.stringify(item), authorization, csrf },
);
export const archiveMenuItem = (item, authorization, csrf) => menuRequest(
  `/staff/menu/items/${item.id}?version=${item.version}`,
  { method: 'DELETE', authorization, csrf },
);
