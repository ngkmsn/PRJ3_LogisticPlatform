import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { UserProfile } from '../api/types'
import { listUsers, setUserStatus } from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { CreateDriverProfileModal } from '../components/CreateDriverProfileModal'
import { CreateUserModal } from '../components/CreateUserModal'
import { formatDateTime } from '../components/format'
import { Badge, Button, Pagination, SectionTitle, StateMessage } from '../components/ui'

const PAGE_SIZE = 20

export function UsersPage() {
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [profileFor, setProfileFor] = useState<UserProfile | null>(null)
  const [notice, setNotice] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null)

  const query = useQuery({ queryKey: ['users', page], queryFn: () => listUsers({ page, size: PAGE_SIZE }) })

  async function toggleLock(u: UserProfile) {
    const next = u.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED'
    try {
      await setUserStatus(u.id, next)
      setNotice({ tone: 'ok', text: `${u.username} is now ${next}` })
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    } catch (e) {
      setNotice({ tone: 'error', text: e instanceof Error ? e.message : 'Status change failed' })
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Users</h1>
          <p>Accounts for administrators, dispatchers, drivers and customers</p>
        </div>
        <Button variant="primary" icon="user" onClick={() => setCreating(true)}>
          New user
        </Button>
      </div>

      {notice && (
        <div className={notice.tone === 'ok' ? 'form-ok' : 'form-error'} role="status">
          {notice.text}
        </div>
      )}

      <section className="panel">
        <SectionTitle title="All users" meta={query.data ? `${query.data.totalElements} users` : undefined} />
        {query.isPending ? (
          <StateMessage>Loading users…</StateMessage>
        ) : query.isError ? (
          <StateMessage tone="error">{query.error.message}</StateMessage>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Username</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {query.data.content.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <strong>{u.username}</strong>
                      </td>
                      <td>{u.email}</td>
                      <td>
                        <Badge>{u.role}</Badge>
                      </td>
                      <td>
                        <Badge>{u.status}</Badge>
                      </td>
                      <td>{formatDateTime(u.createdAt)}</td>
                      <td>
                        <div className="row-actions">
                          {u.role === 'DRIVER' && <Button onClick={() => setProfileFor(u)}>Create driver profile</Button>}
                          {u.id !== me?.id && (
                            <Button onClick={() => toggleLock(u)}>{u.status === 'LOCKED' ? 'Unlock' : 'Lock'}</Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} size={PAGE_SIZE} totalElements={query.data.totalElements} onPage={setPage} />
          </>
        )}
      </section>

      {creating && (
        <CreateUserModal
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false)
            setNotice({ tone: 'ok', text: 'User created' })
            queryClient.invalidateQueries({ queryKey: ['users'] })
          }}
        />
      )}
      {profileFor && (
        <CreateDriverProfileModal
          userId={profileFor.id}
          username={profileFor.username}
          onClose={() => setProfileFor(null)}
          onDone={() => {
            setNotice({ tone: 'ok', text: `Driver profile created for ${profileFor.username}` })
            setProfileFor(null)
            queryClient.invalidateQueries({ queryKey: ['drivers'] })
          }}
        />
      )}
    </div>
  )
}
