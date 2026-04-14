import axios from 'axios'

export type JobStatus = 'PENDING' | 'SCHEDULED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'DEAD' | 'CANCELLED'

export interface JobExecution {
  id: string
  runNumber: number
  attempt: number
  status: JobStatus
  workerId: string
  startedAt: string
  finishedAt?: string
  output?: string
  errorMessage?: string
}

export interface Job {
  id: string
  type: string
  payload: string
  status: JobStatus
  idempotencyKey: string
  runNumber: number
  attempt: number
  maxAttempts: number
  scheduledAt?: string
  createdAt: string
  updatedAt: string
  executions?: JobExecution[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface JobStats {
  total: number
  pending: number
  scheduled: number
  running: number
  success: number
  failed: number
  dead: number
  cancelled: number
}

export interface CreateJobPayload {
  type: string
  payload: string
  idempotencyKey?: string
  scheduledAt?: string
  maxAttempts?: number
}

const api = axios.create({
  baseURL: '/api/v1',
})

export function getApiErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data
    if (responseData && typeof responseData === 'object' && 'message' in responseData) {
      const message = responseData.message
      if (typeof message === 'string' && message.trim()) {
        return message
      }
    }

    if (typeof error.message === 'string' && error.message.trim()) {
      return error.message
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return 'Unexpected error'
}

export function fetchJobs(params: { status?: string; type?: string; page?: number; size?: number }) {
  return api.get<Page<Job>>('/jobs', { params }).then((r) => r.data)
}

export function fetchJob(id: string) {
  return api.get<Job>(`/jobs/${id}`).then((r) => r.data)
}

export function fetchStats() {
  return api.get<JobStats>('/jobs/stats').then((r) => r.data)
}

export function createJob(data: CreateJobPayload) {
  return api.post<Job>('/jobs', data).then((r) => r.data)
}

export function cancelJob(id: string) {
  return api.post<Job>(`/jobs/${id}/cancel`).then((r) => r.data)
}

export function retryJob(id: string) {
  return api.post<Job>(`/jobs/${id}/retry`).then((r) => r.data)
}
