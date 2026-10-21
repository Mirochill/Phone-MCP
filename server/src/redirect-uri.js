// Literal loopback redirects for native OAuth clients: RFC 8252, section 7.3.
export function validRedirectUri(value) {
  if(typeof value!=='string'||value.length>2048||value!==value.trim())return false;
  try {
    const u=new URL(value);
    return !u.username&&!u.password&&!u.hash &&
      (u.protocol==='https:' || (u.protocol==='http:'&&['127.0.0.1','[::1]'].includes(u.hostname)));
  }catch{return false;}
}
export function matchesRedirectUri(registered,requested) {
  if(!validRedirectUri(registered)||!validRedirectUri(requested))return false;
  if(registered===requested)return true;
  const a=new URL(registered),b=new URL(requested);
  return a.protocol==='http:'&&b.protocol==='http:'&&a.hostname===b.hostname&&a.pathname===b.pathname&&a.search===b.search;
}
export function authorizationCsp(redirectUri) {
  if(!validRedirectUri(redirectUri))throw Error('Invalid redirect URI');
  return `default-src 'none'; form-action 'self' ${new URL(redirectUri).origin}; base-uri 'none'; frame-ancestors 'none'`;
}
