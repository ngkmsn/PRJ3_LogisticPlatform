import { Tabs } from 'expo-router'
import { tabIcon } from '../../../components/tabIcon'
import { colors } from '../../../components/ui'

export default function DriverTabs() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
        headerTintColor: colors.primary,
        headerTitleStyle: { color: colors.text },
        sceneStyle: { backgroundColor: colors.bg },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Deliveries', tabBarIcon: tabIcon('bicycle-outline', 'bicycle') }} />
      <Tabs.Screen name="history" options={{ title: 'History', tabBarIcon: tabIcon('time-outline', 'time') }} />
      <Tabs.Screen name="account" options={{ title: 'Account', tabBarIcon: tabIcon('person-outline', 'person') }} />
    </Tabs>
  )
}
