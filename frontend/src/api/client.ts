const TOKEN_KEY = 'pump_session'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (!token) localStorage.removeItem(TOKEN_KEY)
  else localStorage.setItem(TOKEN_KEY, token)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parseError(res: Response): Promise<never> {
  let msg = res.statusText || 'Request failed'
  try {
    const j = await res.json()
    msg = j.message || j.error || msg
  } catch {
    try {
      msg = (await res.text()) || msg
    } catch {
      /* ignore */
    }
  }
  throw new ApiError(res.status, msg)
}

export async function api<T = unknown>(
  path: string,
  opts: RequestInit & { auth?: boolean } = {},
): Promise<T> {
  const headers = new Headers(opts.headers || {})
  if (opts.body && !(opts.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (opts.auth !== false) {
    const t = getToken()
    if (t) headers.set('X-Session-Token', t)
  }
  const res = await fetch(path, { ...opts, headers })
  if (!res.ok) await parseError(res)
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
