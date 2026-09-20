import { useEffect } from 'react'
import { resolveDarkMode, usePreferences } from '../store/preferences'

/** Keeps the {@code dark} class on the document in sync with the preference (and the OS for "system"). */
export function useThemeSync() {
  const theme = usePreferences((s) => s.theme)
  useEffect(() => {
    const apply = () => document.documentElement.classList.toggle('dark', resolveDarkMode(theme))
    apply()
    if (theme !== 'system') {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])
}
