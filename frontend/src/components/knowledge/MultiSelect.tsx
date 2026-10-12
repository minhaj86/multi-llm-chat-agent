import { Check, ChevronDown, X } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { cn } from '../../lib/format'

export interface MultiSelectOption {
  value: string
  label: string
  description?: string | null
}

/** Compact multi-select dropdown with checkboxes (keyboard: Tab/Space to toggle, Escape to close). */
export function MultiSelect({
  options,
  value,
  onChange,
  ariaLabel,
  placeholder,
  disabled,
  className,
  buttonClassName,
}: {
  options: MultiSelectOption[]
  value: string[]
  onChange: (value: string[]) => void
  ariaLabel: string
  placeholder: string
  disabled?: boolean
  className?: string
  buttonClassName?: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const listId = useId()

  useEffect(() => {
    if (!open) {
      return
    }
    const onPointer = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const selected = options.filter((option) => value.includes(option.value))
  const label =
    selected.length === 0 ? placeholder : selected.length === 1 ? selected[0].label : `${selected.length} selected`
  const toggle = (optionValue: string) =>
    onChange(value.includes(optionValue) ? value.filter((v) => v !== optionValue) : [...value, optionValue])

  return (
    <div ref={ref} className={cn('relative min-w-0', className)}>
      <button
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'flex h-9 w-full min-w-0 items-center justify-between gap-2 rounded-lg border border-zinc-200 bg-white px-2.5 text-left text-sm shadow-sm transition hover:border-zinc-300 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:hover:border-zinc-600',
          buttonClassName,
        )}
      >
        <span className="truncate">{label}</span>
        <ChevronDown className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden="true" />
      </button>
      {open && (
        <div
          id={listId}
          role="listbox"
          aria-label={ariaLabel}
          aria-multiselectable="true"
          className="absolute bottom-full left-0 z-30 mb-1 max-h-72 w-72 max-w-[85vw] overflow-y-auto rounded-xl border border-zinc-200 bg-white p-1 shadow-lg dark:border-zinc-700 dark:bg-zinc-900 sm:bottom-auto sm:top-full sm:mt-1 sm:mb-0"
        >
          {options.length === 0 && <p className="px-2 py-2 text-sm text-zinc-500">Nothing to select</p>}
          {options.map((option) => {
            const checked = value.includes(option.value)
            return (
              <label
                key={option.value}
                role="option"
                aria-selected={checked}
                className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <input
                  type="checkbox"
                  className="mt-0.5 h-4 w-4 accent-indigo-600"
                  checked={checked}
                  onChange={() => toggle(option.value)}
                />
                <span className="min-w-0">
                  <span className="block truncate font-medium">{option.label}</span>
                  {option.description && (
                    <span className="block truncate text-xs text-zinc-500 dark:text-zinc-400">{option.description}</span>
                  )}
                </span>
                {checked && <Check className="ml-auto h-4 w-4 shrink-0 text-indigo-500" aria-hidden="true" />}
              </label>
            )
          })}
          {value.length > 0 && (
            <button
              type="button"
              onClick={() => onChange([])}
              className="mt-1 flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800"
            >
              <X className="h-3.5 w-3.5" /> Clear selection ({placeholder.toLowerCase()})
            </button>
          )}
        </div>
      )}
    </div>
  )
}
