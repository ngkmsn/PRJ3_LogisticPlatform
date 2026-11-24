import * as Location from 'expo-location'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { sendLocation } from '../api/endpoints'

/** Minimum time between two GPS uploads, so a moving phone does not flood the gateway. */
const SEND_EVERY_MS = 10_000

interface LocationSharingState {
  sharing: boolean
  lastSentAt: Date | null
  lastPosition: { latitude: number; longitude: number } | null
  error: string | null
  start(): Promise<void>
  stop(): void
  /** Order the driver is currently delivering; attached to each GPS upload. */
  setActiveOrderId(orderId: string | undefined): void
}

const LocationSharingContext = createContext<LocationSharingState | null>(null)

/** Foreground GPS sharing for the signed-in driver (only while the app is open). */
export function LocationSharingProvider({ children }: { children: ReactNode }) {
  const [sharing, setSharing] = useState(false)
  const [lastSentAt, setLastSentAt] = useState<Date | null>(null)
  const [lastPosition, setLastPosition] = useState<{ latitude: number; longitude: number } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const subscription = useRef<Location.LocationSubscription | null>(null)
  const lastSendTime = useRef(0)
  const activeOrderId = useRef<string | undefined>(undefined)
  const starting = useRef(false)
  const wanted = useRef(false)

  const upload = useCallback(async (coords: Location.LocationObjectCoords, force = false) => {
    const now = Date.now()
    if (!force && now - lastSendTime.current < SEND_EVERY_MS) return
    lastSendTime.current = now
    setLastPosition({ latitude: coords.latitude, longitude: coords.longitude })
    try {
      await sendLocation(coords.latitude, coords.longitude, {
        orderId: activeOrderId.current,
        accuracy: coords.accuracy ?? undefined,
        speed: coords.speed !== null && coords.speed >= 0 ? coords.speed : undefined,
        bearing: coords.heading !== null && coords.heading >= 0 ? coords.heading : undefined,
      })
      setLastSentAt(new Date())
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not send location')
    }
  }, [])

  const stop = useCallback(() => {
    wanted.current = false
    subscription.current?.remove()
    subscription.current = null
    setSharing(false)
  }, [])

  const start = useCallback(async () => {
    // Callers run this from effects, so it can be called again while a start is still awaiting.
    if (subscription.current || starting.current) return
    starting.current = true
    wanted.current = true
    try {
      const permission = await Location.requestForegroundPermissionsAsync()
      if (permission.status !== 'granted') {
        setError('Location permission is needed to share your position with customers.')
        return
      }
      setError(null)
      const watch = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.Balanced, timeInterval: SEND_EVERY_MS, distanceInterval: 15 },
        (position) => void upload(position.coords),
      )
      if (!wanted.current) {
        watch.remove() // stop() was called while we were starting
        return
      }
      subscription.current = watch
      setSharing(true)
      // Send a first position right away; without a GPS fix the watch above sends it later.
      try {
        const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
        await upload(current.coords, true)
      } catch {
        setError('Waiting for a GPS signal…')
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not start location sharing')
    } finally {
      starting.current = false
    }
  }, [upload])

  const setActiveOrderId = useCallback((orderId: string | undefined) => {
    activeOrderId.current = orderId
  }, [])

  useEffect(() => stop, [stop])

  const value = useMemo(
    () => ({ sharing, lastSentAt, lastPosition, error, start, stop, setActiveOrderId }),
    [sharing, lastSentAt, lastPosition, error, start, stop, setActiveOrderId],
  )
  return <LocationSharingContext.Provider value={value}>{children}</LocationSharingContext.Provider>
}

export function useLocationSharing(): LocationSharingState {
  const ctx = useContext(LocationSharingContext)
  if (!ctx) throw new Error('useLocationSharing must be used inside LocationSharingProvider')
  return ctx
}
