import { useMemo, useState } from 'react'
import { Outlet, useLocation, useParams } from 'react-router'
import { useConversations } from '../../hooks/useConversations'
import { SettingsDialog } from '../settings/SettingsDialog'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const location = useLocation()
  const { conversationId } = useParams()
  const { data: conversations } = useConversations()

  const title = useMemo(() => {
    if (location.pathname.startsWith('/compare')) {
      return 'Compare models'
    }
    if (location.pathname === '/audio') return 'New audio chat'
    if (location.pathname.startsWith('/admin')) {
      return 'Administration'
    }
    if (conversationId) {
      return conversations?.find((c) => c.id === conversationId)?.title ?? 'Conversation'
    }
    return 'New chat'
  }, [location.pathname, conversationId, conversations])

  return (
    <div className="flex h-dvh overflow-hidden">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} onOpenSettings={() => setSettingsOpen(true)} />
      <main className="flex min-w-0 flex-1 flex-col">
        <TopBar onOpenSidebar={() => setSidebarOpen(true)} title={title} />
        <div className="min-h-0 flex-1">
          <Outlet />
        </div>
      </main>
      <SettingsDialog open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  )
}
