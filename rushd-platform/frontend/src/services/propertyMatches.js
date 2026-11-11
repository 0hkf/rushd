import { formatNeedsDecimal } from './propertyNeeds.js'
import { priceSuffix } from './property.js'
import { areaBasisLabels } from './advancedPropertyNeeds.js'
export { createRequestGate as createNeedsRequestGate } from './requestGate.js'

export const matchPageSize = 10

export function snapshotNeeds(payload) {
  const freeze = (value) => {
    if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value) }
    return value
  }
  return freeze(structuredClone(payload))
}

export function matchPagination(result) {
  const page = result.page
  const totalPages = result.totalPages
  if (totalPages === 0) return { previous: null, next: null, label: 'لا توجد صفحات نتائج' }
  if (page >= totalPages)
    return {
      previous: totalPages - 1,
      next: null,
      label: 'الصفحة المطلوبة لم تعد ضمن النتائج الحالية',
    }
  return {
    previous: page > 0 ? page - 1 : null,
    next: page + 1 < totalPages ? page + 1 : null,
    label: `صفحة ${page + 1} من ${totalPages}`,
  }
}

export function describeCriterionMatch(criterion, status, needs) {
  const isDistrict = criterion === 'district'
  const label = isDistrict ? 'الحي' : 'المساحة'
  const mode = needs[isDistrict ? 'districtRequirement' : 'areaRequirement']
  if (status === 'NOT_REQUESTED') return `${label}: ${isDistrict ? 'لم يُحدد' : 'لم يُحدد نطاق'}`
  if (status === 'UNKNOWN') return `${label}: البيانات غير معروفة`
  const requirement = mode === 'REQUIRED' ? 'الشرط الأساسي' : 'التفضيل المرن'
  if (status === 'MATCHED') return `${label}: يحقق ${requirement}`
  if (status === 'MISMATCHED') return `${label}: لا يحقق ${requirement}`
  return `${label}: حالة المطابقة غير معروفة`
}

export function describeMatchProperty(property, needs) {
  const total = property.preferredCount
  const matched = property.preferredMatchedCount
  const maxPreferences = 2 + Object.values(needs.specificationCriteria ?? {}).filter((criterion) => criterion.requirement === 'PREFERRED').length
  const validCounts =
    Number.isInteger(total) &&
    Number.isInteger(matched) &&
    total >= 0 &&
    total <= Math.min(maxPreferences, 30) &&
    matched >= 0 &&
    matched <= total
  return {
    priceAmount: formatNeedsDecimal(property.price),
    priceUnit: priceSuffix(property),
    areaAmount: property.area == null ? null : formatNeedsDecimal(property.area),
    matchedAreaAmount: property.matchedArea == null ? null : formatNeedsDecimal(property.matchedArea),
    areaBasisLabel: areaBasisLabels[needs.areaBasis ?? 'ADVERTISED'] ?? 'معنى مساحة غير معروف',
    districtMatchText: describeCriterionMatch('district', property.districtMatch, needs),
    areaMatchText: describeCriterionMatch('area', property.areaMatch, needs),
    preferredText: !validCounts
      ? 'عدد التفضيلات المتحققة غير متاح'
      : total === 0
        ? 'لا توجد تفضيلات مرنة محددة'
        : `حقق ${matched} من ${total} تفضيلات مرنة`,
  }
}
