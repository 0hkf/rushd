import { compareDecimals, decimalErrorMessage, normalizeDecimalInput, parseDecimalInput, propertyDecimalRules } from './decimal.js'
import { errorMessage, propertyTypes } from './property.js'
import { createRequestGate } from './requestGate.js'

export const catalogPageSize = 12
export const catalogSort = 'createdAt,desc'
export const catalogCityChoices = Object.freeze(['مكة المكرمة', 'مكة', 'Makkah'])
const filterNames = ['city', 'district', 'type', 'listingType', 'rentalPeriod', 'minPrice', 'maxPrice']
const queryNames = new Set([...filterNames, 'page', 'size', 'sort'])
const priceRules = Object.freeze({ ...propertyDecimalRules.price, required: false, positive: false, min: '0' })

export function blankCatalogFilters() {
  return { city: 'مكة المكرمة', district: '', type: '', listingType: '', rentalPeriod: '', minPrice: '', maxPrice: '' }
}

export function catalogPriceBasis(values) {
  if (values.listingType === 'SALE') return 'SALE'
  return values.listingType === 'RENT' && ['MONTHLY', 'YEARLY'].includes(values.rentalPeriod) ? values.rentalPeriod : null
}

export function catalogPriceLabel(values) {
  return { SALE: 'سعر البيع بالريال', MONTHLY: 'الإيجار الشهري بالريال', YEARLY: 'الإيجار السنوي بالريال' }[catalogPriceBasis(values)] ?? 'حدد نوع العرض وفترة الإيجار قبل استخدام السعر'
}

const hasPrice = (values) => [values.minPrice, values.maxPrice].some((value) => typeof value === 'string' && value.trim() !== '')

export function editCatalogDraft(state, name, value) {
  if (!filterNames.includes(name) || typeof value !== 'string') throw new TypeError('Unsupported catalog field')
  const draft = { ...state.draft, [name]: value }
  if (name === 'listingType' && value !== state.draft.listingType) draft.rentalPeriod = ''
  // Preserve amounts across unit changes, but require an explicit review before reuse.
  const reviewedPriceBasis = !hasPrice(state.draft) || !hasPrice(draft)
    ? catalogPriceBasis(draft) : state.reviewedPriceBasis
  return { draft, reviewedPriceBasis }
}

export function catalogPriceReviewRequired(state) {
  return hasPrice(state.draft) && catalogPriceBasis(state.draft) != null && state.reviewedPriceBasis !== catalogPriceBasis(state.draft)
}

export function validateCatalogFilters(raw) {
  const values = blankCatalogFilters(), errors = {}
  for (const name of filterNames) {
    if (typeof raw?.[name] !== 'string') errors[name] = 'قيمة المرشح غير صالحة.'
    else values[name] = raw[name].trim()
  }
  const cityKey = values.city.replace(/\s+/gu, ' ').toLowerCase()
  if (!catalogCityChoices.some((city) => city.toLowerCase() === cityKey)) errors.city = 'التصفح الحالي ضمن أسماء مكة المعتمدة فقط.'
  else values.city = 'مكة المكرمة'
  if (values.district.length > 100 || [...values.district].some((character) => character.codePointAt(0) < 32 || character.codePointAt(0) === 127)) errors.district = 'اسم الحي يقبل حتى 100 حرف دون محارف تحكم.'
  if (values.type && !Object.hasOwn(propertyTypes, values.type)) errors.type = 'اختر نوع عقار صحيحًا.'
  if (!['', 'SALE', 'RENT'].includes(values.listingType)) errors.listingType = 'اختر نوع عرض صحيحًا.'
  if (!['', 'MONTHLY', 'YEARLY'].includes(values.rentalPeriod) || (values.listingType !== 'RENT' && values.rentalPeriod)) errors.rentalPeriod = 'فترة الإيجار متاحة لعروض الإيجار فقط.'
  for (const name of ['minPrice', 'maxPrice']) {
    const parsed = parseDecimalInput(raw?.[name], priceRules)
    if (parsed.error) errors[name] = parsed.error === 'precision'
      ? decimalErrorMessage(parsed.error, name === 'minPrice' ? 'السعر الأدنى' : 'السعر الأعلى', priceRules)
      : 'أدخل سعرًا عشريًا من صفر إلى 99999999999999999.99، دون فواصل آلاف أو صيغة علمية أو تقريب.'
    else values[name] = parsed.value ?? ''
  }
  if (hasPrice(values) && !catalogPriceBasis(values)) errors.priceBasis = 'لتطبيق السعر اختر البيع، أو الإيجار مع تحديد شهري أو سنوي؛ لا نقارن هذه القيم ببعضها.'
  if (!errors.minPrice && !errors.maxPrice && values.minPrice && values.maxPrice && compareDecimals(values.minPrice, values.maxPrice) > 0) errors.maxPrice = 'السعر الأعلى يجب ألا يقل عن السعر الأدنى.'
  return { values, errors }
}

export function readCatalogSearch(search) {
  const query = new URLSearchParams(search), draft = blankCatalogFilters(), errors = {}
  if (search.length > 4096) errors.query = 'رابط المرشحات أطول من الحد المدعوم.'
  for (const name of query.keys()) {
    if (!queryNames.has(name)) errors.query = 'الرابط يحتوي معاملات غير مدعومة. راجع المرشحات ثم اضغط تطبيق لإصلاحه.'
    if (query.getAll(name).length !== 1) errors[filterNames.includes(name) ? name : 'query'] = 'الرابط يحتوي مرشحًا مكررًا؛ حدد قيمة واحدة ثم أعد التطبيق.'
  }
  for (const name of filterNames) if (query.has(name)) draft[name] = query.get(name)
  const validated = validateCatalogFilters(draft)
  Object.assign(errors, validated.errors)
  let page = 0
  if (query.has('page')) {
    const value = normalizeDecimalInput(query.get('page'))
    if (!value || !/^\d{1,10}$/.test(value) || Number(value) > 2147483647) errors.page = 'رقم الصفحة يجب أن يكون عددًا صحيحًا من 0 إلى 2147483647.'
    else page = Number(value) // Pagination only; monetary values never pass through Number.
  }
  if (query.has('size') && query.get('size') !== String(catalogPageSize)) errors.query = 'حجم صفحة التصفح ثابت: 12 عقارًا.'
  if (query.has('sort') && query.get('sort') !== catalogSort) errors.query = 'ترتيب هذه الواجهة ثابت: الأحدث أولًا.'
  return { draft, page, errors, params: Object.keys(errors).length ? null : catalogParams(validated.values, page) }
}

function catalogParams(values, page) {
  return { ...Object.fromEntries(filterNames.filter((name) => values[name] !== '').map((name) => [name, values[name]])), page, size: catalogPageSize, sort: catalogSort }
}

export function catalogSearch(raw, page = 0) {
  const { values, errors } = validateCatalogFilters(raw)
  if (Object.keys(errors).length || !Number.isInteger(page) || page < 0 || page > 2147483647) throw new TypeError('Invalid catalog search')
  const query = new URLSearchParams()
  for (const name of filterNames) if (values[name] !== '') query.set(name, values[name])
  if (page !== 0) query.set('page', String(page))
  return query.toString()
}

export function safeCatalogReturnTo(value) {
  if (value === '/properties') return value
  if (typeof value !== 'string' || !value.startsWith('/properties?') || value.includes('#')) return '/properties'
  const parsed = readCatalogSearch(value.slice('/properties?'.length))
  return parsed.params ? `/properties?${catalogSearch(parsed.draft, parsed.page)}` : '/properties'
}

export function catalogPagination(result) {
  if (result.totalPages === 0) return { previous: null, next: null, recovery: result.page > 0 ? 0 : null, label: 'لا توجد صفحات نتائج' }
  if (result.page >= result.totalPages) return { previous: null, next: null, recovery: result.totalPages - 1, label: 'الصفحة المطلوبة لم تعد ضمن النتائج الحالية' }
  return { previous: result.page > 0 ? result.page - 1 : null, next: result.page + 1 < result.totalPages ? result.page + 1 : null, recovery: null, label: `صفحة ${result.page + 1} من ${result.totalPages}` }
}

function validPage(data, params) {
  return data && Array.isArray(data.content) && data.content.length <= catalogPageSize
    && data.page === params.page && data.size === catalogPageSize
    && Number.isSafeInteger(data.totalElements) && data.totalElements >= 0
    && Number.isSafeInteger(data.totalPages) && data.totalPages === Math.ceil(data.totalElements / catalogPageSize)
}

export function createCatalogLoader(client, publish) {
  const gate = createRequestGate()
  return {
    async load(params) {
      const ticket = gate.begin()
      publish({ phase: 'loading', result: null, error: '' })
      try {
        const { data } = await client.get('/api/properties', { params: { ...params }, signal: ticket.controller.signal })
        if (!gate.isCurrent(ticket)) return
        if (!validPage(data, params)) throw new Error('Invalid catalog response')
        publish({ phase: 'ready', result: data, error: '' })
      } catch (error) {
        if (gate.isCurrent(ticket)) publish({ phase: 'error', result: null, error: errorMessage(error) })
      }
    },
    cancel() { gate.cancel() },
  }
}
