import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { advancedContextImpact, areaBasisChoices, changeAdvancedContext, criterionOutcomeLabels, describeSpecificationRequest, describeSpecificationValue, matchingSpecificationFields } from './advancedPropertyNeeds.js'
import { blankPropertyNeeds, validatePropertyNeeds } from './propertyNeeds.js'
import { allowedSpecificationFields } from './propertySpecifications.js'
import { describeMatchProperty, snapshotNeeds, createNeedsRequestGate } from './propertyMatches.js'

const types = ['LAND', 'APARTMENT', 'VILLA', 'BUILDING', 'COMMERCIAL', 'FARM', 'OTHER']
function context(name) {
  const type = types.find((type) => allowedSpecificationFields(type, 'UNIT').has(name))
  return { type, commercialScope: type === 'COMMERCIAL' ? 'UNIT' : '' }
}
const needs = (extra = {}) => ({ ...blankPropertyNeeds(), type: 'APARTMENT', listingType: 'SALE', maxBudget: '٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١', ...extra })
const withCriterion = (field, criterion) => needs({ ...context(field.name), specificationCriteria: { [field.name]: criterion } })

test('28 searchable fields reuse the 10 counter / 13 boolean / 5 enum contracts without age or free text', () => {
  assert.equal(matchingSpecificationFields.length, 28)
  assert.deepEqual(matchingSpecificationFields.reduce((counts, field) => ({ ...counts, [field.kind]: (counts[field.kind] ?? 0) + 1 }), {}), { integer: 10, enum: 5, boolean: 13 })
  for (const name of ['estimatedAgeYears', 'ageAsOfYear', 'otherTypeDescription', 'primaryAreaSource', 'unitNetArea', 'commercialScope']) assert.equal(matchingSpecificationFields.some((field) => field.name === name), false)
})

for (const field of matchingSpecificationFields) {
  test(`${field.name}: explicit importance, typed values, bounds and optional disabling`, () => {
    const valid = field.kind === 'integer' ? { min: String(field.min), max: String(field.max) }
      : { value: field.kind === 'boolean' ? 'false' : Object.keys(field.choices)[0] }
    for (const requirement of ['REQUIRED', 'PREFERRED']) {
      const values = withCriterion(field, { ...valid, requirement })
      const { payload, errors } = validatePropertyNeeds(values)
      assert.deepEqual(errors, {})
      assert.equal(payload.specificationCriteria[field.name].requirement, requirement)
      assert.doesNotThrow(() => JSON.stringify(payload))
      if (field.kind === 'integer') {
        assert.equal(payload.specificationCriteria[field.name].min, field.min)
        assert.equal(payload.specificationCriteria[field.name].max, field.max)
      } else assert.equal(payload.specificationCriteria[field.name].value, field.kind === 'boolean' ? false : valid.value)
    }
    for (const requirement of [undefined, null, 'UNKNOWN', 'required']) assert.ok(validatePropertyNeeds(withCriterion(field, { ...valid, requirement })).errors[`specificationCriteria.${field.name}`])
    const disabled = validatePropertyNeeds(withCriterion(field, { requirement: '', value: 'invalid', min: '1e99999' }))
    assert.deepEqual(disabled.errors, {})
    assert.equal(Object.hasOwn(disabled.payload, 'specificationCriteria'), false)
    if (field.kind === 'integer') {
      for (const min of ['1.5', '١٫٥', '1e999999', '9'.repeat(129), String(field.min - 1), String(field.max + 1), 'NaN', '+1', 9007199254740992]) assert.ok(validatePropertyNeeds(withCriterion(field, { min, requirement: 'REQUIRED' })).errors[`specificationCriteria.${field.name}`])
      assert.ok(validatePropertyNeeds(withCriterion(field, { min: String(field.max), max: String(field.min), requirement: 'REQUIRED' })).errors[`specificationCriteria.${field.name}`])
      assert.ok(validatePropertyNeeds(withCriterion(field, { min: '', max: '', requirement: 'REQUIRED' })).errors[`specificationCriteria.${field.name}`])
      const digit = field.min === 1900 ? '۲۰۲۶' : String(Math.max(0, field.min)).replace(/0/g, '٠').replace(/1/g, '۱')
      assert.ok(validatePropertyNeeds(withCriterion(field, { min: digit, requirement: 'REQUIRED' })).payload)
    } else {
      for (const value of ['', null, 0, false, '__proto__', 'unknown']) assert.ok(validatePropertyNeeds(withCriterion(field, { value, requirement: 'PREFERRED' })).errors[`specificationCriteria.${field.name}`])
      for (const value of field.kind === 'boolean' ? ['true', 'false'] : Object.keys(field.choices)) assert.ok(validatePropertyNeeds(withCriterion(field, { value, requirement: 'REQUIRED' })).payload)
    }
  })
}

for (const type of types) {
  test(`${type} only exposes and serializes applicable criteria and named measurements`, () => {
    const scope = type === 'COMMERCIAL' ? 'WHOLE_BUILDING' : ''
    const allowed = allowedSpecificationFields(type, scope)
    for (const field of matchingSpecificationFields) {
      const criterion = { requirement: 'REQUIRED', ...(field.kind === 'integer' ? { min: String(field.min) } : { value: field.kind === 'boolean' ? 'false' : Object.keys(field.choices)[0] }) }
      const checked = validatePropertyNeeds(needs({ type, commercialScope: scope, specificationCriteria: { [field.name]: criterion } }))
      assert.equal(!!checked.payload, allowed.has(field.name), `${type}:${field.name}`)
    }
    for (const basis of Object.keys(areaBasisChoices(type, scope))) {
      const checked = validatePropertyNeeds(needs({ type, commercialScope: scope, areaBasis: basis, minArea: '٠٫٠١', maxArea: '۹۹۹۹۹۹۹۹۹۹٫۹۹' }))
      assert.deepEqual(checked.errors, {})
      assert.equal(checked.payload.minArea, '0.01')
      assert.equal(checked.payload.maxArea, '9999999999.99')
    }
  })
}

test('commercial scope is structural, enables correct areas and is not a weighted criterion', () => {
  assert.deepEqual(Object.keys(areaBasisChoices('COMMERCIAL', '')), ['ADVERTISED'])
  assert.deepEqual(Object.keys(areaBasisChoices('COMMERCIAL', 'UNIT')), ['ADVERTISED', 'UNIT_NET', 'UNIT_GROSS'])
  assert.deepEqual(Object.keys(areaBasisChoices('COMMERCIAL', 'WHOLE_BUILDING')), ['ADVERTISED', 'LAND', 'TOTAL_BUILT'])
  assert.ok(validatePropertyNeeds(needs({ commercialScope: 'UNIT' })).errors.commercialScope)
  assert.ok(validatePropertyNeeds(needs({ areaBasis: 'UNIT_NET' })).errors.areaBasis)
  assert.ok(validatePropertyNeeds(needs({ type: 'COMMERCIAL', minArea: '1', areaBasis: 'UNIT_NET' })).errors.areaBasis)
  const checked = validatePropertyNeeds(needs({ type: 'COMMERCIAL', commercialScope: 'UNIT', minArea: '1', areaBasis: 'UNIT_NET' }))
  assert.deepEqual(checked.errors, {})
  assert.equal(checked.payload.commercialScope, 'UNIT')
  assert.equal(Object.hasOwn(checked.payload, 'specificationCriteria'), false)
})

test('changes affecting criteria/named areas are explicit and immutable; no reinterpretation of a removed area', () => {
  const values = needs({ minArea: '١٥٠٫٢٥', maxArea: '۱۸۰٫۵۰', areaBasis: 'UNIT_NET', specificationCriteria: { floorNumber: { min: '-١٠', max: '٠', requirement: 'REQUIRED' }, hasElevator: { value: 'false', requirement: 'PREFERRED' } } })
  const original = structuredClone(values)
  assert.deepEqual(new Set(advancedContextImpact(values, 'LAND', '')), new Set(['رقم الدور', 'مصعد يخدم العقار', 'مدى المساحة ومعناه المسمى']))
  assert.deepEqual(values, original)
  const next = changeAdvancedContext(values, 'LAND', '')
  assert.equal(next.areaBasis, 'ADVERTISED')
  assert.equal(next.minArea, '')
  assert.equal(next.maxArea, '')
  assert.deepEqual(next.specificationCriteria, {})
  assert.deepEqual(values, original)
})

test('paging uses a recursively immutable independent snapshot and changes invalidate older responses', () => {
  const raw = validatePropertyNeeds(needs({ specificationCriteria: { bedrooms: { min: '٣', requirement: 'PREFERRED' } } })).payload
  const saved = snapshotNeeds(raw)
  raw.specificationCriteria.bedrooms.min = 99
  assert.equal(saved.specificationCriteria.bedrooms.min, 3)
  assert.ok(Object.isFrozen(saved.specificationCriteria.bedrooms))
  assert.throws(() => { saved.specificationCriteria.bedrooms.min = 2 }, TypeError)
  const gate = createNeedsRequestGate(), old = gate.begin()
  gate.cancel()
  assert.equal(gate.isCurrent(old), false)
  assert.equal(old.controller.signal.aborted, true)
})

test('new evidence uses precise matched area separately and supports >2 preferences without inventing scores', () => {
  const requested = { areaBasis: 'UNIT_NET', specificationCriteria: { bedrooms: { min: 3, requirement: 'PREFERRED' }, hasElevator: { value: false, requirement: 'PREFERRED' } } }
  const facts = describeMatchProperty({ price: '9007199254740993.01', area: '9999999999.99', matchedArea: '150.25', preferredCount: 4, preferredMatchedCount: 3 }, requested)
  assert.equal(facts.priceAmount, '9٬007٬199٬254٬740٬993٫01')
  assert.equal(facts.areaAmount, '9٬999٬999٬999٫99')
  assert.equal(facts.matchedAreaAmount, '150٫25')
  assert.equal(facts.areaBasisLabel, 'صافي مساحة الوحدة')
  assert.equal(facts.preferredText, 'حقق 3 من 4 تفضيلات مرنة')
  assert.equal(describeSpecificationValue('hasElevator', false), 'لا')
  assert.equal(describeSpecificationValue('hasElevator', null), 'غير محدد')
  assert.equal(describeSpecificationValue('bedrooms', 0), '0')
  assert.equal(describeSpecificationValue('condition', '__proto__'), 'غير محدد')
  assert.equal(describeSpecificationRequest('floorNumber', { min: -10, max: 0 }), 'من -10، إلى 0')
  assert.match(criterionOutcomeLabels.UNKNOWN, /لا يحتسب/)
})

test('precise decimal tokens never wrap between digits in narrow result cards', () => {
  const css = readFileSync(new URL('../pages/PropertyNeeds.css', import.meta.url), 'utf8')
  assert.match(css, /\.needs-match-card\s*\{\s*container-type:\s*inline-size/)
  assert.match(css, /\.needs-match-facts dd bdi,\s*\.needs-summary dd bdi\s*\{\s*white-space:\s*nowrap/)
  assert.match(css, /@container\s*\(max-width:\s*520px\)\s*\{\s*\.needs-match-facts\s*\{\s*grid-template-columns:\s*1fr/)
})
