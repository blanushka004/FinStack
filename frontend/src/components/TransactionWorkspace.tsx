import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { TransactionComposer } from './TransactionComposer'
import { CreatableCategorySelect } from './CreatableCategorySelect'
import { api, message } from '../lib/api'
import type { Account, Category } from '../types/api'
import {
  paymentMethods,
  transactionStatuses,
  transactionTypes,
  type PaymentMethod,
  type TransactionFilters,
  type TransactionPage,
  type TransactionResponse,
  type TransactionStatus,
  type TransactionUpdateRequest,
} from '../types/transaction'

const defaultFilters: TransactionFilters = {
  accountId: '', categoryId: '', type: '', status: '', from: '', to: '', search: '', page: 0, size: 20, sort: 'transactionTime,desc',
}

const filterParams = (filters: TransactionFilters) => ({
  accountId: filters.accountId || undefined,
  categoryId: filters.categoryId || undefined,
  type: filters.type || undefined,
  status: filters.status || undefined,
  from: filters.from ? new Date(filters.from).toISOString() : undefined,
  to: filters.to ? new Date(filters.to).toISOString() : undefined,
  search: filters.search.trim() || undefined,
  page: filters.page,
  size: filters.size,
  sort: filters.sort,
})

const money = (value: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value)
type FraudAssessment = { transactionId: string; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'; suspicious: boolean }

export function TransactionWorkspace() {
  const [draft, setDraft] = useState(defaultFilters)
  const [filters, setFilters] = useState(defaultFilters)
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api.get<Account[]>('/api/v1/accounts', { params: { active: true } }).then((response) => response.data) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/v1/categories').then((response) => response.data) })
  const transactions = useQuery({
    queryKey: ['transactions', filters],
    queryFn: () => api.get<TransactionPage>('/api/v1/transactions', { params: filterParams(filters) }).then((response) => response.data),
  })
  const fraud = useQuery({ queryKey: ['fraud-assessments'], queryFn: () => api.get<{ content: FraudAssessment[] }>('/api/v1/fraud/assessments', { params: { page: 0, size: 100, sort: 'evaluatedAt,desc' } }).then((response) => response.data) })
  const riskByTransaction = new Map(fraud.data?.content.map((assessment) => [assessment.transactionId, assessment]))

  const apply = () => setFilters({ ...draft, page: 0 })
  const reset = () => {
    setDraft(defaultFilters)
    setFilters(defaultFilters)
  }
  const goToPage = (page: number) => {
    const next = { ...filters, page }
    setFilters(next)
    setDraft(next)
  }

  return <section>
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h2 className="text-2xl font-bold">Transactions</h2>
      <span className="text-sm text-slate-500">{transactions.data?.totalElements ?? 0} records</span>
    </div>
    <TransactionComposer />
    <div className="mb-5 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Select label="Account" value={draft.accountId} onChange={(accountId) => setDraft({ ...draft, accountId })}>
          <option value="">All accounts</option>
          {accounts.data?.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
        </Select>
        <Select label="Category" value={draft.categoryId} onChange={(categoryId) => setDraft({ ...draft, categoryId })}>
          <option value="">All categories</option>
          {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </Select>
        <Select label="Type" value={draft.type} onChange={(type) => setDraft({ ...draft, type: type as TransactionFilters['type'] })}>
          <option value="">All types</option><option value="INCOME">Income</option><option value="EXPENSE">Expense</option>
        </Select>
        <Select label="Status" value={draft.status} onChange={(status) => setDraft({ ...draft, status: status as TransactionFilters['status'] })}>
          <option value="">All statuses</option>{transactionStatuses.map((status) => <option key={status} value={status}>{status}</option>)}
        </Select>
        <label className="text-sm font-medium text-slate-700">From
          <input type="datetime-local" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} className="mt-1 w-full rounded-lg border p-2 font-normal" />
        </label>
        <label className="text-sm font-medium text-slate-700">To
          <input type="datetime-local" value={draft.to} onChange={(event) => setDraft({ ...draft, to: event.target.value })} className="mt-1 w-full rounded-lg border p-2 font-normal" />
        </label>
        <label className="text-sm font-medium text-slate-700 lg:col-span-2">Search merchant or description
          <input value={draft.search} onChange={(event) => setDraft({ ...draft, search: event.target.value })} placeholder="Search transactions" className="mt-1 w-full rounded-lg border p-2 font-normal" />
        </label>
      </div>
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <button type="button" onClick={reset} className="rounded-lg border px-3 py-2 text-sm">Reset</button>
        <button type="button" onClick={apply} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white">Apply filters</button>
      </div>
    </div>
    {transactions.isLoading && <div className="rounded-2xl border bg-white p-8 text-sm text-slate-500">Loading transactions…</div>}
    {transactions.error && <div className="rounded-2xl border bg-white p-6"><p className="text-sm text-rose-600">{message(transactions.error)}</p><button type="button" onClick={() => transactions.refetch()} className="mt-3 rounded-lg border px-3 py-2 text-sm">Try again</button></div>}
    {transactions.data && <>
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500"><tr><th className="p-3">Date</th><th className="p-3">Account</th><th className="p-3">Category</th><th className="p-3">Description</th><th className="p-3">Amount</th><th className="p-3">Status</th><th className="p-3">Actions</th></tr></thead>
          <tbody>{transactions.data.content.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} accounts={accounts.data ?? []} categories={categories.data ?? []} risk={riskByTransaction.get(transaction.id)} />)}</tbody>
        </table>
      </div>
      {transactions.data.empty && <p className="mt-5 rounded-2xl border border-dashed p-10 text-center text-slate-500">Start tracking your income and expenses.</p>}
      <div className="mt-4 flex items-center justify-between gap-3 text-sm">
        <span>Page {transactions.data.number + 1} of {Math.max(transactions.data.totalPages, 1)}</span>
        <div className="flex gap-2">
          <button type="button" disabled={transactions.data.first} onClick={() => goToPage(transactions.data.number - 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Previous</button>
          <button type="button" disabled={transactions.data.last} onClick={() => goToPage(transactions.data.number + 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Next</button>
        </div>
      </div>
    </>}
  </section>
}

function Select({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: React.ReactNode }) {
  return <label className="text-sm font-medium text-slate-700">{label}<select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-lg border p-2 font-normal">{children}</select></label>
}

const updateSchema = z.object({
  accountId: z.string().min(1, 'Choose an account.'),
  categoryId: z.string(),
  transactionType: z.enum(transactionTypes),
  amount: z.string().regex(/^\d{1,17}(\.\d{1,2})?$/, 'Enter an amount greater than zero with up to two decimal places.').refine((value) => Number(value) > 0, 'Amount must be greater than zero.'),
  merchant: z.string().trim().max(180, 'Merchant must be 180 characters or fewer.'),
  description: z.string().trim().max(500, 'Description must be 500 characters or fewer.'),
  paymentMethod: z.string(),
  transactionTime: z.string().min(1, 'Transaction time is required.'),
  status: z.enum(['COMPLETED', 'PENDING']),
  reference: z.string().trim().max(120, 'Reference must be 120 characters or fewer.'),
})

type TransactionUpdateForm = z.infer<typeof updateSchema>

const initialFormValues = (transaction: TransactionResponse): TransactionUpdateForm => ({
  accountId: transaction.accountId,
  categoryId: transaction.categoryId ?? '',
  transactionType: transaction.transactionType,
  amount: transaction.amount.toString(),
  merchant: transaction.merchant ?? '',
  description: transaction.description ?? '',
  paymentMethod: transaction.paymentMethod ?? '',
  transactionTime: new Date(transaction.transactionTime).toISOString().slice(0, 16),
  status: transaction.status === 'PENDING' ? 'PENDING' : 'COMPLETED',
  reference: transaction.reference ?? '',
})

const updateRequest = (values: TransactionUpdateForm): TransactionUpdateRequest => ({
  accountId: values.accountId,
  categoryId: values.categoryId || null,
  transactionType: values.transactionType,
  amount: Number(values.amount),
  merchant: values.merchant.trim() || null,
  description: values.description.trim() || null,
  paymentMethod: values.paymentMethod === '' ? null : values.paymentMethod as PaymentMethod,
  transactionTime: new Date(values.transactionTime).toISOString(),
  status: values.status as TransactionStatus,
  reference: values.reference.trim() || null,
})

function TransactionRow({ transaction, accounts, categories, risk }: { transaction: TransactionResponse; accounts: Account[]; categories: Category[]; risk?: FraudAssessment }) {
  const [dialog, setDialog] = useState<'edit' | 'cancel' | null>(null)
  const queryClient = useQueryClient()
  const form = useForm<TransactionUpdateForm>({ resolver: zodResolver(updateSchema), defaultValues: initialFormValues(transaction) })
  const selectedType = useWatch({ control: form.control, name: 'transactionType' })
  const selectedCategoryId = useWatch({ control: form.control, name: 'categoryId' })
  useEffect(() => {
    const categoryId = form.getValues('categoryId')
    if (categoryId && categories.length > 0 && !categories.some((category) => category.id === categoryId && category.categoryType === selectedType)) {
      form.setValue('categoryId', '')
    }
  }, [categories, form, selectedType])
  const refreshFinancialData = () => {
    void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    void queryClient.invalidateQueries({ queryKey: ['accounts'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-analytics'] })
    void queryClient.invalidateQueries({ queryKey: ['budgets'] })
    void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    void queryClient.invalidateQueries({ queryKey: ['fraud-assessments'] })
    void queryClient.invalidateQueries({ queryKey: ['fraud-summary'] })
    void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === 'budget-progress' })
  }
  const update = useMutation({
    mutationFn: (values: TransactionUpdateForm) => api.put<TransactionResponse>(`/api/v1/transactions/${transaction.id}`, updateRequest(values)),
    onSuccess: () => { refreshFinancialData(); setDialog(null) },
  })
  const cancel = useMutation({
    mutationFn: () => api.delete(`/api/v1/transactions/${transaction.id}`),
    onSuccess: () => { refreshFinancialData(); setDialog(null) },
  })
  const openEdit = () => { form.reset(initialFormValues(transaction)); update.reset(); setDialog('edit') }
  const cancelled = transaction.status === 'CANCELLED'

  return <>
    <tr className="border-t border-slate-100">
      <td className="p-3 whitespace-nowrap">{new Date(transaction.transactionTime).toLocaleDateString()}</td>
      <td className="p-3">{transaction.accountName}</td>
      <td className="p-3">{transaction.categoryName ?? 'Uncategorized'}</td>
      <td className="p-3">{transaction.description ?? transaction.merchant ?? '—'}</td>
      <td className={`p-3 font-medium ${transaction.transactionType === 'INCOME' ? 'text-emerald-700' : 'text-rose-700'}`}>{transaction.transactionType === 'INCOME' ? '+' : '-'}{money(transaction.amount)}</td>
      <td className="p-3"><Status status={transaction.status} /> {risk ? <RiskBadge risk={risk} /> : <span className="ml-1 text-xs text-slate-400">Not assessed</span>}</td>
      <td className="p-3">{cancelled ? <span className="text-xs text-slate-400">Cancelled</span> : <div className="flex gap-3"><button type="button" onClick={openEdit} className="font-medium text-brand">Edit</button><button type="button" onClick={() => { cancel.reset(); setDialog('cancel') }} className="font-medium text-rose-600">Cancel</button></div>}</td>
    </tr>
    {dialog && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby={`transaction-dialog-${transaction.id}`}>
      <div className="max-h-[calc(100vh-2rem)] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        {dialog === 'edit' ? <form onSubmit={form.handleSubmit((values) => update.mutate(values))} className="grid gap-3 sm:grid-cols-2">
          <h3 id={`transaction-dialog-${transaction.id}`} className="col-span-full text-lg font-semibold">Edit transaction</h3>
          <FormField label="Account" error={form.formState.errors.accountId?.message}><select {...form.register('accountId')} className="mt-1 w-full rounded-lg border p-2"><option value="">Choose an account</option>{accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}</select></FormField>
          <FormField label="Type" error={form.formState.errors.transactionType?.message}><select {...form.register('transactionType')} className="mt-1 w-full rounded-lg border p-2">{transactionTypes.map((type) => <option key={type} value={type}>{type}</option>)}</select></FormField>
          <FormField label="Category" error={form.formState.errors.categoryId?.message}><CreatableCategorySelect categoryType={selectedType} categories={categories} selectedCategoryId={selectedCategoryId} onSelect={(id) => form.setValue('categoryId', id, { shouldValidate: true })} /></FormField>
          <FormField label="Amount" error={form.formState.errors.amount?.message}><input type="number" min="0.01" step="0.01" inputMode="decimal" {...form.register('amount')} className="mt-1 w-full rounded-lg border p-2" /></FormField>
          <FormField label="Transaction time" error={form.formState.errors.transactionTime?.message}><input type="datetime-local" {...form.register('transactionTime')} className="mt-1 w-full rounded-lg border p-2" /></FormField>
          <FormField label="Status" error={form.formState.errors.status?.message}><select {...form.register('status')} className="mt-1 w-full rounded-lg border p-2"><option value="COMPLETED">COMPLETED</option><option value="PENDING">PENDING</option></select></FormField>
          <FormField label="Merchant" error={form.formState.errors.merchant?.message}><input {...form.register('merchant')} className="mt-1 w-full rounded-lg border p-2" /></FormField>
          <FormField label="Payment method" error={form.formState.errors.paymentMethod?.message}><select {...form.register('paymentMethod')} className="mt-1 w-full rounded-lg border p-2"><option value="">Not specified</option>{paymentMethods.map((method) => <option key={method} value={method}>{method.replaceAll('_', ' ')}</option>)}</select></FormField>
          <FormField label="Description" error={form.formState.errors.description?.message}><input {...form.register('description')} className="mt-1 w-full rounded-lg border p-2" /></FormField>
          <FormField label="Reference" error={form.formState.errors.reference?.message}><input {...form.register('reference')} className="mt-1 w-full rounded-lg border p-2" /></FormField>
          {update.error && <p className="col-span-full text-sm text-rose-600" role="alert">{message(update.error)}</p>}
          <div className="col-span-full flex flex-wrap justify-end gap-2 pt-2"><button type="button" onClick={() => setDialog(null)} disabled={update.isPending} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Cancel</button><button type="submit" disabled={update.isPending} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{update.isPending ? 'Saving…' : 'Save changes'}</button></div>
        </form> : <div>
          <h3 id={`transaction-dialog-${transaction.id}`} className="text-lg font-semibold">Cancel transaction?</h3>
          <p className="mt-2 text-sm text-slate-600">This cancels—not permanently deletes—the transaction. Cancelling a completed transaction may update the account balance and budget progress.</p>
          {cancel.error && <p className="mt-3 text-sm text-rose-600" role="alert">{message(cancel.error)}</p>}
          <div className="mt-5 flex flex-wrap justify-end gap-2"><button type="button" onClick={() => setDialog(null)} disabled={cancel.isPending} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Keep transaction</button><button type="button" onClick={() => cancel.mutate()} disabled={cancel.isPending} className="rounded-lg bg-rose-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{cancel.isPending ? 'Cancelling…' : 'Cancel transaction'}</button></div>
        </div>}
      </div>
    </div>}
  </>
}

function FormField({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <label className="text-sm font-medium text-slate-700">{label}{children}{error && <span className="mt-1 block text-sm font-normal text-rose-600" role="alert">{error}</span>}</label>
}

function Status({ status }: { status: TransactionStatus }) {
  const classes = status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-700' : status === 'PENDING' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'
  return <span className={`rounded-full px-2 py-1 text-xs font-medium ${classes}`}>{status}</span>
}

function RiskBadge({ risk }: { risk: FraudAssessment }) {
  const classes = risk.riskLevel === 'HIGH' ? 'bg-rose-100 text-rose-700' : risk.riskLevel === 'MEDIUM' ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'
  return <span className={`ml-1 rounded-full px-2 py-1 text-xs font-medium ${classes}`}>{risk.riskLevel} risk{risk.suspicious ? ' · Suspicious' : ''}</span>
}
