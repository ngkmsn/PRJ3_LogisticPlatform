import { describe, expect, it } from 'vitest'
import { assignModeFor } from './assignMode'

describe('assignModeFor', () => {
  it('assigns a driver to an order that never had one', () => {
    expect(assignModeFor('PENDING')).toBe('assign')
  })

  it('reassigns a failed delivery (order shows CONFIRMED while awaiting re-dispatch)', () => {
    expect(assignModeFor('CONFIRMED')).toBe('reassign')
  })

  it('offers no driver action once a driver is on it or the order is closed', () => {
    expect(assignModeFor('ASSIGNED')).toBeNull()
    expect(assignModeFor('IN_TRANSIT')).toBeNull()
    expect(assignModeFor('DELIVERED')).toBeNull()
    expect(assignModeFor('CANCELLED')).toBeNull()
  })
})
