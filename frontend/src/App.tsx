import { NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { Cpu, LayoutDashboard, List } from 'lucide-react'
import clsx from 'clsx'
import Dashboard from './pages/Dashboard'
import JobList from './pages/JobList'
import JobDetail from './pages/JobDetail'

const links = [
  { to: '/', label: 'Queue status', icon: LayoutDashboard, end: true },
  { to: '/jobs', label: 'Job list', icon: List, end: false },
]

const topology = [
  { step: '01', title: 'API', detail: 'Control plane and scheduler' },
  { step: '02', title: 'Queue', detail: 'Primary, retry and dead-letter flows' },
  { step: '03', title: 'Workers', detail: 'Stateless consumers processing jobs' },
  { step: '04', title: 'History', detail: 'PostgreSQL execution and audit trail' },
]

export default function App() {
  return (
    <div className="app-shell">
      <div className="grid min-h-screen lg:grid-cols-[19rem_minmax(0,1fr)]">
        <DesktopRail />
        <div className="min-w-0">
          <MobileHeader />
          <main className="px-4 pb-10 pt-4 sm:px-6 sm:pb-12 lg:px-10 lg:pb-16 lg:pt-10">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/jobs" element={<JobList />} />
              <Route path="/jobs/:id" element={<JobDetail />} />
            </Routes>
          </main>
        </div>
      </div>
    </div>
  )
}

function DesktopRail() {
  return (
    <aside className="hidden lg:flex lg:min-h-screen lg:flex-col lg:justify-between lg:border-r lg:border-[color:var(--line)] lg:px-8 lg:py-8">
      <div className="space-y-10">
        <div className="space-y-5 page-enter">
          <div className="inline-flex items-center gap-3 text-[0.72rem] uppercase tracking-[0.28em] text-[color:var(--muted)]">
            <Cpu size={14} />
            Distributed processing
          </div>
          <div className="space-y-3">
            <p className="brand-display">Job Processor</p>
            <p className="max-w-xs text-sm leading-7 text-[color:var(--muted)]">
              API, queue, workers and execution history in one operational view.
            </p>
          </div>
        </div>

        <div className="rail-surface page-enter reveal-2">
          <p className="section-kicker">System flow</p>
          <ol className="mt-5 space-y-4">
            {topology.map((item, index) => (
              <li key={item.step} className="flow-step" style={{ animationDelay: `${index * 120}ms` }}>
                <span className="flow-step-index">{item.step}</span>
                <div className="space-y-1">
                  <p className="text-sm font-semibold text-[color:var(--text-strong)]">{item.title}</p>
                  <p className="text-xs leading-6 text-[color:var(--muted)]">{item.detail}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </div>

      <nav className="space-y-2 page-enter reveal-3">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => clsx('nav-link', isActive && 'nav-link-active')}
          >
            <Icon size={16} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

function MobileHeader() {
  const location = useLocation()
  const activeView = links.find(({ to, end }) =>
    end ? location.pathname === to : location.pathname.startsWith(to)
  )

  return (
    <header className="sticky top-0 z-30 border-b border-[color:var(--line)] bg-[color:rgba(244,239,231,0.84)] px-4 py-4 backdrop-blur-xl sm:px-6 lg:hidden">
      <div className="space-y-4">
        <div className="space-y-1">
          <p className="brand-signal">Job Processor</p>
          <p className="text-sm text-[color:var(--muted)]">{activeView?.label ?? 'Job details'}</p>
        </div>
        <nav className="grid grid-cols-2 gap-2">
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => clsx('nav-link justify-center', isActive && 'nav-link-active')}
            >
              <Icon size={16} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </header>
  )
}
