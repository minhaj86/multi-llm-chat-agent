import { ChevronDown, Check } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from '../../lib/format'

export interface ListboxOption {
  value: string
  label: string
  description?: string | null
  disabled?: boolean
  icon?: ReactNode
}

interface ListboxProps {
  value: string | null
  options: ListboxOption[]
  onChange: (value: string) => void
  ariaLabel: string
  placeholder?: string
  disabled?: boolean
  className?: string
  buttonClassName?: string
  align?: 'left' | 'right'
}

/** Accessible single-select dropdown (WAI-ARIA listbox pattern) with support for disabled options. */
export function Listbox({
  value,
  options,
  onChange,
  ariaLabel,
  placeholder = 'Select…',
  disabled,
  className,
  buttonClassName,
  align = 'left',
}: ListboxProps) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const rootRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const id = useId()
  const selected = useMemo(() => options.find((o) => o.value === value) ?? null, [options, value])

  useEffect(() => {
    if (!open) {
      return
    }
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    listRef.current?.focus()
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  useEffect(() => {
    if (open && active >= 0) {
      document.getElementById(`${id}-option-${active}`)?.scrollIntoView({ block: 'nearest' })
    }
  }, [open, active, id])

  const openList = () => {
    if (disabled) {
      return
    }
    const index = options.findIndex((o) => o.value === value)
    setActive(index >= 0 ? index : options.findIndex((o) => !o.disabled))
    setOpen(true)
  }

  const close = (focusButton = true) => {
    setOpen(false)
    if (focusButton) {
      buttonRef.current?.focus()
    }
  }

  const choose = (option: ListboxOption) => {
    if (option.disabled) {
      return
    }
    onChange(option.value)
    close()
  }

  const move = (delta: number) => {
    if (options.length === 0) {
      return
    }
    let index = active
    for (let i = 0; i < options.length; i++) {
      index = (index + delta + options.length) % options.length
      if (!options[index].disabled) {
        setActive(index)
        return
      }
    }
  }

  const onListKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        move(1)
        break
      case 'ArrowUp':
        event.preventDefault()
        move(-1)
        break
      case 'Home':
        event.preventDefault()
        setActive(options.findIndex((o) => !o.disabled))
        break
      case 'End':
        event.preventDefault()
        for (let i = options.length - 1; i >= 0; i--) {
          if (!options[i].disabled) {
            setActive(i)
            break
          }
        }
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        if (active >= 0) {
          choose(options[active])
        }
        break
      case 'Escape':
        event.preventDefault()
        close()
        break
      case 'Tab':
        close(false)
        break
      default:
        break
    }
  }

  const onButtonKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      openList()
    }
  }

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => (open ? close() : openList())}
        onKeyDown={onButtonKeyDown}
        className={cn(
          'flex h-9 w-full min-w-0 items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 text-left text-sm shadow-xs transition hover:border-zinc-300 focus-visible:outline-2 focus-visible:outline-indigo-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:hover:border-zinc-600',
          buttonClassName,
        )}
      >
        {selected?.icon}
        <span className={cn('min-w-0 flex-1 truncate', !selected && 'text-zinc-400')}>
          {selected?.label ?? placeholder}
        </span>
        <ChevronDown className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden="true" />
      </button>
      {open && (
        <ul
          ref={listRef}
          role="listbox"
          tabIndex={-1}
          aria-label={ariaLabel}
          aria-activedescendant={active >= 0 ? `${id}-option-${active}` : undefined}
          onKeyDown={onListKeyDown}
          className={cn(
            'absolute z-40 mt-1 max-h-80 min-w-[max(100%,14rem)] overflow-auto rounded-xl border border-zinc-200 bg-white p-1 text-sm shadow-xl outline-none sm:min-w-64 dark:border-zinc-700 dark:bg-zinc-900',
            align === 'right' ? 'right-0' : 'left-0',
          )}
        >
          {options.length === 0 && <li className="px-3 py-2 text-zinc-500">No options</li>}
          {options.map((option, index) => {
            const isSelected = option.value === value
            return (
              <li
                key={option.value}
                id={`${id}-option-${index}`}
                role="option"
                aria-selected={isSelected}
                aria-disabled={option.disabled || undefined}
                onMouseEnter={() => !option.disabled && setActive(index)}
                onClick={() => choose(option)}
                className={cn(
                  'flex cursor-pointer items-start gap-2 rounded-lg px-3 py-2',
                  index === active && !option.disabled && 'bg-zinc-100 dark:bg-zinc-800',
                  option.disabled && 'cursor-not-allowed opacity-50',
                )}
              >
                {option.icon && <span className="mt-1">{option.icon}</span>}
                <span className="min-w-0 flex-1">
                  <span className="block truncate">{option.label}</span>
                  {option.description && (
                    <span className="block truncate text-xs text-zinc-500 dark:text-zinc-400">{option.description}</span>
                  )}
                </span>
                {isSelected && <Check className="mt-0.5 h-4 w-4 shrink-0 text-indigo-500" aria-hidden="true" />}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
