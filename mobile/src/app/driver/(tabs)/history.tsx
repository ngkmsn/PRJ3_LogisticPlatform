import { useInfiniteQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { View } from 'react-native'
import { listDriverOrders } from '../../../api/endpoints'
import type { AssignmentStatus } from '../../../api/types'
import { DriverOrderCard } from '../../../components/DriverOrderCard'
import { FilterChips, type ChipOption } from '../../../components/FilterChips'
import { nextPageParam, PagedList } from '../../../components/PagedList'
import { STATUS_LABELS } from '../../../components/ui'

const PAGE_SIZE = 10

const FILTERS: ChipOption<AssignmentStatus>[] = [
  { label: 'All' },
  { value: 'DELIVERED', label: STATUS_LABELS.DELIVERED },
  { value: 'FAILED', label: STATUS_LABELS.FAILED },
  { value: 'CANCELLED', label: STATUS_LABELS.CANCELLED },
  { value: 'ASSIGNED', label: STATUS_LABELS.ASSIGNED },
  { value: 'IN_TRANSIT', label: STATUS_LABELS.IN_TRANSIT },
]

export default function DriverHistory() {
  const [status, setStatus] = useState<AssignmentStatus | undefined>(undefined)

  const orders = useInfiniteQuery({
    queryKey: ['driver-orders', 'list', status ?? 'ALL'],
    queryFn: ({ pageParam }) => listDriverOrders(pageParam, PAGE_SIZE, status),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  })

  return (
    <View style={{ flex: 1 }}>
      <FilterChips options={FILTERS} value={status} onChange={setStatus} />
      <PagedList
        query={orders}
        keyExtractor={(o) => o.assignmentId}
        renderItem={(o) => <DriverOrderCard order={o} />}
        noun="delivery"
        nounPlural="deliveries"
        emptyText={status ? 'No deliveries with this status.' : 'You have no deliveries yet.'}
      />
    </View>
  )
}
