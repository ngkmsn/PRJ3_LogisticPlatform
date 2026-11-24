import { useInfiniteQuery } from '@tanstack/react-query'
import { router, useLocalSearchParams } from 'expo-router'
import { View } from 'react-native'
import { listMyOrders } from '../../../api/endpoints'
import type { OrderStatus } from '../../../api/types'
import { FilterChips, type ChipOption } from '../../../components/FilterChips'
import { OrderCard } from '../../../components/OrderCard'
import { nextPageParam, PagedList } from '../../../components/PagedList'
import { STATUS_LABELS } from '../../../components/ui'

const PAGE_SIZE = 10

const FILTERS: ChipOption<OrderStatus>[] = [
  { label: 'All' },
  { value: 'PENDING', label: STATUS_LABELS.PENDING },
  { value: 'CONFIRMED', label: STATUS_LABELS.CONFIRMED },
  { value: 'ASSIGNED', label: STATUS_LABELS.ASSIGNED },
  { value: 'IN_TRANSIT', label: STATUS_LABELS.IN_TRANSIT },
  { value: 'DELIVERED', label: STATUS_LABELS.DELIVERED },
  { value: 'CANCELLED', label: STATUS_LABELS.CANCELLED },
]

export default function CustomerOrderList() {
  // The filter lives in the URL so the home tiles can open this tab pre-filtered.
  const params = useLocalSearchParams<{ status?: string }>()
  const status = FILTERS.find((f) => f.value && f.value === params.status)?.value

  const orders = useInfiniteQuery({
    queryKey: ['my-orders', 'list', status ?? 'ALL'],
    queryFn: ({ pageParam }) => listMyOrders(pageParam, PAGE_SIZE, status),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  })

  return (
    <View style={{ flex: 1 }}>
      <FilterChips options={FILTERS} value={status} onChange={(v) => router.setParams({ status: v ?? '' })} />
      <PagedList
        query={orders}
        keyExtractor={(o) => o.id}
        renderItem={(o) => <OrderCard order={o} />}
        noun="order"
        emptyText={status ? 'No orders with this status.' : 'You have no orders yet.'}
      />
    </View>
  )
}
