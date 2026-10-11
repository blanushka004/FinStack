import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, message } from '../lib/api'
import type { FraudSummary, Page } from '../types/api'

type Assessment = {
  id: string; transactionId: string; amount: number; merchant?: string; categoryName?: string
  riskScore: number; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'; suspicious: boolean; reasons: string[]; evaluatedAt: string
}

const money = (value: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value)

export function FraudPage() {
  const summary = useQuery({ queryKey: ['fraud-summary'], queryFn: () => api.get<FraudSummary>('/api/v1/fraud/summary').then(response => response.data), refetchInterval: 30000 })
  const assessments = useQuery({ queryKey: ['fraud-assessments'], queryFn: () => api.get<Page<Assessment>>('/api/v1/fraud/assessments', { params: { page: 0, size: 30, sort: 'evaluatedAt,desc' } }).then(response => response.data), refetchInterval: 30000 })
  if (summary.error || assessments.error) return <p className="text-rose-600">{message(summary.error ?? assessments.error)}</p>
  const risk = summary.data ? [{ level: 'Low', count: summary.data.lowRisk, color: '#059669' }, { level: 'Medium', count: summary.data.mediumRisk, color: '#d97706' }, { level: 'High', count: summary.data.highRisk, color: '#e11d48' }] : []

  return <section>
    <h2 className="mb-6 text-2xl font-bold">Fraud &amp; Risk</h2>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{([['Low risk', summary.data?.lowRisk], ['Medium risk', summary.data?.mediumRisk], ['High risk', summary.data?.highRisk], ['Suspicious', summary.data?.suspicious]] as const).map(([label, value]) => <div key={label} className="rounded-2xl border bg-white p-5"><p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-2xl font-bold">{value ?? '—'}</p></div>)}</div>
    <section className="mt-6 rounded-2xl border bg-white p-5 shadow-sm"><h3 className="font-semibold">Risk distribution</h3><p className="mt-1 text-sm text-slate-500">Persisted assessments grouped by evaluated risk.</p>{summary.isLoading ? <div className="mt-5 h-56 animate-pulse rounded-xl bg-slate-100" /> : risk.some((entry) => entry.count > 0) ? <div className="mt-5 h-64"><ResponsiveContainer width="100%" height="100%"><BarChart data={risk}><XAxis dataKey="level" tickLine={false} axisLine={false} /><YAxis allowDecimals={false} tickLine={false} axisLine={false} /><Tooltip formatter={(value) => [Number(Array.isArray(value) ? value[0] : value ?? 0), 'Assessments']} /><Bar dataKey="count" radius={[7, 7, 0, 0]}>{risk.map((entry) => <Cell key={entry.level} fill={entry.color} />)}</Bar></BarChart></ResponsiveContainer></div> : <p className="mt-5 rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">No persisted fraud assessments yet.</p>}</section>
    <div className="mt-6 overflow-x-auto rounded-2xl border bg-white"><table className="w-full min-w-[650px] text-left text-sm"><thead className="bg-slate-50"><tr><th className="p-3">Risk</th><th className="p-3">Score</th><th className="p-3">Transaction</th><th className="p-3">Reasons</th><th className="p-3">Evaluated</th></tr></thead><tbody>{assessments.data?.content.map(assessment => <tr key={assessment.id} className="border-t"><td className={`p-3 font-semibold ${assessment.riskLevel === 'HIGH' ? 'text-rose-600' : assessment.riskLevel === 'MEDIUM' ? 'text-amber-600' : 'text-emerald-700'}`}>{assessment.riskLevel}{assessment.suspicious ? ' · Suspicious' : ''}</td><td className="p-3">{assessment.riskScore}</td><td className="p-3">{assessment.merchant ?? assessment.categoryName ?? 'Transaction'}<span className="ml-2 text-xs text-slate-500">{money(assessment.amount)}</span></td><td className="p-3">{assessment.reasons.length ? assessment.reasons.join(', ') : 'No triggered rules'}</td><td className="p-3">{new Date(assessment.evaluatedAt).toLocaleString()}</td></tr>)}</tbody></table>{!assessments.isLoading && assessments.data?.content.length === 0 && <p className="p-8 text-center text-sm text-slate-500">No assessments have been persisted yet.</p>}</div>
  </section>
}
