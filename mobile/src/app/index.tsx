import { Redirect } from 'expo-router'
import { useAuth } from '../auth/AuthContext'
import { homeRouteFor } from '../auth/roles'
import { Loading } from '../components/ui'

export default function Index() {
  const { ready, user } = useAuth()
  if (!ready) return <Loading />
  if (!user) return <Redirect href="/login" />
  return <Redirect href={homeRouteFor(user.role)} />
}
