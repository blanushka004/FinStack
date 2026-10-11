import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, message } from '../lib/api'
import type { Alert, Dashboard, Page } from '../types/api'

export function AlertsPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const alerts = useQuery({
    queryKey: page === 0 ? ['alerts'] : ['alerts', page],
    queryFn: () => api.get<Page<Alert>>('/api/v1/alerts', { params: { page, size: 30 } }).then((response) => response.data),
  })
  const summary = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: () => api.get<Dashboard>('/api/v1/dashboard/summary').then((response) => response.data),
  })
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
  }
  const mark = useMutation({ mutationFn: (id: string) => api.patch(`/api/v1/alerts/${id}/read`), onSuccess: refresh })
  const markAll = useMutation({ mutationFn: () => api.patch('/api/v1/alerts/read-all'), onSuccess: refresh })
  const unread = summary.data?.unreadAlerts ?? alerts.data?.content.filter((alert) => !alert.read).length ?? 0

  return <section>
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h2 className="text-2xl font-bold">Alerts</h2>
      <button type="button" disabled={!unread || markAll.isPending} onClick={() => markAll.mutate()}
        className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">
        {markAll.isPending ? 'Marking read…' : `Mark all as read${unread ? ` (${unread})` : ''}`}
      </button>
    </div>
    {(mark.error || markAll.error) && <p role="alert" className="mb-4 text-sm text-rose-700">{message(mark.error ?? markAll.error)}</p>}
    {alerts.isLoading && <div className="h-48 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading alerts" />}
    {alerts.error && <div className="rounded-2xl border border-rose-200 bg-rose-50 p-5" role="alert">
      <p className="text-sm text-rose-700">{message(alerts.error)}</p>
      <button type="button" onClick={() => void alerts.refetch()} className="mt-3 rounded border px-3 py-2 text-sm">Try again</button>
    </div>}
    <div className="space-y-3">{alerts.data?.content.map((alert) => {
      const severe = alert.alertType.includes('EXCEEDED') || alert.alertType === 'FRAUD_SUSPECTED'
        || alert.severity === 'HIGH' || alert.severity === 'CRITICAL'
      const warning = alert.alertType.includes('WARNING')
      return <article key={alert.id} className={`rounded-2xl border p-5 shadow-sm ${alert.read ? 'border-slate-200 bg-white'
        : severe ? 'border-rose-400 bg-rose-50' : 'border-amber-300 bg-amber-50'}`}>
        <div className="flex justify-between gap-4">
          <div>
            <p className={`text-xs font-bold ${severe ? 'text-rose-700' : warning ? 'text-amber-700' : 'text-slate-600'}`}>{alert.severity} · {alert.alertType.replaceAll('_', ' ')}</p>
            <p className="mt-1 font-semibold">{alert.title}</p>
            <p className="text-sm text-slate-600">{alert.message}</p>
            <p className="mt-2 text-xs text-slate-500">{new Date(alert.createdAt).toLocaleString()}</p>
          </div>
          {!alert.read && <button type="button" disabled={mark.isPending} onClick={() => mark.mutate(alert.id)}
            className="h-fit shrink-0 text-sm font-medium text-brand disabled:opacity-50">Mark read</button>}
        </div>
      </article>
    })}</div>
    {alerts.data?.content.length === 0 && <p className="rounded-2xl border border-dashed p-10 text-center text-slate-500">You’re all caught up.</p>}
    {alerts.data && alerts.data.totalPages > 1 && <div className="mt-5 flex items-center justify-between gap-3 text-sm">
      <button type="button" disabled={page === 0 || alerts.isFetching} onClick={() => setPage((value) => value - 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Previous</button>
      <span>Page {page + 1} of {alerts.data.totalPages}</span>
      <button type="button" disabled={page + 1 >= alerts.data.totalPages || alerts.isFetching} onClick={() => setPage((value) => value + 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Next</button>
    </div>}
  </section>
}
