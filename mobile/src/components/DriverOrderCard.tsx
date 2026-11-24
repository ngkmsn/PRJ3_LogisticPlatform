import { router } from 'expo-router'
import { Text, View } from 'react-native'
import type { DriverOrderDto } from '../api/types'
import { Card, formatDateTime, formatMoney, Muted, StatusBadge } from './ui'

export function DriverOrderCard({ order }: { order: DriverOrderDto }) {
  return (
    <Card onPress={() => router.push(`/driver/order/${order.orderId}`)}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
        <Text style={{ fontWeight: '700', fontSize: 15 }}>{order.orderNumber}</Text>
        <StatusBadge status={order.status} />
      </View>
      <Text>
        {order.recipientName} · {order.recipientPhone}
      </Text>
      <Text numberOfLines={2}>{order.deliveryAddress}</Text>
      <Muted>
        {formatMoney(order.totalAmount)} · assigned {formatDateTime(order.assignedAt)}
      </Muted>
    </Card>
  )
}
