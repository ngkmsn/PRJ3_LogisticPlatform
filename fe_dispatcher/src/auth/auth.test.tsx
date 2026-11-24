import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { apiFetch } from '../api/client'
import { isDesktopRole } from './AuthContext'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function loginResponse(role: string) {
  return jsonResponse(200, {
    success: true,
    message: 'Login successful',
    data: {
      accessToken: `token-${role}`,
      tokenType: 'Bearer',
      expiresIn: 86400,
      user: { id: 'u1', username: role.toLowerCase(), email: 'x@y.z', role, status: 'ACTIVE' },
    },
  })
}

async function submitLogin(username = 'someone', password = 'secret') {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: /sign in/i }))
}

describe('isDesktopRole', () => {
  it('allows only operations roles', () => {
    expect(isDesktopRole('ADMIN')).toBe(true)
    expect(isDesktopRole('DISPATCHER')).toBe(true)
    expect(isDesktopRole('CUSTOMER')).toBe(false)
    expect(isDesktopRole('DRIVER')).toBe(false)
  })
})

describe('login flow', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    localStorage.clear()
    window.location.hash = ''
    vi.stubGlobal('fetch', fetchMock)
    // Any data request made by pages after login gets an empty successful answer.
    fetchMock.mockImplementation(async () => jsonResponse(200, { success: true, data: null }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
  })

  it('shows the login page when signed out', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('refuses customer accounts without storing a token', async () => {
    fetchMock.mockImplementationOnce(async () => loginResponse('CUSTOMER'))
    render(<App />)

    await submitLogin()

    expect(await screen.findByText('This account cannot use the desktop client')).toBeInTheDocument()
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('explains when the server cannot be reached', async () => {
    fetchMock.mockImplementationOnce(async () => {
      throw new TypeError('Failed to fetch')
    })
    render(<App />)

    await submitLogin()

    expect(await screen.findByText(/^Cannot reach server at/)).toBeInTheDocument()
  })

  it('signs a dispatcher in, stores the token and opens the app shell', async () => {
    fetchMock.mockImplementationOnce(async () => loginResponse('DISPATCHER'))
    render(<App />)

    await submitLogin('dispatcher', 'Dispatcher@123')

    expect(await screen.findByRole('button', { name: /sign out/i })).toBeInTheDocument()
    expect(localStorage.getItem('token')).toBe('token-DISPATCHER')
    expect(screen.queryByRole('link', { name: /users/i })).not.toBeInTheDocument()
  })

  it('shows the Users entry only to admins', async () => {
    fetchMock.mockImplementationOnce(async () => loginResponse('ADMIN'))
    render(<App />)

    await submitLogin('admin', 'Admin@123')

    expect(await screen.findByRole('link', { name: /users/i })).toBeInTheDocument()
  })

  it('returns to the login page when any request answers 401', async () => {
    localStorage.setItem('token', 'expired')
    localStorage.setItem('user', JSON.stringify({ id: 'u1', username: 'dispatcher', email: 'x', role: 'DISPATCHER', status: 'ACTIVE' }))
    render(<App />)
    expect(await screen.findByRole('button', { name: /sign out/i })).toBeInTheDocument()

    fetchMock.mockImplementationOnce(async () => jsonResponse(401, { success: false, message: 'Token is invalid or expired' }))
    await act(async () => {
      await apiFetch('/api/anything').catch(() => undefined)
    })

    expect(await screen.findByRole('button', { name: /sign in/i })).toBeInTheDocument()
    expect(localStorage.getItem('token')).toBeNull()
  })
})
