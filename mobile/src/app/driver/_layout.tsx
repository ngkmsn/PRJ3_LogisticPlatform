import { Stack } from 'expo-router'
import { View } from 'react-native'
import { RoleGate } from '../../components/RoleGate'
import { colors } from '../../components/ui'
import { LocationSharingProvider } from '../../features/LocationSharing'
import { NewOrderAlert } from '../../features/NewOrderAlert'

export default function DriverLayout() {
  return (
    <RoleGate role="DRIVER">
      <LocationSharingProvider>
        <View style={{ flex: 1 }}>
          <Stack screenOptions={{ headerTintColor: colors.primary, contentStyle: { backgroundColor: colors.bg } }}>
            <Stack.Screen name="(tabs)" options={{ headerShown: false, title: 'Back' }} />
            <Stack.Screen name="order/[id]" options={{ title: 'Delivery' }} />
          </Stack>
          {/* Above every driver screen, so a new order is announced wherever the driver is. */}
          <NewOrderAlert />
        </View>
      </LocationSharingProvider>
    </RoleGate>
  )
}
