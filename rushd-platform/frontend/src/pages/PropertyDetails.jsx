import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import api from '../services/api'
import { decimalSizeClass, propertyStatusNotice } from '../services/interface'
import Icon from '../components/Icon'
import PropertySpecificationsDetails from '../components/PropertySpecificationsDetails'
import PropertyContact from '../components/PropertyContact'
import PropertyGallery from '../components/PropertyGallery'
import PropertyActions from '../components/PropertyActions'
import { useFavoriteBatch } from '../services/favoritesState'
import { useAuth } from '../services/authState'
import { safeCatalogReturnTo } from '../services/propertyCatalog'
import {
  propertyTypes,
  purposes,
  facadeLabels,
  money,
  priceSuffix,
  errorMessage,
} from '../services/property'

function PropertyDetails() {
  const { id } = useParams()
  const location = useLocation()
  const { user, loading: authLoading } = useAuth()
  const viewerKey = `${id}:${user?.id ?? 'guest'}:${user?.role ?? 'none'}`
  const [property, setProperty] = useState(null)
  const [loadedFor, setLoadedFor] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retry, setRetry] = useState(0)
  useFavoriteBatch(loadedFor === viewerKey && ['ACTIVE','SOLD'].includes(property?.status) ? [id] : [])
  useEffect(() => {
    let active = true
    setProperty(null)
    setLoading(true)
    setError('')
    if (authLoading) return () => { active = false }
    api
      .get(`/api/properties/${id}`)
      .then(({ data }) => {
        if (active) { setProperty(data); setLoadedFor(viewerKey) }
      })
      .catch((error) => {
        if (active) {
          setLoadedFor(viewerKey)
          setError(
            error.response?.status === 404 ? 'العقار غير موجود أو غير منشور.' : errorMessage(error),
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [id, viewerKey, authLoading, retry])
  return (
    <div className="page container">
      <Link className="back-link" to={location.state?.comparisonReturnTo === true ? '/compare' : location.state?.favoritesReturnTo === true ? '/favorites' : safeCatalogReturnTo(location.state?.catalogReturnTo)}>
        {location.state?.comparisonReturnTo === true ? 'العودة إلى المقارنة' : location.state?.favoritesReturnTo === true ? 'العودة إلى المفضلة' : 'العودة إلى العقارات'}
      </Link>
      {loading || authLoading || loadedFor !== viewerKey ? (
        <p className="notice" role="status">
          جارٍ تحميل المواصفات…
        </p>
      ) : error ? (
        <section className="panel empty-state" role="alert">
          <span className="icon-box"><Icon name="info" /></span>
          <h1>{error === 'العقار غير موجود أو غير منشور.' ? 'العقار غير متاح حاليًا' : 'تعذّر تحميل العقار'}</h1>
          <p>{error}</p>
          <button className="button button-outline" type="button" onClick={() => setRetry((value) => value + 1)}>إعادة تحميل المواصفات</button>
        </section>
      ) : (
        property && (
          <>
            <header className="page-heading">
              <span className="eyebrow">
                {property.listingType === 'RENT' ? 'للإيجار' : 'للبيع'} ·{' '}
                {propertyTypes[property.type]}
              </span>
              <h1>{property.title}</h1>
              <p>
                {property.city} · {property.district}
              </p>
            </header>
            {propertyStatusNotice(property.status) && <p className="notice details-status" role="status"><Icon name="info" />{propertyStatusNotice(property.status)}</p>}
            <div className="details-actions"><PropertyActions property={property} showDetails={false} /></div>
            {property.status === 'ACTIVE' && (
              <p className="details-evaluation-link">
                {user ? <Link className="button button-outline" to={`/property-needs?property=${id}`}>قيّم هذا العقار حسب احتياجاتي</Link>
                  : <Link className="button button-outline" to="/login" state={{ returnTo: `/properties/${id}` }}>سجّل الدخول لتقييم هذا العقار</Link>}
              </p>
            )}
            <PropertyGallery property={property} />
            <div className="details-layout">
              <section className="panel details-specs">
                <h2>مواصفات العقار</h2>
                <dl className="spec-grid">
                  {[
                    ['نوع العقار', propertyTypes[property.type]],
                    ['المدينة', property.city],
                    ['الحي', property.district],
                    ['المساحة الرئيسية', property.area == null ? 'غير محدد' : <><bdi dir="ltr">{money(property.area)}</bdi> م²</>],
                    ['الاستخدام', purposes[property.purpose] || property.purpose],
                    ['الواجهة', facadeLabels[property.facade] || 'غير محددة'],
                    [
                      'عرض الشارع',
                      property.streetWidth != null ? <><bdi dir="ltr">{money(property.streetWidth)}</bdi> م</> : 'غير محدد',
                    ],
                    ['العنوان', property.formattedAddress || 'غير محدد'],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                </dl>
                <PropertySpecificationsDetails property={property} />
                {property.description && (
                  <div className="description">
                    <h3>وصف العقار</h3>
                    <p>{property.description}</p>
                  </div>
                )}
              </section>
              <aside className="panel panel-red details-price">
                <Icon name="home" />
                <span>{property.listingType === 'RENT' ? 'قيمة الإيجار' : 'سعر البيع'}</span>
                <strong className={decimalSizeClass(property.price)}>
                  <bdi dir="ltr">{money(property.price)}</bdi>
                </strong>
                <p>{priceSuffix(property)}</p>
                <p>المواصفات منشورة بإدارة روافد العقارية.</p>
                <PropertyContact property={property} />
              </aside>
            </div>
          </>
        )
      )}
    </div>
  )
}
export default PropertyDetails
