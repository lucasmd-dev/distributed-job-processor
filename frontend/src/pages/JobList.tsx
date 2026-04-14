import { startTransition, useDeferredValue, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import StatusBadge from '../components/StatusBadge'
import CreateJobModal from '../components/CreateJobModal'
import { useJobs } from '../hooks/useJobs'

const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'DEAD', label: 'Dead' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

export default function JobList() {
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [page, setPage] = useState(0)
  const [showModal, setShowModal] = useState(false)

  const deferredStatus = useDeferredValue(status)
  const deferredType = useDeferredValue(type.trim())

  const { data, isLoading } = useJobs({
    status: deferredStatus || undefined,
    type: deferredType || undefined,
    page,
    size: 20,
  })

  const isFiltering = deferredStatus !== status || deferredType !== type.trim()
  const totalPages = data?.totalPages ?? 1

  function updateStatus(value: string) {
    startTransition(() => {
      setStatus(value)
      setPage(0)
    })
  }

  function updateType(value: string) {
    startTransition(() => {
      setType(value)
      setPage(0)
    })
  }

  function goToPage(nextPage: number) {
    startTransition(() => {
      setPage(nextPage)
    })
  }

  return (
    <div className="space-y-8 page-enter">
      <section className="section-shell">
        <div className="flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">
          <div className="space-y-3">
            <p className="section-kicker">Job list</p>
            <h1 className="stage-title">Browse the queue</h1>
            <p className="stage-copy">
              Filter by status or type and open any job to inspect its execution history.
            </p>
          </div>

          <button
            onClick={() => setShowModal(true)}
            className="action-button action-button-primary self-start xl:self-auto"
          >
            <Plus size={16} />
            New job
          </button>
        </div>

        <div className="mt-8 grid gap-4 lg:grid-cols-[14rem_minmax(0,1fr)_auto] lg:items-end">
          <label className="field-shell">
            <span className="field-label">Status</span>
            <select
              value={status}
              onChange={(event) => updateStatus(event.target.value)}
              className="field-input"
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="field-shell">
            <span className="field-label">Type filter</span>
            <input
              type="text"
              value={type}
              onChange={(event) => updateType(event.target.value)}
              placeholder="EMAIL_SEND, REPORT_GENERATE..."
              className="field-input"
            />
          </label>

          <div className="pb-2 text-sm text-[color:var(--muted)]">
            {isFiltering ? 'Updating view…' : `${data?.totalElements ?? 0} jobs in view`}
          </div>
        </div>
      </section>

      <section className="section-shell">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-2">
            <p className="section-kicker">Results</p>
            <h2 className="text-2xl font-semibold tracking-[-0.06em] text-[color:var(--text-strong)]">
              Matching jobs
            </h2>
          </div>

          <div className="text-sm text-[color:var(--muted)]">
            Page {page + 1} of {totalPages}
          </div>
        </div>

        <div className="data-table">
          <div className="table-head">
            <span>Job</span>
            <span>Type</span>
            <span>Status</span>
            <span>Run</span>
            <span>Created</span>
          </div>

          {isLoading && (
            <div className="border-t border-[color:var(--line)] py-10 text-sm text-[color:var(--muted)]">
              Loading jobs…
            </div>
          )}

          {!isLoading &&
            data?.content.map((job) => (
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

          {!isLoading && !data?.content.length && (
            <div className="border-t border-[color:var(--line)] py-10 text-sm text-[color:var(--muted)]">
              No jobs match the current filters.
            </div>
          )}
        </div>

        {totalPages > 1 && (
          <div className="mt-6 flex items-center justify-between border-t border-[color:var(--line)] pt-5">
            <p className="text-sm text-[color:var(--muted)]">
              {data?.totalElements ?? 0} jobs available
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => goToPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="action-button action-button-secondary disabled:cursor-not-allowed disabled:opacity-40"
              >
                <ChevronLeft size={16} />
                Previous
              </button>
              <button
                onClick={() => goToPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
                className="action-button action-button-secondary disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </section>

      {showModal && <CreateJobModal onClose={() => setShowModal(false)} />}
    </div>
  )
}
