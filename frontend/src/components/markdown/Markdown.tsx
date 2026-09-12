import { memo } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import remarkGfm from 'remark-gfm'
import { cn } from '../../lib/format'
import { CodeBlock } from './CodeBlock'

const components: Components = {
  pre: ({ children }) => <CodeBlock>{children}</CodeBlock>,
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
  table: ({ children }) => (
    <div className="overflow-x-auto">
      <table>{children}</table>
    </div>
  ),
}

const remarkPlugins = [remarkGfm]
const rehypePlugins = [rehypeHighlight]

/**
 * Renders model output as GitHub-flavoured Markdown. Raw HTML in model output is never rendered (react-markdown
 * escapes it), which prevents script injection through prompts.
 */
export const Markdown = memo(function Markdown({ content, streaming = false }: { content: string; streaming?: boolean }) {
  return (
    <div className={cn('markdown', streaming && 'streaming-caret')}>
      <ReactMarkdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  )
})
