import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { listDrivers } from '../api/drivers'
import type { DriverMonitoringItem } from '../api/types'
import { DriverMap } from '../components/DriverMap'
import { formatAge } from '../components/format'
import { Badge, Button, SectionTitle, StateMessage } from '../components/ui'

function availabilityLabel(d: DriverMonitoringItem): string {
  if (d.status !== 'ACTIVE') return d.status
  if (d.availability !== 'AVAILABLE') return 'UNAVAILABLE'
  return d.operationalStatus || 'AVAILABLE'
}

export function DriversPage() {
  const navigate = useNavigate()
  const query = useQuery({ queryKey: ['drivers'], queryFn: listDrivers, refetchInterval: 10_000 })

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Drivers</h1>
          <p>Availability, active orders and latest GPS position · refreshes every 10 seconds</p>
        </div>
        <Button icon="refresh" onClick={() => query.refetch()}>
          {query.isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {query.isPending ? (
        <StateMessage>Loading drivers…</StateMessage>
      ) : query.isError ? (
        <StateMessage tone="error">{query.error.message}</StateMessage>
      ) : (
        <div className="drivers-layout">
          <section className="panel">
            <SectionTitle title="Fleet" meta={`${query.data.length} drivers`} />
            {query.data.length === 0 ? (
              <StateMessage>No drivers registered yet.</StateMessage>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Driver</th>
                      <th>Vehicle</th>
                      <th>Status</th>
                      <th>Active order</th>
                      <th>Last GPS</th>
                    </tr>
                  </thead>
                  <tbody>
                    {query.data.map((d) => (
                      <tr key={d.driverId}>
                        <td>
                          <strong>{d.fullName}</strong>
                          <br />
                          <small>{d.phoneNumber}</small>
                        </td>
                        <td>
                          {d.vehicleType}
                          <br />
                          <small>{d.vehiclePlate}</small>
                        </td>
                        <td>
                          <Badge>{availabilityLabel(d)}</Badge>
                        </td>
                        <td>
                          {d.activeOrderId ? (
                            <button className="text-button" onClick={() => navigate(`/orders/${d.activeOrderId}`)}>
                              {d.activeOrderNumber ?? 'Open order'}
                            </button>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>
                          {d.hasGps ? formatAge(d.gpsUpdatedAt) : 'No GPS yet'}
                          {d.locationStale && (
                            <>
                              {' '}
                              <Badge tone="warning">STALE</Badge>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="panel map-panel">
            <SectionTitle title="Live map" meta="OpenStreetMap · needs Internet" />
            <DriverMap drivers={query.data} />
            <div className="coords">
              {query.data.map((d) => (
                <span key={d.driverId}>
                  {d.fullName}:{' '}
                  {typeof d.latitude === 'number' && typeof d.longitude === 'number'
                    ? `${d.latitude.toFixed(5)}, ${d.longitude.toFixed(5)}`
                    : 'no position'}
                </span>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
