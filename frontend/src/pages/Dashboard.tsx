import { Link } from 'react-router-dom'
import {
  Activity,
  ArrowRight,
  Ban,
  CheckCircle2,
  Clock3,
  Play,
  ShieldAlert,
  XCircle,
} from 'lucide-react'
import { useJobs, useStats } from '../hooks/useJobs'
import StatsCard from '../components/StatsCard'
import StatusBadge from '../components/StatusBadge'

export default function Dashboard() {
  const { data: stats } = useStats()
  const { data: jobs } = useJobs({ size: 8 })

  const metrics = [
    { icon: Activity, label: 'Total', value: stats?.total, tone: 'total' as const },
    { icon: Clock3, label: 'Pending', value: stats?.pending, tone: 'pending' as const },
    { icon: Clock3, label: 'Scheduled', value: stats?.scheduled, tone: 'scheduled' as const },
    { icon: Play, label: 'Running', value: stats?.running, tone: 'running' as const },
    { icon: CheckCircle2, label: 'Success', value: stats?.success, tone: 'success' as const },
    { icon: XCircle, label: 'Failed', value: stats?.failed, tone: 'failed' as const },
    { icon: ShieldAlert, label: 'Dead', value: stats?.dead, tone: 'dead' as const },
    { icon: Ban, label: 'Cancelled', value: stats?.cancelled, tone: 'cancelled' as const },
  ]

  return (
    <div className="space-y-8 page-enter">
      <section className="section-shell">
        <div className="grid gap-10 xl:grid-cols-[minmax(0,1.15fr)_minmax(22rem,0.85fr)] xl:items-end">
          <div className="space-y-6">
            <div className="space-y-3">
              <p className="section-kicker">Job Processor</p>
              <h1 className="stage-title">Queue status</h1>
              <p className="stage-copy">
                Overview of queue volume, scheduled backlog and the latest processing activity.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Link to="/jobs" className="action-button action-button-primary">
                View jobs
                <ArrowRight size={16} />
              </Link>
              <p className="text-sm text-[color:var(--muted)]">
                Auto-refresh every 5 seconds.
              </p>
            </div>
          </div>

          <div className="grid gap-x-6 sm:grid-cols-2">
            {metrics.map((metric) => (
              <StatsCard key={metric.label} {...metric} />
            ))}
          </div>
        </div>
      </section>

      <section className="section-shell">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-2">
            <p className="section-kicker">Recent jobs</p>
            <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
              Recent activity
            </h2>
            <p className="max-w-2xl text-sm leading-7 text-[color:var(--muted)]">
              Open a job to inspect payload, retry history and worker output.
            </p>
          </div>
          <Link to="/jobs" className="action-button action-button-secondary self-start sm:self-auto">
            View all jobs
          </Link>
        </div>

        <div className="data-table">
          <div className="table-head">
            <span>Job</span>
            <span>Type</span>
            <span>Status</span>
            <span>Run</span>
            <span>Created</span>
          </div>

          {jobs?.content.map((job) => (
            <Link key={job.id} to={`/jobs/${job.id}`} className="table-row">
              <div>
                <span className="table-row-label">Job</span>
                <p className="font-mono-ui text-sm text-[color:var(--brand)]">{job.id.slice(0, 8)}</p>
                <p className="mt-1 text-xs text-[color:var(--muted)]">View details</p>
              </div>
              <div>
                <span className="table-row-label">Type</span>
                <p className="text-sm font-medium text-[color:var(--text-strong)]">{job.type}</p>
              </div>
              <div>
                <span className="table-row-label">Status</span>
                <StatusBadge status={job.status} />
              </div>
              <div>
                <span className="table-row-label">Run</span>
                <p className="text-sm text-[color:var(--text-strong)]">#{job.runNumber}</p>
                <p className="mt-1 text-xs text-[color:var(--muted)]">
                  {job.attempt}/{job.maxAttempts} attempts
                </p>
              </div>
              <div>
                <span className="table-row-label">Created</span>
                <p className="text-sm text-[color:var(--text-strong)]">
                  {new Date(job.createdAt).toLocaleDateString()}
                </p>
                <p className="mt-1 text-xs text-[color:var(--muted)]">
                  {new Date(job.createdAt).toLocaleTimeString()}
                </p>
              </div>
            </Link>
          ))}

          {!jobs?.content.length && (
            <div className="border-t border-[color:var(--line)] py-10 text-sm text-[color:var(--muted)]">
              No jobs found in the latest refresh.
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
