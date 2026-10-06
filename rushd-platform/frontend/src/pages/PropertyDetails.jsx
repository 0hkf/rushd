import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api from '../services/api'
import Icon from '../components/Icon'
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
  const [property, setProperty] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    api
      .get(`/api/properties/${id}`)
      .then(({ data }) => {
        if (active) setProperty(data)
      })
      .catch((error) => {
        if (active)
          setError(
            error.response?.status === 404 ? 'العقار غير موجود أو غير منشور.' : errorMessage(error),
          )
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [id])
  return (
    <div className="page container">
      <Link className="back-link" to="/properties">
        العودة إلى العقارات
      </Link>
      {loading ? (
        <p className="notice" role="status">
          جارٍ تحميل المواصفات…
        </p>
      ) : error ? (
        <p className="notice feedback-error" role="alert">
          {error}
        </p>
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
            <div className="details-layout">
              <section className="panel details-specs">
                <h2>مواصفات العقار</h2>
                <dl className="spec-grid">
                  {[
                    ['نوع العقار', propertyTypes[property.type]],
                    ['المدينة', property.city],
                    ['الحي', property.district],
                    ['المساحة', `${money(property.area)} م²`],
                    ['الاستخدام', purposes[property.purpose] || property.purpose],
                    ['الواجهة', facadeLabels[property.facade] || 'غير محددة'],
                    [
                      'عرض الشارع',
                      property.streetWidth ? `${money(property.streetWidth)} م` : 'غير محدد',
                    ],
                    ['العنوان', property.formattedAddress || 'غير محدد'],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                </dl>
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
                <strong>{money(property.price)}</strong>
                <p>{priceSuffix(property)}</p>
                <p>المواصفات منشورة بإدارة روافد العقارية.</p>
              </aside>
            </div>
          </>
        )
      )}
    </div>
  )
}
export default PropertyDetails
