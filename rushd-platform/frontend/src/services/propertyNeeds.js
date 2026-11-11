import { propertyTypes } from './property.js'
import { compareDecimals, decimalErrorMessage, formatDecimal, parseDecimalInput } from './decimal.js'
import { validateAdvancedNeeds } from './advancedPropertyNeeds.js'

export const needsCity = 'مكة المكرمة'
export const requirementLabels = {
  REQUIRED: 'شرط أساسي — لا أتنازل عنه',
  PREFERRED: 'تفضيل مرن',
}
export const searchGoalLabels = {
  RESIDENTIAL: 'السكن',
  INVESTMENT: 'الاستثمار',
  COMMERCIAL: 'استخدام تجاري',
  AGRICULTURAL: 'استخدام زراعي',
  OTHER: 'غرض آخر',
}

export function blankPropertyNeeds() {
  return {
    listingType: '',
    rentalPeriod: '',
    type: '',
    maxBudget: '',
    district: '',
    districtRequirement: 'PREFERRED',
    minArea: '',
    maxArea: '',
    areaRequirement: 'REQUIRED',
    searchGoal: '',
    areaBasis: 'ADVERTISED',
    commercialScope: '',
    specificationCriteria: {},
  }
}

export function changePropertyNeeds(values, name, value) {
  const next = { ...values, [name]: value }
  if (name === 'listingType' && value !== 'RENT') next.rentalPeriod = ''
  return next
}

function decimal(value, label, integerDigits, required, errors, field) {
  const rules = { integerDigits, required, scale: 2, positive: true }
  const result = parseDecimalInput(value, rules)
  if (result.error) errors[field] = decimalErrorMessage(result.error, label, rules)
  return result.value
}

// Decimal strings preserve the full database precision; never round through JavaScript Number.
export function validatePropertyNeeds(values) {
  const errors = {}
  if (!['SALE', 'RENT'].includes(values.listingType)) errors.listingType = 'اختر البيع أو الإيجار.'
  if (!Object.hasOwn(propertyTypes, values.type)) errors.type = 'اختر نوع العقار.'
  if (values.listingType === 'RENT' && !['MONTHLY', 'YEARLY'].includes(values.rentalPeriod))
    errors.rentalPeriod = 'حدد الميزانية الشهرية أو السنوية للإيجار.'
  if (values.listingType === 'SALE' && values.rentalPeriod)
    errors.rentalPeriod = 'فترة الإيجار لا تنطبق على الشراء.'
  const budget = decimal(values.maxBudget, 'الحد الأعلى للميزانية', 17, true, errors, 'maxBudget')
  const minArea = decimal(values.minArea, 'أقل مساحة', 10, false, errors, 'minArea')
  const maxArea = decimal(values.maxArea, 'أكبر مساحة', 10, false, errors, 'maxArea')
  if (minArea && maxArea && compareDecimals(minArea, maxArea) > 0)
    errors.maxArea = 'أكبر مساحة يجب أن تكون مساوية لأقل مساحة أو أكبر منها.'
  const district = String(values.district ?? '').trim()
  if (district.length > 100) errors.district = 'اسم الحي لا يتجاوز 100 حرف.'
  for (const field of ['districtRequirement', 'areaRequirement']) {
    if (!Object.hasOwn(requirementLabels, values[field]))
      errors[field] = 'اختر شرطًا أساسيًا أو تفضيلًا مرنًا.'
  }
  if (values.searchGoal && !Object.hasOwn(searchGoalLabels, values.searchGoal))
    errors.searchGoal = 'اختر هدف البحث من الخيارات المتاحة.'
  const advanced = validateAdvancedNeeds(values)
  Object.assign(errors, advanced.errors)
  if (Object.keys(errors).length) return { errors, payload: null }
  return {
    errors,
    payload: {
      listingType: values.listingType,
      rentalPeriod: values.listingType === 'RENT' ? values.rentalPeriod : null,
      type: values.type,
      maxBudget: budget,
      district: district || null,
      districtRequirement: district ? values.districtRequirement : null,
      minArea,
      maxArea,
      areaRequirement: minArea || maxArea ? values.areaRequirement : null,
      searchGoal: values.searchGoal || null,
      ...advanced.payload,
    },
  }
}

export function formatNeedsDecimal(value) {
  return formatDecimal(value) ?? 'غير محدد'
}
