import { useCallback, useEffect, useRef, useState } from 'react'

/** Copies text to the clipboard and exposes a short-lived "copied" flag for feedback. */
export function useCopyToClipboard(resetAfterMs = 1800) {
  const [copied, setCopied] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => () => {
    if (timer.current) {
      clearTimeout(timer.current)
    }
  }, [])

  const copy = useCallback(
    async (text: string) => {
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(text)
        } else {
          const area = document.createElement('textarea')
          area.value = text
          area.style.position = 'fixed'
          area.style.opacity = '0'
          document.body.appendChild(area)
          area.select()
          document.execCommand('copy')
          area.remove()
        }
        setCopied(true)
        if (timer.current) {
          clearTimeout(timer.current)
        }
        timer.current = setTimeout(() => setCopied(false), resetAfterMs)
        return true
      } catch {
        return false
      }
    },
    [resetAfterMs],
  )

  return { copied, copy }
}
