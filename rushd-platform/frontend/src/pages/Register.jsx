import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import Icon from '../components/Icon'
import PasswordInput from '../components/PasswordInput'
import { BrandMark } from '../components/Brand'
import api from '../services/api'
import { useAuth } from '../services/authState'
import { loginDestination } from '../services/loginDestination'
import { registrationErrorMessage, registrationErrors } from '../services/registration'

export default function Register() {
  const { user, loading } = useAuth()
  const { state } = useLocation()
  const returnTo = loginDestination(state)
  const [values, setValues] = useState({ name: '', email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [success, setSuccess] = useState(false)
  const [invalidAttempt, setInvalidAttempt] = useState(0)
  const errorSummary = useRef(null)
  useEffect(() => { if (invalidAttempt > 0) errorSummary.current?.focus() }, [invalidAttempt])

  function change(event) {
    const { name, value } = event.target
    setValues((previous) => ({ ...previous, [name]: value }))
    setErrors((previous) => ({ ...previous, [name]: '' }))
    setError('')
  }
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const validation = registrationErrors(values)
    setErrors(validation)
    setError('')
    if (Object.keys(validation).length) { setInvalidAttempt((value) => value + 1); return }
    setBusy(true)
    try {
      await api.post('/api/auth/register', { ...values, role: 'BUYER' })
      setValues((previous) => ({ ...previous, password: '' }))
      setSuccess(true)
    } catch (failure) {
      setError(registrationErrorMessage(failure))
    } finally {
      setBusy(false)
    }
  }
  if (loading) return <div className="page container"><p className="notice" role="status">جارٍ التحقق من الجلسة…</p></div>
  return (
    <div className="page container"><div className="login-grid">
      <section className="login-form-panel panel panel-beige" aria-labelledby="register-title">
        <span className="icon-box"><Icon name="lock" /></span>
        <h1 id="register-title">إنشاء حساب عميل</h1>
        {user ? <><p>أنت مسجل باسم {user.name}.</p><Link className="button button-red" to={returnTo}>متابعة إلى حسابك</Link></>
          : success ? <><p className="feedback feedback-success" role="status">تم إنشاء حسابك. سجّل الدخول للمتابعة؛ لم تُفتح جلسة تلقائيًا.</p>
            <Link className="button button-red button-wide" to="/login" state={{ returnTo }}>تسجيل الدخول</Link></>
            : <>
              <p className="login-subtitle">أنشئ حسابًا لاستكشاف العقارات وحفظ اختياراتك.</p>
              {error && <p className="feedback feedback-error" role="alert">{error}</p>}
              <form onSubmit={submit} noValidate>
                {Object.keys(errors).some((key) => errors[key]) && <div className="feedback feedback-error auth-error-summary" role="alert" tabIndex={-1} ref={errorSummary}><p>راجع البيانات التالية لإكمال التسجيل:</p><ul>{Object.entries(errors).filter(([, message]) => message).map(([key, message]) => <li key={key}><a href={`#register-${key}`}>{message}</a></li>)}</ul></div>}
                <div className="form-field"><label htmlFor="register-name">الاسم</label>
                  <input id="register-name" name="name" autoComplete="name" maxLength={255} required value={values.name} onChange={change} disabled={busy} aria-invalid={!!errors.name} aria-describedby={errors.name ? 'register-name-error' : undefined} />
                  {errors.name && <p id="register-name-error" className="feedback-error">{errors.name}</p>}</div>
                <div className="form-field"><label htmlFor="register-email">البريد الإلكتروني</label>
                  <input id="register-email" name="email" type="email" autoComplete="email" dir="ltr" maxLength={255} required value={values.email} onChange={change} disabled={busy} aria-invalid={!!errors.email} aria-describedby={errors.email ? 'register-email-error' : undefined} />
                  {errors.email && <p id="register-email-error" className="feedback-error">{errors.email}</p>}</div>
                <div className="form-field"><label htmlFor="register-password">كلمة المرور</label>
                  <PasswordInput id="register-password" name="password" autoComplete="new-password" dir="auto" required value={values.password} onChange={change} disabled={busy} aria-invalid={!!errors.password} aria-describedby={errors.password ? 'register-password-error register-password-hint' : 'register-password-hint'} />
                  <p id="register-password-hint" className="form-note">6 أحرف على الأقل، وبحد أقصى 72 بايت؛ الأحرف العربية قد تستهلك أكثر من بايت.</p>
                  {errors.password && <p id="register-password-error" className="feedback-error">{errors.password}</p>}</div>
                <button className="button button-red button-wide" type="submit" disabled={busy}>{busy ? 'جارٍ إنشاء الحساب…' : 'إنشاء الحساب'}</button>
              </form>
              <p className="form-note">لديك حساب؟ <Link to="/login" state={{ returnTo }}>سجّل الدخول</Link></p>
            </>}
      </section>
      <section className="login-intro panel panel-red"><span className="eyebrow">روافد العقارية</span><BrandMark className="login-mark" />
        <h2>ابدأ رحلتك العقارية.</h2><p>حساب العميل لاستكشاف العروض. إضافة العقارات وإدارتها متاحة للأدمن فقط.</p>
        <Link to="/properties" className="button button-outline">استكشف العقارات <Icon name="arrow" /></Link></section>
    </div></div>
  )
}
