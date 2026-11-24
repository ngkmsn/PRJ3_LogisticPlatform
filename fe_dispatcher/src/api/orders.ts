import { apiFetch } from './client'
import type { AssignmentHistoryDto, OrderDto, OrderStatusHistoryDto, Paged } from './types'

export function listOrders(params: { page: number; size: number; status?: string }): Promise<Paged<OrderDto>> {
  const search = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status && params.status !== 'ALL') search.set('status', params.status)
  return apiFetch<Paged<OrderDto>>(`/api/orders?${search.toString()}`)
}

export function getOrder(id: string): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/api/orders/${encodeURIComponent(id)}`)
}

export function getOrderHistory(id: string): Promise<OrderStatusHistoryDto[]> {
  return apiFetch<OrderStatusHistoryDto[]>(`/api/orders/${encodeURIComponent(id)}/history`)
}

export function getAssignmentHistory(orderId: string): Promise<AssignmentHistoryDto[]> {
  return apiFetch<AssignmentHistoryDto[]>(`/api/shipments/assignments/${encodeURIComponent(orderId)}/history`)
}
