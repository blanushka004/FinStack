import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { api, message } from '../lib/api'

type AccountFields = {
  name: string
  accountType: string
  institutionName: string
  currency: string
  currentBalance: string
  creditLimit: string
}

export function AccountCreate() {
  const queryClient = useQueryClient()
  const form = useForm<AccountFields>({ defaultValues: {
    name: '', accountType: 'BANK_ACCOUNT', institutionName: '', currency: 'INR', currentBalance: '0', creditLimit: '',
  } })
  const create = useMutation({
    mutationFn: (values: AccountFields) => api.post('/api/v1/accounts', {
      ...values,
      currentBalance: Number(values.currentBalance),
      creditLimit: values.creditLimit ? Number(values.creditLimit) : null,
    }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
    },
  })
  return <form onSubmit={form.handleSubmit((values) => create.mutate(values))} className="mb-5 grid gap-2 rounded-xl border bg-white p-4 md:grid-cols-3">
    <input aria-label="Account name" placeholder="Account name" {...form.register('name', { required: true })} className="rounded border p-2" />
    <select {...form.register('accountType')} className="rounded border p-2">{['BANK_ACCOUNT', 'SAVINGS_ACCOUNT', 'CREDIT_CARD', 'CASH', 'WALLET', 'INVESTMENT'].map((type) => <option key={type}>{type}</option>)}</select>
    <input placeholder="Institution" {...form.register('institutionName')} className="rounded border p-2" />
    <input placeholder="Currency" {...form.register('currency')} className="rounded border p-2" />
    <input type="number" step="0.01" placeholder="Initial balance" {...form.register('currentBalance', { required: true })} className="rounded border p-2" />
    <button disabled={create.isPending} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{create.isPending ? 'Saving…' : 'Add account'}</button>
    {create.error && <p className="text-sm text-rose-600">{message(create.error)}</p>}
  </form>
}

export function CategoryCreate() {
  const queryClient = useQueryClient()
  const form = useForm({ defaultValues: { name: '', categoryType: 'EXPENSE' } })
  const create = useMutation({
    mutationFn: (values: { name: string; categoryType: string }) => api.post('/api/v1/categories', values),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['categories'] }) },
  })
  return <form onSubmit={form.handleSubmit((values) => create.mutate(values))} className="mb-5 flex flex-wrap gap-2">
    <input aria-label="Category name" placeholder="New category" {...form.register('name', { required: true })} className="rounded border p-2" />
    <select {...form.register('categoryType')} className="rounded border p-2"><option>EXPENSE</option><option>INCOME</option></select>
    <button disabled={create.isPending} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Add category</button>
    {create.error && <p className="text-sm text-rose-600">{message(create.error)}</p>}
  </form>
}
