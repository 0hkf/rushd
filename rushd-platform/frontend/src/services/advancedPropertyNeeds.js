import { allowedSpecificationFields, areaSourceFields, parseSpecificationIntegerInput, specificationFields } from './propertySpecifications.js'

const excluded = new Set(['primaryAreaSource', ...Object.values(areaSourceFields), 'estimatedAgeYears', 'ageAsOfYear', 'commercialScope', 'otherTypeDescription'])
export const matchingSpecificationFields = Object.freeze(specificationFields.filter((field) => !excluded.has(field.name)))
const byName = Object.fromEntries(matchingSpecificationFields.map((field) => [field.name, field]))
export const areaBasisLabels = Object.freeze({ ADVERTISED: 'المساحة المعلنة في الإعلان — غير مفترضة المعنى', ...specificationFields.find((field) => field.name === 'primaryAreaSource').choices })
export const commercialScopeChoices = specificationFields.find((field) => field.name === 'commercialScope').choices
const modes = new Set(['REQUIRED', 'PREFERRED'])

export function areaBasisChoices(type, scope) {
  const allowed = allowedSpecificationFields(type, scope)
  return Object.fromEntries(Object.entries(areaBasisLabels).filter(([value]) => value === 'ADVERTISED' || allowed.has(areaSourceFields[value])))
}

export function validateAdvancedNeeds(values) {
  const errors = {}, payload = {}, criteria = {}
  const scope = values.commercialScope || ''
  const basis = values.areaBasis ?? 'ADVERTISED'
  if (scope && (values.type !== 'COMMERCIAL' || !Object.hasOwn(commercialScopeChoices, scope))) errors.commercialScope = 'اختر نطاقًا صالحًا للتجاري فقط.'
  if (scope) payload.commercialScope = scope
  if (!Object.hasOwn(areaBasisChoices(values.type, scope), basis)) errors.areaBasis = 'معنى المساحة لا ينطبق على النوع أو النطاق المحدد.'
  if (basis !== 'ADVERTISED') {
    if (!String(values.minArea ?? '').trim() && !String(values.maxArea ?? '').trim()) errors.areaBasis = 'حدد مدى مساحة قبل اختيار معنى مسمى.'
    payload.areaBasis = basis
  }
  const allowed = allowedSpecificationFields(values.type, scope)
  for (const [name, criterion] of Object.entries(values.specificationCriteria ?? {})) {
    const field = byName[name], path = `specificationCriteria.${name}`
    if (!field || !criterion || typeof criterion !== 'object' || Array.isArray(criterion)) { errors[path] = 'معيار غير صالح.'; continue }
    if (!criterion.requirement) {
      // An explicitly disabled form criterion is not a search constraint.
      if (criterion.requirement !== '') errors[path] = 'حدد أهمية المعيار صراحة.'
      continue
    }
    if (!modes.has(criterion.requirement) || !allowed.has(name)) { errors[path] = `${field.label}: معيار أو أهمية غير منطبقة على النوع والنطاق.`; continue }
    const request = { requirement: criterion.requirement }
    if (field.kind === 'integer') {
      const min = parseSpecificationIntegerInput(criterion.min, name)
      const max = parseSpecificationIntegerInput(criterion.max, name)
      if (min.error || max.error) errors[path] = min.error || max.error
      else if (min.value == null && max.value == null) errors[path] = `${field.label}: أدخل حدًا واحدًا على الأقل.`
      else if (min.value != null && max.value != null && min.value > max.value) errors[path] = `${field.label}: الأدنى لا يتجاوز الأعلى.`
      else { if (min.value != null) request.min = min.value; if (max.value != null) request.max = max.value }
    } else if (field.kind === 'boolean') {
      if (!['true', 'false'].includes(criterion.value)) errors[path] = `${field.label}: اختر نعم أو لا صراحة.`
      else request.value = criterion.value === 'true'
    } else if (!Object.hasOwn(field.choices, criterion.value)) errors[path] = `${field.label}: اختر قيمة صالحة.`
    else request.value = criterion.value
    criteria[name] = request
  }
  if (Object.keys(criteria).length) payload.specificationCriteria = criteria
  return { errors, payload }
}

export function advancedContextImpact(values, type, scope) {
  const allowed = allowedSpecificationFields(type, scope)
  const removed = matchingSpecificationFields.filter((field) => values.specificationCriteria?.[field.name]?.requirement && !allowed.has(field.name)).map((field) => field.label)
  if (!Object.hasOwn(areaBasisChoices(type, scope), values.areaBasis ?? 'ADVERTISED')) removed.push('مدى المساحة ومعناه المسمى')
  return removed
}

export function changeAdvancedContext(values, type, scope) {
  const allowed = allowedSpecificationFields(type, scope)
  const next = { ...values, type, commercialScope: type === 'COMMERCIAL' ? scope : '',
    specificationCriteria: Object.fromEntries(Object.entries(values.specificationCriteria ?? {}).filter(([name]) => allowed.has(name)).map(([name, criterion]) => [name, { ...criterion }])) }
  if (!Object.hasOwn(areaBasisChoices(type, scope), values.areaBasis ?? 'ADVERTISED')) {
    next.areaBasis = 'ADVERTISED'; next.minArea = ''; next.maxArea = ''
  }
  return next
}

export function describeSpecificationValue(name, value) {
  const field = byName[name]
  if (!field || value == null) return 'غير محدد'
  if (field.kind === 'boolean') return value === true ? 'نعم' : value === false ? 'لا' : 'غير محدد'
  if (field.kind === 'integer') return Number.isSafeInteger(value) ? String(value) : 'غير محدد'
  return Object.hasOwn(field.choices, value) ? field.choices[value] : 'غير محدد'
}

export function describeSpecificationRequest(name, criterion) {
  if (!criterion) return 'غير محدد'
  const field = byName[name]
  if (field?.kind === 'integer') return `${criterion.min == null ? 'دون حد أدنى' : `من ${criterion.min}`}، ${criterion.max == null ? 'دون حد أعلى' : `إلى ${criterion.max}`}`
  return describeSpecificationValue(name, criterion.value)
}

export const criterionOutcomeLabels = Object.freeze({ MATCHED: 'متحقق', MISMATCHED: 'غير متحقق', UNKNOWN: 'بيانات غير محددة — لا يحتسب تطابقًا', NOT_REQUESTED: 'غير مطلوب' })
