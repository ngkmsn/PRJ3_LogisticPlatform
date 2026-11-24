import type { OrderStatus } from '../api/types'

/**
 * Which driver action operations can take on an order.
 * PENDING = no driver yet → manual assign. CONFIRMED = a delivery failed and the order is
 * waiting for re-dispatch (PB-059 status mapping) → reassign. Anything else: none.
 */
export function assignModeFor(status: OrderStatus): 'assign' | 'reassign' | null {
  if (status === 'PENDING') return 'assign'
  if (status === 'CONFIRMED') return 'reassign'
  return null
}
