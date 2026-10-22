import { Columns3, LogOut, MessageSquarePlus, Mic, Search, Settings, Shield, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, NavLink, useNavigate, useParams } from 'react-router'
import { useConversations } from '../../hooks/useConversations'
import { cn, dateGroup, type DateGroup } from '../../lib/format'
import { useAuthStore } from '../../store/auth'
import { ProviderStatusList } from '../providers/ProviderStatusList'
import { Spinner } from '../ui/Spinner'
import { ConversationItem } from './ConversationItem'

const GROUP_ORDER: DateGroup[] = ['Today', 'Yesterday', 'Previous 7 days', 'Previous 30 days', 'Older']

export function Sidebar({ open, onClose, onOpenSettings }: {
  open: boolean
  onClose: () => void
  onOpenSettings: () => void
}) {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const { data: conversations = [], isLoading } = useConversations()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const [query, setQuery] = useState('')

  const groups = useMemo(() => {
    const term = query.trim().toLowerCase()
    const filtered = term ? conversations.filter((c) => c.title.toLowerCase().includes(term)) : conversations
    const grouped = new Map<DateGroup, typeof conversations>()
    for (const conversation of filtered) {
      const group = dateGroup(conversation.updatedAt)
      grouped.set(group, [...(grouped.get(group) ?? []), conversation])
    }
    return GROUP_ORDER.filter((g) => grouped.has(g)).map((g) => ({ name: g, items: grouped.get(g) ?? [] }))
  }, [conversations, query])

  const navClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm transition',
      isActive ? 'bg-zinc-200/80 font-medium dark:bg-zinc-800' : 'text-zinc-700 hover:bg-zinc-200/50 dark:text-zinc-300 dark:hover:bg-zinc-800/60',
    )

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-zinc-950/40 md:hidden" onClick={onClose} aria-hidden="true" />}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-72 max-w-[85vw] flex-col border-r border-zinc-200 bg-zinc-50 shadow-xl transition-transform duration-200 md:static md:max-w-none md:translate-x-0 md:shadow-none dark:border-zinc-800 dark:bg-zinc-900 md:dark:bg-zinc-900/60',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
        aria-label="Sidebar"
      >
        <div className="flex items-center justify-between px-4 pt-4 pb-3">
          <Link to="/" onClick={onClose} className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-cyan-500 text-sm font-bold text-white">
              A
            </span>
            <span className="text-base font-semibold tracking-tight">AI Workspace</span>
          </Link>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-zinc-500 md:hidden" aria-label="Close sidebar">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="space-y-1 px-3">
          <button
            type="button"
            onClick={() => {
              navigate('/')
              onClose()
            }}
            className="flex w-full items-center gap-2 rounded-xl border border-zinc-200 bg-white px-3 py-2.5 text-sm font-medium shadow-xs transition hover:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-900 dark:hover:bg-zinc-800"
          >
            <MessageSquarePlus className="h-4 w-4" /> New chat
          </button>
          <NavLink to="/audio" onClick={onClose} className={navClass}>
            <Mic className="h-4 w-4" /> New audio chat
          </NavLink>
          <NavLink to="/compare" onClick={onClose} className={navClass}>
            <Columns3 className="h-4 w-4" /> Compare models
          </NavLink>
          {user?.role === 'ADMIN' && (
            <NavLink to="/admin" onClick={onClose} className={navClass}>
              <Shield className="h-4 w-4" /> Admin
            </NavLink>
          )}
        </div>

        <div className="mt-3 px-3">
          <div className="relative">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search chats"
              aria-label="Search conversations"
              className="h-9 w-full rounded-lg border border-transparent bg-zinc-200/60 pr-3 pl-8 text-sm outline-none focus:border-zinc-300 focus:bg-white dark:bg-zinc-800/80 dark:focus:border-zinc-700 dark:focus:bg-zinc-900"
            />
          </div>
        </div>

        <nav className="mt-3 min-h-0 flex-1 overflow-y-auto px-3 pb-3" aria-label="Conversations">
          {isLoading && (
            <div className="flex justify-center py-6 text-zinc-400">
              <Spinner />
            </div>
          )}
          {!isLoading && groups.length === 0 && (
            <p className="px-2 py-6 text-center text-xs text-zinc-500">
              {query ? 'No matching conversations' : 'No conversations yet'}
            </p>
          )}
          {groups.map((group) => (
            <div key={group.name} className="mb-3">
              <h3 className="px-2.5 pb-1 text-[11px] font-semibold tracking-wide text-zinc-500 uppercase">{group.name}</h3>
              <div className="space-y-0.5">
                {group.items.map((conversation) => (
                  <ConversationItem key={conversation.id} conversation={conversation} activeId={conversationId} onNavigate={onClose} />
                ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="space-y-3 border-t border-zinc-200 p-3 dark:border-zinc-800">
          <div className="px-1">
            <ProviderStatusList />
          </div>
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-200 text-xs font-semibold uppercase dark:bg-zinc-700">
              {(user?.displayName ?? user?.username ?? '?').slice(0, 2)}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{user?.displayName ?? user?.username}</div>
              <div className="truncate text-xs text-zinc-500">{user?.role === 'ADMIN' ? 'Administrator' : user?.email}</div>
            </div>
            <button type="button" onClick={onOpenSettings} className="rounded-lg p-1.5 text-zinc-500 hover:bg-zinc-200 hover:text-zinc-900 dark:hover:bg-zinc-800 dark:hover:text-zinc-100" aria-label="Settings">
              <Settings className="h-4 w-4" />
            </button>
            <button type="button" onClick={() => logout('manual')} className="rounded-lg p-1.5 text-zinc-500 hover:bg-zinc-200 hover:text-zinc-900 dark:hover:bg-zinc-800 dark:hover:text-zinc-100" aria-label="Log out">
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>
    </>
  )
}
