import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { SystemPrompt, SystemPromptInput } from '../../api/types'
import { Button } from '../ui/Button'
import { Badge, Card, Field, Input, TextArea, Toggle } from '../ui/Form'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'

const EMPTY: SystemPromptInput = { name: '', description: '', content: '', isDefault: false, enabled: true, sortOrder: 100 }

export function PromptsTab() {
  const queryClient = useQueryClient()
  const prompts = useQuery({ queryKey: ['admin-prompts'], queryFn: adminApi.prompts })
  const [editing, setEditing] = useState<{ id: string | null; input: SystemPromptInput } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-prompts'] })
    void queryClient.invalidateQueries({ queryKey: ['system-prompts'] })
  }

  const save = useMutation({
    mutationFn: ({ id, input }: { id: string | null; input: SystemPromptInput }) =>
      id ? adminApi.updatePrompt(id, input) : adminApi.createPrompt(input),
    onSuccess: () => {
      setEditing(null)
      setError(null)
      refresh()
    },
    onError: (e) => setError(errorMessage(e)),
  })

  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deletePrompt(id),
    onSuccess: refresh,
    onError: (e) => setError(errorMessage(e)),
  })

  if (prompts.isLoading) {
    return (
      <div className="flex justify-center py-16 text-zinc-400">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }

  const startEdit = (prompt: SystemPrompt) =>
    setEditing({
      id: prompt.id,
      input: {
        name: prompt.name,
        description: prompt.description ?? '',
        content: prompt.content,
        isDefault: prompt.isDefault,
        enabled: prompt.enabled,
        sortOrder: prompt.sortOrder,
      },
    })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (editing) {
      save.mutate({ id: editing.id, input: { ...editing.input, description: editing.input.description || null } })
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-zinc-500">System prompt profiles users can choose per conversation.</p>
        <Button variant="primary" size="sm" onClick={() => setEditing({ id: null, input: EMPTY })}>
          <Plus className="h-4 w-4" /> New profile
        </Button>
      </div>
      {error && !editing && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {error}
        </p>
      )}
      <div className="grid gap-3 md:grid-cols-2">
        {(prompts.data ?? []).map((prompt) => (
          <Card key={prompt.id} className="flex flex-col gap-2">
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-semibold">{prompt.name}</h3>
                  {prompt.isDefault && <Badge tone="info">Default</Badge>}
                  {!prompt.enabled && <Badge tone="warning">Disabled</Badge>}
                </div>
                {prompt.description && <p className="text-xs text-zinc-500">{prompt.description}</p>}
              </div>
              <Button size="icon-sm" variant="ghost" aria-label={`Edit ${prompt.name}`} onClick={() => startEdit(prompt)}>
                <Pencil className="h-4 w-4" />
              </Button>
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label={`Delete ${prompt.name}`}
                className="hover:text-red-600"
                onClick={() => window.confirm(`Delete “${prompt.name}”?`) && remove.mutate(prompt.id)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
            <p className="line-clamp-4 whitespace-pre-wrap rounded-lg bg-zinc-50 p-2 font-mono text-xs text-zinc-600 dark:bg-zinc-800/60 dark:text-zinc-400">
              {prompt.content}
            </p>
          </Card>
        ))}
      </div>

      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={editing?.id ? 'Edit profile' : 'New profile'}
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" form="prompt-form" loading={save.isPending}>
              Save
            </Button>
          </>
        }
      >
        {editing && (
          <form id="prompt-form" onSubmit={submit} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
              <Field label="Name" htmlFor="prompt-name">
                <Input id="prompt-name" value={editing.input.name} maxLength={100} onChange={(e) => setEditing({ ...editing, input: { ...editing.input, name: e.target.value } })} required />
              </Field>
              <Field label="Sort order" htmlFor="prompt-order">
                <Input id="prompt-order" type="number" value={editing.input.sortOrder} onChange={(e) => setEditing({ ...editing, input: { ...editing.input, sortOrder: Number(e.target.value) } })} />
              </Field>
            </div>
            <Field label="Description" htmlFor="prompt-description">
              <Input id="prompt-description" value={editing.input.description ?? ''} maxLength={500} onChange={(e) => setEditing({ ...editing, input: { ...editing.input, description: e.target.value } })} />
            </Field>
            <Field label="Prompt" htmlFor="prompt-content">
              <TextArea id="prompt-content" rows={10} className="font-mono text-xs" value={editing.input.content} maxLength={20000} onChange={(e) => setEditing({ ...editing, input: { ...editing.input, content: e.target.value } })} required />
            </Field>
            <div className="flex flex-wrap gap-6">
              <label className="flex items-center gap-2 text-sm">
                <Toggle checked={editing.input.enabled} onChange={(enabled) => setEditing({ ...editing, input: { ...editing.input, enabled } })} label="Enabled" /> Enabled
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Toggle checked={editing.input.isDefault} onChange={(isDefault) => setEditing({ ...editing, input: { ...editing.input, isDefault } })} label="Default profile" /> Default for new chats
              </label>
            </div>
            {error && (
              <p className="text-sm text-red-600 dark:text-red-400" role="alert">
                {error}
              </p>
            )}
          </form>
        )}
      </Modal>
    </div>
  )
}
