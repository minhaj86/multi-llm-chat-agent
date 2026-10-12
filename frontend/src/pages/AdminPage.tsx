import { useSearchParams } from 'react-router'
import { AdminNav } from '../components/admin/AdminNav'
import { PromptsTab } from '../components/admin/PromptsTab'
import { ProvidersTab } from '../components/admin/ProvidersTab'
import { UsageTab } from '../components/admin/UsageTab'
import { UsersTab } from '../components/admin/UsersTab'

const TABS = ['usage', 'providers', 'users', 'prompts'] as const
type TabId = (typeof TABS)[number]

export function AdminPage() {
  const [params] = useSearchParams()
  const tab = (TABS.find((t) => t === params.get('tab')) ?? 'usage') as TabId

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <AdminNav />
        <div role="region" aria-label="Administration section">
          {tab === 'usage' && <UsageTab />}
          {tab === 'providers' && <ProvidersTab />}
          {tab === 'users' && <UsersTab />}
          {tab === 'prompts' && <PromptsTab />}
        </div>
      </div>
    </div>
  )
}
