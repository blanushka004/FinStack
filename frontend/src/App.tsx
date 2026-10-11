import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, message } from './lib/api'
import { useAuth } from './app/AuthContext'
import type { Account, Category, Dashboard } from './types/api'
import { AccountCreate, CategoryCreate } from './components/CreateForms'
import { AccountActions } from './components/AccountActions'
import { EnhancedAuthPage, VerificationPending, VerifyEmailPage } from './components/AuthPages'
import { ForgotPasswordPage, ResetPasswordPage } from './components/PasswordResetPages'
import { AdminLoginActivity } from './components/AdminLoginActivity'
import { AppShell } from './components/AppShell'
import { AlertsPage } from './components/AlertsPage'

const FraudPage = lazy(() => import('./components/FraudPage').then((module) => ({ default: module.FraudPage })))
const TransactionWorkspace = lazy(() => import('./components/TransactionWorkspace').then((module) => ({ default: module.TransactionWorkspace })))
const BudgetWorkspace = lazy(() => import('./components/BudgetWorkspace').then((module) => ({ default: module.BudgetWorkspace })))
const BudgetDetail = lazy(() => import('./components/BudgetWorkspace').then((module) => ({ default: module.BudgetDetail })))

function LoadingPage() {
  return <div className="h-56 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading page" />
}

const money = (amount: number, currency = 'INR') => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency, maximumFractionDigits: 2,
}).format(amount)

function Card({ children }: { children: React.ReactNode }) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">{children}</div>
}

function QueryError({ error, retry }: { error: unknown; retry: () => void }) {
  return <div className="rounded-2xl border border-rose-200 bg-rose-50 p-5" role="alert">
    <p className="text-sm text-rose-700">{message(error)}</p>
    <button type="button" onClick={retry} className="mt-3 rounded-lg border border-rose-300 bg-white px-3 py-2 text-sm font-medium text-rose-800">Try again</button>
  </div>
}

function Protected() {
  return useAuth().user ? <AppShell /> : <Navigate to="/login" replace />
}

function DashboardPage() {
  const summary = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: () => api.get<Dashboard>('/api/v1/dashboard/summary').then((response) => response.data),
    refetchInterval: 45000,
  })
  if (summary.isLoading) return <div className="h-56 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading dashboard" />
  if (summary.error) return <QueryError error={summary.error} retry={() => void summary.refetch()} />
  const data = summary.data!
  return <section>
    <h2 className="mb-6 text-2xl font-bold">Dashboard</h2>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {([['Total balance', money(data.totalBalance)], ['Income this month', money(data.totalIncome)],
        ['Expenses this month', money(data.totalExpenses)], ['Net cash flow', money(data.netCashFlow)]] as const)
        .map(([label, value]) => <Card key={label}><p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-2xl font-bold">{value}</p></Card>)}
    </div>
    <div className="mt-5 grid gap-4 md:grid-cols-3">
      {([['Active accounts', data.activeAccounts], ['Active budgets', data.activeBudgets],
        ['Unread alerts', data.unreadAlerts]] as const)
        .map(([label, value]) => <Card key={label}><p className="font-semibold">{label}</p><p className="mt-2 text-3xl">{value}</p></Card>)}
    </div>
  </section>
}

function Accounts() {
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api.get<Account[]>('/api/v1/accounts', { params: { active: true } }).then((response) => response.data) })
  return <section>
    <h2 className="mb-6 text-2xl font-bold">Accounts</h2>
    <AccountCreate />
    {accounts.isLoading && <div className="h-40 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading accounts" />}
    {accounts.error && <QueryError error={accounts.error} retry={() => void accounts.refetch()} />}
    {accounts.data && <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{accounts.data.map((account) => {
      const negative = account.currentBalance < 0
      return <Card key={account.id}>
        <p className="font-semibold">{account.name}</p>
        <p className="text-sm text-slate-500">{account.accountType.replaceAll('_', ' ')} · {account.institutionName || 'Personal'}</p>
        <p className={`mt-5 text-2xl font-bold ${negative ? 'text-rose-600' : ''}`}>{money(account.currentBalance, account.currency)}</p>
        {negative && <p className="mt-1 text-xs font-medium text-rose-600">Credit utilized / negative balance</p>}
        <AccountActions account={account} />
      </Card>
    })}</div>}
    {accounts.data?.length === 0 && <Empty text="Add your first financial account." />}
  </section>
}

function Categories() {
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/v1/categories').then((response) => response.data) })
  return <section>
    <h2 className="mb-6 text-2xl font-bold">Categories</h2>
    <CategoryCreate />
    {categories.isLoading && <div className="h-40 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading categories" />}
    {categories.error && <QueryError error={categories.error} retry={() => void categories.refetch()} />}
    {categories.data && <div className="grid gap-3 sm:grid-cols-2">{categories.data.map((category) => <Card key={category.id}>
      <span className={`rounded-full px-2 py-1 text-xs ${category.categoryType === 'INCOME' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>{category.categoryType}</span>
      <p className="mt-3 font-semibold">{category.name}</p>
      <p className="text-sm text-slate-500">{category.systemDefined ? 'System category' : 'Custom category'}</p>
    </Card>)}</div>}
    {categories.data?.length === 0 && <Empty text="No categories available yet." />}
  </section>
}

function Empty({ text }: { text: string }) {
  return <div className="mt-6 rounded-2xl border border-dashed p-10 text-center text-slate-500">{text}</div>
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<EnhancedAuthPage />} />
    <Route path="/register" element={<EnhancedAuthPage register />} />
    <Route path="/forgot-password" element={<ForgotPasswordPage />} />
    <Route path="/reset-password" element={<ResetPasswordPage />} />
    <Route path="/verify-email" element={<VerifyEmailPage />} />
    <Route element={<Protected />}>
      <Route path="/verification-pending" element={<VerificationPending />} />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/accounts" element={<Accounts />} />
      <Route path="/transactions" element={<Suspense fallback={<LoadingPage />}><TransactionWorkspace /></Suspense>} />
      <Route path="/categories" element={<Categories />} />
      <Route path="/budgets" element={<Suspense fallback={<LoadingPage />}><BudgetWorkspace /></Suspense>} />
      <Route path="/budgets/:id" element={<Suspense fallback={<LoadingPage />}><BudgetDetail /></Suspense>} />
      <Route path="/alerts" element={<AlertsPage />} />
      <Route path="/fraud" element={<Suspense fallback={<LoadingPage />}><FraudPage /></Suspense>} />
      <Route path="/admin/login-activity" element={<AdminLoginActivity />} />
    </Route>
    <Route path="*" element={<Navigate to="/dashboard" replace />} />
  </Routes>
}
