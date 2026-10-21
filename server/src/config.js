import { timingSafeEqual, createHash, randomBytes } from 'node:crypto';

export const randomToken = () => randomBytes(32).toString('base64url');
export const digest = value => createHash('sha256').update(String(value)).digest('hex');
export function sameSecret(a, b) {
  return typeof a === 'string' && typeof b === 'string' && timingSafeEqual(Buffer.from(digest(a)), Buffer.from(digest(b)));
}
export function validateConfig(config) {
  const url = new URL(config.publicUrl);
  const local = config.allowLocalHttp === true && url.protocol === 'http:' && ['localhost','127.0.0.1'].includes(url.hostname);
  if ((!local && url.protocol !== 'https:') || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('PUBLIC_URL doit être une origine HTTPS sans chemin.');
  }
  if (!/^[A-Za-z0-9_-]{32,256}$/.test(config.phoneToken ?? '') || (config.adminPassword ?? '').length < 24) {
    throw new Error('Configurez PHONE_TOKEN (32 caractères minimum) et ADMIN_PASSWORD (24 minimum) avec npm run setup.');
  }
  return { ...config, publicUrl: url.origin, resource: `${url.origin}/mcp` };
}
