const SERVER_URL_KEY = 'serverUrl'

/** "localhost:8080/" -> "http://localhost:8080"; "" stays "" (same origin / dev proxy). */
export function normalizeServerUrl(raw: string): string {
  let url = raw.trim()
  if (url === '') return ''
  url = url.replace(/\/+$/, '')
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(url)) url = `http://${url}`
  return url
}

export function getServerUrl(): string {
  try {
    // An empty stored value means "use the default" (in Electron that is the gateway URL;
    // in the browser dev server it is "" = same origin via the Vite proxy).
    const stored = localStorage.getItem(SERVER_URL_KEY)
    if (stored) return stored
  } catch {
    // storage unavailable: fall back to the build-time default
  }
  return normalizeServerUrl(import.meta.env.VITE_API_BASE_URL ?? '')
}

export function setServerUrl(url: string): void {
  try {
    localStorage.setItem(SERVER_URL_KEY, normalizeServerUrl(url))
  } catch {
    // ignore: the session still works with the default url
  }
}
