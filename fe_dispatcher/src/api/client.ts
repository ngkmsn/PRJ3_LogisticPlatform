import { getServerUrl } from './config'

export const TOKEN_KEY = 'token'
export const USER_KEY = 'user'

/** status 0 = the server could not be reached (network error). */
export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let unauthorizedHandler: () => void = () => {}

/** Called whenever the backend answers 401 (token missing, invalid or expired). */
export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

/**
 * Calls the API gateway and returns the `data` field of the `{ success, message, data }` envelope.
 * Throws ApiError for HTTP errors, `success: false`, and network failures.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const base = getServerUrl()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = readToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${base}${path}`, { ...init, headers })
  } catch {
    const target = base || window.location.origin
    throw new ApiError(`Cannot reach server at ${target}`, 0)
  }

  let body: { success?: boolean; message?: string; data?: unknown } | null = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (response.status === 401) {
    unauthorizedHandler()
  }
  if (!response.ok || body?.success === false) {
    const message = body?.message || response.statusText || `Request failed (HTTP ${response.status})`
    throw new ApiError(message, response.status)
  }
  return (body?.data ?? null) as T
}
