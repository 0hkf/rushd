function Properties() {
  const placeholderProperties = [
    { id: 1, title: 'شقة في الرياض – حي النرجس', price: '850,000 ر.س', area: '150 م²', type: 'شقة' },
    { id: 2, title: 'أرض في جدة – حي الشاطئ', price: '1,200,000 ر.س', area: '400 م²', type: 'أرض' },
    { id: 3, title: 'فيلا في الدمام – حي الفيصلية', price: '2,500,000 ر.س', area: '350 م²', type: 'فيلا' },
  ]

  return (
    <div className="page" dir="rtl" style={{ padding: '2rem' }}>
      <h1 style={{ color: '#1a1a2e', marginBottom: '0.5rem' }}>العقارات</h1>
      <p style={{ color: '#666', marginBottom: '2rem' }}>استعرض العقارات المتاحة وحلل أسعارها</p>

      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
        {placeholderProperties.map((property) => (
          <div
            key={property.id}
            style={{
              background: '#fff',
              border: '1px solid #eee',
              borderRadius: '10px',
              padding: '1.5rem',
              width: '260px',
              boxShadow: '0 2px 10px rgba(0,0,0,0.06)',
            }}
          >
            <span
              style={{
                background: '#f0e8d8',
                color: '#8a6a2e',
                fontSize: '0.75rem',
                padding: '0.2rem 0.6rem',
                borderRadius: '12px',
                fontWeight: '600',
              }}
            >
              {property.type}
            </span>
            <h3 style={{ color: '#1a1a2e', margin: '0.75rem 0 0.5rem', fontSize: '1rem' }}>
              {property.title}
            </h3>
            <p style={{ color: '#e2b96f', fontWeight: '700', fontSize: '1.05rem', margin: '0.25rem 0' }}>
              {property.price}
            </p>
            <p style={{ color: '#888', fontSize: '0.875rem' }}>المساحة: {property.area}</p>
            <button
              disabled
              style={{
                marginTop: '1rem',
                width: '100%',
                padding: '0.5rem',
                background: '#1a1a2e',
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                cursor: 'not-allowed',
                opacity: 0.6,
                fontSize: '0.875rem',
              }}
            >
              عرض التفاصيل
            </button>
          </div>
        ))}
      </div>

      <p style={{ marginTop: '2rem', color: '#aaa', fontSize: '0.85rem' }}>
        📌 هذه بيانات تجريبية — سيتم ربط العقارات بالـ Backend لاحقاً
      </p>
    </div>
  )
}

export default Properties
