import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, RefreshCw, RotateCcw, Save } from 'lucide-react'
import { useEffect, useState } from 'react'
import { adminApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { AdminProvider, ProviderSettingsUpdate } from '../../api/types'
import { formatRelative } from '../../lib/format'
import { Button } from '../ui/Button'
import { Badge, Card, Field, Input, Toggle } from '../ui/Form'
import { Spinner } from '../ui/Spinner'
import { StatusDot, statusLabel } from '../ui/StatusDot'

export function ProvidersTab() {
  const providers = useQuery({ queryKey: ['admin-providers'], queryFn: adminApi.providers, refetchInterval: 30_000 })
  if (providers.isLoading) {
    return (
      <div className="flex justify-center py-16 text-zinc-400">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }
  if (providers.error) {
    return <p className="text-sm text-red-600">{errorMessage(providers.error)}</p>
  }
  return (
    <div className="space-y-4">
      <p className="text-sm text-zinc-500">
        Endpoints and credentials come from server configuration (environment variables). The settings below apply
        immediately, without restarting anything. API keys are never shown in plain text.
      </p>
      {(providers.data ?? []).map((provider) => (
        <ProviderCard key={provider.id} provider={provider} />
      ))}
    </div>
  )
}

function toForm(provider: AdminProvider) {
  return {
    enabled: provider.enabled,
    defaultModel: provider.defaultModel ?? '',
    timeoutSeconds: provider.timeoutSeconds?.toString() ?? '',
    temperature: provider.temperature?.toString() ?? '',
    maxTokens: provider.maxTokens?.toString() ?? '',
  }
}

function ProviderCard({ provider }: { provider: AdminProvider }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(() => toForm(provider))
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    setForm(toForm(provider))
  }, [provider])

  const onSaved = (updated: AdminProvider) => {
    queryClient.setQueryData<AdminProvider[]>(['admin-providers'], (old) => old?.map((p) => (p.id === updated.id ? updated : p)))
    void queryClient.invalidateQueries({ queryKey: ['providers'] })
    void queryClient.invalidateQueries({ queryKey: ['models', updated.id] })
  }

  const save = useMutation({
    mutationFn: () => {
      const settings: ProviderSettingsUpdate = {
        enabled: form.enabled,
        defaultModel: form.defaultModel.trim() || null,
        timeoutSeconds: form.timeoutSeconds ? Number(form.timeoutSeconds) : null,
        temperature: form.temperature ? Number(form.temperature) : null,
        maxTokens: form.maxTokens ? Number(form.maxTokens) : null,
      }
      return adminApi.updateProvider(provider.id, settings)
    },
    onSuccess: (updated) => {
      onSaved(updated)
      setMessage('Saved')
    },
    onError: (error) => setMessage(errorMessage(error)),
  })
  const reset = useMutation({
    mutationFn: () => adminApi.resetProvider(provider.id),
    onSuccess: (updated) => {
      onSaved(updated)
      setMessage('Restored configured defaults')
    },
    onError: (error) => setMessage(errorMessage(error)),
  })
  const check = useMutation({
    mutationFn: () => adminApi.checkProvider(provider.id),
    onSuccess: onSaved,
    onError: (error) => setMessage(errorMessage(error)),
  })

  const health = provider.health
  const defaults = provider.configuredDefaults

  return (
    <Card className="space-y-4" >
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <StatusDot status={health.status} enabled={provider.enabled} />
            <h3 className="text-base font-semibold">{provider.name}</h3>
            <Badge>{provider.id}</Badge>
            <Badge tone="info">{provider.type}</Badge>
            <Badge>{provider.kind === 'LOCAL' ? 'Local' : 'Cloud'}</Badge>
            {provider.overridden && <Badge tone="warning">Overridden</Badge>}
          </div>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            <span className={health.available ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}>
              {statusLabel(health.status, provider.enabled)}
            </span>
            {health.message && ` — ${health.message}`}
            {health.latencyMs !== null && ` · ${health.latencyMs} ms`}
            {health.checkedAt && ` · checked ${formatRelative(health.checkedAt)}`}
          </p>
        </div>
        <Button size="sm" variant="outline" loading={check.isPending} onClick={() => check.mutate()}>
          <RefreshCw className="h-3.5 w-3.5" /> Check now
        </Button>
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs text-zinc-500">Base URL</dt>
          <dd className="truncate font-mono text-xs" title={provider.baseUrl}>{provider.baseUrl}</dd>
        </div>
        <div>
          <dt className="text-xs text-zinc-500">API key</dt>
          <dd className="flex items-center gap-1.5 font-mono text-xs">
            <KeyRound className="h-3.5 w-3.5 text-zinc-400" />
            {provider.apiKeyConfigured ? provider.apiKeyHint : <span className="text-amber-600">not configured</span>}
          </dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs text-zinc-500">Available models</dt>
          <dd className="mt-1 flex max-h-24 flex-wrap gap-1 overflow-y-auto">
            {provider.availableModels.length === 0 ? (
              <span className="text-xs text-zinc-500">Unknown (provider offline or not checked yet)</span>
            ) : (
              provider.availableModels.map((model) => <Badge key={model}>{model}</Badge>)
            )}
          </dd>
        </div>
      </dl>

      <form
        className="grid gap-4 border-t border-zinc-200 pt-4 sm:grid-cols-2 lg:grid-cols-5 dark:border-zinc-800"
        onSubmit={(event) => {
          event.preventDefault()
          setMessage(null)
          save.mutate()
        }}
      >
        <div className="flex flex-col justify-between gap-2">
          <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">Enabled</span>
          <Toggle checked={form.enabled} onChange={(enabled) => setForm({ ...form, enabled })} label={`${provider.name} enabled`} />
        </div>
        <Field label="Default model" htmlFor={`${provider.id}-model`} hint={`Configured: ${defaults.defaultModel ?? '—'}`}>
          <Input
            id={`${provider.id}-model`}
            list={`${provider.id}-models`}
            value={form.defaultModel}
            onChange={(event) => setForm({ ...form, defaultModel: event.target.value })}
          />
          <datalist id={`${provider.id}-models`}>
            {provider.availableModels.map((model) => (
              <option key={model} value={model} />
            ))}
          </datalist>
        </Field>
        <Field label="Timeout (s)" htmlFor={`${provider.id}-timeout`} hint={`Configured: ${defaults.timeoutSeconds ?? '—'}`}>
          <Input id={`${provider.id}-timeout`} type="number" min={1} max={3600} value={form.timeoutSeconds} onChange={(event) => setForm({ ...form, timeoutSeconds: event.target.value })} />
        </Field>
        <Field label="Temperature" htmlFor={`${provider.id}-temperature`} hint={`Configured: ${defaults.temperature ?? 'model default'}`}>
          <Input id={`${provider.id}-temperature`} type="number" min={0} max={2} step={0.1} value={form.temperature} placeholder="default" onChange={(event) => setForm({ ...form, temperature: event.target.value })} />
        </Field>
        <Field label="Max tokens" htmlFor={`${provider.id}-max-tokens`} hint={`Configured: ${defaults.maxTokens ?? '—'}`}>
          <Input id={`${provider.id}-max-tokens`} type="number" min={1} value={form.maxTokens} onChange={(event) => setForm({ ...form, maxTokens: event.target.value })} />
        </Field>
        <div className="flex flex-wrap items-center gap-2 sm:col-span-2 lg:col-span-5">
          <Button type="submit" variant="primary" size="sm" loading={save.isPending}>
            <Save className="h-3.5 w-3.5" /> Save
          </Button>
          <Button size="sm" variant="ghost" loading={reset.isPending} disabled={!provider.overridden} onClick={() => reset.mutate()}>
            <RotateCcw className="h-3.5 w-3.5" /> Reset to configured defaults
          </Button>
          {message && <span className="text-xs text-zinc-500" role="status">{message}</span>}
          {provider.updatedAt && (
            <span className="ml-auto text-xs text-zinc-400">
              Last changed by {provider.updatedBy ?? 'unknown'} {formatRelative(provider.updatedAt)}
            </span>
          )}
        </div>
      </form>
    </Card>
  )
}
