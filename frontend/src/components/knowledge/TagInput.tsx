import { X } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'

/** Tag chips: Enter or comma adds a tag, Backspace on an empty input removes the last one. */
export function TagInput({ value, onChange, id, max = 20 }: {
  value: string[]
  onChange: (tags: string[]) => void
  id?: string
  max?: number
}) {
  const [draft, setDraft] = useState('')
  const add = (raw: string) => {
    const tags = raw
      .split(',')
      .map((t) => t.trim().replace(/^#/, '').toLowerCase())
      .filter((t) => t.length > 0 && t.length <= 50)
    const next = [...value]
    for (const tag of tags) {
      if (!next.includes(tag) && next.length < max) {
        next.push(tag)
      }
    }
    onChange(next)
    setDraft('')
  }
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if ((event.key === 'Enter' || event.key === ',') && draft.trim()) {
      event.preventDefault()
      add(draft)
    } else if (event.key === 'Backspace' && !draft && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }
  return (
    <div className="flex min-h-10 flex-wrap items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-2 py-1.5 focus-within:ring-2 focus-within:ring-indigo-500/40 dark:border-zinc-700 dark:bg-zinc-900">
      {value.map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-1.5 py-0.5 text-xs font-medium text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300"
        >
          {tag}
          <button
            type="button"
            aria-label={`Remove tag ${tag}`}
            onClick={() => onChange(value.filter((t) => t !== tag))}
            className="rounded hover:bg-indigo-100 dark:hover:bg-indigo-500/25"
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
      <input
        id={id}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => draft.trim() && add(draft)}
        placeholder={value.length === 0 ? 'Add tags (Enter or comma)' : ''}
        className="min-w-24 flex-1 bg-transparent text-sm outline-none placeholder:text-zinc-400"
      />
    </div>
  )
}
