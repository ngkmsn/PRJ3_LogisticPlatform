export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${Math.round(value).toLocaleString('en-US')} ₫`
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export function formatAge(iso: string | null | undefined): string {
  if (!iso) return '—'
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (Number.isNaN(minutes)) return '—'
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/** "UNASSIGNED_ORDER" -> "Unassigned order" */
export function humanize(code: string | null | undefined): string {
  if (!code) return '—'
  const text = code.replaceAll('_', ' ').toLowerCase()
  return text.charAt(0).toUpperCase() + text.slice(1)
}
