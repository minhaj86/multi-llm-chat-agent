import { Menu, Moon, Sun } from 'lucide-react'
import { useLocation } from 'react-router'
import { resolveDarkMode, usePreferences } from '../../store/preferences'
import { ProviderModelSelector } from '../providers/ProviderModelSelector'

export function TopBar({ onOpenSidebar, title }: { onOpenSidebar: () => void; title: string }) {
  const theme = usePreferences((s) => s.theme)
  const setTheme = usePreferences((s) => s.setTheme)
  const location = useLocation()
  const showSelector = location.pathname === '/' || location.pathname.startsWith('/c/')
  const dark = resolveDarkMode(theme)

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-zinc-200 px-3 sm:px-4 dark:border-zinc-800">
      <button
        type="button"
        onClick={onOpenSidebar}
        className="rounded-lg p-2 text-zinc-600 hover:bg-zinc-100 md:hidden dark:text-zinc-300 dark:hover:bg-zinc-800"
        aria-label="Open sidebar"
      >
        <Menu className="h-5 w-5" />
      </button>
      <h1
        className={
          showSelector
            ? 'hidden min-w-0 flex-1 truncate text-base font-semibold text-zinc-800 lg:block dark:text-zinc-100'
            : 'min-w-0 flex-1 truncate text-sm font-semibold text-zinc-800 sm:text-base dark:text-zinc-100'
        }
      >
        {title}
      </h1>
      {showSelector && <ProviderModelSelector className="min-w-0 flex-1 justify-end lg:flex-none" />}
      <button
        type="button"
        onClick={() => setTheme(dark ? 'light' : 'dark')}
        className="shrink-0 rounded-lg p-2 text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
        aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      >
        {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      </button>
    </header>
  )
}
