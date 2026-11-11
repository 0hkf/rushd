import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Icon from '../components/Icon'
import PasswordInput from '../components/PasswordInput'
import { BrandMark } from '../components/Brand'
import { useAuth } from '../services/authState'
import { errorMessage } from '../services/property'
import { loginDestination } from '../services/loginDestination'
import api from '../services/api'
import {
  LocalDemoAdminError,
  readLocalDemoAdmin,
  signInLocalDemoAdmin,
} from '../services/localDemoAdmin'

function Login() {
  const { user, login, loading: authLoading } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const destination = loginDestination(state)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const localDemo = readLocalDemoAdmin(api.defaults.baseURL)
  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(email, password)
      navigate(destination, { replace: true })
    } catch (error) {
      setError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }
  async function demoLogin() {
    if (!import.meta.env.DEV || busy) return
    // Recheck the actual Axios destination at click time, not only when the page rendered.
    const configuration = readLocalDemoAdmin(api.defaults.baseURL)
    if (!configuration) return
    setEmail(configuration.email)
    setPassword(configuration.password)
    setError('')
    setBusy(true)
    try {
      const target = await signInLocalDemoAdmin(configuration, login)
      navigate(target, { replace: true })
    } catch (error) {
      setError(error instanceof LocalDemoAdminError ? error.message : errorMessage(error))
    } finally {
      setBusy(false)
    }
  }
  // Do not offer fields that would be remounted while the session is being restored.
  if (authLoading) return <div className="page container"><p className="notice" role="status">جارٍ التحقق من الجلسة…</p></div>
  return (
    <div className="page container">
      <div className="login-grid">
        <section className="login-form-panel panel panel-beige" aria-labelledby="login-title">
          <span className="icon-box">
            <Icon name="lock" />
          </span>
          <h1 id="login-title">أهلًا بعودتك</h1>
          <p className="login-subtitle">تسجيل الدخول إلى روافد العقارية</p>
          {error && (
            <p className="feedback feedback-error" role="alert">
              {error}
            </p>
          )}
          {user ? (
            <>
              <p>أنت مسجل باسم {user.name}.</p>
              <Link to={destination} className="button button-red">
                {destination === '/property-needs'
                  ? 'متابعة احتياجاتي العقارية'
                  : destination === '/favorites' ? 'متابعة مفضلتي'
                    : destination === '/admin/market-references' ? 'العودة إلى مراجع السوق'
                    : destination.startsWith('/properties/') ? 'العودة إلى العقار' : 'الذهاب إلى لوحة التحكم'}
              </Link>
            </>
          ) : (
            <>
              <form onSubmit={submit}>
                <div className="form-field">
                  <label htmlFor="email">البريد الإلكتروني</label>
                  <input
                    id="email"
                    type="email"
                    placeholder="example@email.com"
                    autoComplete="email"
                    dir="ltr"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={busy}
                  />
                </div>
                <div className="form-field">
                  <label htmlFor="password">كلمة المرور</label>
                  <PasswordInput
                    id="password"
                    autoComplete="current-password"
                    dir="auto"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={busy}
                  />
                </div>
                <button className="button button-red button-wide" type="submit" disabled={busy}>
                  {busy ? 'جارٍ تسجيل الدخول…' : 'تسجيل الدخول'}
                  <Icon name="arrow" />
                </button>
                <p className="form-note">
                  <Icon name="info" />
                  نشر العقارات متاح لحسابات الأدمن المعتمدة فقط.
                </p>
              </form>
              <p className="form-note">ليس لديك حساب؟ <Link to="/register" state={{ returnTo: destination }}>إنشاء حساب عميل</Link></p>
              {import.meta.env.DEV && localDemo && (
                <div>
                  <p className="form-note">
                    حساب تجريبي محلي — للتطوير على هذا الجهاز فقط. بيانات دخوله مرئية لواجهة
                    التطوير، ولا يستخدم في الإنتاج.
                  </p>
                  <button
                    type="button"
                    className="button button-outline button-wide"
                    disabled={busy}
                    onClick={demoLogin}
                  >
                    تعبئة ودخول الأدمن التجريبي
                  </button>
                </div>
              )}
            </>
          )}
        </section>
        <section className="login-intro panel panel-red">
          <span className="eyebrow">روافد العقارية</span>
          <BrandMark className="login-mark" />
          <h2>للبيع أو للإيجار.<br />خيارات أوضح.</h2>
          <p>الأدمن يضيف مواصفات العقارات ويتولى إدارة نشرها. استكشف العروض واختر ما يناسبك.</p>
          <Link to="/properties" className="button button-outline">استكشف العقارات <Icon name="arrow" /></Link>
        </section>
      </div>
    </div>
  )
}
export default Login
