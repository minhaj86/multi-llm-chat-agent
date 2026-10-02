import { useQuery } from '@tanstack/react-query'
import { Activity, AlertTriangle, Clock, Coins, Cpu, MessageSquare, Server, Zap } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { adminApi } from '../../api/client'
import { errorMessage } from '../../api/http'
import type { UsageDashboard } from '../../api/types'
import { cn, formatCost, formatLatency, formatPercent, formatTokens } from '../../lib/format'
import { Card } from '../ui/Form'
import { Spinner } from '../ui/Spinner'

const PALETTE = ['#6366f1', '#06b6d4', '#f59e0b', '#10b981', '#ef4444', '#a855f7', '#ec4899', '#84cc16']
const RANGES = [7, 30, 90]

function Kpi({ icon, label, value, hint }: { icon: ReactNode; label: string; value: string; hint?: string }) {
  return (
    <Card className="flex items-start gap-3">
      <div className="rounded-xl bg-indigo-50 p-2 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-300">{icon}</div>
      <div className="min-w-0">
        <div className="text-xs text-zinc-500 dark:text-zinc-400">{label}</div>
        <div className="text-xl font-semibold tabular-nums">{value}</div>
        {hint && <div className="truncate text-xs text-zinc-500">{hint}</div>}
      </div>
    </Card>
  )
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <h3 className="mb-3 text-sm font-medium">{title}</h3>
      <div className="h-64">{children}</div>
    </Card>
  )
}

export function pivotDaily(dashboard: UsageDashboard): { days: Record<string, string | number>[]; providers: string[] } {
  const providers = Array.from(new Set(dashboard.daily.map((d) => d.provider))).sort()
  const byDay = new Map<string, Record<string, string | number>>()
  for (const row of dashboard.daily) {
    const entry = byDay.get(row.day) ?? { day: row.day }
    entry[row.provider] = row.requests
    byDay.set(row.day, entry)
  }
  return { days: Array.from(byDay.values()).sort((a, b) => String(a.day).localeCompare(String(b.day))), providers }
}

export function UsageTab() {
  const [days, setDays] = useState(7)
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
  const usage = useQuery({ queryKey: ['admin-usage', days, timeZone], queryFn: () => adminApi.usage(days, timeZone), refetchInterval: 30_000 })

  const daily = useMemo(() => (usage.data ? pivotDaily(usage.data) : { days: [], providers: [] }), [usage.data])

  if (usage.isLoading) {
    return (
      <div className="flex justify-center py-16 text-zinc-400">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }
  if (usage.error || !usage.data) {
    return <p className="text-sm text-red-600">{errorMessage(usage.error)}</p>
  }

  const { today, period, byProvider } = usage.data
  const tokensByProvider = byProvider.map((p) => ({ provider: p.provider, input: p.inputTokens, output: p.outputTokens }))
  const localVsCloud = [
    { name: 'Local', value: period.localRequests },
    { name: 'Cloud', value: period.cloudRequests },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <p className="text-sm text-zinc-500">
          Times in {usage.data.timeZone}. Includes chat, compare, title and summary calls.
        </p>
        <div className="inline-flex rounded-lg border border-zinc-200 p-0.5 dark:border-zinc-700" role="group" aria-label="Time range">
          {RANGES.map((range) => (
            <button
              key={range}
              type="button"
              aria-pressed={days === range}
              onClick={() => setDays(range)}
              className={cn('rounded-md px-3 py-1 text-xs font-medium', days === range ? 'bg-zinc-900 text-white dark:bg-white dark:text-zinc-900' : 'text-zinc-600 dark:text-zinc-300')}
            >
              {range} days
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={<MessageSquare className="h-5 w-5" />} label="Requests today" value={today.requests.toLocaleString()} hint={`${period.requests.toLocaleString()} in the last ${days} days`} />
        <Kpi icon={<AlertTriangle className="h-5 w-5" />} label="Error rate" value={formatPercent(period.errorRate)} hint={`${period.errors} errors · ${period.cancelled} stopped`} />
        <Kpi icon={<Zap className="h-5 w-5" />} label="Tokens" value={formatTokens(period.inputTokens + period.outputTokens)} hint={`${formatTokens(period.inputTokens)} in · ${formatTokens(period.outputTokens)} out`} />
        <Kpi icon={<Coins className="h-5 w-5" />} label="Cloud cost estimate" value={formatCost(period.cloudCost, false)} hint={`Today ${formatCost(today.cloudCost, false)}`} />
        <Kpi icon={<Clock className="h-5 w-5" />} label="Average latency" value={formatLatency(period.averageLatencyMs)} hint="Successful requests" />
        <Kpi icon={<Cpu className="h-5 w-5" />} label="Local requests" value={period.localRequests.toLocaleString()} hint={period.requests ? formatPercent(period.localRequests / period.requests) : '—'} />
        <Kpi icon={<Server className="h-5 w-5" />} label="Cloud requests" value={period.cloudRequests.toLocaleString()} hint={period.requests ? formatPercent(period.cloudRequests / period.requests) : '—'} />
        <Kpi icon={<Activity className="h-5 w-5" />} label="Providers used" value={String(byProvider.length)} hint={byProvider.map((p) => p.provider).join(', ') || '—'} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Requests per day">
          <ResponsiveContainer>
            <BarChart data={daily.days}>
              <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
              <XAxis dataKey="day" fontSize={11} />
              <YAxis allowDecimals={false} fontSize={11} width={36} />
              <Tooltip />
              <Legend />
              {daily.providers.map((provider, index) => (
                <Bar key={provider} dataKey={provider} stackId="requests" fill={PALETTE[index % PALETTE.length]} />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Token consumption by provider">
          <ResponsiveContainer>
            <BarChart data={tokensByProvider}>
              <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
              <XAxis dataKey="provider" fontSize={11} />
              <YAxis fontSize={11} width={48} tickFormatter={(v: number) => formatTokens(v)} />
              <Tooltip />
              <Legend />
              <Bar dataKey="input" name="Input tokens" stackId="tokens" fill={PALETTE[0]} />
              <Bar dataKey="output" name="Output tokens" stackId="tokens" fill={PALETTE[1]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Requests by provider">
          <ResponsiveContainer>
            <BarChart data={byProvider} layout="vertical" margin={{ left: 16 }}>
              <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
              <XAxis type="number" allowDecimals={false} fontSize={11} />
              <YAxis type="category" dataKey="provider" fontSize={11} width={70} />
              <Tooltip />
              <Bar dataKey="requests" name="Requests" fill={PALETTE[2]} />
              <Bar dataKey="errors" name="Errors" fill={PALETTE[4]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Local vs cloud">
          {localVsCloud.length === 0 ? (
            <p className="flex h-full items-center justify-center text-sm text-zinc-500">No requests yet</p>
          ) : (
            <ResponsiveContainer>
              <PieChart>
                <Pie data={localVsCloud} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2} label>
                  {localVsCloud.map((entry, index) => (
                    <Cell key={entry.name} fill={PALETTE[index === 0 ? 3 : 0]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      <Card className="overflow-x-auto p-0">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-zinc-200 text-xs text-zinc-500 uppercase dark:border-zinc-800">
            <tr>
              <th className="px-4 py-3 font-medium">Provider</th>
              <th className="px-4 py-3 font-medium">Requests</th>
              <th className="px-4 py-3 font-medium">Error rate</th>
              <th className="px-4 py-3 font-medium">Input tokens</th>
              <th className="px-4 py-3 font-medium">Output tokens</th>
              <th className="px-4 py-3 font-medium">Avg latency</th>
              <th className="px-4 py-3 font-medium">Cost</th>
            </tr>
          </thead>
          <tbody>
            {byProvider.map((p) => (
              <tr key={p.provider} className="border-b border-zinc-100 last:border-0 dark:border-zinc-800/60">
                <td className="px-4 py-3 font-medium">
                  {p.provider} <span className="text-xs text-zinc-500">{p.providerKind === 'LOCAL' ? 'local' : 'cloud'}</span>
                </td>
                <td className="px-4 py-3 tabular-nums">{p.requests.toLocaleString()}</td>
                <td className="px-4 py-3 tabular-nums">{formatPercent(p.requests ? p.errors / p.requests : 0)}</td>
                <td className="px-4 py-3 tabular-nums">{formatTokens(p.inputTokens)}</td>
                <td className="px-4 py-3 tabular-nums">{formatTokens(p.outputTokens)}</td>
                <td className="px-4 py-3 tabular-nums">{formatLatency(p.averageLatencyMs)}</td>
                <td className="px-4 py-3 tabular-nums">{formatCost(p.cost, p.providerKind === 'LOCAL')}</td>
              </tr>
            ))}
            {byProvider.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-zinc-500">
                  No usage recorded in this period.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>
    </div>
  )
}
