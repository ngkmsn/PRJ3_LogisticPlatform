import { apiFetch } from './client'
import type {
  AssignmentStatus,
  Availability,
  CreateOrderBody,
  DriverAvailabilityDto,
  DriverOrderDto,
  DriverProfileDto,
  LoginResponse,
  OrderDto,
  OrderStatus,
  OrderStatusHistoryDto,
  OrderTrackingDto,
  Paged,
} from './types'

const json = (body: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(body) })

// ---- Auth ----

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/auth/login', json({ username, password }))
}

export function registerCustomer(username: string, email: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/auth/register', json({ username, email, password }))
}

// ---- Customer ----

export function listMyOrders(page = 0, size = 10, status?: OrderStatus): Promise<Paged<OrderDto>> {
  const filter = status ? `&status=${status}` : ''
  return apiFetch<Paged<OrderDto>>(`/api/orders?page=${page}&size=${size}${filter}`)
}

export function getOrder(id: string): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/api/orders/${encodeURIComponent(id)}`)
}

export function getOrderHistory(id: string): Promise<OrderStatusHistoryDto[]> {
  return apiFetch<OrderStatusHistoryDto[]>(`/api/orders/${encodeURIComponent(id)}/history`)
}

export function createOrder(body: CreateOrderBody): Promise<OrderDto> {
  return apiFetch<OrderDto>('/api/orders', json(body))
}

export function cancelOrder(id: string, reason: string): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/api/orders/${encodeURIComponent(id)}/cancel`, json({ reason }))
}

/** allowStale: still show the last known position (with a warning) when GPS is older than 30 min. */
export function getOrderTracking(id: string): Promise<OrderTrackingDto> {
  return apiFetch<OrderTrackingDto>(`/api/orders/${encodeURIComponent(id)}/tracking?allowStale=true`)
}

// ---- Driver ----

export function getMyDriverProfile(): Promise<DriverProfileDto> {
  return apiFetch<DriverProfileDto>('/api/drivers/me')
}

export function setMyAvailability(availability: Availability): Promise<DriverAvailabilityDto> {
  return apiFetch<DriverAvailabilityDto>('/api/drivers/me/availability', {
    method: 'PATCH',
    body: JSON.stringify({ availability }),
  })
}

export function listDriverOrders(page = 0, size = 10, status?: AssignmentStatus): Promise<Paged<DriverOrderDto>> {
  const filter = status ? `&status=${status}` : ''
  return apiFetch<Paged<DriverOrderDto>>(`/api/shipments/assignments/my-orders?page=${page}&size=${size}${filter}`)
}

/** There is no driver-scoped "get one" endpoint, so walk the driver's pages until the order shows up. */
export async function findDriverOrder(orderId: string): Promise<DriverOrderDto | null> {
  for (let page = 0; ; page++) {
    const res = await listDriverOrders(page, 50)
    const hit = res.content.find((o) => o.orderId === orderId)
    if (hit) return hit
    if (page + 1 >= res.totalPages) return null
  }
}

export type DriverAction = 'pickup' | 'start-transit' | 'deliver' | 'fail'

export function runDriverAction(orderId: string, action: DriverAction, body: Record<string, string> = {}): Promise<unknown> {
  return apiFetch(`/api/shipments/assignments/${encodeURIComponent(orderId)}/${action}`, json(body))
}

export function sendLocation(latitude: number, longitude: number, extra: { orderId?: string; accuracy?: number; speed?: number; bearing?: number } = {}) {
  return apiFetch('/api/shipments/tracking/location', json({ latitude, longitude, ...extra }))
}
