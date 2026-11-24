import { Stack } from 'expo-router'
import { RoleGate } from '../../components/RoleGate'
import { colors } from '../../components/ui'

export default function CustomerLayout() {
  return (
    <RoleGate role="CUSTOMER">
      <Stack screenOptions={{ headerTintColor: colors.primary, contentStyle: { backgroundColor: colors.bg } }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false, title: 'Back' }} />
        <Stack.Screen name="new" options={{ title: 'New delivery', presentation: 'modal' }} />
        <Stack.Screen name="order/[id]" options={{ title: 'Order' }} />
        <Stack.Screen name="track/[id]" options={{ title: 'Track driver' }} />
      </Stack>
    </RoleGate>
  )
}
