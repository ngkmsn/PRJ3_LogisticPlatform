import type { DriverAction } from '../api/endpoints'
import type { AssignmentStatus, CreateOrderBody, OrderStatus } from '../api/types'

// ---- Driver delivery flow ----

const NEXT_ACTION: Partial<Record<AssignmentStatus, { action: DriverAction; label: string }>> = {
  ASSIGNED: { action: 'pickup', label: 'Confirm pickup' },
  PICKED_UP: { action: 'start-transit', label: 'Start delivery' },
  IN_TRANSIT: { action: 'deliver', label: 'Mark as delivered' },
}

/** The single forward step a driver can take for an assignment, or null when finished. */
export function nextDriverAction(status: AssignmentStatus): { action: DriverAction; label: string } | null {
  return NEXT_ACTION[status] ?? null
}

/** A failed delivery can be reported once the parcel is picked up. */
export function canReportFailure(status: AssignmentStatus): boolean {
  return status === 'PICKED_UP' || status === 'IN_TRANSIT'
}

// ---- Customer order rules ----

export function canCancel(status: OrderStatus): boolean {
  return status === 'PENDING'
}

export function canTrack(status: OrderStatus): boolean {
  return status === 'ASSIGNED' || status === 'IN_TRANSIT'
}

// ---- Create order form ----

export interface OrderFormFields {
  recipientName: string
  recipientPhone: string
  deliveryAddress: string
  notes: string
  productName: string
  quantity: string
  unitPrice: string
}

export type OrderFormResult = { ok: true; body: CreateOrderBody } | { ok: false; error: string }

const REQUIRED: Array<[keyof OrderFormFields, string]> = [
  ['recipientName', 'Recipient name'],
  ['recipientPhone', 'Phone number'],
  ['deliveryAddress', 'Delivery address'],
  ['productName', 'Product name'],
]

export function validateOrderForm(fields: OrderFormFields): OrderFormResult {
  for (const [key, label] of REQUIRED) {
    if (fields[key].trim() === '') return { ok: false, error: `${label} is required` }
  }
  const phone = fields.recipientPhone.replace(/[\s.-]/g, '')
  if (!/^\+?\d{9,11}$/.test(phone)) return { ok: false, error: 'Phone number must be 9 to 11 digits' }

  const quantity = Number(fields.quantity)
  if (!Number.isInteger(quantity) || quantity < 1) {
    return { ok: false, error: 'Quantity must be a whole number of at least 1' }
  }
  const unitPrice = Number(fields.unitPrice.replace(/[,\s]/g, ''))
  if (!Number.isFinite(unitPrice) || unitPrice <= 0) return { ok: false, error: 'Price must be greater than 0' }

  return {
    ok: true,
    body: {
      recipientName: fields.recipientName.trim(),
      recipientPhone: phone,
      deliveryAddress: fields.deliveryAddress.trim(),
      notes: fields.notes.trim() || undefined,
      items: [{ productName: fields.productName.trim(), quantity, unitPrice }],
    },
  }
}
