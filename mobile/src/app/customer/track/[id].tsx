import { useQuery } from '@tanstack/react-query'
import { useLocalSearchParams } from 'expo-router'
import { StyleSheet, Text, View } from 'react-native'
import MapView, { Marker } from 'react-native-maps'
import { getOrderTracking } from '../../../api/endpoints'
import { Card, ErrorText, formatDateTime, Loading, Muted, StatusBadge } from '../../../components/ui'

export default function TrackDriver() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const tracking = useQuery({ queryKey: ['tracking', id], queryFn: () => getOrderTracking(id), refetchInterval: 5_000 })

  if (tracking.isPending) return <Loading label="Finding your driver…" />
  if (tracking.isError) {
    return (
      <View style={{ padding: 16 }}>
        <ErrorText>{tracking.error.message}</ErrorText>
      </View>
    )
  }

  const t = tracking.data
  const region = { latitude: t.latitude, longitude: t.longitude, latitudeDelta: 0.03, longitudeDelta: 0.03 }

  return (
    <View style={{ flex: 1 }}>
      <MapView style={StyleSheet.absoluteFill} region={region}>
        <Marker coordinate={{ latitude: t.latitude, longitude: t.longitude }} title={t.driverName ?? 'Your driver'} description={t.orderNumber} />
      </MapView>
      <View style={local.panel}>
        <Card>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
            <Text style={{ fontWeight: '700', fontSize: 16 }}>{t.driverName ?? 'Your driver'}</Text>
            <StatusBadge status={t.orderStatus} />
          </View>
          <Muted>
            Last update {formatDateTime(t.updatedAt)}
            {typeof t.locationAgeMinutes === 'number' ? ` · ${t.locationAgeMinutes} min ago` : ''}
          </Muted>
          {t.stale ? <Text style={local.warning}>{t.staleWarning ?? 'The driver location may be out of date.'}</Text> : null}
          <Muted>Updates every 5 seconds</Muted>
        </Card>
      </View>
    </View>
  )
}

const local = StyleSheet.create({
  panel: { position: 'absolute', left: 12, right: 12, bottom: 28 },
  warning: { color: '#8e651e', backgroundColor: '#fff7e8', padding: 8, borderRadius: 8, overflow: 'hidden' },
})
