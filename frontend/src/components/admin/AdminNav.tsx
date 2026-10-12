import { BarChart3, BookUser, Library, Server, Users } from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { cn } from '../../lib/format'

export const ADMIN_TABS = [
  { id: 'usage', label: 'Usage', icon: BarChart3, to: '/admin?tab=usage' },
  { id: 'providers', label: 'Providers', icon: Server, to: '/admin?tab=providers' },
  { id: 'users', label: 'Users', icon: Users, to: '/admin?tab=users' },
  { id: 'prompts', label: 'System prompts', icon: BookUser, to: '/admin?tab=prompts' },
  { id: 'knowledge', label: 'Knowledge base', icon: Library, to: '/admin/knowledge' },
] as const

export type AdminTabId = (typeof ADMIN_TABS)[number]['id']

/** Navigation shared by all administration pages. */
export function AdminNav() {
  const location = useLocation()
  const [params] = useSearchParams()
  const active: AdminTabId = location.pathname.startsWith('/admin/knowledge')
    ? 'knowledge'
    : ((ADMIN_TABS.find((t) => t.id === params.get('tab'))?.id ?? 'usage') as AdminTabId)
  return (
    <nav aria-label="Administration" className="mb-6 flex gap-1 overflow-x-auto border-b border-zinc-200 dark:border-zinc-800">
      {ADMIN_TABS.map(({ id, label, icon: Icon, to }) => (
        <Link
          key={id}
          to={to}
          aria-current={active === id ? 'page' : undefined}
          className={cn(
            '-mb-px flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition',
            active === id
              ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200',
          )}
        >
          <Icon className="h-4 w-4" /> {label}
        </Link>
      ))}
    </nav>
  )
}
