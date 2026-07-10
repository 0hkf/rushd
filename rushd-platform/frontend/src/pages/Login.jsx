function Login() {
  return (
    <div className="page" dir="rtl" style={{ maxWidth: '420px', margin: '4rem auto', padding: '0 1rem' }}>
      <h1 style={{ color: '#1a1a2e', marginBottom: '0.5rem' }}>تسجيل الدخول</h1>
      <p style={{ color: '#666', marginBottom: '2rem' }}>أدخل بياناتك للوصول إلى حسابك</p>

      <form style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '0.35rem', color: '#444', fontWeight: '500' }}>
            البريد الإلكتروني
          </label>
          <input
            type="email"
            placeholder="example@email.com"
            disabled
            style={{
              width: '100%',
              padding: '0.65rem 0.9rem',
              borderRadius: '6px',
              border: '1px solid #ddd',
              fontSize: '0.95rem',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div>
          <label style={{ display: 'block', marginBottom: '0.35rem', color: '#444', fontWeight: '500' }}>
            كلمة المرور
          </label>
          <input
            type="password"
            placeholder="••••••••"
            disabled
            style={{
              width: '100%',
              padding: '0.65rem 0.9rem',
              borderRadius: '6px',
              border: '1px solid #ddd',
              fontSize: '0.95rem',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <button
          type="button"
          disabled
          style={{
            backgroundColor: '#e2b96f',
            color: '#1a1a2e',
            border: 'none',
            padding: '0.75rem',
            borderRadius: '6px',
            fontSize: '1rem',
            fontWeight: '600',
            cursor: 'not-allowed',
            opacity: 0.7,
            marginTop: '0.5rem',
          }}
        >
          دخول
        </button>

        <p style={{ textAlign: 'center', color: '#888', fontSize: '0.85rem', marginTop: '0.5rem' }}>
          🔒 تسجيل الدخول غير متاح حالياً — قيد التطوير
        </p>
      </form>
    </div>
  )
}

export default Login
