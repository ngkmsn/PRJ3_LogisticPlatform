import type { ReactNode } from 'react'
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
  type ViewStyle,
} from 'react-native'

export const colors = {
  bg: '#f4f6f8',
  card: '#ffffff',
  text: '#1b2430',
  muted: '#6b7685',
  border: '#dfe3e8',
  primary: '#2b6ff2',
  danger: '#d7464f',
  success: '#25a56a',
  warning: '#e6a23c',
  navy: '#111d2c',
}

export function Screen({
  children,
  refreshing,
  onRefresh,
}: {
  children: ReactNode
  refreshing?: boolean
  onRefresh?: () => void
}) {
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.screenContent}
      keyboardShouldPersistTaps="handled"
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} /> : undefined}
    >
      {children}
    </ScrollView>
  )
}

export function Card({ children, style, onPress }: { children: ReactNode; style?: ViewStyle; onPress?: () => void }) {
  if (onPress) {
    return (
      <Pressable onPress={onPress} style={({ pressed }) => [styles.card, style, pressed && { opacity: 0.7 }]}>
        {children}
      </Pressable>
    )
  }
  return <View style={[styles.card, style]}>{children}</View>
}

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled,
  loading,
}: {
  title: string
  onPress: () => void
  variant?: 'primary' | 'secondary' | 'danger'
  disabled?: boolean
  loading?: boolean
}) {
  const bg = variant === 'primary' ? colors.primary : variant === 'danger' ? colors.danger : colors.card
  const fg = variant === 'secondary' ? colors.text : '#fff'
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, borderColor: variant === 'secondary' ? colors.border : bg },
        (disabled || loading) && { opacity: 0.5 },
        pressed && { opacity: 0.8 },
      ]}
    >
      {loading ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>}
    </Pressable>
  )
}

export function Field({ label, ...input }: { label: string } & TextInputProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput accessibilityLabel={label} placeholderTextColor="#9aa4b1" style={styles.input} {...input} />
    </View>
  )
}

export function ErrorText({ children }: { children: ReactNode }) {
  if (!children) return null
  return (
    <Text accessibilityRole="alert" style={styles.error}>
      {children}
    </Text>
  )
}

export function Muted({ children }: { children: ReactNode }) {
  return <Text style={styles.muted}>{children}</Text>
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <View style={styles.center}>
      <ActivityIndicator />
      <Text style={styles.muted}>{label}</Text>
    </View>
  )
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: colors.warning,
  CONFIRMED: colors.warning,
  ASSIGNED: colors.primary,
  PICKED_UP: '#7456d8',
  IN_TRANSIT: '#7456d8',
  DELIVERED: colors.success,
  FAILED: colors.danger,
  CANCELLED: colors.muted,
  AVAILABLE: colors.success,
  UNAVAILABLE: colors.muted,
}

/** Customer-friendly wording for order statuses. */
export const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Finding a driver',
  CONFIRMED: 'Re-assigning driver',
  ASSIGNED: 'Driver assigned',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'On the way',
  DELIVERED: 'Delivered',
  FAILED: 'Delivery failed',
  CANCELLED: 'Cancelled',
}

export function StatusBadge({ status }: { status: string }) {
  const color = STATUS_COLORS[status] ?? colors.muted
  return (
    <View style={[styles.badge, { backgroundColor: `${color}22` }]}>
      <Text style={[styles.badgeText, { color }]}>{STATUS_LABELS[status] ?? status}</Text>
    </View>
  )
}

export function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  )
}

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${Math.round(value).toLocaleString('en-US')} ₫`
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return `${d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })} ${d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`
}

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  screenContent: { padding: 16, gap: 12, paddingBottom: 40 },
  card: { backgroundColor: colors.card, borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.border, gap: 6 },
  button: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center', borderWidth: 1, paddingHorizontal: 16 },
  buttonText: { fontSize: 16, fontWeight: '600' },
  field: { gap: 6 },
  label: { fontSize: 13, fontWeight: '600', color: '#344050' },
  input: { height: 46, borderWidth: 1, borderColor: colors.border, borderRadius: 10, paddingHorizontal: 12, backgroundColor: '#fff', fontSize: 16, color: colors.text },
  error: { color: colors.danger, backgroundColor: '#fdecec', padding: 10, borderRadius: 8, overflow: 'hidden' },
  muted: { color: colors.muted, fontSize: 14 },
  center: { padding: 32, alignItems: 'center', gap: 8 },
  badge: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  badgeText: { fontSize: 12, fontWeight: '700' },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: 12, paddingVertical: 4 },
  rowLabel: { color: colors.muted, fontSize: 14 },
  rowValue: { color: colors.text, fontSize: 14, fontWeight: '500', flexShrink: 1, textAlign: 'right' },
  title: { fontSize: 18, fontWeight: '700', color: colors.text },
  subtitle: { fontSize: 14, color: colors.muted },
})
