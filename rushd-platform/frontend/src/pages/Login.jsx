import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Icon from '../components/Icon'
import { BrandMark } from '../components/Brand'
import { useAuth } from '../services/authState'
import { errorMessage } from '../services/property'
import { loginDestination } from '../services/loginDestination'

function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const destination = loginDestination(state)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
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
  return (
    <div className="page container">
      <div className="login-grid">
        <section className="login-intro panel panel-red">
          <span className="eyebrow">روافد العقارية</span>
          <BrandMark className="login-mark" />
          <h1>
            للبيع أو للإيجار.
            <br />
            خيارات أوضح.
          </h1>
          <p>الأدمن يضيف مواصفات العقارات ويتولى إدارة نشرها. استكشف العروض واختر ما يناسبك.</p>
          <Link to="/properties" className="button button-outline">
            استكشف العقارات <Icon name="arrow" />
          </Link>
        </section>
        <section className="login-form-panel panel panel-beige" aria-labelledby="login-title">
          <span className="icon-box">
            <Icon name="lock" />
          </span>
          <h2 id="login-title">أهلًا بعودتك</h2>
          <p className="login-subtitle">تسجيل الدخول إلى روافد العقارية</p>
          {user ? (
            <>
              <p>أنت مسجل باسم {user.name}.</p>
              <Link to={destination} className="button button-red">
                {destination === '/property-needs' ? 'متابعة احتياجاتي العقارية' : 'الذهاب إلى لوحة التحكم'}
              </Link>
            </>
          ) : (
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
                <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={busy}
                />
              </div>
              {error && (
                <p className="feedback feedback-error" role="alert">
                  {error}
                </p>
              )}
              <button className="button button-red button-wide" type="submit" disabled={busy}>
                {busy ? 'جارٍ تسجيل الدخول…' : 'تسجيل الدخول'}
                <Icon name="arrow" />
              </button>
              <p className="form-note">
                <Icon name="info" />
                نشر العقارات متاح لحسابات الأدمن المعتمدة فقط.
              </p>
            </form>
          )}
        </section>
      </div>
    </div>
  )
}
export default Login
