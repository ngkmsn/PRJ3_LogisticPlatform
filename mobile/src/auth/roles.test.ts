import { homeRouteFor, isMobileRole } from './roles'

describe('mobile roles', () => {
  it('accepts customers and drivers only', () => {
    expect(isMobileRole('CUSTOMER')).toBe(true)
    expect(isMobileRole('DRIVER')).toBe(true)
    expect(isMobileRole('ADMIN')).toBe(false)
    expect(isMobileRole('DISPATCHER')).toBe(false)
  })

  it('sends each role to its own home', () => {
    expect(homeRouteFor('CUSTOMER')).toBe('/customer')
    expect(homeRouteFor('DRIVER')).toBe('/driver')
  })
})
