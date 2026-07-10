function Dashboard() {
  return (
    <div className="page" dir="rtl" style={{ padding: '2rem' }}>
      <h1 style={{ color: '#1a1a2e', marginBottom: '0.5rem' }}>لوحة التحكم</h1>
      <p style={{ color: '#666', marginBottom: '2rem' }}>
        مرحباً — هنا ستجد ملخص نشاطك وعقاراتك المحفوظة
      </p>

      <div style={{ display: 'flex', gap: '1.25rem', flexWrap: 'wrap', marginBottom: '2.5rem' }}>
        {[
          { label: 'العقارات المحفوظة', value: '—', icon: '🏠' },
          { label: 'المقارنات المنجزة', value: '—', icon: '📊' },
          { label: 'التحليلات المكتملة', value: '—', icon: '✅' },
        ].map((stat) => (
          <div
            key={stat.label}
            style={{
              background: '#fff',
              border: '1px solid #eee',
              borderRadius: '10px',
              padding: '1.5rem 2rem',
              minWidth: '180px',
              boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
              textAlign: 'center',
            }}
          >
            <div style={{ fontSize: '1.8rem', marginBottom: '0.5rem' }}>{stat.icon}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: '700', color: '#1a1a2e', marginBottom: '0.25rem' }}>
              {stat.value}
            </div>
            <div style={{ fontSize: '0.85rem', color: '#888' }}>{stat.label}</div>
          </div>
        ))}
      </div>

      <div
        style={{
          background: '#f9f7f4',
          border: '1px dashed #ddd',
          borderRadius: '10px',
          padding: '2rem',
          textAlign: 'center',
          color: '#aaa',
        }}
      >
        <p style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>📌 لوحة التحكم قيد التطوير</p>
        <p style={{ fontSize: '0.85rem' }}>سيتم عرض بياناتك بعد تسجيل الدخول وربط الـ Backend</p>
      </div>
    </div>
  )
}

export default Dashboard
