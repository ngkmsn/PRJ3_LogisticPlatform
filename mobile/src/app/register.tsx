import { router } from 'expo-router'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorText, Field, Muted, Screen } from '../components/ui'

export default function RegisterScreen() {
  const { serverUrl, register } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [server, setServer] = useState(serverUrl)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit() {
    setError(null)
    if (password.length < 6) {
      setError('Password must be at least 6 characters')
      return
    }
    setSubmitting(true)
    try {
      await register(username.trim(), email.trim(), password, server)
      router.replace('/customer')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Registration failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Screen>
      <Muted>Customer accounts can create and track deliveries. Drivers get their account from the operations team.</Muted>
      <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} />
      <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
      <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry />
      <Field label="Server" value={server} onChangeText={setServer} autoCapitalize="none" autoCorrect={false} keyboardType="url" />
      <ErrorText>{error}</ErrorText>
      <Button title="Create account" onPress={onSubmit} loading={submitting} disabled={!username || !email || !password} />
    </Screen>
  )
}
