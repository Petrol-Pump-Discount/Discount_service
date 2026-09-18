import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { LoadingBlock, Spinner } from '../components/Busy'
import { TextInput } from '../components/Field'
import { Shell } from '../components/Shell'
import { normalizeVehicle, validateVehicle } from '../lib/validate'
import { compressImageBlob } from '../lib/compressImage'

type UploadRes = {
  id: number
  status: string
  receiptKey: string
  volumeLitres: number
  message: string
}

type Me = { phone: string; role: string }

function isSecure(): boolean {
  return typeof window !== 'undefined' && window.isSecureContext
}

export function UploadPage({ role }: { role?: string }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [blob, setBlob] = useState<Blob | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [signedInPhone, setSignedInPhone] = useState<string | null>(null)
  const [vehicleNo, setVehicleNo] = useState('')
  const [lat, setLat] = useState<number | null>(null)
  const [lng, setLng] = useState<number | null>(null)
  const [geoErr, setGeoErr] = useState('')
  const [err, setErr] = useState('')
  const [result, setResult] = useState<UploadRes | null>(null)
  const [busy, setBusy] = useState(false)
  const [camBusy, setCamBusy] = useState(false)
  const [submitLocked, setSubmitLocked] = useState(false)
  const submitLockedRef = useRef(false)
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const secure = isSecure()

  const vehicleErr = useMemo(
    () => (touched.vehicle ? validateVehicle(vehicleNo) : null),
    [vehicleNo, touched.vehicle],
  )

  useEffect(() => {
    if (!getToken()) {
      setSignedInPhone(null)
      return
    }
    void api<Me>('/api/auth/me')
      .then((m) => setSignedInPhone(m.phone))
      .catch(() => setSignedInPhone(null))
  }, [])

  function requestLocation() {
    if (!navigator.geolocation) {
      setGeoErr('Location unavailable on this device')
      return
    }
    setGeoErr('')
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setLat(p.coords.latitude)
        setLng(p.coords.longitude)
        setGeoErr('')
      },
      (e) => {
        if (!secure) {
          setGeoErr('Location needs HTTPS — open the site via your domain (Cloudflare)')
        } else if (e.code === e.PERMISSION_DENIED) {
          setGeoErr('Location permission denied — allow it in Chrome site settings')
        } else {
          setGeoErr('Could not get location — try again at the pump')
        }
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 },
    )
  }

  useEffect(() => {
    requestLocation()
  }, [])

  function unlockForNewPhoto() {
    submitLockedRef.current = false
    setSubmitLocked(false)
    setResult(null)
  }

  function clearPhoto() {
    setBlob(null)
    setPreview(null)
    unlockForNewPhoto()
  }

  function stopCamera() {
    stream?.getTracks().forEach((t) => t.stop())
    setStream(null)
  }

  async function openCameraStream(): Promise<MediaStream> {
    if (!secure) {
      throw new Error('Camera needs HTTPS — open https://nss01.com (not plain HTTP / IP)')
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Camera is not available on this browser')
    }
    // Prefer rear camera on phones; fall back to any camera (laptop webcam).
    try {
      return await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      })
    } catch {
      return await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    }
  }

  async function startLiveCamera() {
    setErr('')
    clearPhoto()
    stopCamera()
    setCamBusy(true)
    try {
      const s = await openCameraStream()
      setStream(s)
    } catch (ex) {
      const name = ex instanceof DOMException ? ex.name : ''
      if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
        setErr('Camera permission denied — allow Camera for this site in browser settings')
      } else if (ex instanceof Error && ex.message) {
        setErr(ex.message)
      } else {
        setErr('Could not open camera. Allow camera access and try again.')
      }
    } finally {
      setCamBusy(false)
    }
  }

  useEffect(() => {
    if (!stream || !videoRef.current) return
    const video = videoRef.current
    video.srcObject = stream
    void video.play().catch(() => undefined)
  }, [stream])

  useEffect(() => {
    return () => {
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [stream])

  function capture() {
    const video = videoRef.current
    if (!video) return
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 1280
    canvas.height = video.videoHeight || 720
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.drawImage(video, 0, 0)
    canvas.toBlob(
      (b) => {
        if (!b) return
        unlockForNewPhoto()
        setBlob(b)
        setPreview(URL.createObjectURL(b))
        stopCamera()
      },
      'image/jpeg',
      0.92,
    )
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (submitLockedRef.current || busy || !blob) return
    if (!getToken() || !signedInPhone) {
      setErr('Sign in required to upload a bill')
      return
    }
    setTouched({ vehicle: true })
    const vErr = validateVehicle(vehicleNo)
    if (vErr) {
      setErr(vErr)
      return
    }
    if (lat == null || lng == null) {
      setErr(geoErr || 'Allow location first')
      requestLocation()
      return
    }
    setErr('')
    setBusy(true)
    submitLockedRef.current = true
    setSubmitLocked(true)
    try {
      const compressed = await compressImageBlob(blob)
      const fd = new FormData()
      fd.append('vehicleNo', normalizeVehicle(vehicleNo))
      fd.append('lat', String(lat))
      fd.append('lng', String(lng))
      fd.append('image', compressed, 'bill.jpg')
      const res = await api<UploadRes>('/api/claims/upload', {
        method: 'POST',
        body: fd,
      })
      setResult(res)
      setBlob(null)
      setPreview(null)
    } catch (ex) {
      submitLockedRef.current = false
      setSubmitLocked(false)
      setErr(ex instanceof Error ? ex.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  const canSubmit =
    !!signedInPhone && !validateVehicle(vehicleNo) && !!blob && lat != null && lng != null && !busy && !submitLocked

  return (
    <Shell role={role} title="Upload bill">
      <div className={`card${busy ? ' is-busy' : ''}`}>
        {busy && (
          <div className="loading-veil">
            <LoadingBlock
              title="Reading bill…"
              detail="OCR + geofence check in progress. Please wait."
            />
          </div>
        )}
        {!signedInPhone && (
          <p className="err" style={{ marginTop: 0 }}>
            Sign in required to upload.{' '}
            <Link to="/auth">Sign in</Link>
          </p>
        )}
        {!secure && (
          <p className="err" style={{ marginTop: 0 }}>
            Camera needs HTTPS. Open the site via your Cloudflare domain (not plain HTTP / IP).
          </p>
        )}
        <h2 className="upload-guide">
          Take a clear camera photo of your bill at the pump (file upload not allowed)
        </h2>
        <form className="stack" onSubmit={submit} noValidate aria-busy={busy}>
          {signedInPhone ? (
            <p className="muted" style={{ margin: 0 }}>
              Signed in as <strong>{signedInPhone}</strong>
            </p>
          ) : null}
          <TextInput
            label="Vehicle number"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={12}
            value={vehicleNo}
            error={vehicleErr}
            hint="Must match bill and your account"
            placeholder="KA01AB1234"
            onBlur={() => setTouched((t) => ({ ...t, vehicle: true }))}
            onChange={(e) => setVehicleNo(normalizeVehicle(e.target.value))}
          />

          <div className="camera-box">
            {!preview && !stream && (
              <button
                type="button"
                className={`btn btn-primary${camBusy ? ' btn-busy' : ''}`}
                disabled={camBusy || !secure}
                onClick={() => void startLiveCamera()}
              >
                {camBusy ? <Spinner label="Opening camera…" /> : 'Open camera'}
              </button>
            )}
            {stream && (
              <>
                <video ref={videoRef} playsInline muted autoPlay />
                <div className="row" style={{ justifyContent: 'center', marginTop: 10, gap: 8 }}>
                  <button type="button" className="btn btn-primary" onClick={capture}>
                    Capture
                  </button>
                  <button type="button" className="btn btn-danger" onClick={stopCamera}>
                    Cancel
                  </button>
                </div>
              </>
            )}
            {preview && (
              <>
                <img src={preview} alt="Bill" />
                <div className="row" style={{ justifyContent: 'center', marginTop: 10 }}>
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={() => {
                      clearPhoto()
                      void startLiveCamera()
                    }}
                  >
                    Retake with camera
                  </button>
                </div>
              </>
            )}
          </div>

          <div className="row">
            {lat != null && lng != null ? (
              <span className="gps-pill">Location ready</span>
            ) : (
              <span className={`gps-pill ${geoErr ? 'bad' : 'wait'}`}>{geoErr || 'Getting location…'}</span>
            )}
            {lat == null && (
              <button type="button" className="btn btn-dark" onClick={requestLocation}>
                Allow location
              </button>
            )}
          </div>

          <button
            className={`btn btn-primary${busy ? ' btn-busy' : ''}`}
            type="submit"
            disabled={!canSubmit}
          >
            {busy ? (
              <Spinner label="Submitting…" />
            ) : submitLocked ? (
              'Submitted'
            ) : (
              'Submit'
            )}
          </button>
        </form>
        {result && (
          <div style={{ marginTop: 12 }}>
            <p className="ok">{result.message}</p>
            <p className="muted">
              <span className="badge warn">{result.status}</span> · {result.receiptKey} · {result.volumeLitres} L
            </p>
            <p className="muted">Open camera again to submit another bill.</p>
          </div>
        )}
        {err && <p className="err">{err}</p>}
      </div>
    </Shell>
  )
}
