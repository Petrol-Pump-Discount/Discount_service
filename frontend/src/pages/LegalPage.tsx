import { Link } from 'react-router-dom'
import { Shell } from '../components/Shell'

export function LegalPage({ kind }: { kind: 'terms' | 'privacy' | 'disclaimer' }) {
  const title =
    kind === 'terms' ? 'Terms of Use' : kind === 'privacy' ? 'Privacy Policy' : 'Disclaimer'

  return (
    <Shell title={title} showStationBar={false}>
      <div className="card legal-doc">
        <p className="muted legal-updated">Last updated: 17 Sep 2026 · For Nagashree Service Station loyalty programme</p>

        {kind === 'terms' && <TermsBody />}
        {kind === 'privacy' && <PrivacyBody />}
        {kind === 'disclaimer' && <DisclaimerBody />}

        <p style={{ marginTop: '1.25rem' }}>
          <Link to="/auth">Back to Sign in</Link>
          {' · '}
          <Link to="/">Home</Link>
        </p>
      </div>
    </Shell>
  )
}

function TermsBody() {
  return (
    <div className="legal-sections">
      <h2>1. Programme</h2>
      <p>
        This app is a loyalty / coin programme operated by Nagashree Service Station (Tumkur, Karnataka) for
        customers who fuel at this station. Coins are promotional rewards, not a bank deposit, e-money wallet,
        or investment. The station may change rates, rules, or pause the programme with reasonable notice in
        the app or at the station.
      </p>
      <h2>2. Eligibility &amp; account</h2>
      <p>
        You must use your own Indian mobile number. You are responsible for OTPs and devices signed in to your
        account. Do not share OTPs. Fake bills, duplicate bills, location spoofing, or claiming another person&apos;s
        fuel may lead to rejection, blacklist, and/or reporting to authorities.
      </p>
      <h2>3. Claims &amp; OCR</h2>
      <p>
        Bill photos are read automatically (OCR) and verified against station records (e.g. daily PDF). OCR and
        matching can err. Final credit is only after station verification. Rejected or unmatched claims earn no
        coins. Uploads require you to be signed in, at/near the pump (GPS), with a linked vehicle.
      </p>
      <h2>4. Coins &amp; redemption</h2>
      <p>
        Coin value and ageing rules are shown in the app. Redemption is only for fuel at this station, subject to
        OTP, geofence, and attendant process. Coins have no cash surrender value except as the station allows
        for fuel. Lost/stolen phone access is your responsibility—sign out on shared devices.
      </p>
      <h2>5. Limitation of liability</h2>
      <p>
        To the maximum extent permitted by applicable law, the station and its operators are not liable for
        indirect, incidental, or consequential losses (including lost profits, data, or goodwill) arising from
        use of the app, OCR mistakes, SMS delays, network outages, or third-party services (SMS, cloud, maps).
        Aggregate liability for a claim related to this programme is limited to the coin value in dispute for
        that transaction, or ₹1,000, whichever is lower—except where law forbids such a limit (including proven
        fraud or wilful misconduct by the station).
      </p>
      <h2>6. Governing law</h2>
      <p>
        These terms are governed by the laws of India. Courts at Tumkur / Karnataka have subject-matter
        jurisdiction, without limiting mandatory consumer protections.
      </p>
      <h2>7. Contact</h2>
      <p>Station contact details are shown in the app footer. Raise disputes at the station first.</p>
    </div>
  )
}

function PrivacyBody() {
  return (
    <div className="legal-sections">
      <h2>1. Data we process</h2>
      <p>
        Mobile number, name (if provided), vehicle registration you link, bill photos, GPS coordinates at
        upload/redeem time, claim/redeem history, and session tokens. SMS OTP is sent via our SMS provider
        (e.g. Twilio). Photos and data may be stored on our hosting/database providers.
      </p>
      <h2>2. Purpose</h2>
      <p>
        Operate the loyalty programme: verify bills, prevent fraud, credit/redeem coins, contact you about your
        account, and improve service reliability.
      </p>
      <h2>3. Sharing</h2>
      <p>
        We do not sell your personal data. Processors (hosting, database, SMS, OCR) may process data on our
        instructions. We may disclose data if required by law or to investigate fraud/abuse.
      </p>
      <h2>4. Retention</h2>
      <p>
        Claim photos and ledger records are kept as needed for programme operations, dispute handling, and legal
        requirements. You may ask the station to review deletion of your account where feasible (ledger history
        may be retained in anonymised or aggregated form).
      </p>
      <h2>5. Security</h2>
      <p>
        We use access controls, HTTPS, and operational practices to protect data. No method is 100% secure—report
        suspected misuse to the station promptly.
      </p>
      <h2>6. Your choices</h2>
      <p>
        You can stop using the app, unlink vehicles, and request account review at the station. Location and
        camera permissions are required for claim/redeem features.
      </p>
      <h2>7. Contact</h2>
      <p>Privacy questions: use the station contact in the app footer.</p>
    </div>
  )
}

function DisclaimerBody() {
  return (
    <div className="legal-sections">
      <h2>Important notices</h2>
      <ul>
        <li>This is a station loyalty programme, not a bank, UPI app, or guaranteed cashback product.</li>
        <li>OCR may misread bills; only verified claims receive coins.</li>
        <li>GPS can be inaccurate; geofence checks may fail even when you are nearby—retry or ask staff.</li>
        <li>SMS OTP delivery depends on your mobile network and our SMS provider.</li>
        <li>IndianOil branding identifies the fuel brand; this app is operated by the retail outlet, not as an
          official pan-India IndianOil corporate product unless stated otherwise by IndianOil.</li>
        <li>Do not attempt to defraud the programme. Abuse may lead to permanent ban and legal action.</li>
      </ul>
    </div>
  )
}
