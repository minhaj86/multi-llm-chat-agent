import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Markdown } from './Markdown'

describe('Markdown', () => {
  it('renders GitHub flavoured markdown with highlighted, copyable code blocks', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    render(<Markdown content={'# Title\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n```java\nint x = 42;\n```'} />)

    expect(screen.getByRole('heading', { name: 'Title' })).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByText('java')).toBeInTheDocument()
    expect(document.querySelector('code.hljs .hljs-type, code.hljs .hljs-keyword')).not.toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Copy code' }))
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('int x = 42;\n'))
    expect(await screen.findByRole('button', { name: 'Code copied' })).toBeInTheDocument()
  })

  it('never renders raw HTML from model output', () => {
    const { container } = render(<Markdown content={'<img src=x onerror="alert(1)"> <script>alert(1)</script>'} />)
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
  })

  it('shows a streaming caret while tokens arrive', () => {
    const { container } = render(<Markdown content="Partial answer" streaming />)
    expect(container.querySelector('.streaming-caret')).not.toBeNull()
  })
})
