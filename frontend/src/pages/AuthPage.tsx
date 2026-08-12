import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, setToken } from '../api/client'
import { Shell } from '../components/Shell'

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

  async function requestOtp(e: FormEvent) {
    e.preventDefault()
    setErr('')
    setBusy(true)
    try {
      const res = await api<{ status: string; devOtp?: string }>('/api/auth/otp/request', {
        method: 'POST',
        auth: false,
        body: JSON.stringify({ phone }),
      })
      setSent(true)
      setHint(res.devOtp ? `Dev OTP: ${res.devOtp}` : 'OTP sent to your phone')
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  async function verify(e: FormEvent) {
    e.preventDefault()
    setErr('')
    setBusy(true)
    try {
      const res = await api<VerifyRes>('/api/auth/otp/verify', {
        method: 'POST',
        auth: false,
        body: JSON.stringify({ phone, otp, name }),
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
    <Shell>
      <div className="card">
        <h2>Register / Sign in</h2>
        <p className="muted">Phone OTP. Wallet stays on your phone number.</p>
        {!sent ? (
          <form className="stack" onSubmit={requestOtp}>
            <label>
              Name (optional)
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Driver name" />
            </label>
            <label>
              Mobile number
              <input
                required
                inputMode="numeric"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="10-digit mobile"
              />
            </label>
            <button className="btn btn-primary" disabled={busy} type="submit">
              Send OTP
            </button>
          </form>
        ) : (
          <form className="stack" onSubmit={verify}>
            <p className="ok">{hint}</p>
            <label>
              OTP
              <input required value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit OTP" />
            </label>
            <button className="btn btn-primary" disabled={busy} type="submit">
              Verify & continue
            </button>
            <button className="btn btn-danger" type="button" onClick={() => setSent(false)}>
              Change number
            </button>
          </form>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}
