import { createContext, useContext } from 'react'
import type { AuthResponse, User } from '../types/api'

export type AuthContextValue = {
  user: User | null
  login: (response: AuthResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider required')
  return context
}
