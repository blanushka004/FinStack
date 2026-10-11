import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { api, message } from '../lib/api'
import { useAuth } from '../app/AuthContext'

type Summary = { totalLoginAttempts: number; successfulLogins: number; failedLogins: number; uniqueUsersWhoLoggedIn: number; loginsToday: number; loginsThisWeek: number }
type Event = { id: string; userId: string | null; email: string; successful: boolean; occurredAt: string; ipAddress: string | null; userAgent: string | null }
type Page = { content: Event[]; number: number; totalPages: number }

export function AdminLoginActivity() {
  const { user } = useAuth()
  const summary = useQuery({ queryKey: ['admin-login-audit-summary'], queryFn: () => api.get<Summary>('/api/v1/admin/login-audit/summary').then((response) => response.data) })
  const history = useQuery({ queryKey: ['admin-login-audit'], queryFn: () => api.get<Page>('/api/v1/admin/login-audit', { params: { page: 0, size: 20 } }).then((response) => response.data) })
  if (!user?.roles.includes('ROLE_ADMIN')) return <Navigate to="/dashboard" replace />
  if (summary.isLoading || history.isLoading) return <p>Loading login activity…</p>
  if (summary.error || history.error) return <div><p className="text-rose-600">{message(summary.error ?? history.error)}</p><button type="button" onClick={() => { summary.refetch(); history.refetch() }} className="mt-3 rounded border px-3 py-2 text-sm">Try again</button></div>
  const data = summary.data!
  return <section><h2 className="mb-6 text-2xl font-bold">Login activity</h2><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{[['Attempts', data.totalLoginAttempts], ['Successful', data.successfulLogins], ['Failed', data.failedLogins], ['Unique users', data.uniqueUsersWhoLoggedIn]].map(([label, value]) => <div key={String(label)} className="rounded-2xl border bg-white p-5 shadow-sm"><p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-2xl font-bold">{value}</p></div>)}</div><div className="mt-6 overflow-x-auto rounded-2xl border bg-white"><table className="w-full min-w-[650px] text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr><th className="p-3">Time</th><th className="p-3">Email</th><th className="p-3">Result</th><th className="p-3">IP</th><th className="p-3">User agent</th></tr></thead><tbody>{history.data!.content.map((event) => <tr key={event.id} className="border-t"><td className="p-3">{new Date(event.occurredAt).toLocaleString()}</td><td className="p-3">{event.email}</td><td className={`p-3 font-medium ${event.successful ? 'text-emerald-700' : 'text-rose-600'}`}>{event.successful ? 'Success' : 'Failure'}</td><td className="p-3">{event.ipAddress ?? '—'}</td><td className="max-w-xs truncate p-3">{event.userAgent ?? '—'}</td></tr>)}</tbody></table></div></section>
}
