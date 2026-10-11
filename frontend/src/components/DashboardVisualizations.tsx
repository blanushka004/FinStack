import { useQueries, useQuery } from '@tanstack/react-query'
import { Area, AreaChart, Bar, BarChart, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, message } from '../lib/api'
import type { DashboardAnalytics, FraudSummary } from '../types/api'
import type { BudgetProgressResponse, BudgetResponse } from '../types/budget'

const money = (value: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value)
const monthLabel = (month: string) => new Intl.DateTimeFormat('en-IN', { month: 'short' }).format(new Date(`${month}T00:00:00Z`))
const pieColors = ['#0f766e', '#14b8a6', '#f59e0b', '#f97316', '#e11d48', '#64748b']

export function DashboardVisualizations() {
  const analytics = useQuery({ queryKey: ['dashboard-analytics'], queryFn: () => api.get<DashboardAnalytics>('/api/v1/dashboard/analytics').then((response) => response.data), refetchInterval: 45000 })
  const fraud = useQuery({ queryKey: ['fraud-summary'], queryFn: () => api.get<FraudSummary>('/api/v1/fraud/summary').then((response) => response.data), refetchInterval: 45000 })
  const budgets = useQuery({ queryKey: ['budgets'], queryFn: () => api.get<BudgetResponse[]>('/api/v1/budgets', { params: { active: true } }).then((response) => response.data) })
  const visibleBudgets = (budgets.data ?? []).slice(0, 5)
  const progress = useQueries({ queries: visibleBudgets.map((budget) => ({ queryKey: ['budget-progress', budget.id], queryFn: () => api.get<BudgetProgressResponse>(`/api/v1/budgets/${budget.id}/progress`).then((response) => response.data) })) })
  const budgetData = visibleBudgets.map((budget, index) => ({ budget, progress: progress[index]?.data })).filter((entry) => entry.progress)
  const progressError = progress.some((query) => query.isError)
  const hasCashFlow = analytics.data?.cashFlowTrend.some((month) => month.income > 0 || month.expenses > 0) ?? false
  const hasCategorySpending = (analytics.data?.categorySpending.length ?? 0) > 0
  const risk = fraud.data ? [{ name: 'Low', value: fraud.data.lowRisk, fill: '#059669' }, { name: 'Medium', value: fraud.data.mediumRisk, fill: '#d97706' }, { name: 'High', value: fraud.data.highRisk, fill: '#e11d48' }] : []

  const loadError = analytics.error ?? fraud.error ?? budgets.error ?? progress.find((query) => query.error)?.error
  if (loadError) return <div className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 p-5" role="alert">
    <p className="font-semibold text-rose-800">Financial visualizations could not be loaded.</p>
    <p className="mt-1 text-sm text-rose-700">{message(loadError)}</p>
    <button type="button" className="mt-3 rounded-lg border border-rose-300 bg-white px-3 py-2 text-sm font-medium text-rose-800" onClick={() => { void analytics.refetch(); void fraud.refetch(); void budgets.refetch(); if (progressError) progress.forEach((query) => { if (query.isError) void query.refetch() }) }}>Try again</button>
  </div>

  return <div className="mt-6 grid gap-5 xl:grid-cols-2">
    <ChartCard title="Cash flow trend" subtitle="Completed income and expenses over the last six months" loading={analytics.isLoading}>{hasCashFlow ? <ResponsiveContainer width="100%" height={270}><AreaChart data={analytics.data?.cashFlowTrend}><defs><linearGradient id="incomeGradient" x1="0" x2="0" y1="0" y2="1"><stop offset="5%" stopColor="#059669" stopOpacity={0.28} /><stop offset="95%" stopColor="#059669" stopOpacity={0} /></linearGradient><linearGradient id="expenseGradient" x1="0" x2="0" y1="0" y2="1"><stop offset="5%" stopColor="#e11d48" stopOpacity={0.22} /><stop offset="95%" stopColor="#e11d48" stopOpacity={0} /></linearGradient></defs><XAxis dataKey="month" tickFormatter={monthLabel} tickLine={false} axisLine={false} /><YAxis tickFormatter={(value) => `₹${Number(value) / 1000}k`} tickLine={false} axisLine={false} width={48} /><Tooltip formatter={(value) => money(Number(Array.isArray(value) ? value[0] : value ?? 0))} labelFormatter={(label) => monthLabel(String(label))} /><Legend /><Area type="monotone" dataKey="income" name="Income" stroke="#059669" fill="url(#incomeGradient)" strokeWidth={2} /><Area type="monotone" dataKey="expenses" name="Expenses" stroke="#e11d48" fill="url(#expenseGradient)" strokeWidth={2} /></AreaChart></ResponsiveContainer> : <Empty text="No completed transactions in this six-month period." />}</ChartCard>
    <ChartCard title="Spending by category" subtitle="Current-month completed expense transactions" loading={analytics.isLoading}>{hasCategorySpending ? <ResponsiveContainer width="100%" height={270}><PieChart><Pie data={analytics.data?.categorySpending} dataKey="amount" nameKey="categoryName" innerRadius={55} outerRadius={92} paddingAngle={3}>{analytics.data?.categorySpending.map((entry, index) => <Cell key={entry.categoryId ?? entry.categoryName} fill={pieColors[index % pieColors.length]} />)}</Pie><Tooltip formatter={(value) => money(Number(Array.isArray(value) ? value[0] : value ?? 0))} /><Legend /></PieChart></ResponsiveContainer> : <Empty text="No completed expenses this month." />}</ChartCard>
    <ChartCard title="Budget utilization" subtitle="Active budget progress, calculated by the budget engine" loading={budgets.isLoading}>{budgetData.length ? <div className="space-y-5">{budgetData.slice(0, 5).map(({ budget, progress }) => <BudgetBar key={budget.id} name={budget.name} progress={progress!} />)}</div> : <Empty text="No active budgets to visualize." />}</ChartCard>
    <ChartCard title="Fraud risk distribution" subtitle="Persisted fraud assessments by risk level" loading={fraud.isLoading}>{risk.some((entry) => entry.value > 0) ? <ResponsiveContainer width="100%" height={270}><BarChart data={risk} layout="vertical" margin={{ left: 8 }}><XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} /><YAxis dataKey="name" type="category" tickLine={false} axisLine={false} width={65} /><Tooltip formatter={(value) => [Number(Array.isArray(value) ? value[0] : value ?? 0), 'Assessments']} /><Bar dataKey="value" radius={[0, 7, 7, 0]}>{risk.map((entry) => <Cell key={entry.name} fill={entry.fill} />)}</Bar></BarChart></ResponsiveContainer> : <Empty text="No persisted fraud assessments yet." />}</ChartCard>
  </div>
}

function BudgetBar({ name, progress }: { name: string; progress: BudgetProgressResponse }) {
  const status = progress.exceeded ? 'Exceeded' : progress.usagePercent >= progress.warningThresholdPercent ? 'Warning' : 'On track'
  const color = progress.exceeded ? 'bg-rose-500' : progress.usagePercent >= progress.warningThresholdPercent ? 'bg-amber-500' : 'bg-emerald-600'
  return <div><div className="flex flex-wrap items-end justify-between gap-2"><div><p className="font-medium text-slate-800">{name}</p><p className="text-sm text-slate-500">{money(progress.totalSpent)} of {money(progress.totalLimit)}</p></div><span className={`text-sm font-semibold ${progress.exceeded ? 'text-rose-600' : progress.usagePercent >= progress.warningThresholdPercent ? 'text-amber-700' : 'text-emerald-700'}`}>{progress.usagePercent}% · {status}</span></div><div className="mt-2 h-3 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${color}`} style={{ width: `${Math.min(progress.usagePercent, 100)}%` }} /></div></div>
}

function ChartCard({ title, subtitle, loading, children }: { title: string; subtitle: string; loading: boolean; children: React.ReactNode }) {
  return <section className="min-h-[350px] rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-semibold text-slate-900">{title}</h3><p className="mt-1 text-sm text-slate-500">{subtitle}</p><div className="mt-5">{loading ? <div className="h-[270px] animate-pulse rounded-xl bg-slate-100" /> : children}</div></section>
}

function Empty({ text }: { text: string }) {
  return <div className="grid h-[270px] place-items-center rounded-xl border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500">{text}</div>
}
