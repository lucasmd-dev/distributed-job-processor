import { LucideIcon } from 'lucide-react'
import clsx from 'clsx'

interface Props {
  icon: LucideIcon
  label: string
  value: number | undefined
  tone:
    | 'total'
    | 'pending'
    | 'scheduled'
    | 'running'
    | 'success'
    | 'failed'
    | 'dead'
    | 'cancelled'
}

export default function StatsCard({ icon: Icon, label, value, tone }: Props) {
  return (
    <div className="metric-lane">
      <div className={clsx('metric-mark', `metric-mark-${tone}`)}>
        <Icon size={16} />
      </div>
      <div>
        <p className="metric-kicker">{label}</p>
      </div>
      <p className="metric-value">{value ?? '—'}</p>
    </div>
  )
}
