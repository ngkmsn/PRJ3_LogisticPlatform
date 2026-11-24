import Ionicons from '@expo/vector-icons/Ionicons'
import type { ComponentProps } from 'react'
import type { ColorValue } from 'react-native'

type IconName = ComponentProps<typeof Ionicons>['name']

/** Tab bar icon that switches to the filled glyph when the tab is selected. */
export function tabIcon(name: IconName, focusedName: IconName) {
  return function TabIcon({ color, size, focused }: { color: ColorValue; size: number; focused: boolean }) {
    return <Ionicons name={focused ? focusedName : name} size={size} color={color as string} />
  }
}
