import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch, setUnauthorizedHandler, TOKEN_KEY, USER_KEY } from '../api/client'
import { setServerUrl } from '../api/config'
import type { LoginResponse, Role, UserSummary } from '../api/types'

export const DESKTOP_ROLE_ERROR = 'This account cannot use the desktop client'

export function isDesktopRole(role: Role): boolean {
  return role === 'ADMIN' || role === 'DISPATCHER'
}

interface AuthState {
  user: UserSummary | null
  login(username: string, password: string, serverUrl: string): Promise<void>
  logout(): void
}

const AuthContext = createContext<AuthState | null>(null)

function readStoredUser(): UserSummary | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    const raw = localStorage.getItem(USER_KEY)
    if (!token || !raw) return null
    const user = JSON.parse(raw) as UserSummary
    return isDesktopRole(user.role) ? user : null
  } catch {
    return null
  }
}

function clearSession() {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  } catch {
    // ignore
  }
}

export function AuthProvider({ children, onSignedOut }: { children: ReactNode; onSignedOut?: () => void }) {
  const [user, setUser] = useState<UserSummary | null>(readStoredUser)

  const logout = useCallback(() => {
    clearSession()
    setUser(null)
    onSignedOut?.()
  }, [onSignedOut])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(() => {})
  }, [logout])

  const login = useCallback(async (username: string, password: string, serverUrl: string) => {
    setServerUrl(serverUrl)
    clearSession()
    const response = await apiFetch<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    if (!isDesktopRole(response.user.role)) {
      throw new Error(DESKTOP_ROLE_ERROR)
    }
    localStorage.setItem(TOKEN_KEY, response.accessToken)
    localStorage.setItem(USER_KEY, JSON.stringify(response.user))
    setUser(response.user)
  }, [])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
