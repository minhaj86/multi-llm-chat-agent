import { useProviders } from '../../hooks/useProviders'
import { StatusDot, statusLabel } from '../ui/StatusDot'

/** Compact live status of all providers ("● OpenAI Online / ○ Llama Local Offline"). */
export function ProviderStatusList() {
  const { data: providers = [] } = useProviders()
  if (providers.length === 0) {
    return null
  }
  return (
    <ul className="space-y-1.5" aria-label="Provider status">
      {providers.map((p) => (
        <li key={p.id} className="flex items-center gap-2 text-xs" title={p.statusMessage ?? undefined}>
          <StatusDot status={p.status} enabled={p.enabled} />
          <span className="min-w-0 flex-1 truncate text-zinc-700 dark:text-zinc-300">{p.name}</span>
          <span className={p.available ? 'text-emerald-600 dark:text-emerald-400' : 'text-zinc-500'}>
            {statusLabel(p.status, p.enabled)}
          </span>
        </li>
      ))}
    </ul>
  )
}
