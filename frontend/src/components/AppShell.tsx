import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Bell, CreditCard, LayoutDashboard, LogOut, Menu, PiggyBank, ShieldAlert, ShieldCheck, Tags, Wallet, X } from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../app/AuthContext'
import type { Alert, Dashboard, Page } from '../types/api'

const DashboardVisualizations = lazy(() => import('./DashboardVisualizations').then((module) => ({ default: module.DashboardVisualizations })))

const important = new Set(['BUDGET_WARNING', 'BUDGET_EXCEEDED', 'CATEGORY_BUDGET_WARNING', 'CATEGORY_BUDGET_EXCEEDED', 'LOW_BALANCE', 'LARGE_TRANSACTION', 'FRAUD_SUSPECTED'])

export function AppShell() {
  const { user, logout } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const alerts = useQuery({
    queryKey: ['alerts'],
    queryFn: () => api.get<Page<Alert>>('/api/v1/alerts', { params: { page: 0, size: 30 } }).then((response) => response.data),
    refetchInterval: 45000,
  })
  const summary = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: () => api.get<Dashboard>('/api/v1/dashboard/summary').then((response) => response.data),
    refetchInterval: 45000,
  })
  const unread = summary.data?.unreadAlerts ?? alerts.data?.content.filter((alert) => !alert.read).length ?? 0
  const items = [
    { label: 'Dashboard', path: '/dashboard', Icon: LayoutDashboard },
    { label: 'Accounts', path: '/accounts', Icon: Wallet },
    { label: 'Transactions', path: '/transactions', Icon: CreditCard },
    { label: 'Categories', path: '/categories', Icon: Tags },
    { label: 'Budgets', path: '/budgets', Icon: PiggyBank },
    { label: 'Alerts', path: '/alerts', Icon: Bell },
    { label: 'Fraud & Risk', path: '/fraud', Icon: ShieldAlert },
  ]
  if (user?.roles.includes('ROLE_ADMIN')) items.push({ label: 'Admin Login Activity', path: '/admin/login-activity', Icon: ShieldCheck })

  const sidebar = <aside className="flex h-full w-64 flex-col border-r border-slate-200 bg-white p-5">
    <h1 className="mb-8 text-2xl font-bold tracking-tight">Fin<span className="text-brand">Stack</span></h1>
    <nav className="flex flex-1 flex-col gap-1">{items.map(({ label, path, Icon }) => <NavLink key={path} to={path} onClick={() => setMobileOpen(false)} className={({ isActive }) => `flex items-center justify-between rounded-xl px-3 py-2.5 text-sm font-medium ${isActive ? 'bg-emerald-50 text-brand' : 'text-slate-600 hover:bg-slate-50'}`}>
      <span className="flex items-center gap-3"><Icon size={18} />{label}</span>
      {label === 'Alerts' && unread > 0 && <span className="rounded-full bg-rose-600 px-2 py-0.5 text-xs font-bold text-white">{unread}</span>}
    </NavLink>)}</nav>
    <div className="border-t pt-4"><p className="truncate text-sm font-semibold">{user?.firstName} {user?.lastName}</p><p className="truncate text-xs text-slate-500">{user?.email}</p><button type="button" onClick={logout} className="mt-4 flex items-center gap-2 text-sm text-slate-500 hover:text-slate-900"><LogOut size={16} />Log out</button></div>
  </aside>

  return <div className="min-h-screen bg-paper lg:flex">
    <div className="sticky top-0 hidden h-screen lg:block">{sidebar}</div>
    <button type="button" onClick={() => setMobileOpen(true)} aria-label="Open navigation" className="fixed left-4 top-4 z-40 rounded-lg border bg-white p-2 shadow-sm lg:hidden"><Menu size={20} /></button>
    {mobileOpen && <div className="fixed inset-0 z-50 lg:hidden"><button type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} className="absolute inset-0 bg-slate-950/40" /><div className="relative h-full w-72 shadow-xl">{sidebar}<button type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} className="absolute right-4 top-5"><X size={20} /></button></div></div>}
    <main className="min-w-0 flex-1"><header className="flex min-h-20 items-center justify-end border-b border-slate-200 bg-white px-5 py-4"><NotificationBell alerts={alerts.data?.content ?? []} unread={unread} onViewAll={() => navigate('/alerts')} /></header><div className="mx-auto max-w-7xl p-5 pt-8 sm:p-8"><Outlet />{location.pathname === '/dashboard' && <Suspense fallback={<div className="mt-6 h-72 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading charts" />}><DashboardVisualizations /></Suspense>}</div></main>
    <AlertPopup alerts={alerts.data?.content ?? []} ready={alerts.isSuccess} onView={() => navigate('/alerts')} />
  </div>
}

function NotificationBell({ alerts, unread, onViewAll }: { alerts: Alert[]; unread: number; onViewAll: () => void }) {
  const [open, setOpen] = useState(false)
  const panel = useRef<HTMLDivElement>(null)
  const queryClient = useQueryClient()
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
  }
  const markRead = useMutation({ mutationFn: (id: string) => api.patch(`/api/v1/alerts/${id}/read`), onSuccess: refresh })
  const markAll = useMutation({ mutationFn: () => api.patch('/api/v1/alerts/read-all'), onSuccess: refresh })

  useEffect(() => {
    const closeOutside = (event: MouseEvent) => { if (panel.current && !panel.current.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', closeOutside)
    return () => document.removeEventListener('mousedown', closeOutside)
  }, [])

  const openAlert = (alert: Alert) => {
    if (!alert.read) markRead.mutate(alert.id)
    setOpen(false)
    onViewAll()
  }
  return <div ref={panel} className="relative">
    <button type="button" onClick={() => setOpen((value) => !value)} aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`} aria-expanded={open} aria-haspopup="dialog" className={`relative rounded-full border p-2.5 transition ${unread ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}>
      <Bell size={19} />{unread > 0 && <span className="absolute -right-1 -top-1 grid min-h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">{unread > 99 ? '99+' : unread}</span>}
    </button>
    {open && <section role="dialog" aria-label="Notifications" className="absolute right-0 z-50 mt-3 w-[min(24rem,calc(100vw-2.5rem))] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl">
      <div className="flex items-center justify-between border-b px-4 py-3"><div><h2 className="font-semibold">Notifications</h2><p className="text-xs text-slate-500">{unread ? `${unread} unread` : 'You’re all caught up'}</p></div><button type="button" disabled={!unread || markAll.isPending} onClick={() => markAll.mutate()} className="text-sm font-medium text-brand disabled:text-slate-400">Mark all read</button></div>
      <div className="max-h-96 overflow-y-auto">{alerts.slice(0, 5).map((alert) => <button key={alert.id} type="button" onClick={() => openAlert(alert)} disabled={markRead.isPending} className={`block w-full border-b px-4 py-3 text-left transition hover:bg-slate-50 disabled:opacity-60 ${alert.read ? 'bg-white' : alert.severity === 'HIGH' || alert.severity === 'CRITICAL' || alert.alertType === 'FRAUD_SUSPECTED' ? 'bg-rose-50' : 'bg-amber-50'}`}>
        <div className="flex items-start justify-between gap-3"><p className={`text-xs font-bold uppercase ${alert.severity === 'HIGH' || alert.severity === 'CRITICAL' || alert.alertType === 'FRAUD_SUSPECTED' ? 'text-rose-700' : 'text-amber-700'}`}>{alert.severity} · {alert.alertType.replaceAll('_', ' ')}</p>{!alert.read && <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-rose-600" aria-label="Unread" />}</div>
        <p className="mt-1 font-semibold text-slate-900">{alert.title}</p><p className="mt-1 line-clamp-2 text-sm text-slate-600">{alert.message}</p><p className="mt-2 text-xs text-slate-500">{new Date(alert.createdAt).toLocaleString()}</p>
      </button>)}{alerts.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No notifications yet.</p>}</div>
      <button type="button" onClick={() => { setOpen(false); onViewAll() }} className="w-full px-4 py-3 text-sm font-semibold text-brand hover:bg-slate-50">View all alerts</button>
    </section>}
  </div>
}

function AlertPopup({ alerts, ready, onView }: { alerts: Alert[]; ready: boolean; onView: () => void }) {
  const known = useRef<Set<string>>(new Set())
  const initialized = useRef(false)
  const queued = useRef<Alert[]>([])
  const [current, setCurrent] = useState<Alert | null>(null)
  useEffect(() => {
    if (!ready) return
    const newAlerts = alerts.filter((alert) => !alert.read && important.has(alert.alertType) && !known.current.has(alert.id))
    if (!initialized.current) { initialized.current = true; alerts.forEach((alert) => known.current.add(alert.id)); return }
    newAlerts.forEach((alert) => known.current.add(alert.id))
    queued.current.push(...newAlerts)
    if (!current && queued.current.length) setCurrent(queued.current.shift() ?? null)
  }, [alerts, ready, current])
  if (!current) return null
  const severe = current.alertType.includes('EXCEEDED') || current.alertType === 'FRAUD_SUSPECTED' || current.severity === 'HIGH' || current.severity === 'CRITICAL'
  return <div className={`fixed bottom-5 right-5 z-50 w-[min(24rem,calc(100vw-2.5rem))] rounded-2xl border p-4 shadow-xl ${severe ? 'border-rose-300 bg-rose-50' : 'border-amber-300 bg-amber-50'}`} role="status" aria-live="polite"><div className="flex justify-between gap-3"><div><p className="text-xs font-bold uppercase">{current.severity}</p><h2 className="mt-1 font-semibold">{current.title}</h2><p className="mt-1 text-sm text-slate-700">{current.message}</p><p className="mt-2 text-xs text-slate-500">{new Date(current.createdAt).toLocaleString()}</p></div><button type="button" onClick={() => setCurrent(null)} aria-label="Dismiss alert notification" className="h-7 w-7 rounded text-lg">×</button></div><button type="button" onClick={() => { setCurrent(null); onView() }} className="mt-3 rounded-lg bg-slate-900 px-3 py-2 text-sm font-semibold text-white">View alerts</button></div>
}
