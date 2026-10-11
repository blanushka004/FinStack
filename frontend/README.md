# FinStack frontend

React/TypeScript/Vite client for the FinStack Spring Boot API. See the [root README](../README.md) for the full Windows PowerShell setup and manual QA checklist.

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
npm.cmd run lint
```

Open `http://localhost:5173`. The Vite development server proxies `/api` to the backend's default `http://localhost:8081`. For another origin, set `VITE_API_BASE_URL` in a local `frontend/.env` (never commit credentials). Login and registration receive a JWT, which the Axios client attaches to protected requests. Logging out and logging in clear the owner-scoped TanStack Query cache. There is no refresh-token or cookie-auth flow.

Pages cover dashboard, accounts, categories, transactions, budgets, alerts, fraud risk, and admin login activity. Charts use real API aggregates; fraud scores are **rule-based**, not ML. Browser interaction and mobile layout still require manual QA against a running backend.
