import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { Spinner } from '../components/Busy'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import { validateCoins, validateRupees } from '../lib/validate'

type Me = { phone: string; role: string; walletCoins: number; name: string }
type Pump = { pumpId: number; name: string; token: string }
type PayRes = { txnId: number; coins: number; rupees: number; walletCoins: number; message: string }

export function RedeemPage({ onRole }: { onRole?: (r: string) => void }) {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const nav = useNavigate()
  const [pump, setPump] = useState<Pump | null>(null)
  const [me, setMe] = useState<Me | null>(null)
  const [mode, setMode] = useState<'rupees' | 'coins'>('rupees')
  const [amount, setAmount] = useState('')
  const [otp, setOtp] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const [err, setErr] = useState('')
  const [paid, setPaid] = useState<PayRes | null>(null)
  const [busy, setBusy] = useState(false)
  const [otpBusy, setOtpBusy] = useState(false)
  const [confirmPay, setConfirmPay] = useState(false)
  const [touched, setTouched] = useState(false)

  const maxRupees = (me?.walletCoins ?? 0) / 100
  const maxCoins = me?.walletCoins ?? 0

  const amountErr = useMemo(() => {
    if (!touched || !me) return null
    return mode === 'rupees' ? validateRupees(amount, maxRupees) : validateCoins(amount, maxCoins)
  }, [amount, mode, maxRupees, maxCoins, touched, me])

  useEffect(() => {
    if (!token) {
      setErr('Invalid QR')
      return
    }
    void (async () => {
      try {
        setPump(await api<Pump>(`/api/redeem/pump/${encodeURIComponent(token)}`, { auth: false }))
      } catch (ex) {
        setErr(ex instanceof Error ? ex.message : 'Unknown pump QR')
      }
    })()
  }, [token])

  useEffect(() => {
    if (!getToken()) return
    void (async () => {
      try {
        const m = await api<Me>('/api/auth/me')
        setMe(m)
        onRole?.(m.role)
      } catch {
        /* need login */
      }
    })()
  }, [])

  function onAmountChange(raw: string) {
    if (mode === 'rupees') {
      const cleaned = raw.replace(/[^\d.]/g, '')
      const parts = cleaned.split('.')
      const next =
        parts.length <= 1 ? cleaned : `${parts[0]}.${parts.slice(1).join('').slice(0, 2)}`
      setAmount(next.slice(0, 10))
    } else {
      setAmount(raw.replace(/\D/g, '').slice(0, 12))
    }
  }

  async function sendPayOtp() {
    setErr('')
    setOtpBusy(true)
    try {
      await api('/api/redeem/otp/request', { method: 'POST', body: '{}' })
      setOtpSent(true)
      setConfirmPay(true)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Could not send OTP')
    } finally {
      setOtpBusy(false)
    }
  }

  async function pay() {
    setTouched(true)
    if (!getToken()) {
      nav('/auth')
      return
    }
    const aErr = mode === 'rupees' ? validateRupees(amount, maxRupees) : validateCoins(amount, maxCoins)
    if (aErr) {
      setErr(aErr)
      setConfirmPay(false)
      return
    }
    if (!/^\d{6}$/.test(otp)) {
      setErr('Enter the 6-digit OTP sent to your phone')
      return
    }
    setErr('')
    setPaid(null)
    setBusy(true)
    try {
      const n = Number(amount)
      const body =
        mode === 'rupees'
          ? { pumpToken: token, rupees: n, otp }
          : { pumpToken: token, coins: Math.round(n), otp }
      const res = await api<PayRes>('/api/redeem/pay', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      setPaid(res)
      setMe((prev) => (prev ? { ...prev, walletCoins: res.walletCoins } : prev))
      setAmount('')
      setOtp('')
      setOtpSent(false)
      setTouched(false)
      setConfirmPay(false)
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : 'Payment failed')
    } finally {
      setBusy(false)
    }
  }

  function onPaySubmit(e: FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (!getToken()) {
      nav('/auth')
      return
    }
    const aErr = mode === 'rupees' ? validateRupees(amount, maxRupees) : validateCoins(amount, maxCoins)
    if (aErr) {
      setErr(aErr)
      return
    }
    void sendPayOtp()
  }

  return (
    <Shell role={me?.role} title="Redeem">
      <ConfirmDialog
        open={confirmPay}
        title="Confirm with OTP"
        message={
          mode === 'rupees'
            ? `OTP sent to your phone. Enter it to pay ₹${amount}.`
            : `OTP sent to your phone. Enter it to redeem ${amount} coins.`
        }
        confirmLabel="Yes, pay"
        busy={busy}
        onCancel={() => !busy && setConfirmPay(false)}
        onConfirm={() => void pay()}
      >
        <TextInput
          label="OTP"
          inputMode="numeric"
          maxLength={6}
          value={otp}
          placeholder="6-digit code"
          onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
        />
        {otpSent && <p className="muted">OTP sent. Valid for a few minutes.</p>}
      </ConfirmDialog>
      <div className="card fade-in">
        {pump && <p className="muted">{pump.name}</p>}
        {!getToken() && (
          <Link className="btn btn-primary" to="/auth">
            Sign in to pay
          </Link>
        )}
        {me && (
          <>
            <div className="wallet" style={{ marginTop: 0 }}>
              <div>
                <div style={{ opacity: 0.75, fontSize: '0.85rem' }}>Available</div>
                <strong>₹{maxRupees.toFixed(2)}</strong>
              </div>
              <div>{maxCoins.toLocaleString()} coins</div>
            </div>
            <form className="stack" onSubmit={onPaySubmit} style={{ marginTop: 12 }} noValidate>
              <div className="row">
                <button
                  type="button"
                  className={`btn ${mode === 'rupees' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => {
                    setMode('rupees')
                    setAmount('')
                    setTouched(false)
                  }}
                >
                  ₹
                </button>
                <button
                  type="button"
                  className={`btn ${mode === 'coins' ? 'btn-primary' : 'btn-dark'}`}
                  onClick={() => {
                    setMode('coins')
                    setAmount('')
                    setTouched(false)
                  }}
                >
                  Coins
                </button>
              </div>
              <TextInput
                label={mode === 'rupees' ? 'Amount (₹)' : 'Coins'}
                inputMode={mode === 'rupees' ? 'decimal' : 'numeric'}
                value={amount}
                error={amountErr}
                hint={mode === 'rupees' ? 'Min ₹1 · OTP required' : 'Min 100 coins · OTP required'}
                placeholder={mode === 'rupees' ? '50' : '5000'}
                onBlur={() => setTouched(true)}
                onChange={(e) => onAmountChange(e.target.value)}
              />
              <button
                className={`btn btn-primary${otpBusy || busy ? ' btn-busy' : ''}`}
                disabled={
                  otpBusy ||
                  busy ||
                  !amount ||
                  !!(mode === 'rupees'
                    ? validateRupees(amount, maxRupees)
                    : validateCoins(amount, maxCoins))
                }
                type="submit"
              >
                {otpBusy ? <Spinner label="Sending OTP…" /> : 'Pay with OTP'}
              </button>
            </form>
          </>
        )}
        {paid && (
          <div style={{ marginTop: 14 }}>
            <p className="ok">{paid.message}</p>
            <p className="muted">Txn #{paid.txnId} — show attendant</p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}
