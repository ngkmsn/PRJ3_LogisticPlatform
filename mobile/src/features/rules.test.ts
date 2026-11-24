import { canCancel, canTrack, nextDriverAction, canReportFailure, validateOrderForm } from './rules'

describe('nextDriverAction', () => {
  it('walks a delivery forward one step at a time', () => {
    expect(nextDriverAction('ASSIGNED')).toEqual({ action: 'pickup', label: 'Confirm pickup' })
    expect(nextDriverAction('PICKED_UP')).toEqual({ action: 'start-transit', label: 'Start delivery' })
    expect(nextDriverAction('IN_TRANSIT')).toEqual({ action: 'deliver', label: 'Mark as delivered' })
  })

  it('offers nothing once the delivery is finished', () => {
    expect(nextDriverAction('DELIVERED')).toBeNull()
    expect(nextDriverAction('FAILED')).toBeNull()
    expect(nextDriverAction('CANCELLED')).toBeNull()
  })

  it('lets the driver report a failure only after pickup', () => {
    expect(canReportFailure('ASSIGNED')).toBe(false)
    expect(canReportFailure('PICKED_UP')).toBe(true)
    expect(canReportFailure('IN_TRANSIT')).toBe(true)
    expect(canReportFailure('DELIVERED')).toBe(false)
  })
})

describe('customer order rules', () => {
  it('allows cancelling only while the order is pending', () => {
    expect(canCancel('PENDING')).toBe(true)
    expect(canCancel('ASSIGNED')).toBe(false)
  })

  it('allows tracking while a driver has the order', () => {
    expect(canTrack('ASSIGNED')).toBe(true)
    expect(canTrack('IN_TRANSIT')).toBe(true)
    expect(canTrack('PENDING')).toBe(false)
    expect(canTrack('DELIVERED')).toBe(false)
  })
})

describe('validateOrderForm', () => {
  const valid = {
    recipientName: ' Nguyen Van B ',
    recipientPhone: '0987654321',
    deliveryAddress: '123 Nguyen Trai, Quan 1',
    notes: '',
    productName: 'Laptop',
    quantity: '2',
    unitPrice: '150000',
  }

  it('builds the request body from a valid form', () => {
    expect(validateOrderForm(valid)).toEqual({
      ok: true,
      body: {
        recipientName: 'Nguyen Van B',
        recipientPhone: '0987654321',
        deliveryAddress: '123 Nguyen Trai, Quan 1',
        notes: undefined,
        items: [{ productName: 'Laptop', quantity: 2, unitPrice: 150000 }],
      },
    })
  })

  it('reports the first missing field', () => {
    expect(validateOrderForm({ ...valid, deliveryAddress: '  ' })).toEqual({ ok: false, error: 'Delivery address is required' })
  })

  it('rejects a phone number that is not 9-11 digits', () => {
    expect(validateOrderForm({ ...valid, recipientPhone: '12ab' })).toEqual({ ok: false, error: 'Phone number must be 9 to 11 digits' })
  })

  it('rejects a non-positive quantity or price', () => {
    expect(validateOrderForm({ ...valid, quantity: '0' })).toEqual({ ok: false, error: 'Quantity must be a whole number of at least 1' })
    expect(validateOrderForm({ ...valid, unitPrice: '-5' })).toEqual({ ok: false, error: 'Price must be greater than 0' })
  })
})
