import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { conversationApi, promptApi } from '../api/client'
import type { Conversation } from '../api/types'

export function useConversations() {
  return useQuery({ queryKey: ['conversations'], queryFn: conversationApi.list, staleTime: 15_000 })
}

export function useConversation(id: string | undefined) {
  return useQuery({
    queryKey: ['conversation', id],
    queryFn: () => conversationApi.get(id as string),
    enabled: !!id,
    retry: (count, error) => (error as { status?: number }).status !== 404 && count < 2,
  })
}

export function useSystemPrompts() {
  return useQuery({ queryKey: ['system-prompts'], queryFn: promptApi.list, staleTime: 300_000 })
}

export function useRenameConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => conversationApi.rename(id, title),
    onSuccess: (updated) => {
      queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
        old?.map((c) => (c.id === updated.id ? updated : c)),
      )
      queryClient.invalidateQueries({ queryKey: ['conversation', updated.id] })
    },
  })
}

export function useDeleteConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => conversationApi.remove(id),
    onSuccess: (_, id) => {
      queryClient.setQueryData<Conversation[]>(['conversations'], (old) => old?.filter((c) => c.id !== id))
      queryClient.removeQueries({ queryKey: ['conversation', id] })
    },
  })
}
