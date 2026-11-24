import { Redirect } from 'expo-router'
import type { ReactNode } from 'react'
import type { Role } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { homeRouteFor } from '../auth/roles'
import { Loading } from './ui'

/** Sends signed-out users to /login and users of the other role to their own home. */
export function RoleGate({ role, children }: { role: Role; children: ReactNode }) {
  const { ready, user } = useAuth()
  if (!ready) return <Loading />
  if (!user) return <Redirect href="/login" />
  if (user.role !== role) return <Redirect href={homeRouteFor(user.role)} />
  return <>{children}</>
}
