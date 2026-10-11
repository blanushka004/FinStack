export const accountTypes = [
  'BANK_ACCOUNT',
  'SAVINGS_ACCOUNT',
  'CREDIT_CARD',
  'CASH',
  'WALLET',
  'INVESTMENT',
] as const

export type AccountType = (typeof accountTypes)[number]

/** Exact payload accepted by PUT /api/v1/accounts/{accountId}. */
export type AccountUpdateRequest = {
  name: string
  accountType: AccountType
  institutionName: string | null
  currency: string
  creditLimit: number | null
}
