import { apiFetch } from './client'
import type { DriverMonitoringItem, Paged } from './types'

/** Every driver profile with availability, active order and latest GPS (operations view). */
export async function listDrivers(): Promise<DriverMonitoringItem[]> {
  const data = await apiFetch<Paged<DriverMonitoringItem> | DriverMonitoringItem[]>(
    '/api/shipments/tracking/operations/drivers?page=0&size=100',
  )
  if (Array.isArray(data)) return data
  return data?.content ?? []
}

export function getDriver(driverId: string): Promise<DriverMonitoringItem> {
  return apiFetch<DriverMonitoringItem>(`/api/shipments/tracking/operations/drivers/${encodeURIComponent(driverId)}`)
}

/** Drivers whose latitude and longitude are both finite numbers. */
export function driversWithPosition(drivers: DriverMonitoringItem[]): DriverMonitoringItem[] {
  return drivers.filter(
    (d) => typeof d.latitude === 'number' && Number.isFinite(d.latitude) && typeof d.longitude === 'number' && Number.isFinite(d.longitude),
  )
}
