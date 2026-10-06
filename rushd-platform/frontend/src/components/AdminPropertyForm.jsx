import { useState } from 'react'
import api from '../services/api'
import Icon from './Icon'
import {
  propertyTypes,
  purposes,
  facadeLabels,
  statusLabels,
  errorMessage,
} from '../services/property'

const blank = {
  title: '',
  type: 'LAND',
  city: '',
  district: '',
  area: '',
  price: '',
  listingType: 'SALE',
  rentalPeriod: '',
  streetWidth: '',
  facade: 'UNKNOWN',
  purpose: 'RESIDENTIAL',
  description: '',
  status: 'ACTIVE',
  formattedAddress: '',
  neighborhood: '',
  latitude: '',
  longitude: '',
  googlePlaceId: '',
}
const optionalNumbers = ['streetWidth', 'latitude', 'longitude']
const optionalTexts = ['formattedAddress', 'neighborhood', 'googlePlaceId', 'description']

function AdminPropertyForm({ property, onSaved, onCancel }) {
  const [values, setValues] = useState(() =>
    Object.fromEntries(Object.keys(blank).map((key) => [key, property?.[key] ?? blank[key]])),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  function change(event) {
    const { name, value } = event.target
    setValues((previous) => {
      const next = { ...previous, [name]: value }
      if (name === 'listingType') {
        next.rentalPeriod = value === 'RENT' ? 'YEARLY' : ''
        if (value === 'RENT' && next.status === 'SOLD') next.status = 'ACTIVE'
      }
      return next
    })
  }
  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    const payload = {
      ...values,
      area: Number(values.area),
      price: Number(values.price),
      rentalPeriod: values.listingType === 'RENT' ? values.rentalPeriod : null,
    }
    optionalNumbers.forEach((key) => {
      payload[key] = values[key] === '' ? null : Number(values[key])
    })
    optionalTexts.forEach((key) => {
      payload[key] = values[key] || null
    })
    try {
      const { data } = property
        ? await api.put(`/api/properties/${property.id}`, payload)
        : await api.post('/api/properties', payload)
      if (!property) setValues({ ...blank })
      onSaved(data)
    } catch (error) {
      setError(
        error.response?.status === 401
          ? 'انتهت الجلسة. سجّل الدخول مجددًا قبل الحفظ.'
          : errorMessage(error),
      )
    } finally {
      setBusy(false)
    }
  }
  const input = (name, label, type = 'text', options = {}) => (
    <div className="form-field" key={name}>
      <label htmlFor={`property-${name}`}>{label}</label>
      <input
        id={`property-${name}`}
        name={name}
        type={type}
        value={values[name]}
        onChange={change}
        {...options}
      />
    </div>
  )
  const select = (name, label, choices) => (
    <div className="form-field" key={name}>
      <label htmlFor={`property-${name}`}>{label}</label>
      <select id={`property-${name}`} name={name} value={values[name]} onChange={change} required>
        {Object.entries(choices).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </div>
  )
  return (
    <section className="panel admin-form-panel" id="property-editor" aria-labelledby="editor-title">
      <div className="section-heading">
        <span className="eyebrow">إدخال يدوي بواسطة الأدمن</span>
        <h2 id="editor-title">{property ? 'تعديل مواصفات العقار' : 'إضافة عقار جديد'}</h2>
        <p>الحقول المطلوبة تحدد المواصفات والسعر؛ بيانات الموقع التفصيلية اختيارية.</p>
      </div>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <div className="form-grid">
            {input('title', 'عنوان العقار', 'text', { required: true, maxLength: 150 })}
            {select('type', 'نوع العقار', propertyTypes)}
            {select('listingType', 'نوع العرض', { SALE: 'للبيع', RENT: 'للإيجار' })}
            {values.listingType === 'RENT' &&
              select('rentalPeriod', 'فترة الإيجار', { YEARLY: 'سنوي', MONTHLY: 'شهري' })}
            {input(
              'price',
              values.listingType === 'RENT'
                ? `قيمة الإيجار ${values.rentalPeriod === 'MONTHLY' ? 'الشهري' : 'السنوي'} (ر.س)`
                : 'سعر البيع (ر.س)',
              'number',
              { required: true, min: '0.01', step: '0.01' },
            )}
            {input('area', 'المساحة (م²)', 'number', { required: true, min: '0.01', step: '0.01' })}
            {input('city', 'المدينة', 'text', { required: true, maxLength: 100 })}
            {input('district', 'الحي', 'text', { required: true, maxLength: 100 })}
            {select('purpose', 'استخدام العقار', purposes)}
            {select('facade', 'الواجهة', facadeLabels)}
            {input('streetWidth', 'عرض الشارع (م) — اختياري', 'number', {
              min: '0.01',
              step: '0.01',
            })}
            {select(
              'status',
              'حالة النشر',
              Object.fromEntries(
                Object.entries(statusLabels).filter(
                  ([key]) => values.listingType === 'SALE' || key !== 'SOLD',
                ),
              ),
            )}
          </div>
          <div className="form-field">
            <label htmlFor="property-description">وصف العقار — اختياري</label>
            <textarea
              id="property-description"
              name="description"
              rows={4}
              maxLength={3000}
              value={values.description}
              onChange={change}
            />
          </div>
          <details className="location-fields">
            <summary>بيانات الموقع التفصيلية — اختيارية</summary>
            <div className="form-grid">
              {input('formattedAddress', 'العنوان التفصيلي', 'text', { maxLength: 500 })}
              {input('neighborhood', 'اسم المنطقة أو الحي', 'text', { maxLength: 150 })}
              {input('latitude', 'خط العرض', 'number', { min: -90, max: 90, step: 'any' })}
              {input('longitude', 'خط الطول', 'number', { min: -180, max: 180, step: 'any' })}
              {input('googlePlaceId', 'معرّف الموقع إن توفر', 'text', { maxLength: 255 })}
            </div>
          </details>
          {error && (
            <p className="feedback feedback-error" role="alert">
              {error}
            </p>
          )}
          <div className="form-actions">
            <button className="button button-red" type="submit">
              {busy ? 'جارٍ الحفظ…' : property ? 'حفظ التعديلات' : 'حفظ العقار'}
              <Icon name="check" />
            </button>
            {property && (
              <button className="button button-outline" type="button" onClick={onCancel}>
                إلغاء التعديل
              </button>
            )}
          </div>
        </fieldset>
      </form>
    </section>
  )
}
export default AdminPropertyForm
