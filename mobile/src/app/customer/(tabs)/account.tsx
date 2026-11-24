import { router } from 'expo-router'
import { Text } from 'react-native'
import { useAuth } from '../../../auth/AuthContext'
import { Button, Card, Row, Screen, styles } from '../../../components/ui'

export default function CustomerAccount() {
  const { user, logout, serverUrl } = useAuth()

  return (
    <Screen>
      <Card>
        <Text style={styles.title}>Profile</Text>
        <Row label="Username" value={user?.username ?? '—'} />
        <Row label="Email" value={user?.email ?? '—'} />
        <Row label="Account type" value="Customer" />
      </Card>

      <Card>
        <Text style={styles.title}>App</Text>
        <Row label="Server" value={serverUrl} />
      </Card>

      <Button
        title="Sign out"
        variant="danger"
        onPress={async () => {
          await logout()
          router.replace('/login')
        }}
      />
    </Screen>
  )
}
