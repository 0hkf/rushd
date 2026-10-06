import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../services/authState'
import { money, priceSuffix, statusLabels, errorMessage } from '../services/property'
import AdminPropertyForm from '../components/AdminPropertyForm'
import Icon from '../components/Icon'

function Dashboard() {
  const { user, loading: authLoading, authError } = useAuth()
  const [result, setResult] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [editing, setEditing] = useState(null)
  const [success, setSuccess] = useState('')
  useEffect(() => {
    if (user?.role !== 'ADMIN') return
    let active = true
    setLoading(true)
    setError('')
    api
      .get('/api/admin/properties', { params: { page } })
      .then(({ data }) => {
        if (active) setResult(data)
      })
      .catch((error) => {
        if (active) setError(errorMessage(error))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [user, page, refresh])
  function saved(property) {
    setEditing(null)
    setSuccess(
      property.status === 'ACTIVE'
        ? 'تم حفظ العقار ونشر مواصفاته.'
        : 'تم حفظ العقار دون نشره للزوار.',
    )
    setRefresh((value) => value + 1)
  }
  function edit(property) {
    setEditing(property)
    setSuccess('')
    document.getElementById('property-editor')?.scrollIntoView({ behavior: 'auto', block: 'start' })
  }
  return (
    <div className="page container">
      <header className="page-heading">
        <span className="eyebrow">
          {user?.role === 'ADMIN' ? 'إدارة روافد العقارية' : 'مساحتك في روافد'}
        </span>
        <h1>
          {user?.role === 'ADMIN' ? 'عقاراتك. مواصفاتك. إدارتك.' : 'رحلتك العقارية تبدأ هنا.'}
        </h1>
        <p>
          {user?.role === 'ADMIN'
            ? 'أضف عقارات البيع والإيجار يدويًا، وحدد المواصفات والسعر وحالة النشر.'
            : 'استعرض عقارات البيع والإيجار التي تنشرها إدارة المنصة.'}
        </p>
      </header>
      {authLoading ? (
        <p className="notice" role="status">
          جارٍ التحقق من الحساب…
        </p>
      ) : !user ? (
        <div className="panel empty-state">
          <span className="icon-box">
            <Icon name="lock" />
          </span>
          <h2>سجّل الدخول إلى حسابك</h2>
          <p>{authError || 'إدارة نشر العقارات متاحة للأدمن المعتمد فقط.'}</p>
          <Link to="/login" className="button button-red">
            تسجيل الدخول
          </Link>
        </div>
      ) : user.role !== 'ADMIN' ? (
        <div className="panel empty-state">
          <h2>أهلًا، {user.name}</h2>
          <p>
            العقارات يضيفها الأدمن. يمكنك استكشاف العروض ومواصفاتها دون صلاحية النشر أو التعديل.
          </p>
          <Link to="/properties" className="button button-red">
            استعرض العقارات
          </Link>
        </div>
      ) : (
        <>
          {success && (
            <p className="feedback feedback-success" role="status">
              {success}
            </p>
          )}
          <AdminPropertyForm
            key={editing?.id || 'new'}
            property={editing}
            onSaved={saved}
            onCancel={() => setEditing(null)}
          />
          <section className="managed-properties">
            <div className="catalog-heading">
              <h2>العقارات المُدارة</h2>
              <span className="badge">{result?.totalElements ?? '—'} عقار</span>
            </div>
            {loading ? (
              <p className="notice" role="status">
                جارٍ تحميل العقارات…
              </p>
            ) : error ? (
              <div className="notice feedback-error" role="alert">
                <p>{error}</p>
                <button className="button button-outline" onClick={() => setRefresh(refresh + 1)}>
                  إعادة المحاولة
                </button>
              </div>
            ) : result?.content.length ? (
              <>
                <div className="managed-list">
                  {result.content.map((property) => (
                    <article className="panel managed-row" key={property.id}>
                      <div>
                        <span className="badge">
                          {statusLabels[property.status]} ·{' '}
                          {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'}
                        </span>
                        <h3>{property.title}</h3>
                        <p>
                          {property.city} · {property.district} · {money(property.price)}{' '}
                          {priceSuffix(property)}
                        </p>
                      </div>
                      <div className="row-actions">
                        <button
                          className="button button-outline"
                          type="button"
                          onClick={() => edit(property)}
                        >
                          تعديل
                        </button>
                        <Link className="button button-red" to={`/properties/${property.id}`}>
                          المواصفات
                        </Link>
                      </div>
                    </article>
                  ))}
                </div>
                <div className="pagination">
                  <button
                    className="button button-outline"
                    disabled={result.first}
                    onClick={() => setPage(page - 1)}
                  >
                    السابق
                  </button>
                  <span>
                    صفحة {page + 1} من {result.totalPages}
                  </span>
                  <button
                    className="button button-outline"
                    disabled={result.last}
                    onClick={() => setPage(page + 1)}
                  >
                    التالي
                  </button>
                </div>
              </>
            ) : (
              <p className="notice">
                لم تتم إضافة عقارات بعد. استخدم النموذج أعلاه لإضافة أول عرض.
              </p>
            )}
          </section>
        </>
      )}
    </div>
  )
}
export default Dashboard
