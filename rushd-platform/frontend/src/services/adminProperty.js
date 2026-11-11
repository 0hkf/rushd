import { decimalErrorMessage, parseDecimalInput, propertyDecimalRules } from './decimal.js'
import { statusLabels } from './property.js'

export const blankProperty = Object.freeze({
  title: '', type: 'LAND', city: '', district: '', area: '', price: '', listingType: 'SALE',
  rentalPeriod: '', streetWidth: '', facade: 'UNKNOWN', purpose: 'RESIDENTIAL', description: '',
  status: 'ACTIVE', formattedAddress: '', neighborhood: '', latitude: '', longitude: '', googlePlaceId: '',
})

const labels = { price: 'السعر', area: 'المساحة', streetWidth: 'عرض الشارع', latitude: 'خط العرض', longitude: 'خط الطول' }
const optionalTexts = ['formattedAddress', 'neighborhood', 'googlePlaceId', 'description']

export function propertyStatusChoices(listingType) {
  return Object.fromEntries(
    Object.entries(statusLabels).filter(([status]) => listingType !== 'RENT' || status !== 'SOLD'),
  )
}

export function propertyStatusError(listingType, status) {
  if (!Object.hasOwn(statusLabels, status)) return 'اختر حالة نشر صالحة للعقار.'
  if (!Object.hasOwn(propertyStatusChoices(listingType), status))
    return 'حالة مباع لعروض البيع فقط. اختر حالة مناسبة للإيجار قبل الحفظ.'
  return null
}

export function propertyFormChange(values, name, value) {
  const next = { ...values, [name]: value }
  if (name === 'listingType') next.rentalPeriod = value === 'RENT' ? 'YEARLY' : ''
  return next
}

export function propertySavedMessage(status) {
  switch (status) {
    case 'ACTIVE': return 'تم حفظ العقار ونشر مواصفاته.'
    case 'SOLD': return 'تم حفظ العقار بحالة مباع. يبقى رابط المواصفات متاحًا للزوار، ولا يظهر ضمن العروض المتاحة.'
    case 'DRAFT': return 'تم حفظ العقار كمسودة دون نشره للزوار.'
    case 'INACTIVE': return 'تم حفظ العقار بحالة غير منشور دون إتاحته للزوار.'
    default: return 'تم حفظ العقار.'
  }
}

export function propertyFormState(property) {
  const values = Object.fromEntries(Object.keys(blankProperty).map((key) => [key, property?.[key] ?? blankProperty[key]]))
  const sourceErrors = {}
  for (const key of Object.keys(propertyDecimalRules)) {
    const raw = property?.[key]
    values[key] = typeof raw === 'string' ? raw : ''
    if (raw != null && typeof raw !== 'string')
      sourceErrors[key] = `تعذر قراءة ${labels[key]} بدقة من الخادم. أعد تحميل العقار أو أدخل القيمة الصحيحة قبل الحفظ.`
  }
  return { values, sourceErrors }
}

export function propertyFormPayload(values, sourceErrors = {}) {
  const errors = { ...sourceErrors }
  const payload = Object.fromEntries(Object.keys(blankProperty).map((key) => [key, values[key]]))
  const statusError = propertyStatusError(values.listingType, values.status)
  if (statusError) errors.status = statusError
  for (const [key, rules] of Object.entries(propertyDecimalRules)) {
    const result = parseDecimalInput(values[key], rules)
    if (result.error && !errors[key]) errors[key] = decimalErrorMessage(result.error, labels[key], rules)
    payload[key] = result.value
  }
  payload.rentalPeriod = values.listingType === 'RENT' ? values.rentalPeriod : null
  optionalTexts.forEach((key) => { payload[key] = values[key] || null })
  return { payload: Object.keys(errors).length ? null : payload, errors }
}
