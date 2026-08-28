/** Shared input rules — keep in sync with backend AuthService / VehicleNormalizer. */

export function digitsOnly(raw: string, max = 20): string {
  return raw.replace(/\D/g, '').slice(0, max)
}

export function normalizePhone(raw: string): string {
  const d = digitsOnly(raw, 15)
  return d.length > 10 ? d.slice(-10) : d
}

export function validatePhone(raw: string): string | null {
  const p = normalizePhone(raw)
  if (!p) return 'Enter mobile number'
  if (p.length < 10) return 'Enter 10-digit mobile number'
  if (p.length > 10) return 'Mobile number must be 10 digits'
  if (!/^[6-9]\d{9}$/.test(p)) return 'Indian mobile must start with 6, 7, 8 or 9'
  return null
}

export function normalizeName(raw: string): string {
  return raw.replace(/\s+/g, ' ').trimStart().slice(0, 50)
}

export function validateName(raw: string, { required = false } = {}): string | null {
  const n = raw.replace(/\s+/g, ' ').trim()
  if (!n) return required ? 'Enter your name' : null
  if (n.length < 2) return 'Name must be at least 2 characters'
  if (n.length > 50) return 'Name is too long'
  if (!/^[A-Za-z][A-Za-z .']*$/.test(n)) return 'Use letters only (spaces . \' allowed)'
  if (/\s{2,}/.test(n) || /\.{2,}/.test(n)) return 'Remove extra spaces or dots'
  return null
}

export function normalizeVehicle(raw: string): string {
  return raw.replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 12)
}

/** KA01AB1234 / 22BH1234AA style after normalize */
const VEHICLE_STD = /^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$/
const VEHICLE_BH = /^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$/

export function validateVehicle(raw: string): string | null {
  const v = normalizeVehicle(raw)
  if (!v) return 'Enter vehicle number'
  if (v === 'NOTENTERED' || v === 'NOTENTRED') return 'Invalid vehicle number'
  if (v.length < 8 || v.length > 11) return 'Vehicle number looks incomplete'
  if (!VEHICLE_STD.test(v) && !VEHICLE_BH.test(v)) {
    return 'Format like KA01AB1234'
  }
  return null
}

export function normalizeOtp(raw: string): string {
  return digitsOnly(raw, 6)
}

export function validateOtp(raw: string): string | null {
  const o = normalizeOtp(raw)
  if (!o) return 'Enter OTP'
  if (o.length < 6) return 'OTP must be 6 digits'
  if (!/^\d{6}$/.test(o)) return 'OTP must be 6 digits'
  return null
}

export function validateRupees(raw: string, maxRupees: number): string | null {
  const t = raw.trim()
  if (!t) return 'Enter amount'
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return 'Use ₹ amount like 50 or 50.50'
  const n = Number(t)
  if (!Number.isFinite(n) || n <= 0) return 'Amount must be greater than 0'
  if (n < 1) return 'Minimum redeem is ₹1'
  if (n > maxRupees + 1e-9) return `Max available is ₹${maxRupees.toFixed(2)}`
  return null
}

export function validateCoins(raw: string, maxCoins: number): string | null {
  const t = raw.trim()
  if (!t) return 'Enter coins'
  if (!/^\d+$/.test(t)) return 'Coins must be a whole number'
  const n = Number(t)
  if (n < 100) return 'Minimum redeem is 100 coins (₹1)'
  if (n > maxCoins) return `Max available is ${maxCoins} coins`
  return null
}

export function validateLat(raw: string): string | null {
  const t = raw.trim()
  if (!t) return 'Enter latitude'
  if (!/^-?\d+(\.\d+)?$/.test(t)) return 'Invalid latitude'
  const n = Number(t)
  if (n < -90 || n > 90) return 'Latitude must be between -90 and 90'
  return null
}

export function validateLng(raw: string): string | null {
  const t = raw.trim()
  if (!t) return 'Enter longitude'
  if (!/^-?\d+(\.\d+)?$/.test(t)) return 'Invalid longitude'
  const n = Number(t)
  if (n < -180 || n > 180) return 'Longitude must be between -180 and 180'
  return null
}

export function validateRadiusMeters(raw: string): string | null {
  const t = raw.trim()
  if (!t) return 'Enter radius'
  if (!/^\d+(\.\d+)?$/.test(t)) return 'Radius must be a number'
  const n = Number(t)
  if (!(n > 0)) return 'Radius must be greater than 0'
  return null
}

export function validatePositiveInt(raw: string, label: string, min = 0, max = 1_000_000): string | null {
  const t = String(raw).trim()
  if (!t) return `Enter ${label}`
  if (!/^\d+$/.test(t)) return `${label} must be a whole number`
  const n = Number(t)
  if (n < min || n > max) return `${label} must be ${min}–${max}`
  return null
}

export function validateRejectIdsCsv(raw: string): string | null {
  const t = raw.trim()
  if (!t) return null
  const parts = t.split(/[,\s]+/).filter(Boolean)
  for (const p of parts) {
    if (!/^[A-Za-z0-9-]{4,32}$/.test(p)) return `Invalid receipt ID: ${p}`
  }
  return null
}

export function validateReason(raw: string, { required = false, max = 120 } = {}): string | null {
  const t = raw.replace(/\s+/g, ' ').trim()
  if (!t) return required ? 'Enter a reason' : null
  if (t.length > max) return `Keep under ${max} characters`
  return null
}

// ponytail: quick self-check — run via node if needed; kept for sanity during edits
export function __selfCheck(): void {
  const assert = (c: boolean, m: string) => {
    if (!c) throw new Error(m)
  }
  assert(validatePhone('9876543210') === null, 'phone ok')
  assert(validatePhone('0876543210') !== null, 'phone bad start')
  assert(validateVehicle('KA-01-AB-1234') === null, 'vehicle ok')
  assert(validateVehicle('KA01') !== null, 'vehicle short')
  assert(validateName('Ravi Kumar') === null, 'name ok')
  assert(validateOtp('123456') === null, 'otp ok')
}
