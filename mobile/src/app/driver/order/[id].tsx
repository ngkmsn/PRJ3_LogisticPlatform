import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { Linking, Text, View } from 'react-native'
import { findDriverOrder, runDriverAction } from '../../../api/endpoints'
import { Button, Card, ErrorText, Field, formatMoney, Loading, Muted, Row, Screen, StatusBadge, styles } from '../../../components/ui'
import { canReportFailure, nextDriverAction } from '../../../features/rules'

const FAILURE_REASONS = [
  ['CUSTOMER_UNAVAILABLE', 'Customer not available'],
  ['WRONG_ADDRESS', 'Wrong address'],
  ['CUSTOMER_REFUSED', 'Customer refused'],
  ['DAMAGED_PACKAGE', 'Package damaged'],
] as const

export default function DriverOrderDetail() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const queryClient = useQueryClient()
  const orders = useQuery({ queryKey: ['driver-orders', 'detail', id], queryFn: () => findDriverOrder(id), refetchInterval: 10_000 })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notes, setNotes] = useState('')
  const [failing, setFailing] = useState(false)

  if (orders.isPending) return <Loading />
  if (orders.isError) return <Screen><ErrorText>{orders.error.message}</ErrorText></Screen>

  const order = orders.data
  if (!order) {
    return (
      <Screen>
        <Muted>This order is no longer assigned to you.</Muted>
      </Screen>
    )
  }

  const next = nextDriverAction(order.status)

  async function run(action: Parameters<typeof runDriverAction>[1], body: Record<string, string>) {
    setBusy(true)
    setError(null)
    try {
      await runDriverAction(id, action, body)
      setNotes('')
      setFailing(false)
      await queryClient.invalidateQueries({ queryKey: ['driver-orders'] })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed')
      await queryClient.invalidateQueries({ queryKey: ['driver-orders'] })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen refreshing={orders.isRefetching} onRefresh={() => void orders.refetch()}>
      <Card>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
          <Text style={styles.title}>{order.orderNumber}</Text>
          <StatusBadge status={order.status} />
        </View>
        <Row label="Recipient" value={order.recipientName} />
        <Row label="Phone" value={order.recipientPhone} />
        {order.pickupAddress ? <Row label="Pickup" value={order.pickupAddress} /> : null}
        <Row label="Deliver to" value={order.deliveryAddress} />
        <Row label="Collect" value={formatMoney(order.totalAmount)} />
        {order.notes ? <Row label="Notes" value={order.notes} /> : null}
      </Card>

      <Button title={`Call ${order.recipientName}`} variant="secondary" onPress={() => Linking.openURL(`tel:${order.recipientPhone}`)} />

      {next || canReportFailure(order.status) ? (
        <Field label="Note for this step (optional)" value={notes} onChangeText={setNotes} />
      ) : null}

      <ErrorText>{error}</ErrorText>

      {next && (
        <Button
          title={next.label}
          loading={busy}
          onPress={() =>
            run(next.action, next.action === 'deliver' ? { notes, proofOfDelivery: 'Delivered via mobile app' } : notes ? { notes } : {})
          }
        />
      )}

      {canReportFailure(order.status) && !failing && (
        <Button title="Delivery failed" variant="danger" onPress={() => setFailing(true)} disabled={busy} />
      )}
      {failing && (
        <Card>
          <Text style={{ fontWeight: '700' }}>Why did the delivery fail?</Text>
          {FAILURE_REASONS.map(([code, label]) => (
            <Button
              key={code}
              title={label}
              variant="secondary"
              disabled={busy}
              onPress={() => run('fail', { failureReason: code, notes })}
            />
          ))}
          <Button title="Back" variant="secondary" onPress={() => setFailing(false)} />
        </Card>
      )}

      {!next && !canReportFailure(order.status) && <Muted>This delivery is finished.</Muted>}
    </Screen>
  )
}
