import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { knowledgeApi, type KnowledgeQuery } from '../api/client'
import { isProcessing } from '../components/knowledge/knowledgeFormat'

/** Polls while documents are being indexed, so progress appears without reloading. */
const PROCESSING_POLL_MS = 2000

export function useKnowledgeCollections() {
  return useQuery({ queryKey: ['knowledge', 'collections'], queryFn: knowledgeApi.collections })
}

export function useKnowledgeStats() {
  return useQuery({
    queryKey: ['knowledge', 'stats'],
    queryFn: knowledgeApi.stats,
    refetchInterval: (query) => (query.state.data && query.state.data.totals.processing > 0 ? PROCESSING_POLL_MS : false),
  })
}

export function useKnowledgeSources(query: KnowledgeQuery) {
  return useQuery({
    queryKey: ['knowledge', 'sources', query],
    queryFn: () => knowledgeApi.list(query),
    placeholderData: (previous) => previous,
    refetchInterval: (q) =>
      q.state.data?.items.some((s) => isProcessing(s.status) || s.status === 'DELETING') ? PROCESSING_POLL_MS : false,
  })
}

export function useKnowledgeSource(id: string | undefined) {
  return useQuery({
    queryKey: ['knowledge', 'source', id],
    queryFn: () => knowledgeApi.get(id as string),
    enabled: !!id,
    retry: false,
    refetchInterval: (q) =>
      q.state.data && (isProcessing(q.state.data.source.status) || q.state.data.workerActive) ? PROCESSING_POLL_MS : false,
  })
}

/** Refreshes every knowledge-base query (lists, details, stats, collections). */
export function useRefreshKnowledge() {
  const queryClient = useQueryClient()
  return useCallback(() => queryClient.invalidateQueries({ queryKey: ['knowledge'] }), [queryClient])
}
