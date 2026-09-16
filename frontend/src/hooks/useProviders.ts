import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { providerApi } from '../api/client'
import type { Model, Provider } from '../api/types'
import { usePreferences } from '../store/preferences'

export function useProviders() {
  return useQuery({
    queryKey: ['providers'],
    queryFn: providerApi.list,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    staleTime: 10_000,
  })
}

export function useModels(providerId: string | null | undefined, enabled = true) {
  return useQuery({
    queryKey: ['models', providerId],
    queryFn: () => providerApi.models(providerId as string),
    enabled: !!providerId && enabled,
    staleTime: 60_000,
  })
}

/** Picks a usable provider: the remembered one if it still exists, else the server default, else the first available. */
export function resolveProvider(providers: Provider[] | undefined, preferredId: string | null): Provider | null {
  if (!providers || providers.length === 0) {
    return null
  }
  const preferred = providers.find((p) => p.id === preferredId)
  if (preferred) {
    return preferred
  }
  return (
    providers.find((p) => p.defaultProvider && p.available) ??
    providers.find((p) => p.available) ??
    providers.find((p) => p.defaultProvider) ??
    providers[0]
  )
}

export function resolveModel(models: Model[] | undefined, provider: Provider | null, preferred: string | undefined): string | null {
  if (preferred && (!models || models.some((m) => m.id === preferred))) {
    return preferred
  }
  return models?.find((m) => m.isDefault)?.id ?? provider?.defaultModel ?? models?.[0]?.id ?? null
}

/** Current provider/model selection, always derived from what the backend reports (nothing is hard-coded). */
export function useProviderSelection() {
  const providersQuery = useProviders()
  const preferredProvider = usePreferences((s) => s.providerId)
  const preferredModels = usePreferences((s) => s.models)
  const selectProvider = usePreferences((s) => s.selectProvider)
  const selectModel = usePreferences((s) => s.selectModel)

  const provider = useMemo(
    () => resolveProvider(providersQuery.data, preferredProvider),
    [providersQuery.data, preferredProvider],
  )
  const modelsQuery = useModels(provider?.id, provider?.enabled ?? false)
  const model = useMemo(
    () => resolveModel(modelsQuery.data, provider, provider ? preferredModels[provider.id] : undefined),
    [modelsQuery.data, provider, preferredModels],
  )

  return {
    providers: providersQuery.data ?? [],
    providersLoading: providersQuery.isLoading,
    providersError: providersQuery.error,
    provider,
    models: modelsQuery.data ?? [],
    modelsLoading: modelsQuery.isLoading,
    model,
    selectProvider,
    selectModel,
  }
}
