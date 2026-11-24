import AsyncStorage from '@react-native-async-storage/async-storage'
import { DEFAULT_SERVER_URL } from './config'
import type { UserSummary } from './types'

const STORAGE_KEY = 'logistics.session'

export interface Session {
  serverUrl: string
  token: string | null
  user: UserSummary | null
}

/** In-memory copy so every request can read the token synchronously. */
let current: Session = { serverUrl: DEFAULT_SERVER_URL, token: null, user: null }

export function getSession(): Session {
  return current
}

export function setSession(session: Session): void {
  current = session
}

export async function loadSession(): Promise<Session> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY)
    if (raw) current = { ...current, ...(JSON.parse(raw) as Partial<Session>) }
  } catch {
    // corrupted or unavailable storage: start signed out
  }
  return current
}

export async function saveSession(session: Session): Promise<void> {
  current = session
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // the session still works for this app run
  }
}

/** Signs out but remembers the server address for the next sign-in. */
export async function clearSession(): Promise<void> {
  await saveSession({ serverUrl: current.serverUrl, token: null, user: null })
}
