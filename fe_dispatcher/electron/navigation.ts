const APP_ORIGIN = 'app://dispatcher'

/**
 * True when the window may navigate to `url`: the packaged app itself, or the dev server
 * during electron:dev. Everything else (e.g. map attribution links) must open in the
 * system browser instead of replacing the app window.
 */
export function isInAppNavigation(url: string, devUrl?: string): boolean {
  let target: URL
  try {
    target = new URL(url)
  } catch {
    return false
  }
  if (`${target.protocol}//${target.host}` === APP_ORIGIN) return true
  if (devUrl) {
    try {
      return target.origin === new URL(devUrl).origin
    } catch {
      return false
    }
  }
  return false
}
