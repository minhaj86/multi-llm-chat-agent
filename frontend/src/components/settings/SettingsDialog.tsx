import { Monitor, Moon, Sun } from 'lucide-react'
import { cn } from '../../lib/format'
import { useAuthStore } from '../../store/auth'
import { usePreferences, type Theme } from '../../store/preferences'
import { SystemPromptPicker } from '../chat/SystemPromptPicker'
import { Toggle } from '../ui/Form'
import { Modal } from '../ui/Modal'

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

export function SettingsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const preferences = usePreferences()
  const user = useAuthStore((s) => s.user)

  return (
    <Modal open={open} onClose={onClose} title="Settings">
      <div className="space-y-6">
        <section>
          <h3 className="mb-2 text-sm font-medium">Theme</h3>
          <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Theme">
            {THEMES.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={preferences.theme === value}
                onClick={() => preferences.setTheme(value)}
                className={cn(
                  'flex flex-col items-center gap-1 rounded-xl border px-3 py-3 text-sm transition',
                  preferences.theme === value
                    ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
                    : 'border-zinc-200 hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800',
                )}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>
        </section>

        <section className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="text-sm font-medium">Send with Enter</div>
              <div className="text-xs text-zinc-500">Use Shift+Enter for a new line.</div>
            </div>
            <Toggle checked={preferences.enterToSend} onChange={preferences.setEnterToSend} label="Send with Enter" />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="text-sm font-medium">Expand model reasoning</div>
              <div className="text-xs text-zinc-500">Show the “thinking” of reasoning models by default.</div>
            </div>
            <Toggle checked={preferences.expandReasoning} onChange={preferences.setExpandReasoning} label="Expand model reasoning" />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="text-sm font-medium">Default profile for new chats</div>
              <div className="text-xs text-zinc-500">System prompt used when starting a conversation.</div>
            </div>
            <SystemPromptPicker value={preferences.systemPromptId} onChange={preferences.setSystemPromptId} />
          </div>
        </section>

        {user && (
          <section className="rounded-xl bg-zinc-50 p-3 text-sm dark:bg-zinc-800/50">
            <div className="font-medium">{user.displayName ?? user.username}</div>
            <div className="text-xs text-zinc-500">
              {user.username} · {user.email} · {user.role === 'ADMIN' ? 'Administrator' : 'User'}
            </div>
          </section>
        )}
      </div>
    </Modal>
  )
}
