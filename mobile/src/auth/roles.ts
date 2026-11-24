import type { Role } from '../api/types'

export const MOBILE_ROLE_ERROR = 'This account cannot use the mobile app. Operations staff use the desktop client.'

export function isMobileRole(role: Role): boolean {
  return role === 'CUSTOMER' || role === 'DRIVER'
}

export function homeRouteFor(role: Role): '/customer' | '/driver' {
  return role === 'DRIVER' ? '/driver' : '/customer'
}
