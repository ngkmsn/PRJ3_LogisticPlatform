import { Pressable, ScrollView, StyleSheet, Text } from 'react-native'
import { colors } from './ui'

export interface ChipOption<T extends string> {
  value?: T
  label: string
}

/** Horizontal, scrollable single-select filter. `value` undefined means "All". */
export function FilterChips<T extends string>({
  options,
  value,
  onChange,
}: {
  options: ChipOption<T>[]
  value?: T
  onChange: (value: T | undefined) => void
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={local.bar} contentContainerStyle={local.content}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <Pressable
            key={o.label}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            onPress={() => onChange(o.value)}
            style={[local.chip, active && local.chipActive]}
          >
            <Text style={[local.text, active && local.textActive]}>{o.label}</Text>
          </Pressable>
        )
      })}
    </ScrollView>
  )
}

const local = StyleSheet.create({
  bar: { flexGrow: 0, flexShrink: 0, height: 58, backgroundColor: colors.bg },
  content: { paddingHorizontal: 16, paddingVertical: 10, gap: 8, alignItems: 'center' },
  chip: {
    height: 38,
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  text: { color: colors.text, fontSize: 14, lineHeight: 20, fontWeight: '500' },
  textActive: { color: '#fff' },
})
