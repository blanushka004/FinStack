import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, message } from '../lib/api'
import type { Account } from '../types/api'
import { accountTypes, type AccountType, type AccountUpdateRequest } from '../types/account'

const formSchema = z
  .object({
    name: z.string().trim().min(1, 'Account name is required.').max(120, 'Account name must be 120 characters or fewer.'),
    accountType: z.enum(accountTypes),
    institutionName: z.string().trim().max(150, 'Institution name must be 150 characters or fewer.'),
    currency: z.string().trim().regex(/^[A-Za-z]{3}$/, 'Use a three-letter currency code.'),
    creditLimit: z.string().trim().refine(
      (value) => value === '' || /^\d{1,17}(\.\d{1,2})?$/.test(value),
      'Credit limit must be a non-negative amount with up to two decimal places.',
    ),
  })
  .superRefine((value, context) => {
    if (value.accountType !== 'CREDIT_CARD' && value.creditLimit !== '' && Number(value.creditLimit) > 0) {
      context.addIssue({
        code: 'custom',
        path: ['creditLimit'],
        message: 'A positive credit limit is only valid for a credit-card account.',
      })
    }
  })

type AccountUpdateForm = z.infer<typeof formSchema>

const initialValues = (account: Account): AccountUpdateForm => ({
  name: account.name,
  accountType: account.accountType as AccountType,
  institutionName: account.institutionName ?? '',
  currency: account.currency,
  creditLimit: account.creditLimit?.toString() ?? '',
})

const toRequest = (values: AccountUpdateForm): AccountUpdateRequest => ({
  name: values.name.trim(),
  accountType: values.accountType,
  institutionName: values.institutionName.trim() || null,
  currency: values.currency.trim().toUpperCase(),
  creditLimit: values.creditLimit === '' ? null : Number(values.creditLimit),
})

export function AccountActions({ account }: { account: Account }) {
  const [dialog, setDialog] = useState<'edit' | 'deactivate' | null>(null)
  const queryClient = useQueryClient()
  const form = useForm<AccountUpdateForm>({
    resolver: zodResolver(formSchema),
    defaultValues: initialValues(account),
  })

  const invalidateAccountData = () => {
    void queryClient.invalidateQueries({ queryKey: ['accounts'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
  }

  const update = useMutation({
    mutationFn: (values: AccountUpdateForm) =>
      api.put<Account>(`/api/v1/accounts/${account.id}`, toRequest(values)),
    onSuccess: () => {
      invalidateAccountData()
      setDialog(null)
    },
  })

  const deactivate = useMutation({
    mutationFn: () => api.delete(`/api/v1/accounts/${account.id}`),
    onSuccess: () => {
      invalidateAccountData()
      setDialog(null)
    },
  })

  const openEdit = () => {
    form.reset(initialValues(account))
    update.reset()
    setDialog('edit')
  }

  if (!account.active) {
    return <span className="mt-4 inline-block text-xs font-medium text-slate-400">Inactive</span>
  }

  return <>
    <div className="mt-4 flex flex-wrap gap-3">
      <button type="button" onClick={openEdit} className="text-sm font-medium text-brand focus:outline-none focus:ring-2 focus:ring-brand/40">
        Edit
      </button>
      <button type="button" onClick={() => { deactivate.reset(); setDialog('deactivate') }} className="text-sm font-medium text-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-300">
        Deactivate
      </button>
    </div>

    {dialog && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby="account-dialog-title">
      <div className="max-h-[calc(100vh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        {dialog === 'edit' ? <form onSubmit={form.handleSubmit((values) => update.mutate(values))} className="space-y-3">
          <h3 id="account-dialog-title" className="text-lg font-semibold">Edit account</h3>
          <FieldError error={form.formState.errors.name?.message}>
            <label htmlFor={`account-name-${account.id}`}>Name</label>
            <input id={`account-name-${account.id}`} {...form.register('name')} className="mt-1 w-full rounded-lg border p-2" />
          </FieldError>
          <FieldError error={form.formState.errors.accountType?.message}>
            <label htmlFor={`account-type-${account.id}`}>Account type</label>
            <select id={`account-type-${account.id}`} {...form.register('accountType')} className="mt-1 w-full rounded-lg border p-2">
              {accountTypes.map((type) => <option key={type} value={type}>{type.replaceAll('_', ' ')}</option>)}
            </select>
          </FieldError>
          <FieldError error={form.formState.errors.institutionName?.message}>
            <label htmlFor={`account-institution-${account.id}`}>Institution</label>
            <input id={`account-institution-${account.id}`} {...form.register('institutionName')} className="mt-1 w-full rounded-lg border p-2" />
          </FieldError>
          <FieldError error={form.formState.errors.currency?.message}>
            <label htmlFor={`account-currency-${account.id}`}>Currency</label>
            <input id={`account-currency-${account.id}`} maxLength={3} {...form.register('currency')} className="mt-1 w-full rounded-lg border p-2 uppercase" />
          </FieldError>
          <FieldError error={form.formState.errors.creditLimit?.message}>
            <label htmlFor={`account-credit-limit-${account.id}`}>Credit limit</label>
            <input id={`account-credit-limit-${account.id}`} type="number" min="0" step="0.01" inputMode="decimal" {...form.register('creditLimit')} className="mt-1 w-full rounded-lg border p-2" />
          </FieldError>
          {update.error && <p className="text-sm text-rose-600" role="alert">{message(update.error)}</p>}
          <div className="flex flex-wrap justify-end gap-2 pt-2">
            <button type="button" onClick={() => setDialog(null)} disabled={update.isPending} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Cancel</button>
            <button type="submit" disabled={update.isPending} className="rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">
              {update.isPending ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form> : <div>
          <h3 id="account-dialog-title" className="text-lg font-semibold">Deactivate account?</h3>
          <p className="mt-2 text-sm text-slate-600">{account.name} will become inactive and cannot receive new transactions.</p>
          {deactivate.error && <p className="mt-3 text-sm text-rose-600" role="alert">{message(deactivate.error)}</p>}
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            <button type="button" onClick={() => setDialog(null)} disabled={deactivate.isPending} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Cancel</button>
            <button type="button" onClick={() => deactivate.mutate()} disabled={deactivate.isPending} className="rounded-lg bg-rose-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">
              {deactivate.isPending ? 'Deactivating…' : 'Deactivate'}
            </button>
          </div>
        </div>}
      </div>
    </div>}
  </>
}

function FieldError({ children, error }: { children: React.ReactNode; error?: string }) {
  return <div className="text-sm">
    {children}
    {error && <p className="mt-1 text-rose-600" role="alert">{error}</p>}
  </div>
}
