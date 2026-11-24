import { afterEach, describe, expect, it, vi } from 'vitest'
import { getServerUrl, normalizeServerUrl, setServerUrl } from './config'

describe('normalizeServerUrl', () => {
  it('adds http:// and drops a trailing slash', () => {
    expect(normalizeServerUrl('localhost:8080/')).toBe('http://localhost:8080')
  })

  it('trims whitespace and repeated trailing slashes, keeps https', () => {
    expect(normalizeServerUrl(' https://api.x.com// ')).toBe('https://api.x.com')
  })

  it('keeps an empty value empty (same-origin / dev proxy)', () => {
    expect(normalizeServerUrl('')).toBe('')
    expect(normalizeServerUrl('   ')).toBe('')
  })
})

describe('server url storage', () => {
  afterEach(() => localStorage.clear())

  it('falls back to the build-time default when the stored server is empty', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
    setServerUrl('   ')
    expect(getServerUrl()).toBe('http://localhost:8080')
    vi.unstubAllEnvs()
  })

  it('persists a normalized server url', () => {
    setServerUrl('192.168.1.20:8080/')
    expect(getServerUrl()).toBe('http://192.168.1.20:8080')
  })
})
