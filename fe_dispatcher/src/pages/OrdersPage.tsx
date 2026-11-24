import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listOrders } from '../api/orders'
import { formatDateTime, formatMoney } from '../components/format'
import { Badge, Button, Pagination, SectionTitle, StateMessage } from '../components/ui'

const STATUSES = ['ALL', 'PENDING', 'CONFIRMED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'] as const
const PAGE_SIZE = 20

export function OrdersPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<string>('ALL')
  const [page, setPage] = useState(0)
  const query = useQuery({
    queryKey: ['orders', status, page],
    queryFn: () => listOrders({ page, size: PAGE_SIZE, status }),
  })

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Orders</h1>
          <p>All customer orders, newest first</p>
        </div>
        <Button icon="refresh" onClick={() => query.refetch()}>
          {query.isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      <section className="panel">
        <SectionTitle
          title="Order list"
          meta={query.data ? `${query.data.totalElements} orders` : undefined}
          action={
            <label className="field inline-field">
              <span>Status</span>
              <select
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value)
                  setPage(0)
                }}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s === 'ALL' ? 'All' : s.replaceAll('_', ' ')}
                  </option>
                ))}
              </select>
            </label>
          }
        />
        {query.isPending ? (
          <StateMessage>Loading orders…</StateMessage>
        ) : query.isError ? (
          <StateMessage tone="error">{query.error.message}</StateMessage>
        ) : query.data.content.length === 0 ? (
          <StateMessage>No orders match this filter.</StateMessage>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Recipient</th>
                    <th>Phone</th>
                    <th>Delivery address</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.content.map((o) => (
                    <tr key={o.id} className="clickable-row" onClick={() => navigate(`/orders/${o.id}`)}>
                      <td>
                        <strong>{o.orderNumber}</strong>
                      </td>
                      <td>{o.recipientName}</td>
                      <td>{o.recipientPhone}</td>
                      <td>{o.deliveryAddress}</td>
                      <td>{formatMoney(o.totalAmount)}</td>
                      <td>
                        <Badge>{o.status}</Badge>
                      </td>
                      <td>{formatDateTime(o.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} size={PAGE_SIZE} totalElements={query.data.totalElements} onPage={setPage} />
          </>
        )}
      </section>
    </div>
  )
}
