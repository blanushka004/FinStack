export type BudgetCategoryRequest = { categoryId: string; limit: number }

export type BudgetCreateRequest = {
  name: string
  budgetMonth: string
  totalLimit: number
  warningThresholdPercent: number
  categoryLimits: BudgetCategoryRequest[]
}

export type BudgetUpdateRequest = BudgetCreateRequest

export type BudgetResponse = {
  id: string
  name: string
  budgetMonth: string
  totalLimit: number
  warningThresholdPercent: number
  active: boolean
  categoryLimits: { categoryId: string; categoryName: string; limit: number }[]
  createdAt: string
  updatedAt: string
}

export type CategoryProgress = {
  categoryId: string
  categoryName: string
  limit: number
  spent: number
  remaining: number
  usagePercent: number
  exceeded: boolean
}

export type BudgetProgressResponse = {
  totalLimit: number
  totalSpent: number
  remaining: number
  usagePercent: number
  warningThresholdPercent: number
  exceeded: boolean
  categories: CategoryProgress[]
}
