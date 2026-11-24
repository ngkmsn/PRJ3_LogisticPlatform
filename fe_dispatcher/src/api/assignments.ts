import { apiFetch } from './client'
import type { ActionableOrdersData } from './types'

export type ActionableFilter = 'ALL' | 'UNASSIGNED' | 'FAILED'

export function listActionable(params: { status?: ActionableFilter; page: number; size: number }): Promise<ActionableOrdersData> {
  const search = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status && params.status !== 'ALL') search.set('status', params.status)
  return apiFetch<ActionableOrdersData>(`/api/shipments/assignments/actionable?${search.toString()}`)
}

export async function assignDriver(orderId: string, driverId: string, notes?: string): Promise<void> {
  await apiFetch(`/api/shipments/assignments/${encodeURIComponent(orderId)}/manual-assign`, {
    method: 'POST',
    body: JSON.stringify({ driverId, notes }),
  })
}

export async function reassignDriver(orderId: string, newDriverId: string, reason: string, notes?: string): Promise<void> {
  await apiFetch(`/api/shipments/assignments/${encodeURIComponent(orderId)}/reassign`, {
    method: 'POST',
    body: JSON.stringify({ newDriverId, reason, notes }),
  })
}
