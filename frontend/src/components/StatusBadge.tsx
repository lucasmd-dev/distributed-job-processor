import clsx from 'clsx'
import { JobStatus } from '../api/jobsApi'

const config: Record<JobStatus, { label: string; className: string }> = {
  PENDING: { label: 'Pending', className: 'status-pending' },
  SCHEDULED: { label: 'Scheduled', className: 'status-scheduled' },
  RUNNING: { label: 'Running', className: 'status-running' },
  SUCCESS: { label: 'Success', className: 'status-success' },
  FAILED: { label: 'Failed', className: 'status-failed' },
  DEAD: { label: 'Dead', className: 'status-dead' },
  CANCELLED: { label: 'Cancelled', className: 'status-cancelled' },
}

interface Props {
  status: JobStatus
}

export default function StatusBadge({ status }: Props) {
  const tone = config[status] ?? { label: status, className: 'status-cancelled' }

  return (
    <span className={clsx('status-badge', tone.className)}>
      <span className="status-dot" />
      {tone.label}
    </span>
  )
}
