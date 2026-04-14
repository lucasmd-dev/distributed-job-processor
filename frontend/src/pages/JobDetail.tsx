import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, RotateCcw, XCircle } from 'lucide-react'
import { cancelJob, getApiErrorMessage, retryJob } from '../api/jobsApi'
import StatusBadge from '../components/StatusBadge'
import ExecutionTimeline from '../components/ExecutionTimeline'
import { useJobDetail } from '../hooks/useJobs'

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export default function JobDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: job, isLoading, isError } = useJobDetail(id!)

  const cancelMutation = useMutation({
    mutationFn: () => cancelJob(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job', id] })
      queryClient.invalidateQueries({ queryKey: ['jobs'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
  })

  const retryMutation = useMutation({
    mutationFn: () => retryJob(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job', id] })
      queryClient.invalidateQueries({ queryKey: ['jobs'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
  })

  if (isLoading) {
    return <div className="page-enter py-10 text-sm text-[color:var(--muted)]">Loading job details…</div>
  }

  if (isError || !job) {
    return (
      <div className="page-enter space-y-4 py-10">
        <p className="text-sm text-[color:var(--danger)]">Failed to load this job.</p>
        <button
          onClick={() => navigate(-1)}
          className="action-button action-button-secondary self-start"
        >
          Go back
        </button>
      </div>
    )
  }

  const canCancel = job.status === 'PENDING' || job.status === 'FAILED'
  const canRetry = job.status === 'FAILED' || job.status === 'DEAD'
  const latestExecution = job.executions?.[job.executions.length - 1]
  const mutationError = cancelMutation.error ?? retryMutation.error

  return (
    <div className="space-y-8 page-enter">
      <Link
        to="/jobs"
        className="inline-flex items-center gap-2 text-sm text-[color:var(--muted)] transition-colors hover:text-[color:var(--text-strong)]"
      >
        <ArrowLeft size={15} />
        Back to list
      </Link>

      <section className="section-shell">
        <div className="flex flex-col gap-8 xl:flex-row xl:items-start xl:justify-between">
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="section-kicker">Job details</p>
              <p className="brand-signal">{job.type}</p>
              <p className="font-mono-ui text-xs leading-6 text-[color:var(--muted)]">{job.id}</p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <StatusBadge status={job.status} />
              <p className="text-sm text-[color:var(--muted)]">
                Run #{job.runNumber} · {job.attempt}/{job.maxAttempts} attempts
              </p>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            {canCancel && (
              <button
                onClick={() => cancelMutation.mutate()}
                disabled={cancelMutation.isPending}
                className="action-button action-button-danger disabled:cursor-not-allowed disabled:opacity-50"
              >
                <XCircle size={16} />
                {cancelMutation.isPending ? 'Cancelling…' : 'Cancel'}
              </button>
            )}
            {canRetry && (
              <button
                onClick={() => retryMutation.mutate()}
                disabled={retryMutation.isPending}
                className="action-button action-button-secondary disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RotateCcw size={16} />
                {retryMutation.isPending ? 'Retrying…' : 'Retry'}
              </button>
            )}
          </div>
        </div>
      </section>

      <div className="grid gap-8 xl:grid-cols-[minmax(0,1.2fr)_minmax(21rem,0.8fr)]">
        <div className="space-y-8">
          <section className="section-shell">
            <div className="space-y-2">
              <p className="section-kicker">Payload</p>
              <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
                Request payload
              </h2>
            </div>
            <pre className="code-block mt-6 whitespace-pre-wrap break-all">{formatJson(job.payload)}</pre>
          </section>

          {latestExecution?.output && (
            <section className="section-shell">
              <div className="space-y-2">
                <p className="section-kicker">Latest output</p>
                <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
                  Worker result
                </h2>
              </div>
              <pre className="code-block mt-6 whitespace-pre-wrap break-all">
                {formatJson(latestExecution.output)}
              </pre>
            </section>
          )}
        </div>

        <div className="space-y-8 xl:sticky xl:top-10 xl:self-start">
          <section className="section-shell">
            <div className="space-y-2">
              <p className="section-kicker">Current state</p>
              <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
                Job metadata
              </h2>
            </div>

            <div className="mt-6 space-y-4">
              <div className="detail-item">
                <p className="section-kicker">Idempotency key</p>
                <p className="mt-2 break-all font-mono-ui text-xs leading-6 text-[color:var(--text-strong)]">
                  {job.idempotencyKey || '—'}
                </p>
              </div>
              <div className="detail-item">
                <p className="section-kicker">Created</p>
                <p className="mt-2 text-sm text-[color:var(--text-strong)]">
                  {new Date(job.createdAt).toLocaleString()}
                </p>
              </div>
              <div className="detail-item">
                <p className="section-kicker">Updated</p>
                <p className="mt-2 text-sm text-[color:var(--text-strong)]">
                  {new Date(job.updatedAt).toLocaleString()}
                </p>
              </div>
              {job.scheduledAt && (
                <div className="detail-item">
                  <p className="section-kicker">Scheduled</p>
                  <p className="mt-2 text-sm text-[color:var(--text-strong)]">
                    {new Date(job.scheduledAt).toLocaleString()}
                  </p>
                </div>
              )}
            </div>
          </section>

          <section className="section-shell">
            <div className="space-y-2">
              <p className="section-kicker">Execution history</p>
              <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
                Attempts and results
              </h2>
            </div>

            {mutationError && (
              <p className="mt-5 rounded-[20px] border border-[rgba(184,74,55,0.16)] bg-[rgba(184,74,55,0.08)] px-4 py-3 text-sm text-[color:var(--danger)]">
                {getApiErrorMessage(mutationError)}
              </p>
            )}

            <div className="mt-6">
              <ExecutionTimeline executions={job.executions ?? []} />
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
