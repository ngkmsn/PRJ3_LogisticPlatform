import { useQueries, useQuery } from '@tanstack/react-query'
import { router } from 'expo-router'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import { listMyOrders } from '../../../api/endpoints'
import type { OrderStatus } from '../../../api/types'
import { useAuth } from '../../../auth/AuthContext'
import { OrderCard } from '../../../components/OrderCard'
import { Button, colors, ErrorText, Loading, Muted, Screen, STATUS_LABELS, styles } from '../../../components/ui'

/** Statuses summarised on the home screen; each tile opens the Orders tab filtered to it. */
const SUMMARY: { status?: OrderStatus; label: string; color: string }[] = [
  { status: 'PENDING', label: STATUS_LABELS.PENDING, color: colors.warning },
  { status: 'ASSIGNED', label: STATUS_LABELS.ASSIGNED, color: colors.primary },
  { status: 'IN_TRANSIT', label: STATUS_LABELS.IN_TRANSIT, color: '#7456d8' },
  { status: 'DELIVERED', label: STATUS_LABELS.DELIVERED, color: colors.success },
]

const RECENT = 3

export default function CustomerHome() {
  const { user } = useAuth()
  const recent = useQuery({
    queryKey: ['my-orders', 'recent'],
    queryFn: () => listMyOrders(0, RECENT),
    refetchInterval: 15_000,
  })
  // size=1 is enough: only totalElements is used.
  const counts = useQueries({
    queries: SUMMARY.map((s) => ({
      queryKey: ['my-orders', 'count', s.status],
      queryFn: () => listMyOrders(0, 1, s.status),
      refetchInterval: 15_000,
    })),
  })

  const refreshing = recent.isRefetching || counts.some((c) => c.isRefetching)
  const refresh = () => {
    void recent.refetch()
    counts.forEach((c) => void c.refetch())
  }

  return (
    <Screen refreshing={refreshing} onRefresh={refresh}>
      <View style={{ gap: 2 }}>
        <Text style={styles.subtitle}>Welcome back</Text>
        <Text style={local.hello}>{user?.username}</Text>
      </View>

      <Button title="+ New delivery" onPress={() => router.push('/customer/new')} />

      <View style={local.grid}>
        {SUMMARY.map((s, i) => (
          <Pressable
            key={s.label}
            accessibilityRole="button"
            accessibilityLabel={`${s.label} orders`}
            onPress={() => router.navigate({ pathname: '/customer/orders', params: { status: s.status } })}
            style={({ pressed }) => [local.tile, pressed && { opacity: 0.7 }]}
          >
            <Text style={[local.tileValue, { color: s.color }]}>{counts[i].data?.totalElements ?? '–'}</Text>
            <Text style={local.tileLabel} numberOfLines={1}>
              {s.label}
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={local.sectionHeader}>
        <Text style={styles.title}>Recent orders</Text>
        {recent.data && recent.data.totalElements > RECENT ? (
          <Pressable accessibilityRole="button" hitSlop={10} onPress={() => router.navigate('/customer/orders')}>
            <Text style={{ color: colors.primary, fontSize: 15 }}>See all ({recent.data.totalElements})</Text>
          </Pressable>
        ) : null}
      </View>

      {recent.isPending ? (
        <Loading label="Loading your orders…" />
      ) : recent.isError ? (
        <ErrorText>{recent.error.message}</ErrorText>
      ) : recent.data.content.length === 0 ? (
        <Muted>You have no orders yet. Create your first delivery above.</Muted>
      ) : (
        recent.data.content.map((o) => <OrderCard key={o.id} order={o} />)
      )}
    </Screen>
  )
}

const local = StyleSheet.create({
  hello: { fontSize: 24, fontWeight: '700', color: colors.text },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  tile: {
    flexGrow: 1,
    flexBasis: '45%',
    backgroundColor: colors.card,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    gap: 4,
  },
  tileValue: { fontSize: 26, fontWeight: '700' },
  tileLabel: { color: colors.muted, fontSize: 13 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 },
})
