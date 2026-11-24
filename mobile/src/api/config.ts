/**
 * Default API gateway. The iOS Simulator reaches the Mac's localhost directly; a real phone
 * needs the Mac's LAN address (e.g. http://192.168.1.123:8080), entered on the sign-in screen.
 */
export const DEFAULT_SERVER_URL = normalize(process.env.EXPO_PUBLIC_API_BASE_URL ?? '') || 'http://localhost:8080'

function normalize(raw: string): string {
  let url = raw.trim()
  if (url === '') return ''
  url = url.replace(/\/+$/, '')
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(url)) url = `http://${url}`
  return url
}

/** "192.168.1.20:8080/" -> "http://192.168.1.20:8080"; empty -> the default server. */
export function normalizeServerUrl(raw: string): string {
  return normalize(raw) || DEFAULT_SERVER_URL
}
