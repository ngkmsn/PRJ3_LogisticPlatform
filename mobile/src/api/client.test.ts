import { ApiError, apiFetch, setUnauthorizedHandler } from './client'
import { normalizeServerUrl } from './config'
import { setSession } from './session'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('normalizeServerUrl', () => {
  it('adds http:// and drops trailing slashes', () => {
    expect(normalizeServerUrl('192.168.1.20:8080/')).toBe('http://192.168.1.20:8080')
    expect(normalizeServerUrl(' https://api.x.com// ')).toBe('https://api.x.com')
  })

  it('falls back to the default server when empty', () => {
    expect(normalizeServerUrl('   ')).toBe('http://localhost:8080')
  })
})

describe('apiFetch', () => {
  const fetchMock = jest.fn()
  const onUnauthorized = jest.fn()

  beforeEach(() => {
    globalThis.fetch = fetchMock as unknown as typeof fetch
    setUnauthorizedHandler(onUnauthorized)
    setSession({ serverUrl: 'http://gw:8080', token: 't1', user: null })
  })

  afterEach(() => {
    fetchMock.mockReset()
    onUnauthorized.mockReset()
  })

  it('calls the configured server with the bearer token and returns data', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: { a: 1 } }))

    await expect(apiFetch('/api/x')).resolves.toEqual({ a: 1 })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://gw:8080/api/x')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer t1')
  })

  it('throws the backend message and does not sign out on 403', async () => {
    fetchMock.mockResolvedValue(jsonResponse(403, { success: false, message: 'nope' }))
    await expect(apiFetch('/api/x')).rejects.toMatchObject({ status: 403, message: 'nope' })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('signs out on 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { success: false, message: 'Token is invalid or expired' }))
    await expect(apiFetch('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('names the server when it cannot be reached', async () => {
    fetchMock.mockRejectedValue(new TypeError('Network request failed'))
    await expect(apiFetch('/api/x')).rejects.toMatchObject({ status: 0, message: 'Cannot reach server at http://gw:8080' })
  })
})
