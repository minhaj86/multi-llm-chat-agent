import { Code2, Lightbulb, Network, ShieldCheck } from 'lucide-react'
import { ProviderStatusList } from '../providers/ProviderStatusList'

const SUGGESTIONS = [
  { icon: Code2, title: 'Explain Java virtual threads', prompt: 'Explain Java 21 virtual threads with a short example and when not to use them.' },
  { icon: Network, title: 'Design a REST API', prompt: 'Design a REST API for a library management system. List resources, endpoints and status codes.' },
  { icon: ShieldCheck, title: 'Review code for security issues', prompt: 'What are the most common security issues to look for when reviewing a Spring Boot REST controller?' },
  { icon: Lightbulb, title: 'Compare Kafka and RabbitMQ', prompt: 'Compare Kafka and RabbitMQ in a table: delivery guarantees, ordering, throughput and typical use cases.' },
]

export function WelcomeScreen({ onPick }: { onPick: (prompt: string) => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-4 py-8">
      <div className="w-full max-w-2xl text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-cyan-500 text-xl font-bold text-white shadow-lg">
          A
        </div>
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">How can I help you today?</h1>
        <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
          Pick any provider and model above — you can switch at any time, even in the middle of a conversation.
        </p>
        <div className="mt-8 grid gap-3 text-left sm:grid-cols-2">
          {SUGGESTIONS.map(({ icon: Icon, title, prompt }) => (
            <button
              key={title}
              type="button"
              onClick={() => onPick(prompt)}
              className="group rounded-2xl border border-zinc-200 p-4 transition hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:border-zinc-700 dark:hover:bg-zinc-900"
            >
              <Icon className="mb-2 h-5 w-5 text-indigo-500" aria-hidden="true" />
              <div className="text-sm font-medium">{title}</div>
              <div className="mt-1 line-clamp-2 text-xs text-zinc-500 dark:text-zinc-400">{prompt}</div>
            </button>
          ))}
        </div>
        <div className="mx-auto mt-8 max-w-xs rounded-2xl border border-zinc-200 p-4 text-left dark:border-zinc-800">
          <ProviderStatusList />
        </div>
      </div>
    </div>
  )
}
