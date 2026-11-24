import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { assignDriver, reassignDriver } from '../api/assignments'
import { listDrivers } from '../api/drivers'
import { formatAge } from './format'
import { Badge, Button, Icon, StateMessage } from './ui'

export function AssignDriverModal({
  orderId,
  orderLabel,
  mode,
  onClose,
  onDone,
}: {
  orderId: string
  orderLabel?: string
  mode: 'assign' | 'reassign'
  onClose: () => void
  onDone: () => void
}) {
  const queryClient = useQueryClient()
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: listDrivers })
  const [selected, setSelected] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const canSubmit = selected !== null && (mode === 'assign' || reason.trim() !== '') && !submitting
  const actionLabel = mode === 'assign' ? 'Assign' : 'Reassign'

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'assign') {
        await assignDriver(orderId, selected, notes.trim() || undefined)
      } else {
        await reassignDriver(orderId, selected, reason.trim())
      }
      await Promise.all(
        [['actionable'], ['dashboard'], ['orders'], ['order', orderId], ['drivers']].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      )
      onDone()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Assignment failed')
      queryClient.invalidateQueries({ queryKey: ['drivers'] })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={`${actionLabel} driver`}>
      <form className="modal assign-modal" onSubmit={onSubmit}>
        <div className="modal-head">
          <div>
            <h2>{actionLabel} driver</h2>
            <p>{orderLabel ?? orderId}</p>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <Icon name="close" />
          </button>
        </div>

        <div className="modal-body">
          {drivers.isPending ? (
            <StateMessage>Loading drivers…</StateMessage>
          ) : drivers.isError ? (
            <StateMessage tone="error">{drivers.error.message}</StateMessage>
          ) : drivers.data.length === 0 ? (
            <StateMessage>No drivers registered yet.</StateMessage>
          ) : (
            <div className="driver-options">
              {drivers.data.map((d) => {
                const unavailable = d.availability !== 'AVAILABLE' || d.status !== 'ACTIVE'
                return (
                  <label key={d.driverId} className={`driver-option ${unavailable ? 'disabled' : ''} ${selected === d.driverId ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="driver"
                      value={d.driverId}
                      checked={selected === d.driverId}
                      disabled={unavailable}
                      onChange={() => setSelected(d.driverId)}
                      aria-label={d.fullName}
                    />
                    <div>
                      <strong>{d.fullName}</strong>
                      <span>
                        {d.vehicleType} · {d.vehiclePlate} · {d.phoneNumber}
                      </span>
                    </div>
                    <div className="driver-option-meta">
                      <Badge>{unavailable ? 'UNAVAILABLE' : d.operationalStatus}</Badge>
                      <small>{d.hasActiveOrder ? `Active: ${d.activeOrderNumber ?? 'order'}` : 'No active order'}</small>
                      <small>{d.hasGps ? `GPS ${formatAge(d.gpsUpdatedAt)}` : 'No GPS yet'}</small>
                    </div>
                  </label>
                )
              })}
            </div>
          )}

          {mode === 'reassign' ? (
            <label className="field">
              <span>Reason</span>
              <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Why is the driver being changed?" />
            </label>
          ) : (
            <label className="field">
              <span>Notes (optional)</span>
              <input value={notes} onChange={(e) => setNotes(e.target.value)} />
            </label>
          )}

          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal-foot">
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="primary" disabled={!canSubmit}>
            {submitting ? 'Saving…' : actionLabel}
          </Button>
        </div>
      </form>
    </div>
  )
}
