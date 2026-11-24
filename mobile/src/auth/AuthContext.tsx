import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../api/client'
import { normalizeServerUrl } from '../api/config'
import { login as loginRequest, registerCustomer } from '../api/endpoints'
import { clearSession, getSession, loadSession, saveSession, setSession } from '../api/session'
import type { LoginResponse, UserSummary } from '../api/types'
import { isMobileRole, MOBILE_ROLE_ERROR } from './roles'

interface AuthState {
  ready: boolean
  user: UserSummary | null
  serverUrl: string
  login(username: string, password: string, serverUrl: string): Promise<UserSummary>
  register(username: string, email: string, password: string, serverUrl: string): Promise<UserSummary>
  logout(): Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [ready, setReady] = useState(false)
  const [user, setUser] = useState<UserSummary | null>(null)
  const [serverUrl, setServerUrl] = useState(getSession().serverUrl)

  useEffect(() => {
    loadSession().then((s) => {
      setServerUrl(s.serverUrl)
      setUser(s.token && s.user && isMobileRole(s.user.role) ? s.user : null)
      setReady(true)
    })
  }, [])

  const logout = useCallback(async () => {
    await clearSession()
    queryClient.clear()
    setUser(null)
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      void logout()
    })
    return () => setUnauthorizedHandler(() => {})
  }, [logout])

  const signIn = useCallback(async (request: () => Promise<LoginResponse>, rawServerUrl: string) => {
    const url = normalizeServerUrl(rawServerUrl)
    setSession({ serverUrl: url, token: null, user: null })
    setServerUrl(url)
    const response = await request()
    if (!isMobileRole(response.user.role)) {
      await clearSession()
      throw new Error(MOBILE_ROLE_ERROR)
    }
    await saveSession({ serverUrl: url, token: response.accessToken, user: response.user })
    setUser(response.user)
    return response.user
  }, [])

  const login = useCallback(
    (username: string, password: string, url: string) => signIn(() => loginRequest(username, password), url),
    [signIn],
  )
  const register = useCallback(
    (username: string, email: string, password: string, url: string) =>
      signIn(() => registerCustomer(username, email, password), url),
    [signIn],
  )

  const value = useMemo(
    () => ({ ready, user, serverUrl, login, register, logout }),
    [ready, user, serverUrl, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
