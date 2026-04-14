import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchJobs, fetchJob, fetchStats } from '../api/jobsApi'

export function useJobs(params: { status?: string; type?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['jobs', params],
    queryFn: () => fetchJobs(params),
    placeholderData: keepPreviousData,
    refetchInterval: 5000,
  })
}

export function useJobDetail(id: string) {
  return useQuery({
    queryKey: ['job', id],
    queryFn: () => fetchJob(id),
    refetchInterval: 5000,
    enabled: !!id,
  })
}

export function useStats() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: fetchStats,
    refetchInterval: 5000,
  })
}
