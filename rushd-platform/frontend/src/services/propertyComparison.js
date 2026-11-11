import { createRequestGate } from './requestGate.js'
import { formatDecimal, parseDecimalInput, propertyDecimalRules } from './decimal.js'
import { facadeLabels, propertyTypes, purposes, statusLabels } from './property.js'
import { specificationFields, specificationRows } from './propertySpecifications.js'

export const comparisonLimit = 3
export function comparisonId(value) {
  const id = typeof value === 'number' && Number.isSafeInteger(value) ? String(value) : value
  if (typeof id !== 'string' || !/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) throw new Error('معرّف العقار غير صالح.')
  return id
}

// IDs only: no stale titles, prices or private account data in selection state/storage.
export function changeComparison(state, action, value) {
  if (action === 'clear') return { ids: [], notice: 'تم مسح اختيارات المقارنة.' }
  const id = comparisonId(value)
  if (action === 'remove') return { ids: state.ids.filter((item) => item !== id), notice: 'أُزيل العقار من المقارنة.' }
  if (action !== 'add') throw new Error('عملية مقارنة غير معروفة.')
  if (state.ids.includes(id)) return state
  if (state.ids.length >= comparisonLimit) return { ...state, notice: 'يمكن مقارنة ثلاثة عقارات فقط. أزل أحد الاختيارات أولًا.' }
  return { ids: [...state.ids, id], notice: `أُضيف العقار للمقارنة. اخترت ${state.ids.length + 1} من ${comparisonLimit}.` }
}

function readDecimal(value, name, required = true) {
  if (!required && value == null) return null
  if (typeof value !== 'string' || !/^-?\d+(\.\d+)?$/.test(value)
    || parseDecimalInput(value, { ...propertyDecimalRules[name], required }).error) throw new Error('قيمة عشرية غير صالحة من الخادم.')
  return value // Never pass exact decimals through Number, including values above MAX_SAFE_INTEGER.
}

export function readComparisonProperty(raw, expectedId) {
  if (!raw || comparisonId(raw.id) !== expectedId) throw new Error('بيانات عقار غير متطابقة.')
  // Even an ADMIN response for a private property must not enter the public comparison.
  if (!['ACTIVE', 'SOLD'].includes(raw.status)) return null
  if (!Object.hasOwn(propertyTypes, raw.type) || !['SALE', 'RENT'].includes(raw.listingType)
    || ['title', 'city', 'district'].some((name) => typeof raw[name] !== 'string')
    || (raw.specifications != null && (typeof raw.specifications !== 'object' || Array.isArray(raw.specifications)))) throw new Error('مواصفات العقار غير صالحة.')
  return {
    id: expectedId, title: raw.title, type: raw.type, status: raw.status,
    listingType: raw.listingType, rentalPeriod: raw.rentalPeriod,
    city: raw.city, district: raw.district, neighborhood: raw.neighborhood,
    formattedAddress: raw.formattedAddress, facade: raw.facade, purpose: raw.purpose,
    price: readDecimal(raw.price, 'price'), area: readDecimal(raw.area, 'area'),
    streetWidth: readDecimal(raw.streetWidth, 'streetWidth', false),
    specifications: raw.specifications ?? {},
  }
}

export function createComparisonLoader(client, publish) {
  const gate = createRequestGate()
  return {
    async load(values) {
      const ids = values.map(comparisonId)
      if (ids.length > comparisonLimit || new Set(ids).size !== ids.length) throw new Error('اختيارات المقارنة غير صالحة.')
      const ticket = gate.begin()
      publish({ phase: 'loading', entries: [], ids })
      const entries = await Promise.all(ids.map(async (id) => {
        try {
          const { data } = await client.get(`/api/properties/${id}`, { signal: ticket.controller.signal })
          const property = readComparisonProperty(data, id)
          return { id, phase: property ? 'ready' : 'unavailable', property }
        } catch (error) {
          return { id, phase: [403, 404].includes(error?.response?.status) ? 'unavailable' : 'error', property: null }
        }
      }))
      if (gate.isCurrent(ticket)) publish({ phase: 'ready', entries, ids })
    },
    cancel() { gate.cancel() },
  }
}

const textCell = (value) => ({ text: typeof value === 'string' && value.trim() ? value : 'غير محدد' })
const decimalCell = (value, unit) => ({ text: formatDecimal(value) ?? 'غير محدد', numeric: formatDecimal(value) != null, unit: formatDecimal(value) != null ? unit : '' })
function priceCell(property) {
  const unit = property.listingType === 'SALE' ? 'ر.س — بيع'
    : property.rentalPeriod === 'MONTHLY' ? 'ر.س / شهر'
      : property.rentalPeriod === 'YEARLY' ? 'ر.س / سنة' : 'ر.س — فترة الإيجار غير محددة'
  return decimalCell(property.price, unit)
}
const scopeDependent = new Set(['landArea', 'unitNetArea', 'unitGrossArea', 'totalBuiltArea', 'floorNumber', 'floorCount', 'totalUnits', 'residentialUnits', 'commercialUnits'])

/** Display only; selection order stays intact. No price ranking, period conversion or valuation. */
export function comparisonRows(entries) {
  const publicEntries = entries.filter((entry) => entry.phase === 'ready' && entry.property)
  if (!publicEntries.length) return []
  const row = (name, label, read) => ({ name, label, cells: entries.map((entry) => entry.phase === 'ready' && entry.property ? read(entry.property) : { text: 'غير متاح' }) })
  const rows = [
    row('type', 'نوع العقار', (p) => textCell(propertyTypes[p.type])),
    row('status', 'حالة النشر', (p) => textCell(statusLabels[p.status])),
    row('listingType', 'نوع العرض', (p) => textCell(p.listingType === 'SALE' ? 'للبيع' : 'للإيجار')),
    row('price', 'السعر بوحدته الأصلية', priceCell),
    row('city', 'المدينة', (p) => textCell(p.city)),
    row('district', 'الحي المسجل', (p) => textCell(p.district)),
    row('neighborhood', 'الحي التفصيلي', (p) => textCell(p.neighborhood)),
    row('formattedAddress', 'العنوان', (p) => textCell(p.formattedAddress)),
    row('area', 'المساحة الرئيسية المعلنة', (p) => decimalCell(p.area, 'م²')),
    row('purpose', 'الاستخدام', (p) => textCell(purposes[p.purpose])),
    row('facade', 'الواجهة', (p) => textCell(facadeLabels[p.facade])),
    row('streetWidth', 'عرض الشارع', (p) => decimalCell(p.streetWidth, 'م')),
  ]
  const specifications = new Map(publicEntries.map(({ property }) => [property.id, new Map(specificationRows(property).map((item) => [item.name, item]))]))
  for (const field of specificationFields) {
    if (field.name === 'ageAsOfYear' || !publicEntries.some(({ property }) => specifications.get(property.id).has(field.name))) continue
    rows.push(row(`spec-${field.name}`, field.label, (p) => specifications.get(p.id).get(field.name)
      ?? { text: p.type === 'COMMERCIAL' && !['UNIT', 'WHOLE_BUILDING'].includes(p.specifications.commercialScope) && scopeDependent.has(field.name) ? 'النطاق التجاري غير محدد' : 'لا ينطبق' }))
  }
  return rows
}
