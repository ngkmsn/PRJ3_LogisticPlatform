import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, setUnauthorizedHandler } from './client'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('apiFetch', () => {
  const fetchMock = vi.fn()
  const onUnauthorized = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setUnauthorizedHandler(onUnauthorized)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    onUnauthorized.mockReset()
    localStorage.clear()
  })

  it('returns the data field of a successful response', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, message: 'ok', data: { a: 1 } }))
    await expect(apiFetch('/api/x')).resolves.toEqual({ a: 1 })
  })

  it('prefixes the stored server url and sends the bearer token', async () => {
    localStorage.setItem('serverUrl', 'http://gw:8080')
    localStorage.setItem('token', 't1')
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: null }))

    await apiFetch('/api/x')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://gw:8080/api/x')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer t1')
  })

  it('sends JSON content type when a body is given', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: null }))
    await apiFetch('/api/x', { method: 'POST', body: JSON.stringify({ a: 1 }) })
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Content-Type')).toBe('application/json')
  })

  it('throws ApiError with the backend message on 403 without signing out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(403, { success: false, message: 'nope', data: null }))

    const error = (await apiFetch('/api/x').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(403)
    expect(error.message).toBe('nope')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('calls the unauthorized handler once on 401 and then throws', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { success: false, message: 'Token is invalid or expired' }))

    const error = (await apiFetch('/api/x').catch((e: unknown) => e)) as ApiError

    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(401)
  })

  it('treats success=false with 200 as an error', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: false, message: 'bad' }))
    await expect(apiFetch('/api/x')).rejects.toMatchObject({ status: 200, message: 'bad' })
  })

  it('maps a network failure to ApiError status 0 naming the server', async () => {
    localStorage.setItem('serverUrl', 'http://gw:8080')
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const error = (await apiFetch('/api/x').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.message).toBe('Cannot reach server at http://gw:8080')
  })

  it('uses the status text when an error response has no JSON body', async () => {
    fetchMock.mockResolvedValue(new Response('<html>bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }))
    await expect(apiFetch('/api/x')).rejects.toMatchObject({ status: 502, message: 'Bad Gateway' })
  })
})
