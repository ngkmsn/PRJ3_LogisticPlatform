import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { driversWithPosition } from '../api/drivers'
import type { DriverMonitoringItem } from '../api/types'

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: ({ children }: { children: ReactNode }) => <div data-testid="marker">{children}</div>,
  Popup: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

import { DriverMap } from './DriverMap'

function driver(overrides: Partial<DriverMonitoringItem>): DriverMonitoringItem {
  return {
    driverId: 'd-1',
    userId: 'u-1',
    fullName: 'Driver',
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

describe('driversWithPosition', () => {
  it('keeps only drivers with finite coordinates', () => {
    const result = driversWithPosition([
      driver({ driverId: 'a', latitude: 10.77, longitude: 106.7 }),
      driver({ driverId: 'b', latitude: null, longitude: null, hasGps: false }),
      driver({ driverId: 'c', latitude: undefined, longitude: 106.7 }),
      driver({ driverId: 'd', latitude: Number.NaN, longitude: 106.7 }),
    ])
    expect(result.map((d) => d.driverId)).toEqual(['a'])
  })
})

describe('DriverMap', () => {
  it('renders a marker only for drivers that have a GPS position', () => {
    render(
      <DriverMap
        drivers={[
          driver({ driverId: 'a', fullName: 'With GPS', latitude: 10.77, longitude: 106.7 }),
          driver({ driverId: 'b', fullName: 'No GPS', latitude: null, longitude: null, hasGps: false }),
        ]}
      />,
    )
    expect(screen.getAllByTestId('marker')).toHaveLength(1)
    expect(screen.getByTestId('marker')).toHaveTextContent('With GPS')
  })
})
