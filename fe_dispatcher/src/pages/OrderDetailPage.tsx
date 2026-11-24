import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { listDrivers } from '../api/drivers'
import { getAssignmentHistory, getOrder, getOrderHistory } from '../api/orders'
import { AssignDriverModal } from '../components/AssignDriverModal'
import { formatDateTime, formatMoney, humanize } from '../components/format'
import { Badge, Button, SectionTitle, StateMessage } from '../components/ui'
import { assignModeFor } from './assignMode'


export function OrderDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [assignOpen, setAssignOpen] = useState(false)

  const order = useQuery({ queryKey: ['order', id], queryFn: () => getOrder(id) })
  const history = useQuery({ queryKey: ['order', id, 'history'], queryFn: () => getOrderHistory(id) })
  const assignments = useQuery({
    queryKey: ['order', id, 'assignments'],
    queryFn: () => getAssignmentHistory(id),
  })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: listDrivers })
  const driverName = (driverId?: string | null) => {
    if (!driverId) return '—'
    const d = drivers.data?.find((x) => x.driverId === driverId || x.userId === driverId)
    return d ? d.fullName : driverId
  }

  if (order.isPending) return <StateMessage>Loading order…</StateMessage>
  if (order.isError) {
    const notFound = order.error instanceof ApiError && order.error.status === 404
    return (
      <div className="page">
        <button className="back-button" onClick={() => navigate('/orders')}>
          ‹ Back to orders
        </button>
        <StateMessage tone="error">{notFound ? 'Order not found' : order.error.message}</StateMessage>
      </div>
    )
  }

  const o = order.data
  const mode = assignModeFor(o.status)
  return (
    <div className="page order-detail">
      <button className="back-button" onClick={() => navigate(-1)}>
        ‹ Back
      </button>
      <div className="detail-header">
        <div>
          <div>
            <h1>{o.orderNumber}</h1>
            <Badge>{o.status}</Badge>
          </div>
          <p>Created {formatDateTime(o.createdAt)} · Updated {formatDateTime(o.updatedAt)}</p>
        </div>
        <div>
          {mode && (
            <Button variant="primary" icon="route" onClick={() => setAssignOpen(true)}>
              {mode === 'assign' ? 'Assign driver' : 'Reassign driver'}
            </Button>
          )}
        </div>
      </div>

      <div className="detail-grid">
        <div className="detail-main">
          <section className="panel">
            <SectionTitle title="Items" meta={`${o.items.length} item${o.items.length === 1 ? '' : 's'}`} />
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Qty</th>
                    <th>Unit price</th>
                    <th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {o.items.map((item) => (
                    <tr key={item.id}>
                      <td>{item.productName}</td>
                      <td>{item.quantity}</td>
                      <td>{formatMoney(item.unitPrice)}</td>
                      <td>{formatMoney(item.totalPrice)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel">
            <SectionTitle title="Status history" />
            {history.isPending ? (
              <StateMessage>Loading history…</StateMessage>
            ) : history.isError ? (
              <StateMessage tone="error">{history.error.message}</StateMessage>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Time</th>
                      <th>From</th>
                      <th>To</th>
                      <th>By</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.data.map((h) => (
                      <tr key={h.id}>
                        <td>{formatDateTime(h.createdAt)}</td>
                        <td>{h.previousStatus ? <Badge>{h.previousStatus}</Badge> : '—'}</td>
                        <td>
                          <Badge>{h.newStatus}</Badge>
                        </td>
                        <td>{humanize(h.changedByRole)}</td>
                        <td>{h.reason ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="panel">
            <SectionTitle title="Assignment history" />
            {assignments.isPending ? (
              <StateMessage>Loading assignments…</StateMessage>
            ) : assignments.isError ? (
              <StateMessage tone="error">{assignments.error.message}</StateMessage>
            ) : assignments.data.length === 0 ? (
              <StateMessage>No driver has been assigned yet.</StateMessage>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Time</th>
                      <th>Action</th>
                      <th>Previous driver</th>
                      <th>New driver</th>
                      <th>By</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {assignments.data.map((a) => (
                      <tr key={a.id}>
                        <td>{formatDateTime(a.createdAt)}</td>
                        <td>
                          <Badge>{a.actionType}</Badge>
                        </td>
                        <td>{driverName(a.previousDriverId)}</td>
                        <td>{driverName(a.newDriverId)}</td>
                        <td>{a.performedBy}</td>
                        <td>{a.reason ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        <div className="detail-side">
          <section className="panel">
            <SectionTitle title="Recipient" />
            <dl>
              <div>
                <dt>Name</dt>
                <dd>{o.recipientName}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{o.recipientPhone}</dd>
              </div>
              <div>
                <dt>Address</dt>
                <dd>{o.deliveryAddress}</dd>
              </div>
              <div>
                <dt>Total</dt>
                <dd>{formatMoney(o.totalAmount)}</dd>
              </div>
              <div>
                <dt>Notes</dt>
                <dd>{o.notes || '—'}</dd>
              </div>
            </dl>
          </section>
        </div>
      </div>

      {assignOpen && mode && (
        <AssignDriverModal orderId={o.id} orderLabel={o.orderNumber} mode={mode} onClose={() => setAssignOpen(false)} onDone={() => setAssignOpen(false)} />
      )}
    </div>
  )
}
