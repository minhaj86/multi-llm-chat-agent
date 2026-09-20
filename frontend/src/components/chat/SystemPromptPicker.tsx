import { BookUser } from 'lucide-react'
import { useSystemPrompts } from '../../hooks/useConversations'
import { Listbox } from '../ui/Listbox'

const SERVER_DEFAULT = '__default__'

/** Picker for server-side system prompt profiles ("General Assistant", "Java Expert", ...). */
export function SystemPromptPicker({ value, onChange, disabled }: {
  value: string | null
  onChange: (id: string | null) => void
  disabled?: boolean
}) {
  const { data: prompts = [] } = useSystemPrompts()
  if (prompts.length === 0) {
    return null
  }
  const defaultPrompt = prompts.find((p) => p.isDefault)
  const options = [
    { value: SERVER_DEFAULT, label: defaultPrompt ? `${defaultPrompt.name} (default)` : 'Default', description: defaultPrompt?.description },
    ...prompts.filter((p) => !p.isDefault).map((p) => ({ value: p.id, label: p.name, description: p.description })),
  ]
  const selected = value && prompts.some((p) => p.id === value && !p.isDefault) ? value : SERVER_DEFAULT
  return (
    <div className="flex min-w-0 items-center gap-1.5">
      <BookUser className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden="true" />
      <Listbox
        ariaLabel="System prompt profile"
        value={selected}
        options={options}
        onChange={(id) => onChange(id === SERVER_DEFAULT ? (defaultPrompt?.id ?? null) : id)}
        disabled={disabled}
        className="w-48 max-w-full"
        buttonClassName="h-8 border-transparent bg-zinc-100 text-xs shadow-none dark:border-transparent dark:bg-zinc-800"
      />
    </div>
  )
}
