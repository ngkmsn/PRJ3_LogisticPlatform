import { describe, expect, it } from 'vitest'
import { isInAppNavigation } from './navigation'

describe('isInAppNavigation', () => {
  it('allows navigation inside the packaged app', () => {
    expect(isInAppNavigation('app://dispatcher/index.html#/drivers')).toBe(true)
  })

  it('allows the dev server when running electron:dev', () => {
    expect(isInAppNavigation('http://localhost:5173/#/orders', 'http://localhost:5173')).toBe(true)
  })

  it('blocks external sites such as the OpenStreetMap attribution link', () => {
    expect(isInAppNavigation('https://www.openstreetmap.org/copyright')).toBe(false)
    expect(isInAppNavigation('https://leafletjs.com')).toBe(false)
  })

  it('does not treat a lookalike host as the dev server', () => {
    expect(isInAppNavigation('http://localhost:51730/', 'http://localhost:5173')).toBe(false)
  })
})
