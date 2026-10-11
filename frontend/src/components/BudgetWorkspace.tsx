import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api, message } from '../lib/api'
import { CreatableCategorySelect } from './CreatableCategorySelect'
import type { Category } from '../types/api'
import type { BudgetCreateRequest, BudgetProgressResponse, BudgetResponse, BudgetUpdateRequest } from '../types/budget'

const money = (value: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value)
const amount = z.string().regex(/^\d{1,17}(\.\d{1,2})?$/, 'Enter a positive amount with up to two decimal places.').refine((value) => Number(value) > 0, 'Amount must be greater than zero.')
const budgetSchema = z.object({
  name: z.string().trim().min(1, 'Budget name is required.').max(120, 'Budget name must be 120 characters or fewer.'),
  budgetMonth: z.string().regex(/^\d{4}-\d{2}-01$/, 'Choose the first day of a month.'),
  totalLimit: amount,
  warningThresholdPercent: z.number().int().min(1, 'Threshold must be at least 1%.').max(100, 'Threshold cannot exceed 100%.'),
  categoryLimits: z.array(z.object({ categoryId: z.string().min(1, 'Choose an expense category.'), limit: amount })),
}).superRefine((value, context) => {
  const ids = new Set<string>()
  value.categoryLimits.forEach((allocation, index) => {
    if (ids.has(allocation.categoryId)) context.addIssue({ code: 'custom', path: ['categoryLimits', index, 'categoryId'], message: 'A category can only be allocated once.' })
    ids.add(allocation.categoryId)
  })
  const allocated = value.categoryLimits.reduce((total, allocation) => total + Number(allocation.limit), 0)
  if (allocated > Number(value.totalLimit)) context.addIssue({ code: 'custom', path: ['categoryLimits'], message: 'Category allocations cannot exceed the total budget.' })
})

type BudgetForm = z.infer<typeof budgetSchema>
const initialForm = (budget?: BudgetResponse): BudgetForm => ({
  name: budget?.name ?? '',
  budgetMonth: budget?.budgetMonth ?? new Date().toISOString().slice(0, 7) + '-01',
  totalLimit: budget?.totalLimit.toString() ?? '',
  warningThresholdPercent: budget?.warningThresholdPercent ?? 80,
  categoryLimits: budget?.categoryLimits.map((allocation) => ({ categoryId: allocation.categoryId, limit: allocation.limit.toString() })) ?? [],
})
const request = (values: BudgetForm): BudgetCreateRequest => ({
  name: values.name.trim(),
  budgetMonth: values.budgetMonth,
  totalLimit: Number(values.totalLimit),
  warningThresholdPercent: values.warningThresholdPercent,
  categoryLimits: values.categoryLimits.map((allocation) => ({ categoryId: allocation.categoryId, limit: Number(allocation.limit) })),
})

const progressClass = (progress: BudgetProgressResponse) => progress.exceeded ? 'bg-rose-500' : progress.usagePercent >= progress.warningThresholdPercent ? 'bg-amber-500' : 'bg-brand'

function refreshBudgetData(queryClient: ReturnType<typeof useQueryClient>, budgetId?: string) {
  void queryClient.invalidateQueries({ queryKey: ['budgets'] })
  void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
  void queryClient.invalidateQueries({ queryKey: ['alerts'] })
  void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === 'budget-progress' })
  if (budgetId) void queryClient.invalidateQueries({ queryKey: ['budget', budgetId] })
}

export function BudgetWorkspace() {
  const [editor, setEditor] = useState<BudgetResponse | 'create' | null>(null)
  const budgets = useQuery({ queryKey: ['budgets'], queryFn: () => api.get<BudgetResponse[]>('/api/v1/budgets', { params: { active: true } }).then((response) => response.data) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/v1/categories').then((response) => response.data) })
  const navigate = useNavigate()

  return <section>
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3"><h2 className="text-2xl font-bold">Budgets</h2><button type="button" onClick={() => setEditor('create')} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white">Create budget</button></div>
    {budgets.isLoading && <div className="rounded-2xl border bg-white p-8 text-sm text-slate-500">Loading budgets…</div>}
    {budgets.error && <Retry error={budgets.error} retry={() => budgets.refetch()} />}
    {budgets.data && <div className="grid gap-4 md:grid-cols-2">{budgets.data.map((budget) => <BudgetCard key={budget.id} budget={budget} onEdit={() => setEditor(budget)} onOpen={() => navigate(`/budgets/${budget.id}`)} />)}</div>}
    {budgets.data?.length === 0 && <p className="mt-6 rounded-2xl border border-dashed p-10 text-center text-slate-500">Create a monthly budget to stay on track.</p>}
    {editor && <BudgetEditor budget={editor === 'create' ? undefined : editor} categories={categories.data ?? []} onClose={() => setEditor(null)} />}
  </section>
}

function BudgetCard({ budget, onEdit, onOpen }: { budget: BudgetResponse; onEdit: () => void; onOpen: () => void }) {
  const progress = useQuery({ queryKey: ['budget-progress', budget.id], queryFn: () => api.get<BudgetProgressResponse>(`/api/v1/budgets/${budget.id}/progress`).then((response) => response.data) })
  const data = progress.data
  return <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <button type="button" onClick={onOpen} className="w-full text-left"><p className="font-semibold">{budget.name}</p><p className="text-sm text-slate-500">{budget.budgetMonth}</p></button>
    <p className="mt-4 text-sm">{data ? `${money(data.totalSpent)} of ${money(data.totalLimit)}` : 'Loading progress…'}</p>
    {data && <><div className="mt-2 h-2 overflow-hidden rounded bg-slate-100"><div className={`h-full ${progressClass(data)}`} style={{ width: `${Math.min(data.usagePercent, 100)}%` }} /></div><p className="mt-2 text-xs text-slate-500">{data.usagePercent}% used · {money(data.remaining)} remaining</p></>}
    <div className="mt-4 flex gap-3"><button type="button" onClick={onOpen} className="text-sm font-medium text-brand">View</button><button type="button" onClick={onEdit} className="text-sm font-medium text-brand">Edit</button></div>
  </div>
}

function BudgetEditor({ budget, categories, onClose }: { budget?: BudgetResponse; categories: Category[]; onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<BudgetForm>({ resolver: zodResolver(budgetSchema), defaultValues: initialForm(budget) })
  const allocations = useFieldArray({ control: form.control, name: 'categoryLimits' })
  const values = useWatch({ control: form.control })
  const allocated = (values.categoryLimits ?? []).reduce((total, allocation) => total + (Number(allocation.limit) || 0), 0)
  const remaining = (Number(values.totalLimit) || 0) - allocated
  const expenseCategories = categories.filter((category) => category.categoryType === 'EXPENSE')
  const mutation = useMutation({
    mutationFn: (values: BudgetForm) => budget
      ? api.put<BudgetResponse>(`/api/v1/budgets/${budget.id}`, request(values) as BudgetUpdateRequest)
      : api.post<BudgetResponse>('/api/v1/budgets', request(values)),
    onSuccess: () => { refreshBudgetData(queryClient, budget?.id); onClose() },
  })

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby="budget-editor-title">
    <form onSubmit={form.handleSubmit((values) => mutation.mutate(values))} className="max-h-[calc(100vh-2rem)] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
      <h3 id="budget-editor-title" className="text-lg font-semibold">{budget ? 'Edit budget' : 'Create budget'}</h3>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Field label="Budget name" error={form.formState.errors.name?.message}><input {...form.register('name')} className="mt-1 w-full rounded-lg border p-2" /></Field>
        <Field label="Budget month" error={form.formState.errors.budgetMonth?.message}><input type="date" {...form.register('budgetMonth')} className="mt-1 w-full rounded-lg border p-2" /></Field>
        <Field label="Total limit" error={form.formState.errors.totalLimit?.message}><input type="number" min="0.01" step="0.01" inputMode="decimal" {...form.register('totalLimit')} className="mt-1 w-full rounded-lg border p-2" /></Field>
        <Field label="Warning threshold (%)" error={form.formState.errors.warningThresholdPercent?.message}><input type="number" min="1" max="100" {...form.register('warningThresholdPercent', { valueAsNumber: true })} className="mt-1 w-full rounded-lg border p-2" /></Field>
      </div>
      <div className="mt-6"><div className="flex flex-wrap items-center justify-between gap-2"><div><h4 className="font-semibold">Expense allocations</h4><p className="text-sm text-slate-500">Allocated: {money(allocated)} · Remaining: <span className={remaining < 0 ? 'text-rose-600' : ''}>{money(remaining)}</span></p></div><button type="button" onClick={() => allocations.append({ categoryId: '', limit: '' })} className="rounded-lg border px-3 py-2 text-sm">Add allocation</button></div>
        {form.formState.errors.categoryLimits?.message && <p className="mt-2 text-sm text-rose-600" role="alert">{form.formState.errors.categoryLimits.message}</p>}
        <div className="mt-3 space-y-2">{allocations.fields.map((field, index) => { const selected = values.categoryLimits?.[index]?.categoryId ?? ''; return <div key={field.id} className="grid gap-2 sm:grid-cols-[1fr_150px_auto]"><div><CreatableCategorySelect label="Expense category" categoryType="EXPENSE" categories={expenseCategories} selectedCategoryId={selected} onSelect={(id) => form.setValue(`categoryLimits.${index}.categoryId`, id, { shouldValidate: true })} /></div><Field label="Limit" error={form.formState.errors.categoryLimits?.[index]?.limit?.message}><input type="number" min="0.01" step="0.01" {...form.register(`categoryLimits.${index}.limit`)} className="mt-1 w-full rounded-lg border p-2" /></Field><button type="button" onClick={() => allocations.remove(index)} className="self-end rounded-lg border border-rose-200 px-3 py-2 text-sm text-rose-600">Remove</button></div> })}</div>
      </div>
      {mutation.error && <p className="mt-4 text-sm text-rose-600" role="alert">{message(mutation.error)}</p>}
      <div className="mt-6 flex flex-wrap justify-end gap-2"><button type="button" onClick={onClose} disabled={mutation.isPending} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Cancel</button><button type="submit" disabled={mutation.isPending} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{mutation.isPending ? 'Saving…' : budget ? 'Save changes' : 'Create budget'}</button></div>
    </form>
  </div>
}

export function BudgetDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const queryClient = useQueryClient()
  const budget = useQuery({ queryKey: ['budget', id], enabled: Boolean(id), queryFn: () => api.get<BudgetResponse>(`/api/v1/budgets/${id}`).then((response) => response.data) })
  const progress = useQuery({ queryKey: ['budget-progress', id], enabled: Boolean(id), queryFn: () => api.get<BudgetProgressResponse>(`/api/v1/budgets/${id}/progress`).then((response) => response.data) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/v1/categories').then((response) => response.data) })
  const remove = useMutation({ mutationFn: () => api.delete(`/api/v1/budgets/${id}`), onSuccess: () => { refreshBudgetData(queryClient, id); navigate('/budgets') } })
  if (budget.isLoading || progress.isLoading) return <div className="rounded-2xl border bg-white p-8 text-sm text-slate-500">Loading budget…</div>
  if (budget.error) return <Retry error={budget.error} retry={() => budget.refetch()} />
  if (progress.error) return <Retry error={progress.error} retry={() => progress.refetch()} />
  const b = budget.data!
  const p = progress.data!
  return <section><button type="button" onClick={() => navigate('/budgets')} className="mb-5 text-sm font-medium text-brand">← Back to budgets</button><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-2xl font-bold">{b.name}</h2><p className="text-slate-500">Budget month: {b.budgetMonth}</p></div><div className="flex gap-2"><button type="button" onClick={() => setEditing(true)} className="rounded-lg border px-3 py-2 text-sm">Edit</button><button type="button" onClick={() => setDeleting(true)} className="rounded-lg border border-rose-200 px-3 py-2 text-sm text-rose-600">Delete</button></div></div>
    <div className="mt-6 rounded-2xl border bg-white p-5"><div className="flex flex-wrap justify-between gap-3"><div><p className="text-sm text-slate-500">Total budget</p><p className="text-2xl font-bold">{money(p.totalLimit)}</p></div><div><p className="text-sm text-slate-500">Spent</p><p className="text-2xl font-bold">{money(p.totalSpent)}</p></div><div><p className="text-sm text-slate-500">Remaining</p><p className={`text-2xl font-bold ${p.remaining < 0 ? 'text-rose-600' : ''}`}>{money(p.remaining)}</p></div></div><div className="mt-5 h-3 overflow-hidden rounded bg-slate-100"><div className={`h-full ${progressClass(p)}`} style={{ width: `${Math.min(p.usagePercent, 100)}%` }} /></div><p className="mt-2 text-sm text-slate-500">{p.usagePercent}% used · {p.exceeded ? 'Budget exceeded' : p.usagePercent >= p.warningThresholdPercent ? 'Warning threshold reached' : 'On track'}</p></div>
    <div className="mt-6 rounded-2xl border bg-white p-5"><h3 className="font-semibold">Category progress</h3><div className="mt-4 space-y-4">{p.categories.map((category) => <div key={category.categoryId}><div className="flex flex-wrap justify-between gap-2 text-sm"><span className="font-medium">{category.categoryName}</span><span>{money(category.spent)} of {money(category.limit)} · {category.usagePercent}%</span></div><div className="mt-2 h-2 overflow-hidden rounded bg-slate-100"><div className={`h-full ${category.exceeded ? 'bg-rose-500' : category.usagePercent >= p.warningThresholdPercent ? 'bg-amber-500' : 'bg-brand'}`} style={{ width: `${Math.min(category.usagePercent, 100)}%` }} /></div><p className={`mt-1 text-xs ${category.remaining < 0 ? 'text-rose-600' : 'text-slate-500'}`}>{money(category.remaining)} remaining {category.exceeded ? '· Exceeded' : ''}</p></div>)}{p.categories.length === 0 && <p className="text-sm text-slate-500">No category allocations.</p>}</div></div>
    {editing && <BudgetEditor budget={b} categories={categories.data ?? []} onClose={() => setEditing(false)} />}
    {deleting && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby="delete-budget-title"><div className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl"><h3 id="delete-budget-title" className="text-lg font-semibold">Deactivate budget?</h3><p className="mt-2 text-sm text-slate-600">This safely deactivates the budget; it does not permanently remove its history.</p>{remove.error && <p className="mt-3 text-sm text-rose-600" role="alert">{message(remove.error)}</p>}<div className="mt-5 flex flex-wrap justify-end gap-2"><button type="button" onClick={() => setDeleting(false)} disabled={remove.isPending} className="rounded-lg border px-3 py-2 text-sm">Cancel</button><button type="button" onClick={() => remove.mutate()} disabled={remove.isPending} className="rounded-lg bg-rose-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{remove.isPending ? 'Deactivating…' : 'Deactivate budget'}</button></div></div></div>}
  </section>
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-slate-700">{label}{children}{error && <span className="mt-1 block font-normal text-rose-600" role="alert">{error}</span>}</label> }
function Retry({ error, retry }: { error: unknown; retry: () => void }) { return <div className="rounded-2xl border bg-white p-6"><p className="text-sm text-rose-600">{message(error)}</p><button type="button" onClick={retry} className="mt-3 rounded-lg border px-3 py-2 text-sm">Try again</button></div> }
