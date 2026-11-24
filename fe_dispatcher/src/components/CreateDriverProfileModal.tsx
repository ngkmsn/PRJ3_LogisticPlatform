import { useState, type FormEvent } from 'react'
import { createDriverProfile } from '../api/users'
import { Button, Icon } from './ui'

const VEHICLE_TYPES = ['MOTORBIKE', 'CAR', 'TRUCK_2T'] as const

const REQUIRED: Array<[keyof FormState, string]> = [
  ['fullName', 'Full name'],
  ['phoneNumber', 'Phone'],
  ['licenseNumber', 'License number'],
  ['licenseClass', 'License class'],
  ['vehiclePlate', 'Vehicle plate'],
]

interface FormState {
  fullName: string
  phoneNumber: string
  licenseNumber: string
  licenseClass: string
  vehicleType: string
  vehiclePlate: string
  address: string
}

export function CreateDriverProfileModal({
  userId,
  username,
  onClose,
  onDone,
}: {
  userId: string
  username: string
  onClose: () => void
  onDone: () => void
}) {
  const [form, setForm] = useState<FormState>({
    fullName: '',
    phoneNumber: '',
    licenseNumber: '',
    licenseClass: '',
    vehicleType: 'MOTORBIKE',
    vehiclePlate: '',
    address: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (key: keyof FormState) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const missing = REQUIRED.find(([key]) => form[key].trim() === '')
    if (missing) {
      setError(`${missing[1]} is required`)
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await createDriverProfile({
        userId,
        fullName: form.fullName.trim(),
        phoneNumber: form.phoneNumber.trim(),
        licenseNumber: form.licenseNumber.trim(),
        licenseClass: form.licenseClass.trim(),
        vehicleType: form.vehicleType,
        vehiclePlate: form.vehiclePlate.trim(),
        address: form.address.trim() || undefined,
      })
      onDone()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the driver profile')
    } finally {
      setSubmitting(false)
    }
  }

  const textField = (key: keyof FormState, label: string) => (
    <label className="field" key={key}>
      <span>{label}</span>
      <input value={form[key]} onChange={(e) => set(key)(e.target.value)} />
    </label>
  )

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Create driver profile">
      <form className="modal form-modal" onSubmit={onSubmit} noValidate>
        <div className="modal-head">
          <div>
            <h2>Create driver profile</h2>
            <p>For user {username}</p>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <Icon name="close" />
          </button>
        </div>
        <div className="modal-body form-grid">
          {REQUIRED.map(([key, label]) => textField(key, label))}
          <label className="field">
            <span>Vehicle type</span>
            <select value={form.vehicleType} onChange={(e) => set('vehicleType')(e.target.value)}>
              {VEHICLE_TYPES.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          {textField('address', 'Address (optional)')}
          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
        </div>
        <div className="modal-foot">
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Create profile'}
          </Button>
        </div>
      </form>
    </div>
  )
}
