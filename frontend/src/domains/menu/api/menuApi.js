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
      409: 'This menu resource has changed or its slug already exists. Reload before saving; category placements must be unique and unused before removal.',
    };
    let detail;
    try { detail = (await response.json()).message; } catch { /* Proxy errors may be HTML. */ }
    const error = new Error((typeof detail === 'string' && detail) || messages[response.status] || 'The menu service is unavailable. Please try again.');
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

export const getCollectionConfiguration = (authorization) => menuRequest('/staff/menu/collections', { authorization });
export const saveCollectionConfiguration = (collection, authorization) => menuRequest(
  `/staff/menu/collections${collection.id ? `/${collection.id}` : ''}`,
  { method: collection.id ? 'PUT' : 'POST', body: JSON.stringify({ ...collection.data, version: collection.version }), authorization, csrf: true },
);

export const saveCollectionMembership = (collectionId, itemId, resource, authorization) => menuRequest(
  `/staff/menu/collections/${collectionId}/items/${itemId}`,
  { method: 'PUT', body: JSON.stringify({ ...resource.data, version: resource.version }), authorization, csrf: true },
);
export const saveCollectionChild = (collectionId, kind, resource, authorization) => menuRequest(
  `/staff/menu/collections/${collectionId}/${kind}${resource.id ? `/${resource.id}` : ''}`,
  { method: resource.id ? 'PUT' : 'POST', body: JSON.stringify({ ...resource.data, version: resource.version }), authorization, csrf: true },
);
export const removeCollectionChild = (collectionId, kind, resource, authorization) => menuRequest(
  `/staff/menu/collections/${collectionId}/${kind}/${resource.id}?version=${resource.version}`,
  { method: 'DELETE', authorization, csrf: true },
);

export const getOptionGroups = authorization => menuRequest('/staff/menu/option-groups', { authorization });
export const getItemOptionGroups = (itemId, authorization) => menuRequest(`/staff/menu/items/${itemId}/option-groups`, { authorization });
export const saveItemOptionGroup = (itemId, groupId, data, authorization) => menuRequest(
  `/staff/menu/items/${itemId}/option-groups/${groupId}`, { method: 'PUT', body: JSON.stringify(data), authorization, csrf: true },
);
export const createItemOptionGroup = (itemId, data, authorization) => menuRequest(
  `/staff/menu/items/${itemId}/option-groups`, { method: 'POST', body: JSON.stringify(data), authorization, csrf: true },
);
export const removeItemOptionGroup = (itemId, assignment, authorization) => menuRequest(
  `/staff/menu/items/${itemId}/option-groups/${assignment.id}?version=${assignment.version}`, { method: 'DELETE', authorization, csrf: true },
);
export const saveSharedOption = (groupId, resource, authorization) => menuRequest(
  `/staff/menu/option-groups/${groupId}/options${resource.id ? `/${resource.id}` : ''}`,
  { method: resource.id ? 'PUT' : 'POST', body: JSON.stringify({ ...resource.data, version: resource.version }), authorization, csrf: true },
);
