import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native'
import type { Paged } from '../api/types'
import { ErrorText, Loading, Muted, styles } from './ui'

/** Next page index for a Spring-style page, or undefined on the last page. */
export function nextPageParam(last: Paged<unknown>): number | undefined {
  return last.page + 1 < last.totalPages ? last.page + 1 : undefined
}

/** Infinite-scroll list over a paged endpoint: loads the next page near the bottom, pull to refresh. */
export function PagedList<T>({
  query,
  renderItem,
  keyExtractor,
  noun,
  nounPlural = `${noun}s`,
  emptyText,
}: {
  query: UseInfiniteQueryResult<InfiniteData<Paged<T>, unknown>>
  renderItem: (item: T) => ReactElement
  keyExtractor: (item: T) => string
  /** Singular noun for the count line, e.g. "order" → "12 orders". */
  noun: string
  nounPlural?: string
  emptyText: string
}) {
  if (query.isPending) return <Loading />
  if (query.isError) {
    return (
      <View style={{ padding: 16 }}>
        <ErrorText>{query.error.message}</ErrorText>
      </View>
    )
  }

  const items = query.data.pages.flatMap((p) => p.content)
  const total = query.data.pages[0]?.totalElements ?? 0

  return (
    <FlatList
      data={items}
      keyExtractor={keyExtractor}
      renderItem={({ item }) => renderItem(item)}
      contentContainerStyle={local.list}
      ItemSeparatorComponent={Separator}
      ListHeaderComponent={<Muted>{total === 1 ? `1 ${noun}` : `${total} ${nounPlural}`}</Muted>}
      ListEmptyComponent={<Muted>{emptyText}</Muted>}
      ListFooterComponent={
        query.isFetchingNextPage ? (
          <ActivityIndicator style={{ marginVertical: 16 }} />
        ) : items.length > 0 && !query.hasNextPage ? (
          <Text style={[styles.muted, local.end]}>You&apos;ve reached the end</Text>
        ) : undefined
      }
      onEndReachedThreshold={0.4}
      onEndReached={() => {
        if (query.hasNextPage && !query.isFetchingNextPage) void query.fetchNextPage()
      }}
      refreshControl={
        <RefreshControl refreshing={query.isRefetching && !query.isFetchingNextPage} onRefresh={() => void query.refetch()} />
      }
    />
  )
}

function Separator() {
  return <View style={{ height: 12 }} />
}

const local = StyleSheet.create({
  list: { padding: 16, paddingTop: 4 },
  end: { textAlign: 'center', marginVertical: 16 },
})
