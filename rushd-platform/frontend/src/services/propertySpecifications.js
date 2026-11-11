import { compareDecimals, decimalErrorMessage, formatDecimal, normalizeDecimalInput, parseDecimalInput, propertyDecimalRules } from './decimal.js'

const areaChoices = { LAND: 'مساحة قطعة الأرض', UNIT_NET: 'صافي مساحة الوحدة', UNIT_GROSS: 'إجمالي مساحة الوحدة', TOTAL_BUILT: 'إجمالي مساحة البناء' }
export const areaSourceFields = Object.freeze({ LAND: 'landArea', UNIT_NET: 'unitNetArea', UNIT_GROSS: 'unitGrossArea', TOTAL_BUILT: 'totalBuiltArea' })
const field = (name, label, kind, group, extra = {}) => Object.freeze({ name, label, kind, group, ...extra })
export const specificationFields = Object.freeze([
  field('primaryAreaSource', 'مصدر المساحة الرئيسية', 'enum', 'areas', { choices: areaChoices }),
  field('landArea', 'مساحة قطعة الأرض', 'decimal', 'areas', { help: 'مساحة القطعة المرتبطة بالمعروض، لا نصيب أرض مفترض للوحدة.' }),
  field('unitNetArea', 'صافي مساحة الوحدة', 'decimal', 'areas', { help: 'الداخل القابل للاستخدام، دون الجدران والشرفات والمواقف والمشترك.' }),
  field('unitGrossArea', 'إجمالي مساحة الوحدة', 'decimal', 'areas', { help: 'يشمل سماكات جدران الوحدة، دون الشرفات المفتوحة والمواقف ونصيب المشترك.' }),
  field('totalBuiltArea', 'إجمالي مساحة البناء', 'decimal', 'areas', { help: 'مجموع المساحات المبنية المغلقة للأدوار والأقبية والملاحق، دون الأسطح والشرفات المفتوحة والمواقف غير المبنية.' }),
  field('bedrooms', 'غرف النوم', 'integer', 'counts', { min: 0, max: 100 }),
  field('bathrooms', 'الحمامات', 'integer', 'counts', { min: 0, max: 100 }),
  field('livingRooms', 'الصالات', 'integer', 'counts', { min: 0, max: 100 }),
  field('floorNumber', 'رقم الدور', 'integer', 'counts', { min: -10, max: 200, help: '0 أرضي، و−1 القبو الأول؛ ليس عدد أدوار المبنى.' }),
  field('floorCount', 'عدد الأدوار فوق الأرض', 'integer', 'counts', { min: 1, max: 200, help: 'يشمل الدور الأرضي، دون الأقبية.' }),
  field('totalUnits', 'إجمالي الوحدات', 'integer', 'counts', { min: 0, max: 10000 }),
  field('residentialUnits', 'الوحدات السكنية', 'integer', 'counts', { min: 0, max: 10000 }),
  field('commercialUnits', 'الوحدات التجارية', 'integer', 'counts', { min: 0, max: 10000 }),
  field('parkingSpaces', 'المواقف المخصصة', 'integer', 'equipment', { min: 0, max: 1000 }),
  field('estimatedAgeYears', 'العمر التقديري بالسنوات', 'integer', 'condition', { min: 0, max: 1000, help: 'تقدير مؤرخ، لا سنة بناء. 0 أقل من سنة، ولا يحدد حالة «جديد» تلقائيًا.' }),
  field('ageAsOfYear', 'سنة تقدير العمر (ميلادي)', 'integer', 'condition', { min: 1900, max: 'currentYear' }),
  field('condition', 'حالة العقار', 'enum', 'condition', { choices: { UNDER_CONSTRUCTION: 'تحت الإنشاء', NEW: 'جديد', USED: 'مستخدم' } }),
  field('finishing', 'اكتمال التشطيب', 'enum', 'condition', { choices: { UNFINISHED: 'غير مشطب', PARTIAL: 'جزئي', FULL: 'كامل' } }),
  field('furnishing', 'التأثيث', 'enum', 'condition', { choices: { UNFURNISHED: 'غير مفروش', PARTIAL: 'جزئي', FULL: 'كامل' } }),
  field('hasElevator', 'مصعد يخدم العقار', 'boolean', 'equipment'),
  field('hasAirConditioning', 'تكييف مركب', 'boolean', 'equipment'),
  field('hasFittedKitchen', 'مطبخ راكب', 'boolean', 'equipment'),
  field('hasMaidRoom', 'غرفة عاملة', 'boolean', 'equipment'),
  field('hasDriverRoom', 'غرفة سائق', 'boolean', 'equipment'),
  field('hasPool', 'مسبح', 'boolean', 'equipment'),
  field('hasYard', 'حوش', 'boolean', 'equipment'),
  field('hasBalcony', 'شرفة', 'boolean', 'equipment'),
  field('hasPrivateRoof', 'سطح خاص', 'boolean', 'equipment'),
  field('electricityConnected', 'كهرباء موصولة', 'boolean', 'services'),
  field('waterConnected', 'مياه موصولة', 'boolean', 'services'),
  field('sewerConnected', 'صرف موصول', 'boolean', 'services'),
  field('hasIrrigation', 'نظام ري', 'boolean', 'services'),
  field('wellCount', 'عدد الآبار', 'integer', 'services', { min: 0, max: 100 }),
  field('waterSource', 'مصدر المياه القائم', 'enum', 'services', { choices: { NETWORK: 'شبكة', WELL: 'بئر', EXTERNAL_DELIVERY: 'توريد خارجي', MULTIPLE: 'متعدد', OTHER: 'أخرى', NONE: 'لا يوجد' } }),
  field('commercialScope', 'نطاق العقار التجاري', 'enum', 'commercial', { choices: { UNIT: 'وحدة', WHOLE_BUILDING: 'مبنى كامل' } }),
  field('commercialSubtype', 'التصنيف التجاري', 'enum', 'commercial', { choices: { SHOP: 'محل', OFFICE: 'مكتب', WAREHOUSE: 'مستودع', SHOWROOM: 'معرض', OTHER: 'أخرى' } }),
  field('otherTypeDescription', 'توضيح نوع العقار', 'text', 'other', { maxLength: 100 }),
])
const byName = Object.fromEntries(specificationFields.map((item) => [item.name, item]))
export function specificationInputId(name) { return `property-spec-${name}` }
export const specificationGroups = Object.freeze({ commercial: 'نطاق التجاري', areas: 'المساحات ومعانيها', counts: 'الغرف والأدوار والوحدات', condition: 'العمر والحالة والتشطيب', equipment: 'التجهيزات', services: 'الخدمات', other: 'توضيح النوع' })
const areas = ['landArea', 'unitNetArea', 'unitGrossArea', 'totalBuiltArea']
const age = ['estimatedAgeYears', 'ageAsOfYear']
const rooms = ['bedrooms', 'bathrooms', 'livingRooms']
const units = ['totalUnits', 'residentialUnits', 'commercialUnits']
const services = ['electricityConnected', 'waterConnected', 'sewerConnected']
const equipment = ['hasElevator', 'parkingSpaces', 'hasAirConditioning', 'hasFittedKitchen', 'hasMaidRoom', 'hasDriverRoom', 'hasPool', 'hasYard', 'hasBalcony', 'hasPrivateRoof']

export function allowedSpecificationFields(type, scope) {
  let names = ['primaryAreaSource']
  switch (type) {
    case 'LAND': names.push('landArea', 'parkingSpaces', ...services); break
    case 'APARTMENT': case 'VILLA':
      names.push(...age, ...rooms, ...equipment, 'condition', 'finishing', 'furnishing',
        ...(type === 'APARTMENT' ? ['unitNetArea', 'unitGrossArea', 'floorNumber'] : ['landArea', 'totalBuiltArea', 'floorCount'])); break
    case 'BUILDING': names.push(...age, ...units, ...services, 'landArea', 'totalBuiltArea', 'floorCount', 'parkingSpaces', 'condition', 'finishing', 'hasElevator', 'hasAirConditioning', 'hasPool', 'hasYard', 'hasPrivateRoof'); break
    case 'COMMERCIAL':
      names.push(...age, ...services, 'commercialScope', 'commercialSubtype', 'bathrooms', 'parkingSpaces', 'condition', 'finishing', 'hasElevator', 'hasAirConditioning', 'hasFittedKitchen', 'hasPool', 'hasYard', 'hasBalcony', 'hasPrivateRoof');
      if (scope === 'UNIT') names.push('unitNetArea', 'unitGrossArea', 'floorNumber')
      if (scope === 'WHOLE_BUILDING') names.push('landArea', 'totalBuiltArea', 'floorCount', ...units)
      break
    case 'FARM': names.push('landArea', 'totalBuiltArea', 'parkingSpaces', 'hasPool', 'hasYard', 'electricityConnected', 'hasIrrigation', 'wellCount', 'waterSource'); break
    case 'OTHER': names.push(...areas, ...equipment, 'otherTypeDescription'); break
  }
  return new Set(names)
}

export function specificationYear(now = new Date()) {
  return Number(new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Riyadh', year: 'numeric' }).format(now))
}

export function specificationFormState(property) {
  const values = {}, sourceErrors = {}
  for (const item of specificationFields) {
    const raw = property?.specifications?.[item.name]
    values[item.name] = ''
    if (raw == null) continue
    const valid = item.kind === 'integer' ? Number.isSafeInteger(raw)
      : item.kind === 'boolean' ? typeof raw === 'boolean'
        : typeof raw === 'string' && (item.kind !== 'enum' || Object.hasOwn(item.choices, raw))
    if (valid) values[item.name] = String(raw)
    else {
      if (item.kind === 'enum' || item.kind === 'boolean') values[item.name] = '__invalid__'
      sourceErrors[item.name] = `تعذر قراءة ${item.label} من الخادم؛ أعد التحميل أو صحح الحقل صراحة قبل الحفظ.`
    }
  }
  return { values, sourceErrors }
}

function parseField(raw, item, year) {
  if (raw == null || raw === '') return { value: null }
  if (typeof raw !== 'string') return { error: `قيمة ${item.label} غير صالحة.` }
  if (item.kind === 'decimal') {
    const result = parseDecimalInput(raw, { ...propertyDecimalRules.area, required: false })
    return result.error ? { error: decimalErrorMessage(result.error, item.label, propertyDecimalRules.area) } : { value: result.value }
  }
  if (item.kind === 'integer') {
    const normalized = normalizeDecimalInput(raw)
    const max = item.max === 'currentYear' ? year : item.max
    if (!normalized || !/^-?\d+$/.test(normalized)) return { error: `${item.label}: أدخل عددًا صحيحًا دون كسور أو صيغة علمية.` }
    const integer = BigInt(normalized)
    if (integer < BigInt(item.min) || integer > BigInt(max)) return { error: `${item.label}: المجال ${item.min} إلى ${max}.` }
    // Conversion happens only after the bounded integer check; never for decimal areas.
    return { value: Number(integer) }
  }
  if (item.kind === 'boolean') return ['true', 'false'].includes(raw) ? { value: raw === 'true' } : { error: `اختر نعم أو لا أو غير محدد لـ${item.label}.` }
  if (item.kind === 'enum') return Object.hasOwn(item.choices, raw) ? { value: raw } : { error: `اختر قيمة صالحة لـ${item.label}.` }
  const value = raw.trim()
  return value.length <= item.maxLength ? { value: value || null } : { error: `${item.label}: الحد الأقصى ${item.maxLength} حرف.` }
}

export function parseSpecificationIntegerInput(raw, name) {
  const item = byName[name]
  if (!item || item.kind !== 'integer') return { error: 'عداد غير معروف.' }
  return parseField(raw, item, specificationYear())
}

function sameValue(a, b, item) {
  if (a == null || b == null) return a == null && b == null
  if (item.kind === 'decimal') {
    try { return compareDecimals(a, b) === 0 } catch { return false }
  }
  return a === b
}

export function specificationPayload(values, { property, type, area, sourceErrors = {}, year = specificationYear() } = {}) {
  const errors = { ...sourceErrors }, parsed = {}
  const allowed = allowedSpecificationFields(type, values.commercialScope)
  for (const item of specificationFields) {
    const result = parseField(values[item.name], item, year)
    if (result.error) errors[item.name] = result.error
    parsed[item.name] = result.value ?? null
    if (parsed[item.name] != null && !allowed.has(item.name)) errors[item.name] = `${item.label} لا ينطبق على النوع أو النطاق المحدد.`
  }
  if ((parsed.estimatedAgeYears == null) !== (parsed.ageAsOfYear == null)) errors.estimatedAgeYears = 'أدخل العمر وسنة تقديره معًا، أو امسحهما معًا.'
  if (parsed.unitNetArea != null && parsed.unitGrossArea != null && compareDecimals(parsed.unitNetArea, parsed.unitGrossArea) > 0) errors.unitNetArea = 'صافي مساحة الوحدة لا يتجاوز إجماليها.'
  if (parsed.primaryAreaSource != null) {
    const source = parsed[areaSourceFields[parsed.primaryAreaSource]]
    const main = parseDecimalInput(area, propertyDecimalRules.area)
    if (source == null || main.error || compareDecimals(source, main.value) !== 0) errors.primaryAreaSource = 'اختر مساحة مدخلة ومنطبقة تطابق المساحة الرئيسية، أو اختر معلنة غير مصنفة صراحة.'
  }
  if (parsed.totalUnits != null && ((parsed.residentialUnits != null && parsed.residentialUnits > parsed.totalUnits)
    || (parsed.commercialUnits != null && parsed.commercialUnits > parsed.totalUnits)
    || (parsed.residentialUnits != null && parsed.commercialUnits != null && parsed.residentialUnits + parsed.commercialUnits > parsed.totalUnits))) errors.totalUnits = 'الوحدات الجزئية ومجموعها لا تتجاوز الإجمالي المعلوم.'
  const patch = {}
  for (const item of specificationFields) {
    const before = property?.specifications?.[item.name] ?? null
    if (!sameValue(parsed[item.name], before, item)) patch[item.name] = parsed[item.name]
  }
  if (age.some((name) => Object.hasOwn(patch, name))) age.forEach((name) => { patch[name] = parsed[name] })
  return { patch: Object.keys(errors).length ? null : patch, errors }
}

export function primaryAreaValue(values, specifications) {
  const source = areaSourceFields[specifications.primaryAreaSource]
  return source ? specifications[source] : values.area
}

export function specificationRemovalFields(values, type, scope, stored = {}) {
  const allowed = allowedSpecificationFields(type, scope)
  const removed = specificationFields.filter(({ name }) => !allowed.has(name) && ((values[name] != null && values[name] !== '') || stored?.[name] != null)).map(({ name }) => name)
  const source = areaSourceFields[values.primaryAreaSource || stored?.primaryAreaSource]
  if (source && !allowed.has(source)) removed.push('primaryAreaSource')
  return [...new Set(removed)]
}

export function specificationTransition(values, type, scope) {
  const next = { ...values }
  for (const name of specificationRemovalFields(values, type, scope)) next[name] = ''
  if (type === 'COMMERCIAL') next.commercialScope = scope
  return next
}

export function specificationLabel(name) { return byName[name]?.label ?? name }

export function specificationRows(property) {
  const values = property.specifications ?? {}
  const allowed = allowedSpecificationFields(property.type, values.commercialScope)
  const rows = []
  for (const item of specificationFields) {
    if (!allowed.has(item.name) || item.name === 'ageAsOfYear') continue
    const value = values[item.name]
    let text = 'غير محدد', unit = '', numeric = false, year = null
    if (item.name === 'primaryAreaSource') text = value == null ? 'معلنة غير مصنفة' : Object.hasOwn(item.choices, value) ? item.choices[value] : 'غير محدد'
    else if (item.name === 'estimatedAgeYears') {
      if (Number.isSafeInteger(value) && Number.isSafeInteger(values.ageAsOfYear)) {
        text = String(value); numeric = true; unit = 'سنة'; year = String(values.ageAsOfYear)
      }
    } else if (item.kind === 'boolean') text = value === true ? 'نعم' : value === false ? 'لا' : 'غير محدد'
    else if (item.kind === 'enum') text = Object.hasOwn(item.choices, value) ? item.choices[value] : 'غير محدد'
    else if (item.kind === 'decimal' && formatDecimal(value) != null) { text = formatDecimal(value); numeric = true; unit = 'م²' }
    else if (item.kind === 'integer' && Number.isSafeInteger(value)) { text = String(value); numeric = true; if (item.name === 'floorNumber' && value === 0) unit = '(أرضي)' }
    else if (item.kind === 'text' && typeof value === 'string' && value.trim()) text = value
    rows.push({ name: item.name, label: item.label, text, unit, numeric, year, group: item.group })
  }
  return rows
}
