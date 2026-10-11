import { useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { AuthResponse, User } from '../types/api'
import { AuthContext } from './AuthContext'

function restoredUser(): User | null {
  if (!localStorage.getItem('finstack_token')) return null
  try {
    const value: unknown = JSON.parse(localStorage.getItem('finstack_user') || 'null')
    return value && typeof value === 'object' && 'id' in value && 'email' in value
      && 'roles' in value && Array.isArray(value.roles) ? value as User : null
  } catch {
    localStorage.removeItem('finstack_user')
    localStorage.removeItem('finstack_token')
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(restoredUser)
  const login = (response: AuthResponse) => {
    queryClient.clear() // Never reuse another session's owner-scoped finance data.
    localStorage.setItem('finstack_token', response.accessToken)
    localStorage.setItem('finstack_user', JSON.stringify(response.user))
    setUser(response.user)
  }
  const logout = () => {
    localStorage.removeItem('finstack_token')
    localStorage.removeItem('finstack_user')
    queryClient.clear()
    setUser(null)
  }
  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>
}
