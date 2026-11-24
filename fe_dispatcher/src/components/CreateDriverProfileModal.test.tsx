import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

const createDriverProfile = vi.fn()
vi.mock('../api/users', () => ({
  createDriverProfile: (...args: unknown[]) => createDriverProfile(...args),
}))

import { CreateDriverProfileModal } from './CreateDriverProfileModal'

async function fillRequired(user: ReturnType<typeof userEvent.setup>, skip?: string) {
  const values: Record<string, string> = {
    'Full name': 'Tran Van Moi',
    Phone: '0901234567',
    'License number': 'GPLX-123',
    'License class': 'A2',
    'Vehicle plate': '59X1-999.99',
  }
  for (const [label, value] of Object.entries(values)) {
    if (label !== skip) await user.type(screen.getByLabelText(label), value)
  }
}

describe('CreateDriverProfileModal', () => {
  afterEach(() => createDriverProfile.mockReset())

  it('does not submit when the license number is missing', async () => {
    const user = userEvent.setup()
    render(<CreateDriverProfileModal userId="user-9" username="driver9" onClose={vi.fn()} onDone={vi.fn()} />)

    await fillRequired(user, 'License number')
    await user.click(screen.getByRole('button', { name: /create profile/i }))

    expect(await screen.findByText('License number is required')).toBeInTheDocument()
    expect(createDriverProfile).not.toHaveBeenCalled()
  })

  it("creates the profile for the selected user's id", async () => {
    createDriverProfile.mockResolvedValue(undefined)
    const onDone = vi.fn()
    const user = userEvent.setup()
    render(<CreateDriverProfileModal userId="user-9" username="driver9" onClose={vi.fn()} onDone={onDone} />)

    await fillRequired(user)
    await user.selectOptions(screen.getByLabelText('Vehicle type'), 'CAR')
    await user.click(screen.getByRole('button', { name: /create profile/i }))

    expect(createDriverProfile).toHaveBeenCalledWith({
      userId: 'user-9',
      fullName: 'Tran Van Moi',
      phoneNumber: '0901234567',
      licenseNumber: 'GPLX-123',
      licenseClass: 'A2',
      vehicleType: 'CAR',
      vehiclePlate: '59X1-999.99',
      address: undefined,
    })
    expect(onDone).toHaveBeenCalled()
  })
})
