import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Ban, RotateCcw, ShieldCheck, ShieldOff, Trash2, UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { AdminUser, Role } from '../../api/types'
import { formatRelative } from '../../lib/format'
import { useAuthStore } from '../../store/auth'
import { Button } from '../ui/Button'
import { Badge, Card, Field, Input } from '../ui/Form'
import { Listbox } from '../ui/Listbox'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'

export function UsersTab() {
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((s) => s.user)
  const users = useQuery({ queryKey: ['admin-users'], queryFn: adminApi.users })
  const [creating, setCreating] = useState(false)
  const [toDelete, setToDelete] = useState<AdminUser | null>(null)
  const [error, setError] = useState<string | null>(null)

  const replace = (updated: AdminUser) =>
    queryClient.setQueryData<AdminUser[]>(['admin-users'], (old) => old?.map((u) => (u.id === updated.id ? updated : u)))

  const action = useMutation({
    mutationFn: ({ user, kind }: { user: AdminUser; kind: 'disable' | 'reset' | 'promote' | 'demote' }) => {
      switch (kind) {
        case 'disable':
          return adminApi.disableUser(user.id)
        case 'reset':
          return adminApi.resetUserStatus(user.id)
        case 'promote':
          return adminApi.changeRole(user.id, 'ADMIN')
        case 'demote':
          return adminApi.changeRole(user.id, 'USER')
      }
    },
    onSuccess: (updated) => {
      setError(null)
      replace(updated)
    },
    onError: (e) => setError(errorMessage(e)),
  })

  const remove = useMutation({
    mutationFn: (user: AdminUser) => adminApi.deleteUser(user.id),
    onSuccess: (_, user) => {
      setToDelete(null)
      setError(null)
      queryClient.setQueryData<AdminUser[]>(['admin-users'], (old) => old?.filter((u) => u.id !== user.id))
    },
    onError: (e) => {
      setToDelete(null)
      setError(errorMessage(e))
    },
  })

  if (users.isLoading) {
    return (
      <div className="flex justify-center py-16 text-zinc-400">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-zinc-500">{users.data?.length ?? 0} users</p>
        <Button variant="primary" size="sm" onClick={() => setCreating(true)}>
          <UserPlus className="h-4 w-4" /> Add user
        </Button>
      </div>
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {error}
        </p>
      )}
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-zinc-200 text-xs text-zinc-500 uppercase dark:border-zinc-800">
            <tr>
              <th className="px-4 py-3 font-medium">User</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Last login</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(users.data ?? []).map((user) => {
              const self = user.id === currentUser?.id
              return (
                <tr key={user.id} className="border-b border-zinc-100 last:border-0 dark:border-zinc-800/60">
                  <td className="px-4 py-3">
                    <div className="font-medium">
                      {user.displayName ?? user.username} {self && <Badge tone="info">you</Badge>}
                    </div>
                    <div className="text-xs text-zinc-500">
                      {user.username} · {user.email}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={user.role === 'ADMIN' ? 'info' : 'neutral'}>{user.role === 'ADMIN' ? 'Admin' : 'User'}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={user.status === 'ACTIVE' ? 'success' : user.status === 'LOCKED' ? 'warning' : 'danger'}>
                      {user.status.toLowerCase()}
                    </Badge>
                    {user.failedLoginAttempts > 0 && (
                      <div className="mt-0.5 text-xs text-zinc-500">{user.failedLoginAttempts} failed logins</div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-zinc-500">{formatRelative(user.lastLoginAt)}</td>
                  <td className="px-4 py-3 text-xs text-zinc-500">{new Date(user.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      {user.status === 'ACTIVE' ? (
                        <Button size="icon-sm" variant="ghost" title="Disable user" aria-label={`Disable ${user.username}`} disabled={self} onClick={() => action.mutate({ user, kind: 'disable' })}>
                          <Ban className="h-4 w-4" />
                        </Button>
                      ) : (
                        <Button size="icon-sm" variant="ghost" title="Reset status (re-enable / unlock)" aria-label={`Reset status of ${user.username}`} onClick={() => action.mutate({ user, kind: 'reset' })}>
                          <RotateCcw className="h-4 w-4" />
                        </Button>
                      )}
                      {user.role === 'ADMIN' ? (
                        <Button size="icon-sm" variant="ghost" title="Remove admin role" aria-label={`Remove admin role from ${user.username}`} disabled={self} onClick={() => action.mutate({ user, kind: 'demote' })}>
                          <ShieldOff className="h-4 w-4" />
                        </Button>
                      ) : (
                        <Button size="icon-sm" variant="ghost" title="Make admin" aria-label={`Make ${user.username} admin`} onClick={() => action.mutate({ user, kind: 'promote' })}>
                          <ShieldCheck className="h-4 w-4" />
                        </Button>
                      )}
                      <Button size="icon-sm" variant="ghost" title="Delete user" aria-label={`Delete ${user.username}`} disabled={self} onClick={() => setToDelete(user)} className="hover:text-red-600">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </Card>

      <CreateUserDialog open={creating} onClose={() => setCreating(false)} />
      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Delete user?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={() => toDelete && remove.mutate(toDelete)}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          {toDelete?.username} and all of their conversations will be permanently deleted. Usage statistics are kept.
        </p>
      </Modal>
    </div>
  )
}

function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const empty = { username: '', email: '', password: '', displayName: '', role: 'USER' as Role }
  const [form, setForm] = useState(empty)
  const [error, setError] = useState<string | null>(null)
  const create = useMutation({
    mutationFn: () =>
      adminApi.createUser({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        displayName: form.displayName.trim() || undefined,
        role: form.role,
      }),
    onSuccess: (user) => {
      queryClient.setQueryData<AdminUser[]>(['admin-users'], (old) => [...(old ?? []), user])
      setForm(empty)
      setError(null)
      onClose()
    },
    onError: (e) => setError(errorMessage(e)),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    create.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add user"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="create-user-form" loading={create.isPending}>
            Create user
          </Button>
        </>
      }
    >
      <form id="create-user-form" onSubmit={submit} className="space-y-4">
        <Field label="Username" htmlFor="new-username">
          <Input id="new-username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
        </Field>
        <Field label="E-mail" htmlFor="new-email">
          <Input id="new-email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        </Field>
        <Field label="Display name" htmlFor="new-display-name">
          <Input id="new-display-name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </Field>
        <Field label="Initial password" htmlFor="new-user-password" hint="At least 8 characters">
          <Input id="new-user-password" type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Field label="Role">
          <Listbox
            ariaLabel="Role"
            value={form.role}
            options={[
              { value: 'USER', label: 'User' },
              { value: 'ADMIN', label: 'Administrator' },
            ]}
            onChange={(role) => setForm({ ...form, role: role as Role })}
          />
        </Field>
        {error && (
          <p className="text-sm text-red-600 dark:text-red-400" role="alert">
            {error}
          </p>
        )}
      </form>
    </Modal>
  )
}
