import { Check } from 'lucide-react'
import type { Provider } from '../../api/types'
import { useModels } from '../../hooks/useProviders'
import { cn } from '../../lib/format'
import { Listbox } from '../ui/Listbox'
import { StatusDot, statusLabel } from '../ui/StatusDot'

export interface TargetSelection {
  providerId: string
  model: string | null
}

/** Toggle chips for providers plus a model picker for every selected provider. */
export function TargetPicker({ providers, targets, onChange, maxTargets, disabled }: {
  providers: Provider[]
  targets: TargetSelection[]
  onChange: (targets: TargetSelection[]) => void
  maxTargets: number
  disabled?: boolean
}) {
  const toggle = (provider: Provider) => {
    if (targets.some((t) => t.providerId === provider.id)) {
      onChange(targets.filter((t) => t.providerId !== provider.id))
    } else if (targets.length < maxTargets) {
      onChange([...targets, { providerId: provider.id, model: null }])
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Providers to compare">
        {providers.map((provider) => {
          const selected = targets.some((t) => t.providerId === provider.id)
          const blocked = !provider.available || (!selected && targets.length >= maxTargets)
          return (
            <button
              key={provider.id}
              type="button"
              aria-pressed={selected}
              disabled={disabled || (!selected && blocked)}
              onClick={() => toggle(provider)}
              title={provider.available ? undefined : (provider.statusMessage ?? statusLabel(provider.status, provider.enabled))}
              className={cn(
                'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition disabled:cursor-not-allowed disabled:opacity-50',
                selected
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
                  : 'border-zinc-300 hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800',
              )}
            >
              {selected ? <Check className="h-3.5 w-3.5" /> : <StatusDot status={provider.status} enabled={provider.enabled} />}
              {provider.available ? provider.name : `${provider.name} — ${statusLabel(provider.status, provider.enabled)}`}
            </button>
          )
        })}
      </div>
      {targets.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {targets.map((target) => {
            const provider = providers.find((p) => p.id === target.providerId)
            return provider ? (
              <ModelChooser
                key={target.providerId}
                provider={provider}
                value={target.model}
                disabled={disabled}
                onChange={(model) =>
                  onChange(targets.map((t) => (t.providerId === target.providerId ? { ...t, model } : t)))
                }
              />
            ) : null
          })}
        </div>
      )}
    </div>
  )
}

function ModelChooser({ provider, value, onChange, disabled }: {
  provider: Provider
  value: string | null
  onChange: (model: string) => void
  disabled?: boolean
}) {
  const { data: models = [] } = useModels(provider.id, provider.available)
  const options = models.map((m) => ({ value: m.id, label: m.name, description: m.isDefault ? 'Default model' : null }))
  const effective = value ?? models.find((m) => m.isDefault)?.id ?? provider.defaultModel
  if (effective && !options.some((o) => o.value === effective)) {
    options.unshift({ value: effective, label: effective, description: 'Default model' })
  }
  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="text-zinc-500">{provider.name}</span>
      <Listbox
        ariaLabel={`${provider.name} model`}
        value={effective}
        options={options}
        onChange={onChange}
        disabled={disabled}
        className="w-52"
      />
    </div>
  )
}
