import { JobExecution } from '../api/jobsApi'
import StatusBadge from './StatusBadge'

function duration(startedAt: string, finishedAt?: string) {
  if (!finishedAt) {
    return 'In progress'
  }

  const milliseconds = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  if (milliseconds < 1000) {
    return `${milliseconds}ms`
  }

  return `${(milliseconds / 1000).toFixed(2)}s`
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

interface Props {
  executions: JobExecution[]
}

export default function ExecutionTimeline({ executions }: Props) {
  if (executions.length === 0) {
    return <p className="text-sm text-[color:var(--muted)]">No executions yet.</p>
  }

  return (
    <ol className="space-y-8">
      {executions.map((execution) => (
        <li key={execution.id} className="timeline-item space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm font-semibold tracking-[-0.04em] text-[color:var(--text-strong)]">
              Run #{execution.runNumber} · Attempt #{execution.attempt}
            </p>
            <StatusBadge status={execution.status} />
            <p className="text-xs text-[color:var(--muted)]">{duration(execution.startedAt, execution.finishedAt)}</p>
          </div>

          <div className="grid gap-3 text-sm text-[color:var(--muted)] md:grid-cols-2">
            <div>
              <p className="section-kicker">Worker</p>
              <p className="mt-2 font-mono-ui text-xs leading-6 text-[color:var(--text-strong)]">
                {execution.workerId || '—'}
              </p>
            </div>
            <div>
              <p className="section-kicker">Started</p>
              <p className="mt-2 text-sm text-[color:var(--text-strong)]">
                {new Date(execution.startedAt).toLocaleString()}
              </p>
            </div>
          </div>

          {execution.errorMessage && (
            <div className="rounded-[22px] border border-[rgba(184,74,55,0.16)] bg-[rgba(184,74,55,0.08)] px-4 py-3">
              <p className="section-kicker">Error</p>
              <p className="mt-2 font-mono-ui text-xs leading-6 text-[color:var(--danger)]">
                {execution.errorMessage}
              </p>
            </div>
          )}

          {execution.output && (
            <details className="rounded-[22px] border border-[color:var(--line)] bg-[rgba(255,255,255,0.48)] px-4 py-3">
              <summary className="cursor-pointer list-none text-sm font-medium text-[color:var(--text-strong)]">
                Output
              </summary>
              <pre className="code-block mt-4 whitespace-pre-wrap break-all">{formatJson(execution.output)}</pre>
            </details>
          )}
        </li>
      ))}
    </ol>
  )
}
