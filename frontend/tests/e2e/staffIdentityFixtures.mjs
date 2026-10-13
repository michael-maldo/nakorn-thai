/** Fictional JWT-shaped credentials for mocks only; never accepted by a real backend. */
export function staffSessionFixture({ id = '11111111-1111-4111-8111-111111111111', sessionId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', role = 'ADMIN', tokenId = 'first', expiresAt = new Date(Date.now() + 3600000).toISOString() } = {}) {
  const encode = value => btoa(JSON.stringify(value)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
  const accessToken = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ iss: 'nakorn-thai', sub: id, sid: sessionId, exp: Math.floor(Date.parse(expiresAt) / 1000), jti: tokenId })}.synthetic-signature`;
  return { accessToken, expiresAt, user: { id, username: 'Synthetic staff', role, enabled: true } };
}
