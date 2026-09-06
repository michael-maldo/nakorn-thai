import { fetchWithIdentity } from '../../identity/api/identityApi.js';
export async function menuRequest(path, { authorization, csrf, ...options } = {}) {
  // Obtain the current session CSRF token
  // immediately before each write, rather than reusing the sign-in token.
  if (csrf && !['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
    csrf = await menuRequest('/staff/menu/csrf', { authorization });
  }
  const response = await fetchWithIdentity(`/api${path}`, {
    ...options,
    credentials: 'same-origin',
    headers: {
      ...(options.body && !(options.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
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
    const error = new Error(messages[response.status] || 'The menu service is unavailable. Please try again.');
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  try { return await response.json(); }
  catch { throw new Error('The menu service returned an invalid response. Please try again.'); }
}

export const getMenuCollection = (slug, signal) =>
  menuRequest(`/menu/collections/${encodeURIComponent(slug)}/items`, { signal });
export async function getMenuCollections(signal) {
  const collections = await menuRequest('/menu/collections', { signal });
  if (!Array.isArray(collections) || collections.some((entry) => typeof entry.id !== 'string'
    || typeof entry.slug !== 'string' || typeof entry.name !== 'string' || typeof entry.availability?.available !== 'boolean'))
    throw new Error('The menu service returned an invalid collection list.');
  return collections;
}
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

export const saveMenuImage = (id, body, authorization, csrf) => menuRequest(
  `/staff/menu/items/${id}/image`, { method: 'POST', body, authorization, csrf },
);
