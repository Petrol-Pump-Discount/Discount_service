import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { api, getToken } from './api/client'
import { AccountPage } from './pages/AccountPage'
import { AdminPage } from './pages/AdminPage'
import { AuthPage } from './pages/AuthPage'
import { ClaimsPage } from './pages/ClaimsPage'
import { EmployeePage } from './pages/EmployeePage'
import { HomePage } from './pages/HomePage'
import { RedeemPage } from './pages/RedeemPage'
import { UploadPage } from './pages/UploadPage'

export default function App() {
  const [role, setRole] = useState<string>()

  useEffect(() => {
    if (!getToken()) return
    void api<{ role: string }>('/api/auth/me')
      .then((m) => setRole(m.role))
      .catch(() => undefined)
  }, [])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage role={role} />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/account" element={<AccountPage onRole={setRole} />} />
        <Route path="/claims" element={<ClaimsPage onRole={setRole} />} />
        <Route path="/upload" element={<UploadPage role={role} />} />
        <Route path="/redeem" element={<RedeemPage onRole={setRole} />} />
        <Route path="/admin" element={<AdminPage onRole={setRole} />} />
        <Route path="/employee" element={<EmployeePage onRole={setRole} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
