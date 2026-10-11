export const transactionTypes = ['INCOME', 'EXPENSE'] as const
export const transactionStatuses = ['COMPLETED', 'PENDING', 'CANCELLED'] as const
export const paymentMethods = ['CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'UPI', 'BANK_TRANSFER', 'WALLET', 'OTHER'] as const

export type TransactionType = (typeof transactionTypes)[number]
export type TransactionStatus = (typeof transactionStatuses)[number]
export type PaymentMethod = (typeof paymentMethods)[number]

/** Exact shape returned by the transaction controller. */
export type TransactionResponse = {
  id: string
  accountId: string
  accountName: string
  categoryId: string | null
  categoryName: string | null
  transactionType: TransactionType
  amount: number
  merchant: string | null
  description: string | null
  paymentMethod: PaymentMethod | null
  transactionTime: string
  status: TransactionStatus
  reference: string | null
  createdAt: string
  updatedAt: string
}

/** Exact payload accepted by PUT /api/v1/transactions/{transactionId}. */
export type TransactionUpdateRequest = {
  accountId: string
  categoryId: string | null
  transactionType: TransactionType
  amount: number
  merchant: string | null
  description: string | null
  paymentMethod: PaymentMethod | null
  transactionTime: string
  status: TransactionStatus
  reference: string | null
}

export type TransactionFilters = {
  accountId: string
  categoryId: string
  type: '' | TransactionType
  status: '' | TransactionStatus
  from: string
  to: string
  search: string
  page: number
  size: number
  sort: string
}

export type TransactionPage = {
  content: TransactionResponse[]
  totalPages: number
  totalElements: number
  size: number
  number: number
  numberOfElements: number
  first: boolean
  last: boolean
  empty: boolean
}
