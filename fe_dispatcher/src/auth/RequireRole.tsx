import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import type { Role } from '../api/types'
import { StateMessage } from '../components/ui'
import { useAuth } from './AuthContext'

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!roles.includes(user.role)) {
    return <StateMessage tone="error">You don't have permission to view this page.</StateMessage>
  }
  return <>{children}</>
}
