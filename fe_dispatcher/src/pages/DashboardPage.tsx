import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { fetchDashboard } from '../api/dashboard'
import { formatDateTime, formatMoney, humanize } from '../components/format'
import { Badge, Button, Icon, SectionTitle, StateMessage } from '../components/ui'

const DONUT_COLORS = ['#2b6ff2', '#7456d8', '#e6a23c', '#d7464f']

export function DashboardPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['dashboard'], queryFn: () => fetchDashboard(), refetchInterval: 30_000 })

  async function refresh() {
    const fresh = await fetchDashboard(true)
    queryClient.setQueryData(['dashboard'], fresh)
  }

  if (query.isPending) return <StateMessage>Loading dashboard…</StateMessage>
  if (query.isError) {
    const err = query.error
    return (
      <StateMessage tone="error">
        {err instanceof ApiError && err.status === 403 ? "You don't have permission to view the dashboard." : err.message}{' '}
        <Button onClick={() => query.refetch()}>Retry</Button>
      </StateMessage>
    )
  }

  const { orderMetrics: o, driverMetrics: d, attentionItems, generatedAt } = query.data
  const failed = o.ordersByStatus?.FAILED ?? o.failedOrders
  const waiting = o.pendingOrders + (o.ordersByStatus?.CONFIRMED ?? 0)
  const assigned = o.assignedOrders + o.searchingDriverOrders + o.pickedUpOrders

  const kpis: Array<[string, number, string, string]> = [
    ['Orders today', o.todayOrders, `${o.totalOrders} total`, 'neutral'],
    ['Waiting assignment', waiting, 'pending / re-dispatch', waiting > 0 ? 'warning' : 'neutral'],
    ['Assigned', assigned, 'driver on the way', 'blue'],
    ['In delivery', o.inTransitOrders, 'in transit', 'purple'],
    ['Delivered', o.deliveredOrders, 'completed', 'success'],
    ['Failed', failed, failed > 0 ? 'needs review' : 'none', failed > 0 ? 'danger' : 'neutral'],
    ['Drivers available', d.availableDrivers, `${d.busyDrivers} busy · ${d.totalDrivers} total`, 'neutral'],
  ]

  const legend: Array<[string, number]> = [
    ['Delivered', o.deliveredOrders],
    ['In delivery', o.inTransitOrders + assigned],
    ['Waiting', waiting],
    ['Failed', failed],
  ]
  const legendTotal = legend.reduce((sum, [, n]) => sum + n, 0)
  let cursor = 0
  const gradient = legendTotal
    ? legend
        .map(([, n], i) => {
          const start = cursor
          cursor += (n / legendTotal) * 100
          return `${DONUT_COLORS[i]} ${start}% ${cursor}%`
        })
        .join(', ')
    : '#e6e9ee 0 100%'

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Operations dashboard</h1>
          <p>Live overview of the logistics operation</p>
        </div>
        <div className="updated" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>
            <Icon name="refresh" size={14} /> Updated {formatDateTime(generatedAt)}
          </span>
          <Button onClick={refresh}>{query.isFetching ? 'Refreshing…' : 'Refresh'}</Button>
        </div>
      </div>

      <div className="kpi-grid">
        {kpis.map(([label, value, sub, tone]) => (
          <div className={`kpi kpi-${tone}`} key={label}>
            <div className="kpi-label">
              {label}
              <Icon name={tone === 'danger' ? 'alert' : 'dots'} size={16} />
            </div>
            <strong>{value.toLocaleString('en-US')}</strong>
            <div>{sub}</div>
          </div>
        ))}
      </div>

      <div className="dashboard-grid">
        <section className="panel attention">
          <SectionTitle
            title="Requires attention"
            meta={`${attentionItems.length} operational issue${attentionItems.length === 1 ? '' : 's'}`}
            action={
              <button className="text-button" onClick={() => navigate('/needs-action')}>
                Open queue <Icon name="arrow" size={14} />
              </button>
            }
          />
          <div className="attention-list">
            {attentionItems.length === 0 ? (
              <StateMessage>Nothing needs attention right now.</StateMessage>
            ) : (
              attentionItems.map((x) => (
                <button
                  className="attention-row"
                  key={x.id}
                  onClick={() => navigate(x.referenceId ? `/orders/${x.referenceId}` : '/needs-action')}
                >
                  <div className={`severity-icon severity-${(x.severity || 'medium').toLowerCase()}`}>
                    <Icon name="alert" size={17} />
                  </div>
                  <div className="attention-main">
                    <div>
                      <strong>{humanize(x.type)}</strong>
                      <Badge tone={(x.severity || 'medium').toLowerCase()}>{x.severity}</Badge>
                    </div>
                    <span>{x.title}</span>
                  </div>
                  <div className="attention-age">
                    <Icon name="chevron" size={16} />
                  </div>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="panel status-panel">
          <SectionTitle title="Order status" meta={`${o.totalOrders.toLocaleString('en-US')} total orders`} />
          <div className="donut-wrap">
            <div className="donut" style={{ background: `conic-gradient(${gradient})` }}>
              <div>
                <strong>{o.totalOrders.toLocaleString('en-US')}</strong>
                <span>Total orders</span>
              </div>
            </div>
            <div className="legend">
              {legend.map(([label, n], i) => (
                <div key={label}>
                  <i className={`dot-${i}`} />
                  <span>{label}</span>
                  <strong>{n.toLocaleString('en-US')}</strong>
                  <em>{legendTotal ? `${((n / legendTotal) * 100).toFixed(1)}%` : '0%'}</em>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>

      <section className="panel">
        <SectionTitle
          title="Recent orders"
          action={
            <button className="text-button" onClick={() => navigate('/orders')}>
              All orders <Icon name="arrow" size={14} />
            </button>
          }
        />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Recipient</th>
                <th>Delivery address</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {(o.recentOrders ?? []).length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <StateMessage>No orders yet.</StateMessage>
                  </td>
                </tr>
              ) : (
                (o.recentOrders ?? []).map((r) => (
                  <tr key={r.id} className="clickable-row" onClick={() => navigate(`/orders/${r.id}`)}>
                    <td>
                      <strong>{r.orderNumber}</strong>
                    </td>
                    <td>{r.recipientName}</td>
                    <td>{r.deliveryAddress}</td>
                    <td>{formatMoney(r.totalAmount)}</td>
                    <td>
                      <Badge>{r.status}</Badge>
                    </td>
                    <td>{formatDateTime(r.createdAt)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
