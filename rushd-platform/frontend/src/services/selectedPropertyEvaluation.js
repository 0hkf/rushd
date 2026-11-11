import { areaBasisLabels, commercialScopeChoices, describeSpecificationRequest, describeSpecificationValue, matchingSpecificationFields } from './advancedPropertyNeeds.js'
import { formatDecimal } from './decimal.js'
import { propertyTypes } from './property.js'

const classifications = Object.freeze({
  NOT_SUITABLE: 'غير مناسب لمتطلباتك',
  INSUFFICIENT_DATA: 'بيانات غير كافية للحكم',
  BASIC_MATCH: 'مناسب للشروط الأساسية المدخلة',
  VERY_SUITABLE: 'مناسب جدًا لمتطلباتك المدخلة',
  SUITABLE: 'مناسب لمتطلباتك المدخلة',
  PARTIALLY_SUITABLE: 'مناسب جزئيًا لمتطلباتك المدخلة',
})
const outcomes = Object.freeze({ MATCHED: 'متحقق', MISMATCHED: 'غير متحقق', UNKNOWN: 'بيانات غير معروفة', NOT_COMPARABLE: 'غير قابل للمقارنة' })
const labels = Object.freeze({ type: 'نوع العقار', listingType: 'نوع العرض', rentalPeriod: 'فترة الإيجار', city: 'نطاق المدينة', maxBudget: 'الميزانية', commercialScope: 'نطاق التجاري', district: 'الحي', area: 'نطاق المساحة' })
const enumLabels = { ...propertyTypes, SALE: 'بيع', RENT: 'إيجار', MONTHLY: 'شهري', YEARLY: 'سنوي', ...commercialScopeChoices, ...areaBasisLabels }

export function validEvaluationPropertyId(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return null
  const text = String(value ?? '')
  return /^[1-9][0-9]{0,18}$/.test(text) && BigInt(text) <= 9223372036854775807n ? text : null
}

export const evaluationClassification = (value) => classifications[value] ?? 'تعذر تحديد نتيجة التقييم'
export const evaluationOutcome = (value) => outcomes[value] ?? 'حالة غير معروفة'
export function formatEvaluationTime(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  try {
    const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Riyadh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(date)
    const read = (kind) => parts.find((part) => part.type === kind)?.value
    const [day, month, year, hour, minute, second] = ['day', 'month', 'year', 'hour', 'minute', 'second'].map(read)
    if (![day, month, year, hour, minute, second].every(Boolean)) return null
    return { date: `${day}/${month}/${year}`, time: `${hour}:${minute}:${second}` }
  } catch { return null }
}
// Fail closed when an incomplete response would otherwise present a positive verdict without evidence.
export function isUsableEvaluation(result, propertyId, needs) {
  if (!result || typeof result.propertyId !== 'string' || result.propertyId !== propertyId
    || typeof result.rulesVersion !== 'string' || !result.rulesVersion
    || typeof result.evaluatedAt !== 'string' || !result.evaluatedAt
    || !Object.hasOwn(classifications, result.classification) || !Array.isArray(result.evidence)) return false
  if (!needs || typeof needs !== 'object') return false
  const ids = new Set()
  const requested = new Map([['type', 'REQUIRED'], ['listingType', 'REQUIRED'], ['city', 'REQUIRED'], ['maxBudget', 'REQUIRED']])
  if (needs.listingType === 'RENT') requested.set('rentalPeriod', 'REQUIRED')
  if (needs.commercialScope) requested.set('commercialScope', 'REQUIRED')
  if (needs.district) requested.set('district', needs.districtRequirement)
  if (needs.minArea != null || needs.maxArea != null) requested.set('area', needs.areaRequirement)
  for (const [name, criterion] of Object.entries(needs.specificationCriteria ?? {})) requested.set(`specification.${name}`, criterion.requirement)
  const counts = { MATCHED: 0, MISMATCHED: 0, UNKNOWN: 0, NOT_COMPARABLE: 0 }
  let preferred = 0, preferredMatched = 0, requiredMismatch = false
  for (const item of result.evidence) {
    if (!item || typeof item.id !== 'string' || ids.has(item.id)
      || !Object.hasOwn(outcomes, item.outcome) || !['REQUIRED', 'PREFERRED'].includes(item.requirement)
      || requested.get(item.id) !== item.requirement) return false
    ids.add(item.id)
    counts[item.outcome] += 1
    if (item.requirement === 'PREFERRED') { preferred += 1; if (item.outcome === 'MATCHED') preferredMatched += 1 }
    if (item.requirement === 'REQUIRED' && item.outcome === 'MISMATCHED') requiredMismatch = true
  }
  if (requested.size !== ids.size || ![...requested.keys()].every((id) => ids.has(id))) return false
  if (!result.coverage || result.coverage.matched !== counts.MATCHED || result.coverage.mismatched !== counts.MISMATCHED
    || result.coverage.unknown !== counts.UNKNOWN || result.coverage.notComparable !== counts.NOT_COMPARABLE
    || result.preferredCount !== preferred || result.preferredMatchedCount !== preferredMatched) return false
  const expected = requiredMismatch ? 'NOT_SUITABLE'
    : counts.UNKNOWN + counts.NOT_COMPARABLE ? 'INSUFFICIENT_DATA'
      : preferred === 0 ? 'BASIC_MATCH'
        : preferredMatched === preferred ? 'VERY_SUITABLE'
          : preferredMatched * 2 >= preferred ? 'SUITABLE' : 'PARTIALLY_SUITABLE'
  return result.classification === expected
}
export function evaluationEvidenceLabel(id) {
  if (id?.startsWith('specification.')) return matchingSpecificationFields.find((field) => field.name === id.slice(14))?.label ?? 'مواصفة غير معروفة'
  return labels[id] ?? 'معيار غير معروف'
}

function valueText(value, decimal = false) {
  if (value == null) return 'غير محدد'
  if (decimal) return formatDecimal(value) ?? 'قيمة غير قابلة للعرض بدقة'
  if (value === true) return 'نعم'
  if (value === false) return 'لا'
  if (typeof value === 'string') return enumLabels[value] ?? value
  if (Number.isSafeInteger(value)) return String(value)
  return 'غير محدد'
}

export function evaluationValue(item, value, requested = false) {
  const id = item.id
  if (id?.startsWith('specification.')) {
    const name = id.slice(14)
    return requested ? describeSpecificationRequest(name, value) : describeSpecificationValue(name, value)
  }
  if (id === 'area' && value && typeof value === 'object') {
    const min = value.min == null ? 'دون حد أدنى' : `من ${valueText(value.min, true)}`
    const max = value.max == null ? 'دون حد أعلى' : `إلى ${valueText(value.max, true)}`
    return `${min}، ${max} — ${areaBasisLabels[value.basis ?? 'ADVERTISED'] ?? 'معنى مساحة غير معروف'}`
  }
  return valueText(value, id === 'maxBudget' || id === 'area')
}

export function evaluationUnit(unit) {
  if (!unit) return ''
  return ({ SAR: 'ر.س', SAR_MONTHLY: 'ر.س / شهر', SAR_YEARLY: 'ر.س / سنة', SQUARE_METRE: 'م²' })[unit] ?? areaBasisLabels[unit] ?? ''
}
