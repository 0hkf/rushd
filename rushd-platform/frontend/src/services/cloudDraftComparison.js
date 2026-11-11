import { propertyFormState } from './adminProperty.js'
import { specificationFields, specificationFormState } from './propertySpecifications.js'

const propertyLabels = Object.freeze({
  title: 'عنوان العقار', type: 'نوع العقار', city: 'المدينة', district: 'الحي', area: 'المساحة المعلنة',
  price: 'السعر', listingType: 'نوع العرض', rentalPeriod: 'فترة الإيجار', streetWidth: 'عرض الشارع',
  facade: 'الواجهة', purpose: 'استخدام العقار', description: 'الوصف', status: 'حالة النشر',
  formattedAddress: 'العنوان التفصيلي', neighborhood: 'اسم المنطقة أو الحي', latitude: 'خط العرض',
  longitude: 'خط الطول', googlePlaceId: 'معرّف الموقع',
})
const specLabels = Object.fromEntries(specificationFields.map(({ name, label }) => [name, label]))
const sections = ['values', 'specifications', 'sourceErrors', 'specificationSourceErrors', 'typeDrafts', 'scopes']

function leaves(value, path, output) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const key of Object.keys(value).sort()) leaves(value[key], [...path, key], output)
    return
  }
  // Display unknown/non-text values as unknown rather than coercing a numeric amount.
  output.set(JSON.stringify(path), { path, value: typeof value === 'string' ? value : null })
}

function label(path) {
  const [section, ...parts] = path
  if (section === 'values') return propertyLabels[parts[0]] ?? parts.join(' / ')
  if (section === 'specifications') return specLabels[parts[0]] ?? parts.join(' / ')
  if (section === 'sourceErrors') return `تنبيه قراءة: ${propertyLabels[parts[0]] ?? parts[0]}`
  if (section === 'specificationSourceErrors') return `تنبيه قراءة: ${specLabels[parts[0]] ?? parts[0]}`
  if (section === 'scopes') return `نطاق النوع المحفوظ (${parts.join(' / ')})`
  const [type, group, field] = parts
  const detail = group === 'values' ? specLabels[field] ?? field : group === 'sourceErrors'
    ? `تنبيه قراءة: ${specLabels[field] ?? field}` : group === 'area' ? 'المساحة' : parts.slice(1).join(' / ')
  return `مدخلات نوع آخر (${type}) — ${detail}`
}

/** Exact textual differences. A missing value is distinct from an intentionally empty string. */
export function compareDraftSnapshots(local, remote, { propertyOnly = false } = {}) {
  const left = new Map(), right = new Map()
  const chosen = propertyOnly ? sections.slice(0, 4) : sections
  for (const section of chosen) {
    if (local && Object.hasOwn(local, section)) leaves(local[section], [section], left)
    if (remote && Object.hasOwn(remote, section)) leaves(remote[section], [section], right)
  }
  return [...new Set([...left.keys(), ...right.keys()])].sort().flatMap((key) => {
    const l = left.get(key), r = right.get(key)
    if (l && r && l.value === r.value) return []
    const path = (l ?? r).path
    return [{ key, label: label(path), local: l?.value, remote: r?.value }]
  })
}

/** Only fields which the property response can actually represent are compared. */
export function propertyReviewSnapshot(property) {
  const main = propertyFormState(property), specs = specificationFormState(property)
  return { values: main.values, specifications: specs.values, sourceErrors: main.sourceErrors,
    specificationSourceErrors: specs.sourceErrors }
}

export function reviewValue(value) {
  if (value === undefined) return 'غير موجود في النسخة'
  if (value === null) return 'تعذرت قراءته بدقة'
  return value === '' ? 'فارغ' : value
}
