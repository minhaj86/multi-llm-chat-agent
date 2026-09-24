import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErrorNotice } from './ErrorNotice'

describe('ErrorNotice', () => {
  it('explains the failure and offers one-click alternatives', () => {
    const onSwitch = vi.fn()
    const onRetry = vi.fn()
    render(
      <ErrorNotice
        message="Qwen Local is currently unavailable."
        alternatives={[
          { id: 'openai', name: 'OpenAI' },
          { id: 'llama', name: 'Llama Local' },
        ]}
        onSwitch={onSwitch}
        onRetry={onRetry}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Qwen Local is currently unavailable.')
    expect(screen.getByText('Switch to:')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Llama Local' }))
    expect(onSwitch).toHaveBeenCalledWith('llama')
    fireEvent.click(screen.getByRole('button', { name: /Retry/ }))
    expect(onRetry).toHaveBeenCalled()
  })

  it('hides the switch section when no alternative is available', () => {
    render(<ErrorNotice message="All providers are down." alternatives={[]} onSwitch={vi.fn()} />)
    expect(screen.queryByText('Switch to:')).not.toBeInTheDocument()
  })
})
