let accessToken = null;
let csrfToken = null;
let refreshPromise = null;

export class ApiError extends Error {
  constructor(status, body) {
    super(body.detail || body.message || 'The request could not be completed.');
    this.status = status;
    this.fields = body.errors;
  }
}
async function read(response) {
  const body = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body || {});
  return body;
}
async function csrf() {
  if (!csrfToken) csrfToken = (await read(await fetch('/api/auth/csrf', { credentials: 'same-origin' }))).token;
  return csrfToken;
}
async function send(path, method = 'GET', body, authorized = true) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (authorized && accessToken) headers.Authorization = 'Bearer ' + accessToken;
  // Authentication or another tab may have cleared/replaced the CSRF cookie.
  // Cookie-authenticated operations must obtain the current token first.
  if (!authorized) csrfToken = null;
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrf();
  return fetch('/api' + path, { method, headers, credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body) });
}
export async function authenticate(mode, values) {
  const result = await read(await send('/auth/' + mode, 'POST', values, false));
  accessToken = result.accessToken;
  return result.user;
}
export function restoreSession() {
  if (!refreshPromise) {
    const refresh = async () => {
      const result = await read(await send('/auth/refresh', 'POST', undefined, false));
      accessToken = result.accessToken;
      return result.user;
    };
    // Serialize refresh-cookie rotations across tabs when Web Locks is available.
    const operation = globalThis.navigator?.locks
      ? navigator.locks.request('todo-refresh', refresh) : refresh();
    refreshPromise = operation.catch(error => { accessToken = null; throw error; }).finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}
export async function api(path, options = {}) {
  let response = await send(path, options.method, options.body);
  if (response.status === 401) {
    try { await restoreSession(); }
    catch (error) {
      if (error.status === 401) globalThis.dispatchEvent?.(new Event('session-expired'));
      throw error;
    }
    response = await send(path, options.method, options.body);
  }
  return read(response);
}
export async function logout() {
  await read(await send('/auth/logout', 'POST', undefined, false));
  accessToken = null; csrfToken = null;
}
export function errorMessage(error) {
  if (error.fields) return Object.entries(error.fields).map(([field, message]) => field + ': ' + message).join('; ');
  return error.message || 'Unable to connect. Please try again.';
}
