import type { ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Icon, type IconName } from './ui'

interface NavItem {
  to: string
  label: string
  icon: IconName
  adminOnly?: boolean
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: 'grid' },
  { to: '/orders', label: 'Orders', icon: 'box' },
  { to: '/needs-action', label: 'Needs Action', icon: 'alert' },
  { to: '/drivers', label: 'Drivers', icon: 'truck' },
  { to: '/users', label: 'Users', icon: 'user', adminOnly: true },
]

function initials(name: string): string {
  return name.slice(0, 2).toUpperCase()
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const items = NAV.filter((item) => !item.adminOnly || user?.role === 'ADMIN')
  const current =
    items.find((item) => (item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)))
      ?.label ?? ''

  return (
    <div className="app-shell">
      <aside>
        <div className="brand">
          <div className="brand-mark">
            <Icon name="route" size={20} />
          </div>
          <div>
            <strong>RelayOps</strong>
            <span>Logistics control</span>
          </div>
        </div>
        <nav>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
              <Icon name={item.icon} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="avatar">{initials(user?.username ?? '?')}</div>
          <div>
            <strong>{user?.username}</strong>
            <span>{user?.role === 'ADMIN' ? 'Administrator' : 'Dispatcher'}</span>
          </div>
          <button className="icon-button sign-out" onClick={logout} aria-label="Sign out" title="Sign out">
            <Icon name="arrow" size={18} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header>
          <div className="page-heading">
            <span>Operations</span>
            <b>/</b>
            <strong>{current}</strong>
          </div>
        </header>
        <main>{children}</main>
      </div>
    </div>
  )
}
