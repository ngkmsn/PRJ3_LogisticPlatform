import { useQuery } from '@tanstack/react-query'
import { router } from 'expo-router'
import { Text } from 'react-native'
import { getMyDriverProfile } from '../../../api/endpoints'
import { useAuth } from '../../../auth/AuthContext'
import { Button, Card, ErrorText, Row, Screen, styles } from '../../../components/ui'
import { useLocationSharing } from '../../../features/LocationSharing'

export default function DriverAccount() {
  const { user, logout, serverUrl } = useAuth()
  const location = useLocationSharing()
  const profile = useQuery({ queryKey: ['driver-profile'], queryFn: getMyDriverProfile })

  return (
    <Screen>
      <Card>
        <Text style={styles.title}>{profile.data?.fullName ?? user?.username}</Text>
        <Row label="Username" value={user?.username ?? '—'} />
        <Row label="Email" value={user?.email ?? '—'} />
        <Row label="Phone" value={profile.data?.phoneNumber ?? '—'} />
      </Card>

      <Card>
        <Text style={styles.title}>Vehicle</Text>
        <Row label="Type" value={profile.data?.vehicleType ?? '—'} />
        <Row label="Plate" value={profile.data?.vehiclePlate ?? '—'} />
        <Row label="Status" value={profile.data?.status ?? '—'} />
        {profile.isError ? <ErrorText>{profile.error.message}</ErrorText> : null}
      </Card>

      <Card>
        <Text style={styles.title}>App</Text>
        <Row label="Server" value={serverUrl} />
      </Card>

      <Button
        title="Sign out"
        variant="danger"
        onPress={async () => {
          location.stop()
          await logout()
          router.replace('/login')
        }}
      />
    </Screen>
  )
}
