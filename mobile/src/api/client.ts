import { getSession } from './session'

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

/**
 * Calls the API gateway and returns the `data` field of the `{ success, message, data }` envelope.
 * Throws ApiError for HTTP errors, `success: false`, and network failures.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { serverUrl, token } = getSession()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${serverUrl}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(`Cannot reach server at ${serverUrl}`, 0)
  }

  let body: { success?: boolean; message?: string; data?: unknown } | null = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (response.status === 401) unauthorizedHandler()
  if (!response.ok || body?.success === false) {
    const message = body?.message || `Request failed (HTTP ${response.status})`
    throw new ApiError(message, response.status)
  }
  return (body?.data ?? null) as T
}
