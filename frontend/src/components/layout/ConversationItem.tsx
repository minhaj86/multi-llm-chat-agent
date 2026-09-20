import { Check, MessageSquare, Pencil, Trash2, X } from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, useNavigate } from 'react-router'
import type { Conversation } from '../../api/types'
import { useDeleteConversation, useRenameConversation } from '../../hooks/useConversations'
import { cn } from '../../lib/format'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'

export function ConversationItem({ conversation, activeId, onNavigate }: {
  conversation: Conversation
  activeId: string | undefined
  onNavigate: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(conversation.title)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const rename = useRenameConversation()
  const remove = useDeleteConversation()
  const navigate = useNavigate()
  const active = conversation.id === activeId

  useEffect(() => {
    if (editing) {
      inputRef.current?.select()
    }
  }, [editing])

  const save = () => {
    const value = title.trim()
    setEditing(false)
    if (value && value !== conversation.title) {
      rename.mutate({ id: conversation.id, title: value })
    } else {
      setTitle(conversation.title)
    }
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      save()
    } else if (event.key === 'Escape') {
      setTitle(conversation.title)
      setEditing(false)
    }
  }

  const deleteConversation = () => {
    remove.mutate(conversation.id, {
      onSuccess: () => {
        setConfirmDelete(false)
        if (active) {
          navigate('/')
        }
      },
    })
  }

  if (editing) {
    return (
      <div className="flex items-center gap-1 rounded-lg bg-zinc-200/70 px-2 py-1 dark:bg-zinc-800">
        <input
          ref={inputRef}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={save}
          maxLength={200}
          aria-label="Conversation title"
          className="min-w-0 flex-1 bg-transparent py-1 text-sm outline-none"
        />
        <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={save} aria-label="Save title" className="p-1 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100">
          <Check className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => {
            setTitle(conversation.title)
            setEditing(false)
          }}
          aria-label="Cancel rename"
          className="p-1 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    )
  }

  return (
    <>
      <div className={cn('group relative flex items-center rounded-lg', active ? 'bg-zinc-200/80 dark:bg-zinc-800' : 'hover:bg-zinc-200/50 dark:hover:bg-zinc-800/60')}>
        <NavLink
          to={`/c/${conversation.id}`}
          onClick={onNavigate}
          className="flex min-w-0 flex-1 items-center gap-2 px-2.5 py-2 text-sm"
          title={conversation.title}
        >
          <MessageSquare className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden="true" />
          <span className="truncate">{conversation.title}</span>
        </NavLink>
        <div className={cn('absolute right-1 flex items-center gap-0.5 rounded-md bg-inherit pl-2 opacity-0 transition group-hover:opacity-100 focus-within:opacity-100', active && 'opacity-100')}>
          <button
            type="button"
            onClick={() => {
              setTitle(conversation.title)
              setEditing(true)
            }}
            className="rounded p-1 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
            aria-label={`Rename ${conversation.title}`}
          >
            <Pencil className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            onClick={() => setConfirmDelete(true)}
            className="rounded p-1 text-zinc-500 hover:text-red-600"
            aria-label={`Delete ${conversation.title}`}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete conversation?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={deleteConversation}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          “{conversation.title}” and all of its messages will be permanently deleted.
        </p>
      </Modal>
    </>
  )
}
