import { router } from 'expo-router'
import { Text, View } from 'react-native'
import type { OrderDto } from '../api/types'
import { Card, formatDateTime, formatMoney, Muted, StatusBadge } from './ui'

export function OrderCard({ order }: { order: OrderDto }) {
  return (
    <Card onPress={() => router.push(`/customer/order/${order.id}`)}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
        <Text style={{ fontWeight: '700', fontSize: 15 }}>{order.orderNumber}</Text>
        <StatusBadge status={order.status} />
      </View>
      <Text numberOfLines={2}>
        To {order.recipientName} · {order.deliveryAddress}
      </Text>
      <Muted>
        {formatMoney(order.totalAmount)} · {formatDateTime(order.createdAt)}
      </Muted>
    </Card>
  )
}
