import Ionicons from '@expo/vector-icons/Ionicons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import * as Haptics from 'expo-haptics'
import { router } from 'expo-router'
import { useEffect, useRef, useState } from 'react'
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { listDriverOrders } from '../api/endpoints'
import type { DriverOrderDto } from '../api/types'
import { colors, formatMoney } from '../components/ui'
import { detectNewAssignments } from './newAssignments'

const POLL_MS = 10_000
const VISIBLE_MS = 10_000

/**
 * Watches the driver's newly assigned orders on every driver screen and slides in a banner
 * (with a haptic buzz) when one appears. Shares its query with the Deliveries tab, so it adds
 * no extra requests while that tab is open.
 */
export function NewOrderAlert() {
  const insets = useSafeAreaInsets()
  const queryClient = useQueryClient()
  const assigned = useQuery({
    queryKey: ['driver-orders', 'active', 'ASSIGNED'],
    queryFn: () => listDriverOrders(0, 50, 'ASSIGNED'),
    refetchInterval: POLL_MS,
  })

  const seen = useRef<Set<string> | null>(null)
  const [alert, setAlert] = useState<{ order: DriverOrderDto; count: number } | null>(null)
  const slide = useRef(new Animated.Value(-200)).current

  useEffect(() => {
    if (!assigned.data) return
    const { fresh, seen: next } = detectNewAssignments(seen.current, assigned.data.content)
    seen.current = next
    if (fresh.length === 0) return
    setAlert({ order: fresh[0], count: fresh.length })
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
    // Other driver lists (history, other statuses) should pick the new order up too.
    void queryClient.invalidateQueries({ queryKey: ['driver-orders', 'list'] })
  }, [assigned.data, queryClient])

  useEffect(() => {
    if (!alert) return
    Animated.spring(slide, { toValue: 0, useNativeDriver: true, bounciness: 6 }).start()
    const timer = setTimeout(() => dismiss(), VISIBLE_MS)
    return () => clearTimeout(timer)
    // dismiss only reads refs and setters
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alert])

  function dismiss() {
    Animated.timing(slide, { toValue: -200, duration: 200, useNativeDriver: true }).start(() => setAlert(null))
  }

  if (!alert) return null
  const { order, count } = alert

  return (
    <Animated.View style={[local.wrap, { top: insets.top + 6, transform: [{ translateY: slide }] }]} pointerEvents="box-none">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`New delivery ${order.orderNumber}. Open it.`}
        onPress={() => {
          dismiss()
          router.push(`/driver/order/${order.orderId}`)
        }}
        style={({ pressed }) => [local.card, pressed && { opacity: 0.9 }]}
      >
        <View style={local.icon}>
          <Ionicons name="cube" size={22} color="#fff" />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={local.title}>{count > 1 ? `${count} new deliveries assigned` : 'New delivery assigned'}</Text>
          <Text style={local.body} numberOfLines={1}>
            {order.recipientName} · {order.deliveryAddress}
          </Text>
          <Text style={local.meta} numberOfLines={1}>
            {order.orderNumber} · collect {formatMoney(order.totalAmount)}
          </Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Dismiss" hitSlop={12} onPress={dismiss}>
          <Ionicons name="close" size={20} color="#c7d0dc" />
        </Pressable>
      </Pressable>
    </Animated.View>
  )
}

const local = StyleSheet.create({
  wrap: { position: 'absolute', left: 12, right: 12, zIndex: 1000 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.navy,
    borderRadius: 16,
    padding: 14,
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  icon: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  title: { color: '#fff', fontSize: 15, fontWeight: '700' },
  body: { color: '#e3e8ef', fontSize: 14 },
  meta: { color: '#9aa7b8', fontSize: 12 },
})
