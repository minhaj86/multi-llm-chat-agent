import { BarChart3, BookUser, Server, Users } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { PromptsTab } from '../components/admin/PromptsTab'
import { ProvidersTab } from '../components/admin/ProvidersTab'
import { UsageTab } from '../components/admin/UsageTab'
import { UsersTab } from '../components/admin/UsersTab'
import { cn } from '../lib/format'

const TABS = [
  { id: 'usage', label: 'Usage', icon: BarChart3 },
  { id: 'providers', label: 'Providers', icon: Server },
  { id: 'users', label: 'Users', icon: Users },
  { id: 'prompts', label: 'System prompts', icon: BookUser },
] as const

type TabId = (typeof TABS)[number]['id']

export function AdminPage() {
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'usage') as TabId

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <div role="tablist" aria-label="Administration" className="mb-6 flex gap-1 overflow-x-auto border-b border-zinc-200 dark:border-zinc-800">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              onClick={() => setParams({ tab: id })}
              className={cn(
                '-mb-px flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition',
                tab === id
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200',
              )}
            >
              <Icon className="h-4 w-4" /> {label}
            </button>
          ))}
        </div>
        <div role="tabpanel">
          {tab === 'usage' && <UsageTab />}
          {tab === 'providers' && <ProvidersTab />}
          {tab === 'users' && <UsersTab />}
          {tab === 'prompts' && <PromptsTab />}
        </div>
      </div>
    </div>
  )
}
