import { type FormEvent, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, setToken } from '../api/client'
import { Spinner } from '../components/Busy'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import {
  normalizeName,
  normalizeOtp,
  normalizePhone,
  validateName,
  validateOtp,
  validatePhone,
} from '../lib/validate'

type VerifyRes = {
  token: string
  phone: string
  role: string
  walletCoins: number
  name: string
}

export function AuthPage() {
  const nav = useNavigate()
  const [phone, setPhone] = useState('')
  const [name, setName] = useState('')
  const [otp, setOtp] = useState('')
  const [sent, setSent] = useState(false)
  const [hint, setHint] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const [touched, setTouched] = useState<Record<string, boolean>>({})

  const phoneErr = useMemo(() => (touched.phone ? validatePhone(phone) : null), [phone, touched.phone])
  const nameErr = useMemo(() => (touched.name ? validateName(name) : null), [name, touched.name])
  const otpErr = useMemo(() => (touched.otp ? validateOtp(otp) : null), [otp, touched.otp])

  async function requestOtp(e: FormEvent) {
    e.preventDefault()
    setTouched({ phone: true, name: true })
    const pErr = validatePhone(phone)
    const nErr = validateName(name)
    if (pErr || nErr) {
      setErr(pErr || nErr || '')
      return
    }
    setErr('')
    setBusy(true)
    try {
      await api<{ status: string; message?: string }>('/api/auth/otp/request', {
        method: 'POST',
        auth: false,
        body: JSON.stringify({ phone: normalizePhone(phone) }),
      })
      setSent(true)
      setHint('OTP sent to your mobile')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  async function verify(e: FormEvent) {
    e.preventDefault()
    setTouched((t) => ({ ...t, otp: true, phone: true, name: true }))
    const pErr = validatePhone(phone)
    const oErr = validateOtp(otp)
    const nErr = validateName(name)
    if (pErr || oErr || nErr) {
      setErr(pErr || oErr || nErr || '')
      return
    }
    setErr('')
    setBusy(true)
    try {
      const res = await api<VerifyRes>('/api/auth/otp/verify', {
        method: 'POST',
        auth: false,
        body: JSON.stringify({
          phone: normalizePhone(phone),
          otp: normalizeOtp(otp),
          name: name.trim() || undefined,
        }),
      })
      setToken(res.token)
      if (res.role === 'ADMIN') nav('/admin')
      else if (res.role === 'EMPLOYEE') nav('/employee')
      else nav('/account')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Shell title="Sign in">
      <div className="card">
        {!sent ? (
          <form className="stack" onSubmit={requestOtp} noValidate>
            <TextInput
              label="Mobile"
              inputMode="numeric"
              autoComplete="tel"
              maxLength={10}
              value={phone}
              error={phoneErr}
              hint="10 digits, starts with 6–9"
              placeholder="9876543210"
              onBlur={() => setTouched((t) => ({ ...t, phone: true }))}
              onChange={(e) => setPhone(normalizePhone(e.target.value))}
            />
            <TextInput
              label="Name"
              autoComplete="name"
              maxLength={50}
              value={name}
              error={nameErr}
              hint="Optional"
              placeholder="Your name"
              onBlur={() => setTouched((t) => ({ ...t, name: true }))}
              onChange={(e) => setName(normalizeName(e.target.value))}
            />
            <button
              className={`btn btn-primary${busy ? ' btn-busy' : ''}`}
              disabled={busy || !!validatePhone(phone) || !!validateName(name)}
              type="submit"
            >
              {busy ? <Spinner label="Sending…" /> : 'Send OTP'}
            </button>
          </form>
        ) : (
          <form className="stack" onSubmit={verify} noValidate>
            <p className="muted">OTP sent to {normalizePhone(phone)}</p>
            {hint && <p className="ok">{hint}</p>}
            <TextInput
              label="OTP"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={otp}
              error={otpErr}
              placeholder="6-digit OTP"
              onBlur={() => setTouched((t) => ({ ...t, otp: true }))}
              onChange={(e) => setOtp(normalizeOtp(e.target.value))}
            />
            <button
              className={`btn btn-primary${busy ? ' btn-busy' : ''}`}
              disabled={busy || !!validateOtp(otp)}
              type="submit"
            >
              {busy ? <Spinner label="Verifying…" /> : 'Continue'}
            </button>
            <button
              className="btn btn-danger"
              type="button"
              onClick={() => {
                setSent(false)
                setOtp('')
                setErr('')
              }}
            >
              Change number
            </button>
          </form>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}
