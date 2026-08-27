import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { api, getToken } from '../api/client'
import { Shell } from '../components/Shell'

type Rates = {
  rate0to100: number
  rate100to200: number
  rate200to300: number
  rate300plus: number
  bonusMidPct: number
  bonusHighPct: number
  thresholdMidLitres: number
  thresholdHighLitres: number
}

type Station = {
  rates: Rates
}

function paiseLabel(p: number) {
  if (p >= 100) return `₹${(p / 100).toFixed(2)}/L`
  return `${p} paise/L (${p} coins/L)`
}

export function HomePage({ role }: { role?: string }) {
  const loggedIn = !!getToken()
  const [rates, setRates] = useState<Rates | null>(null)

  useEffect(() => {
    void api<Station>('/api/public/station', { auth: false })
      .then((s) => setRates(s.rates))
      .catch(() => undefined)
  }, [])

  return (
    <Shell role={role}>
      <p className="lead">Cashback coins on fuel. Redeem only at this pump.</p>
      <div className="hero-actions">
        <Link className="btn btn-primary" to="/upload">
          Upload bill
        </Link>
        {!loggedIn && (
          <Link className="btn btn-ghost" to="/auth">
            Sign in
          </Link>
        )}
      </div>

      <div className="card home-explain fade-in">
        <h2>How this works</h2>
        <ol className="simple-steps">
          <li>
            <strong>Sign in</strong> with your mobile OTP and add your vehicle number.
          </li>
          <li>
            Fill fuel at <strong>Nagashree Service Station</strong>. Ask the attendant to print your vehicle
            number on the bill.
          </li>
          <li>
            Stand at the pump, open <strong>Upload bill</strong>, take a clear photo of the bill.
          </li>
          <li>Coins will get credited to you after 24 hrs.</li>
          <li>Scan the pump QR and pay with coins for fuel next time (OTP required).</li>
        </ol>
        <p className="muted" style={{ marginBottom: 0 }}>
          1 coin = 1 paisa. Coins can be used only at this pump.
        </p>
      </div>

      <div className="card fade-in">
        <h2>Discount rates</h2>
        {rates ? (
          <ul className="rate-list">
            <li>
              <span>0–100 L</span>
              <strong>{paiseLabel(rates.rate0to100 ?? 10)}</strong>
            </li>
            <li>
              <span>100–200 L</span>
              <strong>{paiseLabel(rates.rate100to200)}</strong>
            </li>
            <li>
              <span>200–300 L</span>
              <strong>{paiseLabel(rates.rate200to300)}</strong>
            </li>
            <li>
              <span>300 L and above</span>
              <strong>{paiseLabel(rates.rate300plus)}</strong>
            </li>
            <li className="muted">
              Extra bonus: +{rates.bonusMidPct}% after {rates.thresholdMidLitres.toLocaleString()} L / 30 days;
              +{rates.bonusHighPct}% after {rates.thresholdHighLitres.toLocaleString()} L / 30 days
            </li>
          </ul>
        ) : (
          <p className="muted">Loading rates…</p>
        )}
      </div>
    </Shell>
  )
}
