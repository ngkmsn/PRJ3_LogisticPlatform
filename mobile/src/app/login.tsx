import { Link, Redirect, router } from 'expo-router'
import { useEffect, useState } from 'react'
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useAuth } from '../auth/AuthContext'
import { homeRouteFor } from '../auth/roles'
import { Button, colors, ErrorText, Field, Screen } from '../components/ui'

export default function LoginScreen() {
  const { ready, user, serverUrl, login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [server, setServer] = useState(serverUrl)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => setServer(serverUrl), [serverUrl])

  if (ready && user) return <Redirect href={homeRouteFor(user.role)} />

  async function onSubmit() {
    setError(null)
    setSubmitting(true)
    try {
      const signedIn = await login(username.trim(), password, server)
      router.replace(homeRouteFor(signedIn.role))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign in failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.navy }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={local.hero}>
          <Text style={local.brand}>RelayOps</Text>
          <Text style={local.tagline}>Send parcels · Deliver orders</Text>
        </View>
        <Screen>
          <Text style={local.heading}>Sign in</Text>
          <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} />
          <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry />
          <Field
            label="Server"
            value={server}
            onChangeText={setServer}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            placeholder="http://localhost:8080"
          />
          <ErrorText>{error}</ErrorText>
          <Button title="Sign in" onPress={onSubmit} loading={submitting} disabled={!username || !password} />
          <Link href="/register" style={local.link}>
            New customer? Create an account
          </Link>
        </Screen>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

const local = StyleSheet.create({
  hero: { paddingHorizontal: 20, paddingVertical: 28, gap: 6 },
  brand: { color: '#fff', fontSize: 28, fontWeight: '800' },
  tagline: { color: '#a9b5c5', fontSize: 15 },
  heading: { fontSize: 22, fontWeight: '700', color: colors.text },
  link: { color: colors.primary, textAlign: 'center', fontSize: 15, paddingVertical: 8 },
})
