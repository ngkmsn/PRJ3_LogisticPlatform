import { apiFetch } from './client'
import type { Paged, Role, UserProfile, UserStatus } from './types'

export function listUsers(params: { page: number; size: number }): Promise<Paged<UserProfile>> {
  return apiFetch<Paged<UserProfile>>(`/api/users?page=${params.page}&size=${params.size}`)
}

export function createUser(body: { username: string; email: string; password: string; role: Role }): Promise<UserProfile> {
  return apiFetch<UserProfile>('/api/users', { method: 'POST', body: JSON.stringify(body) })
}

export async function setUserStatus(id: string, status: UserStatus): Promise<void> {
  await apiFetch(`/api/users/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
}

export interface CreateDriverProfileBody {
  userId: string
  fullName: string
  phoneNumber: string
  licenseNumber: string
  licenseClass: string
  vehicleType: string
  vehiclePlate: string
  address?: string
}

export async function createDriverProfile(body: CreateDriverProfileBody): Promise<void> {
  await apiFetch('/api/drivers', { method: 'POST', body: JSON.stringify(body) })
}
