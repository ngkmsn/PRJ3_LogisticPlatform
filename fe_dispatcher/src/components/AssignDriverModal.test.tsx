import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { DriverMonitoringItem } from '../api/types'

const assignDriver = vi.fn()
const reassignDriver = vi.fn()
const listDrivers = vi.fn()

vi.mock('../api/assignments', () => ({
  assignDriver: (...args: unknown[]) => assignDriver(...args),
  reassignDriver: (...args: unknown[]) => reassignDriver(...args),
}))
vi.mock('../api/drivers', () => ({
  listDrivers: () => listDrivers(),
}))

import { AssignDriverModal } from './AssignDriverModal'

function driver(overrides: Partial<DriverMonitoringItem>): DriverMonitoringItem {
  return {
    driverId: 'd-1',
    userId: 'u-1',
    fullName: 'Driver One',
    phoneNumber: '0900',
    vehicleType: 'MOTORBIKE',
    vehiclePlate: '59X1',
    status: 'ACTIVE',
    availability: 'AVAILABLE',
    operationalStatus: 'AVAILABLE',
    hasActiveOrder: false,
    hasGps: true,
    ...overrides,
  }
}

function renderModal(props: Partial<Parameters<typeof AssignDriverModal>[0]> = {}) {
  const onClose = vi.fn()
  const onDone = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AssignDriverModal orderId="order-1" mode="assign" onClose={onClose} onDone={onDone} {...props} />
    </QueryClientProvider>,
  )
  return { onClose, onDone }
}

describe('AssignDriverModal', () => {
  beforeEach(() => {
    listDrivers.mockResolvedValue([
      driver({ driverId: 'd-1', fullName: 'Driver One' }),
      driver({ driverId: 'd-2', fullName: 'Driver Two' }),
      driver({ driverId: 'd-3', fullName: 'Off Duty', availability: 'UNAVAILABLE' }),
    ])
  })

  afterEach(() => {
    assignDriver.mockReset()
    reassignDriver.mockReset()
    listDrivers.mockReset()
  })

  it('assigns the selected driver by driver profile id', async () => {
    assignDriver.mockResolvedValue(undefined)
    const { onDone } = renderModal()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('radio', { name: /driver two/i }))
    await user.click(screen.getByRole('button', { name: /^assign$/i }))

    expect(assignDriver).toHaveBeenCalledWith('order-1', 'd-2', undefined)
    expect(onDone).toHaveBeenCalled()
  })

  it('keeps the modal open and shows the backend message when assignment is rejected', async () => {
    assignDriver.mockRejectedValue(new ApiError('Driver is not available', 400))
    const { onDone } = renderModal()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('radio', { name: /driver one/i }))
    await user.click(screen.getByRole('button', { name: /^assign$/i }))

    expect(await screen.findByText('Driver is not available')).toBeInTheDocument()
    expect(onDone).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /^assign$/i })).toBeInTheDocument()
  })

  it('does not let unavailable drivers be selected', async () => {
    renderModal()
    expect(await screen.findByRole('radio', { name: /off duty/i })).toBeDisabled()
  })

  it('requires a reason before reassigning', async () => {
    reassignDriver.mockResolvedValue(undefined)
    renderModal({ mode: 'reassign' })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('radio', { name: /driver two/i }))
    expect(screen.getByRole('button', { name: /^reassign$/i })).toBeDisabled()

    await user.type(screen.getByLabelText('Reason'), 'Customer asked for a later slot')
    await user.click(screen.getByRole('button', { name: /^reassign$/i }))

    expect(reassignDriver).toHaveBeenCalledWith('order-1', 'd-2', 'Customer asked for a later slot')
  })
})
