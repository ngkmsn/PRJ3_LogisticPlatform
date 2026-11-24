import { apiFetch } from './client'
import type { OperationsDashboardDto } from './types'

export function fetchDashboard(refresh = false): Promise<OperationsDashboardDto> {
  return apiFetch<OperationsDashboardDto>(`/api/shipments/operations/dashboard${refresh ? '?refresh=true' : ''}`)
}
