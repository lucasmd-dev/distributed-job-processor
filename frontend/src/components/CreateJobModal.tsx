import { FormEvent, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PlusCircle, X } from 'lucide-react'
import { createJob, CreateJobPayload, getApiErrorMessage } from '../api/jobsApi'

const JOB_TYPES = ['EMAIL_SEND', 'REPORT_GENERATE', 'WEBHOOK_DISPATCH'] as const

interface Props {
  onClose: () => void
}

export default function CreateJobModal({ onClose }: Props) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateJobPayload>({
    type: JOB_TYPES[0],
    payload: '{}',
    idempotencyKey: '',
    scheduledAt: '',
    maxAttempts: 3,
  })
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: createJob,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      onClose()
    },
    onError: (mutationError: unknown) => {
      setError(getApiErrorMessage(mutationError))
    },
  })

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    try {
      JSON.parse(form.payload)
    } catch {
      setError('Payload must be valid JSON')
      return
    }

    const data: CreateJobPayload = {
      type: form.type,
      payload: form.payload,
    }

    if (form.idempotencyKey) {
      data.idempotencyKey = form.idempotencyKey
    }

    if (form.scheduledAt) {
      data.scheduledAt = new Date(form.scheduledAt).toISOString()
    }

    if (form.maxAttempts) {
      data.maxAttempts = form.maxAttempts
    }

    mutation.mutate(data)
  }

  return createPortal(
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal-frame">
        <div
          className="modal-sheet"
          role="dialog"
          aria-modal="true"
          aria-labelledby="create-job-modal-title"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <div className="grid gap-0 lg:grid-cols-[minmax(17rem,0.88fr)_minmax(0,1.12fr)]">
            <div className="border-b border-[color:var(--line)] bg-[rgba(24,69,56,0.94)] px-6 py-6 text-white lg:border-b-0 lg:border-r lg:px-8 lg:py-8">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-4">
                  <div className="inline-flex items-center gap-2 text-[0.68rem] uppercase tracking-[0.24em] text-white/62">
                    <PlusCircle size={14} />
                    New job
                  </div>
                  <div className="space-y-2">
                    <h2 id="create-job-modal-title" className="text-3xl font-semibold tracking-[-0.08em]">
                      Create a job
                    </h2>
                    <p className="max-w-sm text-sm leading-7 text-white/72">
                      Fill in the payload, schedule and retry policy, then submit the job to the queue.
                    </p>
                  </div>
                </div>

                <button
                  onClick={onClose}
                  className="rounded-full border border-white/12 p-2 text-white/70 transition-colors hover:text-white"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="mt-8 space-y-4 text-sm text-white/74 lg:mt-10">
                <div>
                  <p className="text-[0.68rem] uppercase tracking-[0.24em] text-white/52">Supported types</p>
                  <p className="mt-2">EMAIL_SEND · REPORT_GENERATE · WEBHOOK_DISPATCH</p>
                </div>
                <div>
                  <p className="text-[0.68rem] uppercase tracking-[0.24em] text-white/52">Timing</p>
                  <p className="mt-2">Leave the schedule empty to dispatch immediately.</p>
                </div>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5 px-6 py-6 lg:px-8 lg:py-8">
              <label className="field-shell block">
                <span className="field-label">Type</span>
                <select
                  value={form.type}
                  onChange={(event) => setForm({ ...form, type: event.target.value })}
                  className="field-input"
                >
                  {JOB_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </label>

              <label className="field-shell block">
                <span className="field-label">Payload (JSON)</span>
                <textarea
                  rows={7}
                  value={form.payload}
                  onChange={(event) => setForm({ ...form, payload: event.target.value })}
                  className="field-input min-h-[10rem] resize-y font-mono-ui text-sm leading-7"
                  required
                />
              </label>

              <div className="grid gap-5 lg:grid-cols-2">
                <label className="field-shell block">
                  <span className="field-label">Idempotency key</span>
                  <input
                    type="text"
                    value={form.idempotencyKey}
                    onChange={(event) => setForm({ ...form, idempotencyKey: event.target.value })}
                    placeholder="optional; generated automatically if blank"
                    className="field-input"
                  />
                </label>

                <label className="field-shell block">
                  <span className="field-label">Max attempts</span>
                  <input
                    type="number"
                    min={1}
                    max={10}
                    value={form.maxAttempts}
                    onChange={(event) =>
                      setForm({ ...form, maxAttempts: Number.parseInt(event.target.value || '0', 10) })
                    }
                    className="field-input"
                  />
                </label>
              </div>

              <label className="field-shell block">
                <span className="field-label">Schedule at</span>
                <input
                  type="datetime-local"
                  value={form.scheduledAt}
                  onChange={(event) => setForm({ ...form, scheduledAt: event.target.value })}
                  className="field-input"
                />
              </label>

              {error && (
                <p className="rounded-[20px] border border-[rgba(184,74,55,0.16)] bg-[rgba(184,74,55,0.08)] px-4 py-3 text-sm text-[color:var(--danger)]">
                  {error}
                </p>
              )}

              <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
                <button type="button" onClick={onClose} className="action-button action-button-secondary">
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={mutation.isPending}
                  className="action-button action-button-primary disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {mutation.isPending ? 'Creating…' : 'Create job'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>,
    document.body
  )
}
