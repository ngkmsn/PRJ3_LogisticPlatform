import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { router } from 'expo-router'
import { useEffect, useMemo, useState } from 'react'
import { Pressable, Switch, Text, View } from 'react-native'
import { getMyDriverProfile, listDriverOrders, setMyAvailability } from '../../../api/endpoints'
import type { AssignmentStatus } from '../../../api/types'
import { DriverOrderCard } from '../../../components/DriverOrderCard'
import { Card, colors, ErrorText, Loading, Muted, Screen, styles } from '../../../components/ui'
import { useLocationSharing } from '../../../features/LocationSharing'

/** Statuses the driver still has to act on. Fetched per status so they never fall off a page. */
const ACTIVE: AssignmentStatus[] = ['IN_TRANSIT', 'PICKED_UP', 'ASSIGNED']

export default function DriverDeliveries() {
  const queryClient = useQueryClient()
  const location = useLocationSharing()
  const [availabilityError, setAvailabilityError] = useState<string | null>(null)
  const [updating, setUpdating] = useState(false)

  const profile = useQuery({ queryKey: ['driver-profile'], queryFn: getMyDriverProfile })
  const current = useQueries({
    queries: ACTIVE.map((status) => ({
      queryKey: ['driver-orders', 'active', status],
      queryFn: () => listDriverOrders(0, 50, status),
      refetchInterval: 10_000,
    })),
  })

  const onDuty = profile.data?.availability === 'AVAILABLE'
  const loading = current.some((q) => q.isPending)
  const failed = current.find((q) => q.isError)?.error
  const active = useMemo(() => current.flatMap((q) => q.data?.content ?? []), [current])
  const firstActiveId = active[0]?.orderId

  // Share GPS while on duty; attach the order being delivered so tracking follows it.
  const { sharing, start, stop, setActiveOrderId } = location
  useEffect(() => {
    if (onDuty && !sharing) void start()
    if (!onDuty && sharing) stop()
  }, [onDuty, sharing, start, stop])
  useEffect(() => {
    setActiveOrderId(firstActiveId)
  }, [firstActiveId, setActiveOrderId])

  async function toggleDuty(value: boolean) {
    setUpdating(true)
    setAvailabilityError(null)
    try {
      await setMyAvailability(value ? 'AVAILABLE' : 'UNAVAILABLE')
      await queryClient.invalidateQueries({ queryKey: ['driver-profile'] })
    } catch (e) {
      setAvailabilityError(e instanceof Error ? e.message : 'Could not change availability')
    } finally {
      setUpdating(false)
    }
  }

  if (profile.isPending) return <Loading />
  if (profile.isError) {
    return (
      <Screen>
        <ErrorText>{profile.error.message}</ErrorText>
        <Muted>Ask the operations team to create your driver profile.</Muted>
      </Screen>
    )
  }

  const refreshing = current.some((q) => q.isRefetching)

  return (
    <Screen refreshing={refreshing} onRefresh={() => current.forEach((q) => void q.refetch())}>
      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <View style={{ flexShrink: 1 }}>
            <Text style={{ fontWeight: '700', fontSize: 17 }}>{onDuty ? 'On duty' : 'Off duty'}</Text>
            <Muted>{onDuty ? 'You can receive new orders' : 'You will not receive new orders'}</Muted>
          </View>
          <Switch
            accessibilityLabel="On duty"
            value={onDuty}
            disabled={updating}
            onValueChange={toggleDuty}
            trackColor={{ true: colors.success }}
          />
        </View>
        <Muted>
          {location.sharing
            ? `Sharing location${location.lastSentAt ? ` · last sent ${location.lastSentAt.toLocaleTimeString('en-GB')}` : ''}`
            : 'Location sharing is off'}
        </Muted>
        <ErrorText>{availabilityError ?? location.error}</ErrorText>
      </Card>

      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
        <Text style={styles.title}>Current deliveries{active.length ? ` (${active.length})` : ''}</Text>
        <Pressable accessibilityRole="button" hitSlop={10} onPress={() => router.navigate('/driver/history')}>
          <Text style={{ color: colors.primary, fontSize: 15 }}>History</Text>
        </Pressable>
      </View>

      {loading ? (
        <Loading />
      ) : failed ? (
        <ErrorText>{failed.message}</ErrorText>
      ) : active.length === 0 ? (
        <Muted>{onDuty ? 'Waiting for a new order…' : 'Go on duty to receive orders.'}</Muted>
      ) : (
        active.map((o) => <DriverOrderCard key={o.assignmentId} order={o} />)
      )}
    </Screen>
  )
}
