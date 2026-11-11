import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../services/authState'
import { money, priceSuffix, statusLabels, errorMessage } from '../services/property'
import { propertySavedMessage } from '../services/adminProperty'
import AdminPropertyForm from '../components/AdminPropertyForm'
import CloudDraftManager from '../components/CloudDraftManager'
import AdminPropertyImages from '../components/AdminPropertyImages'
import PropertyImage from '../components/PropertyImage'
import Icon from '../components/Icon'

function Dashboard() {
  const cloudDraftsEnabled = import.meta.env?.VITE_ADMIN_CLOUD_DRAFTS_ENABLED === 'true'
  const { user, loading: authLoading, authError } = useAuth()
  const [result, setResult] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [editing, setEditing] = useState(null)
  const [success, setSuccess] = useState('')
  const [imageGuard, setImageGuard] = useState({ busy: false, dirty: false })
  const [pendingEditor, setPendingEditor] = useState(null)
  const editorNotice = useRef(null)
  const saveNotice = useRef(null)
  const cloudSession = useRef(null)
  const cloudSessionChanged = useCallback((session) => { cloudSession.current = session }, [])
  const imagesGuard = useCallback((next) => setImageGuard((previous) => previous.busy === next.busy && previous.dirty === next.dirty ? previous : next), [])
  useEffect(() => { if (pendingEditor) editorNotice.current?.focus() }, [pendingEditor])
  useEffect(() => { if (success) saveNotice.current?.focus() }, [success])
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
    setEditing(property)
    setSuccess(propertySavedMessage(property.status))
    setRefresh((value) => value + 1)
  }
  async function edit(property) {
    if (imageGuard.busy) return
    if (imageGuard.dirty) { setPendingEditor({ property }); return }
    await switchEditor(property)
  }
  async function switchEditor(property) {
    const session = cloudDraftsEnabled && cloudSession.current
    if (session) {
      if (['conflict', 'commit-unknown', 'committing'].includes(session.state().phase)) {
        setError('راجع حالة المسودة الحالية قبل تغيير العقار.')
        return
      }
      try { await session.flush() }
      catch { setError('لم يؤكد الخادم حفظ المسودة الحالية. أعد المحاولة قبل الانتقال.'); return }
    }
    openEditor(property)
  }
  function openEditor(property) {
    setEditing(property)
    setImageGuard({ busy: false, dirty: false })
    setPendingEditor(null)
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
          {user?.role === 'ADMIN' ? 'إدارة العقارات' : 'حسابك في روافد'}
        </h1>
        <p>
          {user?.role === 'ADMIN'
            ? 'أضف عقارات البيع والإيجار يدويًا، وحدد المواصفات والسعر وحالة النشر.'
            : 'استعرض عقارات البيع والإيجار التي تنشرها إدارة المنصة.'}
        </p>
      </header>
      {!authLoading && user?.role === 'ADMIN' && <nav className="ui-section-nav" aria-label="أقسام لوحة الإدارة"><a href={cloudDraftsEnabled ? '#cloud-drafts' : '#property-editor'}>{cloudDraftsEnabled ? 'مسودات العقارات' : 'إضافة عقار'}</a><a href="#managed-properties">العقارات المُدارة</a>{editing && <a href="#property-images">صور العقار المحدد</a>}<Link to="/properties">العروض العامة</Link></nav>}
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
            <p className="feedback feedback-success" role="status" tabIndex={-1} ref={saveNotice}>
              {success}
            </p>
          )}
          {pendingEditor && <section className="feedback image-confirmation" ref={editorNotice} tabIndex={-1} aria-labelledby="leave-images-title">
            <h2 id="leave-images-title">ترك مسودة إدارة الصور؟</h2>
            <p>ستفقد الملفات غير المرفوعة والتعديلات المحلية على الصور. ما حفظه الخادم لا يُلغى، وقد تبقى عملية حذف تحتاج تنظيفًا. مواصفات العقار لا تُحفظ من هذا التأكيد.</p>
            <div className="form-actions"><button type="button" className="button button-red" onClick={() => switchEditor(pendingEditor.property)}>ترك مسودة الصور والمتابعة</button>
              <button type="button" className="button button-outline" onClick={() => setPendingEditor(null)}>البقاء في إدارة الصور</button></div>
          </section>}
          {cloudDraftsEnabled ? <CloudDraftManager editing={editing} onSaved={saved} onCancel={() => edit(null)}
            onSession={cloudSessionChanged} onEditingChange={setEditing} />
            : <AdminPropertyForm key={editing?.id || 'new'} property={editing} onSaved={saved} onCancel={() => edit(null)} />}
          {editing ? <AdminPropertyImages key={`${user.id}:${editing.id}`} property={editing}
            onChanged={() => setRefresh((value) => value + 1)} onGuard={imagesGuard} />
            : <p className="form-note">احفظ بيانات العقار أولًا؛ بعدها يظهر قسم إدارة الصور مستقلًا دون الحاجة لإعادة إدخال مواصفاته.</p>}
          <section className="managed-properties" id="managed-properties">
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
                      <PropertyImage property={property} className="managed-primary-image" />
                      <div>
                        <span className="badge" data-status={property.status}>
                          {statusLabels[property.status]} ·{' '}
                          {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'}
                        </span>
                        <h3>{property.title}</h3>
                        <p>
                          {property.city} · {property.district} · <bdi dir="ltr">{money(property.price)}</bdi>{' '}
                          {priceSuffix(property)}
                        </p>
                      </div>
                      <div className="row-actions">
                        <button
                          className="button button-red"
                          type="button"
                          disabled={imageGuard.busy}
                          onClick={() => edit(property)}
                        >
                          تعديل
                        </button>
                        <Link className="button button-outline" to={`/properties/${property.id}`}>
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
