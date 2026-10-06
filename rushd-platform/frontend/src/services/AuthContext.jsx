import { useEffect, useRef, useState } from 'react'
import api, { onAuthenticationLost, markAuthenticated, loginSession, logoutSession } from './api'
import { AuthContext } from './authState'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const generation = useRef(0)
  useEffect(() => {
    let active = true
    const revision = generation.current
    const unsubscribe = onAuthenticationLost(() => {
      generation.current += 1
      if (active) {
        setUser(null)
        setAuthError('انتهت الجلسة. سجّل الدخول مرة أخرى.')
      }
    })
    api
      .get('/api/auth/me')
      .then(({ data }) => {
        if (active && revision === generation.current) setUser(data)
      })
      .catch((error) => {
        if (active && error.response?.status !== 401 && revision === generation.current)
          setAuthError('تعذّر الاتصال للتحقق من الجلسة.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
      unsubscribe()
    }
  }, [])
  const login = async (email, password) => {
    const revision = ++generation.current
    const { data } = await loginSession(email, password)
    if (revision === generation.current) {
      markAuthenticated()
      setUser(data.user)
      setAuthError('')
      setLoading(false)
    }
    return data.user
  }
  const logout = async () => {
    generation.current += 1
    try {
      await logoutSession()
      setUser(null)
      setAuthError('')
    } catch {
      // Never claim server revocation succeeded if the logout request failed.
      setAuthError('تعذّر تسجيل الخروج من الخادم. حاول مرة أخرى.')
      throw new Error('Server logout failed')
    }
  }
  return (
    <AuthContext.Provider value={{ user, loading, authError, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
