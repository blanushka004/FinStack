import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { api, message } from '../lib/api'
import { useAuth } from '../app/AuthContext'

const emailSchema = z.object({ email: z.string().trim().email('Enter a valid email address.').max(255) })
const resetSchema = z.object({
  password: z.string().min(8).max(72).regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/, 'Use uppercase, lowercase, number, and special character.'),
  confirmPassword: z.string(),
}).refine((value) => value.password === value.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match.' })

function Frame({ title, children }: { title: string; children: React.ReactNode }) {
  return <main className="grid min-h-screen place-items-center bg-paper p-5"><section className="w-full max-w-md rounded-2xl border bg-white p-6 shadow-sm">
    <h1 className="text-2xl font-bold">{title}</h1>{children}
    <Link to="/login" className="mt-5 block text-sm font-medium text-brand">Back to sign in</Link>
  </section></main>
}

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)
  const form = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema), defaultValues: { email: '' } })
  const submit = form.handleSubmit(async ({ email }) => {
    try {
      await api.post('/api/v1/auth/password-reset/request', { email: email.trim() })
      setSent(true)
    } catch (error) { form.setError('root', { message: message(error) }) }
  })
  return <Frame title="Forgot your password?">
    <p className="mt-2 text-sm text-slate-600">Enter your email address. If an account exists, we’ll send a one-time reset link.</p>
    {sent ? <div role="status" className="mt-5 rounded-lg bg-emerald-50 p-4 text-sm text-emerald-800">If an account exists for that email, a reset link has been sent. Check your inbox. You can request another link later.
        {import.meta.env.DEV && <p className="mt-2">Testing locally without SMTP? With the dev-link settings enabled, the link appears in the Spring Boot backend console—not this page, the Vite terminal, or browser console. Only the newest link works; after several requests, wait 15 minutes before retrying.</p>}
      </div>
      : <form onSubmit={submit} className="mt-5 space-y-4"><label className="block text-sm font-medium">Email
        <input type="email" autoComplete="email" {...form.register('email')} className="mt-1 w-full rounded-lg border p-3" />
      </label>{form.formState.errors.email && <p role="alert" className="text-sm text-rose-600">{form.formState.errors.email.message}</p>}
        {form.formState.errors.root && <p role="alert" className="text-sm text-rose-600">{form.formState.errors.root.message}</p>}
        <button disabled={form.formState.isSubmitting} className="w-full rounded-lg bg-brand p-3 font-semibold text-white disabled:opacity-50">{form.formState.isSubmitting ? 'Sending…' : 'Send reset link'}</button>
      </form>}
    {sent && <button type="button" onClick={() => setSent(false)} className="mt-4 text-sm font-medium text-brand">Request another link</button>}
  </Frame>
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const { logout } = useAuth()
  const [done, setDone] = useState(false)
  const [invalid, setInvalid] = useState(false)
  const [expired, setExpired] = useState(false)
  const [token] = useState(() => params.get('token'))
  useEffect(() => { if (token) window.history.replaceState({}, '', '/reset-password') }, [token])
  const form = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema), defaultValues: { password: '', confirmPassword: '' } })
  const submit = form.handleSubmit(async ({ password }) => {
    if (!token) { setInvalid(true); return }
    try {
      await api.post('/api/v1/auth/password-reset/confirm', { token, password })
      logout()
      setDone(true)
      window.history.replaceState({}, '', '/reset-password')
    } catch (error) {
      const code = (error as { response?: { data?: { code?: string } } }).response?.data?.code
      if (code === 'RESET_TOKEN_EXPIRED') setExpired(true)
      else if (code === 'INVALID_RESET_TOKEN') setInvalid(true)
      else form.setError('root', { message: message(error) })
    }
  })
  return <Frame title="Reset your password">
    {done ? <p role="status" className="mt-4 text-sm text-emerald-700">Password updated. Sign in with your new password.</p>
      : expired ? <div role="alert" className="mt-4 text-sm text-rose-700">This reset link has expired. <Link to="/forgot-password" className="font-semibold underline">Request a new link</Link>.</div>
        : invalid || !token ? <div role="alert" className="mt-4 text-sm text-rose-700">This link is invalid or has already been used. <Link to="/forgot-password" className="font-semibold underline">Request a new link</Link>.</div>
        : <form onSubmit={submit} className="mt-5 space-y-4">
          <label className="block text-sm font-medium">New password<input type="password" autoComplete="new-password" {...form.register('password')} className="mt-1 w-full rounded-lg border p-3" /></label>
          {form.formState.errors.password && <p role="alert" className="text-sm text-rose-600">{form.formState.errors.password.message}</p>}
          <label className="block text-sm font-medium">Confirm new password<input type="password" autoComplete="new-password" {...form.register('confirmPassword')} className="mt-1 w-full rounded-lg border p-3" /></label>
          {form.formState.errors.confirmPassword && <p role="alert" className="text-sm text-rose-600">{form.formState.errors.confirmPassword.message}</p>}
          {form.formState.errors.root && <p role="alert" className="text-sm text-rose-600">{form.formState.errors.root.message}</p>}
          <button disabled={form.formState.isSubmitting} className="w-full rounded-lg bg-brand p-3 font-semibold text-white disabled:opacity-50">{form.formState.isSubmitting ? 'Updating…' : 'Update password'}</button>
        </form>}
  </Frame>
}
