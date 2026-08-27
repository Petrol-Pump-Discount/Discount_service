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
  let msg = 'Something went wrong. Please try again.'
  try {
    const j = await res.json()
    if (typeof j.message === 'string' && j.message.trim()) msg = j.message
    else if (typeof j.error === 'string' && j.error.trim()) msg = j.error
  } catch {
    if (res.status === 413) msg = 'Photo is too large. Take a smaller photo and try again.'
    else if (res.status === 429) msg = 'Too many requests. Wait a few seconds and try again.'
    else if (res.status === 502 || res.status === 503 || res.status === 504) {
      msg = 'Server is busy reading bills. Wait a few seconds and try again.'
    } else if (res.status >= 500) {
      msg = 'Something went wrong. Please try again.'
    }
  }
  // Never surface raw SQL / stack traces if a proxy leaked HTML/text
  const lower = msg.toLowerCase()
  if (
    lower.includes('sql') ||
    lower.includes('constraint') ||
    lower.includes('hibernate') ||
    lower.includes('duplicate key') ||
    lower.includes('<html') ||
    msg.length > 200
  ) {
    msg = 'Something went wrong. Please try again.'
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
  let res: Response
  try {
    res = await fetch(path, { ...opts, headers })
  } catch {
    throw new ApiError(0, 'Network error — check your connection and try again.')
  }
  if (!res.ok) await parseError(res)
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
