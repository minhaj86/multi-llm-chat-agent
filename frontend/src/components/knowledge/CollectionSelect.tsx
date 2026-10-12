import { useKnowledgeCollections } from '../../hooks/useKnowledge'
import { Listbox } from '../ui/Listbox'

/** Single collection picker; defaults to the default collection when nothing is selected. */
export function CollectionSelect({
  value,
  onChange,
  ariaLabel = 'Collection',
  allowAll = false,
  className,
  buttonClassName = 'h-10',
}: {
  value: string | null
  onChange: (id: string | null) => void
  ariaLabel?: string
  allowAll?: boolean
  className?: string
  /** Defaults to the height of text inputs, next to which the picker is usually placed. */
  buttonClassName?: string
}) {
  const { data: collections = [] } = useKnowledgeCollections()
  const fallback = allowAll ? '__all__' : (collections.find((c) => c.defaultCollection)?.id ?? collections[0]?.id ?? null)
  const options = [
    ...(allowAll ? [{ value: '__all__', label: 'All collections' }] : []),
    ...collections.map((c) => ({
      value: c.id,
      label: c.enabled ? c.name : `${c.name} (disabled)`,
      description: c.description,
    })),
  ]
  return (
    <Listbox
      ariaLabel={ariaLabel}
      value={value ?? fallback}
      options={options}
      onChange={(id) => onChange(id === '__all__' ? null : id)}
      className={className}
      buttonClassName={buttonClassName}
    />
  )
}
