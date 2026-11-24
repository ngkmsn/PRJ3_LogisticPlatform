import { useQuery, useQueryClient } from '@tanstack/react-query'
import { router, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { Alert, Text, View } from 'react-native'
import { cancelOrder, getOrder, getOrderHistory } from '../../../api/endpoints'
import {
  Button,
  Card,
  ErrorText,
  formatDateTime,
  formatMoney,
  Loading,
  Muted,
  Row,
  Screen,
  StatusBadge,
  styles,
} from '../../../components/ui'
import { canCancel, canTrack } from '../../../features/rules'

export default function CustomerOrderDetail() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)

  // Delivery progress comes from the driver, so keep the status fresh while the order is open.
  const order = useQuery({ queryKey: ['order', id], queryFn: () => getOrder(id), refetchInterval: 10_000 })
  const history = useQuery({ queryKey: ['order', id, 'history'], queryFn: () => getOrderHistory(id), refetchInterval: 10_000 })

  function confirmCancel() {
    Alert.alert('Cancel this order?', 'The delivery request will be withdrawn.', [
      { text: 'Keep order', style: 'cancel' },
      {
        text: 'Cancel order',
        style: 'destructive',
        onPress: async () => {
          setCancelling(true)
          setError(null)
          try {
            await cancelOrder(id, 'Cancelled by customer in the mobile app')
            await Promise.all([
              queryClient.invalidateQueries({ queryKey: ['order', id] }),
              queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
            ])
          } catch (e) {
            setError(e instanceof Error ? e.message : 'Could not cancel the order')
          } finally {
            setCancelling(false)
          }
        },
      },
    ])
  }

  if (order.isPending) return <Loading />
  if (order.isError) return <Screen><ErrorText>{order.error.message}</ErrorText></Screen>

  const o = order.data
  return (
    <Screen refreshing={order.isRefetching} onRefresh={() => order.refetch()}>
      <Card>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
          <Text style={styles.title}>{o.orderNumber}</Text>
          <StatusBadge status={o.status} />
        </View>
        <Muted>Created {formatDateTime(o.createdAt)}</Muted>
      </Card>

      {canTrack(o.status) && <Button title="Track driver on map" onPress={() => router.push(`/customer/track/${o.id}`)} />}

      <Card>
        <Row label="Recipient" value={o.recipientName} />
        <Row label="Phone" value={o.recipientPhone} />
        <Row label="Address" value={o.deliveryAddress} />
        <Row label="Total" value={formatMoney(o.totalAmount)} />
        {o.notes ? <Row label="Notes" value={o.notes} /> : null}
      </Card>

      <Card>
        <Text style={{ fontWeight: '700' }}>Items</Text>
        {o.items.map((item) => (
          <Row key={item.id} label={`${item.quantity} × ${item.productName}`} value={formatMoney(item.totalPrice)} />
        ))}
      </Card>

      <Card>
        <Text style={{ fontWeight: '700' }}>Progress</Text>
        {history.data?.map((h) => (
          <View key={h.id} style={{ paddingVertical: 4 }}>
            <Text>
              {formatDateTime(h.createdAt)} · <Text style={{ fontWeight: '600' }}>{h.newStatus.replaceAll('_', ' ')}</Text>
            </Text>
            {h.reason ? <Muted>{h.reason}</Muted> : null}
          </View>
        )) ?? <Muted>Loading…</Muted>}
      </Card>

      <ErrorText>{error}</ErrorText>
      {canCancel(o.status) && <Button title="Cancel order" variant="danger" onPress={confirmCancel} loading={cancelling} />}
    </Screen>
  )
}
