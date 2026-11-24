import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listActionable, type ActionableFilter } from '../api/assignments'
import type { ActionableOrderItem } from '../api/types'
import { AssignDriverModal } from '../components/AssignDriverModal'
import { formatDateTime, formatMoney, humanize } from '../components/format'
import { Badge, Button, Pagination, SectionTitle, StateMessage } from '../components/ui'

const PAGE_SIZE = 20

interface ModalState {
  orderId: string
  orderLabel?: string
  mode: 'assign' | 'reassign'
}

function isFailed(item: ActionableOrderItem): boolean {
  return item.status === 'FAILED' || item.issueCategory?.toUpperCase().includes('FAIL')
}

export function NeedsActionPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState<ActionableFilter>('ALL')
  const [page, setPage] = useState(0)
  const [modal, setModal] = useState<ModalState | null>(null)

  const query = useQuery({
    queryKey: ['actionable', filter, page],
    queryFn: () => listActionable({ status: filter, page, size: PAGE_SIZE }),
    refetchInterval: 15_000,
  })

  const counts = query.data
  const tabs: Array<[ActionableFilter, string, number | undefined]> = [
    ['ALL', 'All', counts?.totalActionable],
    ['UNASSIGNED', 'Unassigned', counts?.unassignedCount],
    ['FAILED', 'Failed delivery', counts?.failedCount],
  ]

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Needs action</h1>
          <p>Orders without a driver and failed deliveries waiting for a dispatcher</p>
        </div>
        <Button icon="refresh" onClick={() => query.refetch()}>
          {query.isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      <section className="panel">
        <SectionTitle
          title="Action queue"
          action={
            <div className="tab-bar">
              {tabs.map(([key, label, count]) => (
                <button
                  key={key}
                  className={filter === key ? 'active' : ''}
                  onClick={() => {
                    setFilter(key)
                    setPage(0)
                  }}
                >
                  {label}
                  {count !== undefined && <em>{count}</em>}
                </button>
              ))}
            </div>
          }
        />

        {query.isPending ? (
          <StateMessage>Loading queue…</StateMessage>
        ) : query.isError ? (
          <StateMessage tone="error">{query.error.message}</StateMessage>
        ) : query.data.content.length === 0 ? (
          <StateMessage>Nothing to handle. Every order has a driver.</StateMessage>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Issue</th>
                    <th>Recipient</th>
                    <th>Delivery address</th>
                    <th>Amount</th>
                    <th>Reason</th>
                    <th>Since</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {query.data.content.map((item) => {
                    const failed = isFailed(item)
                    return (
                      <tr key={item.orderId}>
                        <td>
                          <button className="text-button" onClick={() => navigate(`/orders/${item.orderId}`)}>
                            <strong>{item.orderNumber ?? item.orderId}</strong>
                          </button>
                        </td>
                        <td>
                          <Badge tone={failed ? 'critical' : 'high'}>{failed ? 'FAILED' : 'UNASSIGNED'}</Badge>
                        </td>
                        <td>{item.recipientName ?? '—'}</td>
                        <td>{item.deliveryAddress ?? '—'}</td>
                        <td>{formatMoney(item.totalAmount)}</td>
                        <td>{humanize(failed ? item.failureReason : item.unassignedReason)}</td>
                        <td>{formatDateTime(failed ? item.failedAt : item.unassignedAt ?? item.createdAt)}</td>
                        <td>
                          <div className="row-actions">
                            <Button
                              variant="primary"
                              onClick={() =>
                                setModal({
                                  orderId: item.orderId,
                                  orderLabel: item.orderNumber,
                                  mode: failed ? 'reassign' : 'assign',
                                })
                              }
                            >
                              {failed ? 'Reassign' : 'Assign'}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <Pagination page={page} size={PAGE_SIZE} totalElements={query.data.totalElements} onPage={setPage} />
          </>
        )}
      </section>

      {modal && (
        <AssignDriverModal
          orderId={modal.orderId}
          orderLabel={modal.orderLabel}
          mode={modal.mode}
          onClose={() => setModal(null)}
          onDone={() => setModal(null)}
        />
      )}
    </div>
  )
}
