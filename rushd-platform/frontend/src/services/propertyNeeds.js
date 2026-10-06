import { propertyTypes } from './property.js'

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
  }
}

export function changePropertyNeeds(values, name, value) {
  const next = { ...values, [name]: value }
  if (name === 'listingType' && value !== 'RENT') next.rentalPeriod = ''
  return next
}

function decimal(value, label, integerDigits, required, errors, field) {
  const normalized = String(value ?? '')
    .trim()
    .replace(/[٠-٩]/g, (digit) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(digit)))
    .replace(/[۰-۹]/g, (digit) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(digit)))
    .replace(/٫/g, '.')
  if (!normalized) {
    if (required) errors[field] = `أدخل ${label}.`
    return null
  }
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) {
    errors[field] = `أدخل ${label} كرقم موجب، بخانتين عشريتين كحد أقصى ودون فواصل آلاف.`
    return null
  }
  const [whole, fraction = ''] = normalized.split('.')
  const canonicalWhole = whole.replace(/^0+(?=\d)/, '')
  if (canonicalWhole.length > integerDigits) {
    errors[field] = `${label} لا يتجاوز ${integerDigits} خانة قبل العلامة العشرية.`
    return null
  }
  const scaled = BigInt(canonicalWhole) * 100n + BigInt(fraction.padEnd(2, '0'))
  if (scaled <= 0n) {
    errors[field] = `${label} يجب أن يكون أكبر من صفر.`
    return null
  }
  return { text: fraction ? `${canonicalWhole}.${fraction}` : canonicalWhole, scaled }
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
  if (minArea && maxArea && minArea.scaled > maxArea.scaled)
    errors.maxArea = 'أكبر مساحة يجب أن تكون مساوية لأقل مساحة أو أكبر منها.'
  const district = String(values.district ?? '').trim()
  if (district.length > 100) errors.district = 'اسم الحي لا يتجاوز 100 حرف.'
  for (const field of ['districtRequirement', 'areaRequirement']) {
    if (!Object.hasOwn(requirementLabels, values[field]))
      errors[field] = 'اختر شرطًا أساسيًا أو تفضيلًا مرنًا.'
  }
  if (values.searchGoal && !Object.hasOwn(searchGoalLabels, values.searchGoal))
    errors.searchGoal = 'اختر هدف البحث من الخيارات المتاحة.'
  if (Object.keys(errors).length) return { errors, payload: null }
  return {
    errors,
    payload: {
      listingType: values.listingType,
      rentalPeriod: values.listingType === 'RENT' ? values.rentalPeriod : null,
      type: values.type,
      maxBudget: budget.text,
      district: district || null,
      districtRequirement: district ? values.districtRequirement : null,
      minArea: minArea?.text ?? null,
      maxArea: maxArea?.text ?? null,
      areaRequirement: minArea || maxArea ? values.areaRequirement : null,
      searchGoal: values.searchGoal || null,
    },
  }
}

export function formatNeedsDecimal(value) {
  // Keep every digit intact even for values above Number.MAX_SAFE_INTEGER.
  const [whole, fraction] = String(value).split('.')
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, '٬')}${fraction ? `٫${fraction}` : ''}`
}
