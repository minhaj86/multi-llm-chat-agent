import { Cloud, Cpu } from 'lucide-react'
import type { Provider } from '../../api/types'
import { useProviderSelection } from '../../hooks/useProviders'
import { cn } from '../../lib/format'
import { Listbox, type ListboxOption } from '../ui/Listbox'
import { StatusDot } from '../ui/StatusDot'

export function providerOptionLabel(provider: Provider): string {
  if (provider.available) {
    return provider.name
  }
  return `${provider.name} — ${provider.enabled ? 'Offline' : 'Disabled'}`
}

export function providerOptions(providers: Provider[]): ListboxOption[] {
  return providers.map((p) => ({
    value: p.id,
    label: providerOptionLabel(p),
    description: p.available
      ? `${p.kind === 'LOCAL' ? 'Local' : 'Cloud'}${p.status === 'DEGRADED' && p.statusMessage ? ` · ${p.statusMessage}` : ''}`
      : (p.statusMessage ?? undefined),
    disabled: !p.available,
    icon: <StatusDot status={p.status} enabled={p.enabled} />,
  }))
}

/**
 * Provider and model dropdowns. Both lists come from the backend ({@code /api/providers},
 * {@code /api/providers/{id}/models}); offline providers are listed but cannot be selected.
 */
export function ProviderModelSelector({ className }: { className?: string }) {
  const { providers, provider, models, model, selectProvider, selectModel, providersLoading, modelsLoading } =
    useProviderSelection()

  const modelOptions: ListboxOption[] = models.map((m) => ({
    value: m.id,
    label: m.name,
    description: m.isDefault ? 'Default model' : m.ownedBy ? `by ${m.ownedBy}` : null,
  }))
  if (model && !modelOptions.some((o) => o.value === model)) {
    modelOptions.unshift({ value: model, label: model })
  }

  return (
    <div className={cn('flex min-w-0 items-center gap-2', className)}>
      <div className="flex min-w-0 flex-1 items-center gap-1.5 sm:flex-none">
        <span className="hidden text-xs font-medium text-zinc-500 xl:inline">Provider</span>
        <Listbox
          ariaLabel="Provider"
          value={provider?.id ?? null}
          options={providerOptions(providers)}
          onChange={selectProvider}
          placeholder={providersLoading ? 'Loading…' : 'No providers'}
          className="min-w-0 flex-1 sm:w-44 sm:flex-none"
          align="left"
        />
      </div>
      <div className="flex min-w-0 flex-1 items-center gap-1.5 sm:flex-none">
        <span className="hidden text-xs font-medium text-zinc-500 xl:inline">Model</span>
        <Listbox
          ariaLabel="Model"
          value={model}
          options={modelOptions}
          onChange={(value) => provider && selectModel(provider.id, value)}
          disabled={!provider?.available}
          placeholder={modelsLoading ? 'Loading…' : 'Default'}
          className="min-w-0 flex-1 sm:w-52 sm:flex-none"
          align="right"
        />
      </div>
      {provider && (
        <span
          className="hidden items-center text-zinc-400 md:inline-flex"
          title={provider.kind === 'LOCAL' ? 'Runs locally' : 'Cloud provider'}
        >
          {provider.kind === 'LOCAL' ? <Cpu className="h-4 w-4" /> : <Cloud className="h-4 w-4" />}
        </span>
      )}
    </div>
  )
}
