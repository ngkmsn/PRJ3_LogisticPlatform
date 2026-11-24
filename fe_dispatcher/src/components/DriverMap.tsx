import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet'
import { Link } from 'react-router-dom'
import { driversWithPosition } from '../api/drivers'
import type { DriverMonitoringItem } from '../api/types'

/** Ho Chi Minh City centre, where the demo fleet operates. */
const DEFAULT_CENTER: [number, number] = [10.7769, 106.7009]

// Vite bundles Leaflet's marker images under hashed names; point the default icon at them.
const defaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
})

export function DriverMap({ drivers, height = 460 }: { drivers: DriverMonitoringItem[]; height?: number }) {
  const positioned = driversWithPosition(drivers)
  const center: [number, number] = positioned.length
    ? [positioned[0].latitude as number, positioned[0].longitude as number]
    : DEFAULT_CENTER

  return (
    <MapContainer center={center} zoom={12} style={{ height, width: '100%' }} scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {positioned.map((d) => (
        <Marker key={d.driverId} position={[d.latitude as number, d.longitude as number]} icon={defaultIcon}>
          <Popup>
            <strong>{d.fullName}</strong>
            <br />
            {d.vehicleType} · {d.vehiclePlate}
            <br />
            {d.activeOrderId ? (
              <Link to={`/orders/${d.activeOrderId}`}>Active order {d.activeOrderNumber ?? ''}</Link>
            ) : (
              'No active order'
            )}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  )
}
