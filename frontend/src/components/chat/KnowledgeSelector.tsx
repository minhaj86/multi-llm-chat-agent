import { useQuery } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { knowledgeApi } from '../../api/client'
import type { KnowledgeSettings } from '../../api/types'
import { cn } from '../../lib/format'
import { MultiSelect } from '../knowledge/MultiSelect'

/**
 * "Use knowledge base" toggle plus the collections to search, shown in the composer toolbar. Hidden when the
 * knowledge base is disabled on the server or has no enabled collections.
 */
export function KnowledgeSelector({ value, onChange, disabled }: {
  value: KnowledgeSettings
  onChange: (value: KnowledgeSettings) => void
  disabled?: boolean
}) {
  const { data } = useQuery({ queryKey: ['knowledge', 'chat'], queryFn: knowledgeApi.forChat, staleTime: 60_000 })
  if (!data?.enabled || data.collections.length === 0) {
    return null
  }
  const available = new Set(data.collections.map((c) => c.id))
  const selected = value.collectionIds.filter((id) => available.has(id))
  return (
    <div className="flex min-w-0 items-center gap-1.5">
      <button
        type="button"
        aria-pressed={value.enabled}
        disabled={disabled}
        onClick={() => onChange({ enabled: !value.enabled, collectionIds: selected })}
        title={value.enabled ? 'Answers use the knowledge base' : 'Answer with the knowledge base'}
        className={cn(
          'inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg px-2.5 text-xs font-medium transition disabled:opacity-50',
          value.enabled
            ? 'bg-indigo-600 text-white hover:bg-indigo-500'
            : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700',
        )}
      >
        <BookOpen className="h-3.5 w-3.5" aria-hidden="true" />
        Knowledge
      </button>
      {value.enabled && (
        <MultiSelect
          ariaLabel="Knowledge collections"
          placeholder="All collections"
          options={data.collections.map((c) => ({ value: c.id, label: c.name, description: c.description }))}
          value={selected}
          onChange={(ids) => onChange({ enabled: true, collectionIds: ids })}
          disabled={disabled}
          className="w-40 max-w-full"
          buttonClassName="h-8 border-transparent bg-zinc-100 text-xs shadow-none dark:border-transparent dark:bg-zinc-800"
        />
      )}
    </div>
  )
}
