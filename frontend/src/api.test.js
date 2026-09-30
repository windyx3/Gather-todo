import { describe, it, expect, vi, beforeEach } from 'vitest';
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
beforeEach(() => { vi.resetModules(); vi.restoreAllMocks(); });
describe('API session handling', () => {
  it('obtains a fresh CSRF token for logout and clears the access token', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json({ token: 'old-csrf' }))
      .mockResolvedValueOnce(json({ accessToken: 'access', user: { id: 1 } }))
      .mockResolvedValueOnce(json({ token: 'current-csrf' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json([]));
    vi.stubGlobal('fetch', fetch);
    const { authenticate, logout, api } = await import('./api');
    await authenticate('login', { email: 'a@example.com', password: 'password' });
    await logout();
    expect(fetch.mock.calls[2][0]).toBe('/api/auth/csrf');
    expect(fetch.mock.calls[3][0]).toBe('/api/auth/logout');
    expect(fetch.mock.calls[3][1].headers['X-XSRF-TOKEN']).toBe('current-csrf');
    expect(fetch.mock.calls[3][1].headers.Authorization).toBeUndefined();
    await api('/projects');
    expect(fetch.mock.calls[4][1].headers.Authorization).toBeUndefined();
  });
  it('adds a CSRF header on login and bearer token only after login', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json({ token: 'csrf' })).mockResolvedValueOnce(json({ accessToken: 'access', user: { id: 1 } })).mockResolvedValueOnce(json([]));
    vi.stubGlobal('fetch', fetch);
    const { authenticate, api } = await import('./api');
    await authenticate('login', { email: 'a@example.com', password: 'password' }); await api('/projects');
    expect(fetch.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('csrf');
    expect(fetch.mock.calls[1][1].headers.Authorization).toBeUndefined();
    expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer access');
  });
  it('refreshes once and retries an expired access token', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json({}, 401)).mockResolvedValueOnce(json({ token: 'csrf' }))
      .mockResolvedValueOnce(json({ accessToken: 'new', user: { id: 1 } })).mockResolvedValueOnce(json([{ id: 9 }]));
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('./api');
    expect(await api('/projects')).toEqual([{ id: 9 }]);
    expect(fetch.mock.calls[2][0]).toBe('/api/auth/refresh');
    expect(fetch.mock.calls[3][1].headers.Authorization).toBe('Bearer new');
  });
  it('does not loop indefinitely when refresh is rejected', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json({}, 401)).mockResolvedValueOnce(json({ token: 'csrf' })).mockResolvedValueOnce(json({ detail: 'Expired' }, 401));
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('./api');
    await expect(api('/projects')).rejects.toMatchObject({ status: 401 });
    expect(fetch).toHaveBeenCalledTimes(3);
  });
});
