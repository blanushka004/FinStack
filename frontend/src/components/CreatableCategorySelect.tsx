import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, message } from '../lib/api'
import type { Category } from '../types/api'

type Props = { categoryType: 'INCOME' | 'EXPENSE'; categories: Category[]; selectedCategoryId: string; onSelect: (categoryId: string) => void; disabled?: boolean; label?: string }

export function CreatableCategorySelect({ categoryType, categories, selectedCategoryId, onSelect, disabled, label = 'Category' }: Props) {
  const queryClient = useQueryClient()
  const available = categories.filter((category) => category.categoryType === categoryType)
  const selected = available.find((category) => category.id === selectedCategoryId)
  const [search, setSearch] = useState({ categoryType, term: '' })
  const [open, setOpen] = useState(false)
  const term = search.categoryType === categoryType ? search.term : ''
  const visibleTerm = open ? term : selected?.name ?? ''
  const normalized = term.trim().toLocaleLowerCase()
  const matches = available.filter((category) => category.name.toLocaleLowerCase().includes(normalized))
  const exact = available.some((category) => category.name.trim().toLocaleLowerCase() === normalized)
  const create = useMutation({
    mutationFn: () => api.post<Category>('/api/v1/categories', { name: term.trim(), categoryType }),
    onSuccess: (response) => { void queryClient.invalidateQueries({ queryKey: ['categories'] }); onSelect(response.data.id); setSearch({ categoryType, term: response.data.name }); setOpen(false) },
  })
  const choose = (category: Category) => { onSelect(category.id); setSearch({ categoryType, term: category.name }); setOpen(false) }
  return <div className="relative"><label className="text-sm font-medium text-slate-700">{label}<input value={visibleTerm} disabled={disabled || create.isPending} onFocus={() => { setSearch({ categoryType, term: selected?.name ?? '' }); setOpen(true) }} onChange={(event) => { setSearch({ categoryType, term: event.target.value }); onSelect(''); setOpen(true) }} placeholder={`Search ${categoryType.toLowerCase()} categories`} className="mt-1 w-full rounded-lg border p-2" role="combobox" aria-expanded={open} aria-label={label} /></label>{open && !disabled && <div className="absolute z-30 mt-1 max-h-48 w-full overflow-y-auto rounded-lg border bg-white p-1 shadow-lg">{matches.map((category) => <button type="button" key={category.id} onClick={() => choose(category)} className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-slate-50">{category.name}</button>)}{normalized && !exact && <button type="button" onClick={() => create.mutate()} disabled={create.isPending} className="block w-full rounded px-3 py-2 text-left text-sm font-medium text-brand hover:bg-emerald-50">{create.isPending ? 'Creating…' : `Create “${term.trim()}”`}</button>}{!matches.length && !normalized && <p className="px-3 py-2 text-sm text-slate-500">Type to search or create a category.</p>}{create.error && <p className="px-3 py-2 text-sm text-rose-600" role="alert">{message(create.error)}</p>}</div>}</div>
}
