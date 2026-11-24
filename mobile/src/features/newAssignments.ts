/**
 * Diffs the driver's assigned orders against the ones already seen.
 * `seen` is null before the first load: everything present then is treated as known, so opening
 * the app does not alert for old orders. Seen ids are never forgotten, so an order that drops out
 * of a page and comes back does not alert twice.
 */
export function detectNewAssignments<T extends { orderId: string }>(
  seen: ReadonlySet<string> | null,
  current: readonly T[],
): { fresh: T[]; seen: Set<string> } {
  const next = new Set(seen ?? [])
  const fresh: T[] = []
  for (const o of current) {
    if (seen && !next.has(o.orderId)) fresh.push(o)
    next.add(o.orderId)
  }
  return { fresh, seen: next }
}
