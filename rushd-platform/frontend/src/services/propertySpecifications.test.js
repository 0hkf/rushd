import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  allowedSpecificationFields, areaSourceFields, primaryAreaValue, specificationFields,
  specificationFormState, specificationPayload, specificationRemovalFields,
  specificationRows, specificationTransition, specificationYear,
} from './propertySpecifications.js'
import { propertyFormPayload, propertyFormState } from './adminProperty.js'

const empty = () => specificationFormState().values
const age = ['estimatedAgeYears', 'ageAsOfYear']
const rooms = ['bedrooms', 'bathrooms', 'livingRooms']
const units = ['totalUnits', 'residentialUnits', 'commercialUnits']
const services = ['electricityConnected', 'waterConnected', 'sewerConnected']
const equipment = ['hasElevator', 'parkingSpaces', 'hasAirConditioning', 'hasFittedKitchen', 'hasMaidRoom', 'hasDriverRoom', 'hasPool', 'hasYard', 'hasBalcony', 'hasPrivateRoof']
const commercialCommon = [...age, ...services, 'commercialScope', 'commercialSubtype', 'bathrooms', 'parkingSpaces', 'condition', 'finishing', 'hasElevator', 'hasAirConditioning', 'hasFittedKitchen', 'hasPool', 'hasYard', 'hasBalcony', 'hasPrivateRoof']
const matrices = [
  ['LAND', '', ['landArea', 'parkingSpaces', ...services]],
  ['APARTMENT', '', [...age, ...rooms, ...equipment, 'condition', 'finishing', 'furnishing', 'unitNetArea', 'unitGrossArea', 'floorNumber']],
  ['VILLA', '', [...age, ...rooms, ...equipment, 'condition', 'finishing', 'furnishing', 'landArea', 'totalBuiltArea', 'floorCount']],
  ['BUILDING', '', [...age, ...units, ...services, 'landArea', 'totalBuiltArea', 'floorCount', 'parkingSpaces', 'condition', 'finishing', 'hasElevator', 'hasAirConditioning', 'hasPool', 'hasYard', 'hasPrivateRoof']],
  ['COMMERCIAL', '', commercialCommon],
  ['COMMERCIAL', 'UNIT', [...commercialCommon, 'unitNetArea', 'unitGrossArea', 'floorNumber']],
  ['COMMERCIAL', 'WHOLE_BUILDING', [...commercialCommon, 'landArea', 'totalBuiltArea', 'floorCount', ...units]],
  ['FARM', '', ['landArea', 'totalBuiltArea', 'parkingSpaces', 'hasPool', 'hasYard', 'electricityConnected', 'hasIrrigation', 'wellCount', 'waterSource']],
  ['OTHER', '', ['landArea', 'unitNetArea', 'unitGrossArea', 'totalBuiltArea', ...equipment, 'otherTypeDescription']],
]
function contextFor(name) { return matrices.find(([, , names]) => names.includes(name)) ?? matrices[0] }
function checked(values, type = 'APARTMENT', property) { return specificationPayload(values, { type, property, area: '150.25', year: 2026 }) }

test('the contract has 37 distinct fields, all initially unknown without invented defaults', () => {
  assert.equal(specificationFields.length, 37)
  assert.equal(new Set(specificationFields.map((item) => item.name)).size, 37)
  assert.equal(Object.values(empty()).every((value) => value === ''), true)
  assert.deepEqual(checked(empty()).patch, {})
})

for (const [type, scope, names] of matrices) {
  test(`${type}/${scope || 'unknown'} matches the approved applicability matrix and round-trips every applicable field`, () => {
    const allowed = allowedSpecificationFields(type, scope)
    assert.deepEqual([...allowed].sort(), ['primaryAreaSource', ...names].sort())
    const values = { ...empty(), commercialScope: scope }
    for (const item of specificationFields.filter((item) => names.includes(item.name))) {
      values[item.name] = item.kind === 'integer' ? String(item.name === 'ageAsOfYear' ? 2026 : Math.max(item.min, 0))
        : item.kind === 'decimal' ? '۱۵۰٫۲۵' : item.kind === 'boolean' ? 'false'
          : item.kind === 'enum' ? Object.keys(item.choices)[0] : 'نوع موضح'
    }
    if (type === 'COMMERCIAL') values.commercialScope = scope
    // Missing commercial scope is intentionally valid for common fields only.
    const { patch, errors } = checked(values, type)
    assert.deepEqual(errors, {})
    assert.ok(patch)
    const property = { type, specifications: patch }
    const reopened = specificationFormState(property)
    assert.deepEqual(reopened.sourceErrors, {})
    assert.deepEqual(checked(reopened.values, type, property).patch, {})
    assert.doesNotThrow(() => JSON.stringify(patch))
    assert.equal(specificationRows(property).some((row) => row.name === 'yearBuilt'), false)
  })
}

for (const item of specificationFields.filter((item) => item.kind === 'integer')) {
  test(`${item.name}: inclusive bounds, Arabic/Persian digits and invalid integer forms`, () => {
    const [type, scope] = contextFor(item.name)
    const max = item.max === 'currentYear' ? 2026 : item.max
    const base = { ...empty(), commercialScope: scope }
    if (age.includes(item.name)) { base.estimatedAgeYears = '0'; base.ageAsOfYear = '2026' }
    for (const value of [item.min, max]) {
      const result = checked({ ...base, [item.name]: String(value) }, type)
      assert.deepEqual(result.errors, {})
      assert.equal(result.patch[item.name], value)
    }
    for (const raw of [String(item.min - 1), String(max + 1), '1.0', '1٫5', '1e0', '1e99999', 'NaN', '+1', '1,000', '1٬000', '9'.repeat(129), 9007199254740992]) {
      assert.ok(checked({ ...base, [item.name]: raw }, type).errors[item.name], `${item.name}: ${raw}`)
    }
    if (item.min <= 3 && max >= 3) {
      for (const raw of ['٣', '۳']) assert.equal(checked({ ...base, [item.name]: raw }, type).patch[item.name], 3)
    }
  })
}

for (const item of specificationFields.filter((item) => item.kind === 'decimal')) {
  test(`${item.name}: exact optional decimal bounds and no rounding/coercion`, () => {
    for (const raw of ['0.01', '9999999999.99', '٩٩٩٩٩٩٩٩٩٩٫٩٩', '۱۵۰٫۲۵']) {
      const result = checked({ ...empty(), [item.name]: raw }, 'OTHER')
      assert.deepEqual(result.errors, {})
      assert.equal(typeof result.patch[item.name], 'string')
    }
    for (const raw of ['0', '-0.01', '10000000000', '0.001', '150.250', '1e100000', 'Infinity', '1٬000', '1,5', '9'.repeat(129), 9007199254740992]) assert.ok(checked({ ...empty(), [item.name]: raw }, 'OTHER').errors[item.name])
    assert.deepEqual(checked({ ...empty(), [item.name]: '' }, 'OTHER').patch, {})
  })
}

for (const item of specificationFields.filter((item) => ['boolean', 'enum', 'text'].includes(item.kind))) {
  test(`${item.name}: valid wire values and rejection of coercion`, () => {
    const [type, scope] = item.name === 'primaryAreaSource' ? ['OTHER', ''] : contextFor(item.name)
    const base = { ...empty(), commercialScope: scope }
    if (item.kind === 'boolean') {
      for (const raw of ['true', 'false']) assert.equal(checked({ ...base, [item.name]: raw }, type).patch[item.name], raw === 'true')
      for (const raw of [true, false, '0', '1', 'False', 0]) assert.ok(checked({ ...base, [item.name]: raw }, type).errors[item.name])
    } else if (item.kind === 'enum') {
      for (const raw of Object.keys(item.choices)) {
        const values = { ...base, [item.name]: raw }
        if (item.name === 'primaryAreaSource') values[areaSourceFields[raw]] = '150.25'
        assert.deepEqual(checked(values, type).errors, {}, `${item.name}:${raw}`)
      }
      for (const raw of ['unknown', '__proto__', '0', 0, {}]) assert.ok(checked({ ...base, [item.name]: raw }, type).errors[item.name])
    } else {
      assert.equal(checked({ ...base, [item.name]: '  مزرعة خاصة  ' }, type).patch[item.name], 'مزرعة خاصة')
      assert.ok(checked({ ...base, [item.name]: 'ع'.repeat(101) }, type).errors[item.name])
    }
  })
}

test('legacy missing/null specifications remain unknown, not zero, false or a guessed area source', () => {
  for (const specifications of [undefined, null, {}]) {
    const property = { type: 'LAND', specifications }
    assert.deepEqual(specificationFormState(property).values, empty())
    assert.deepEqual(checked(empty(), 'LAND', property).patch, {})
    const rows = specificationRows(property)
    assert.equal(rows.find((row) => row.name === 'primaryAreaSource').text, 'معلنة غير مصنفة')
    assert.equal(rows.filter((row) => row.name !== 'primaryAreaSource').every((row) => row.text === 'غير محدد'), true)
  }
})

test('only changed fields are sent, explicit null clears one field, and all omitted fields survive', () => {
  const property = { type: 'APARTMENT', specifications: { hasElevator: true, parkingSpaces: 0, hasPool: false, unitNetArea: '150.25', unitGrossArea: '180.00' } }
  const state = specificationFormState(property)
  assert.deepEqual(checked(state.values, property.type, property).patch, {})
  const next = { ...state.values, hasElevator: '', hasYard: 'true' }
  const original = structuredClone(next)
  assert.deepEqual(checked(next, property.type, property).patch, { hasElevator: null, hasYard: true })
  assert.deepEqual(next, original)
  assert.equal(state.values.parkingSpaces, '0')
  assert.equal(state.values.hasPool, 'false')
})

test('changing either age value includes both, and clearing requires both without inventing the current year', () => {
  const property = { type: 'APARTMENT', specifications: { estimatedAgeYears: 8, ageAsOfYear: 2025 } }
  const { values } = specificationFormState(property)
  assert.deepEqual(checked({ ...values, estimatedAgeYears: '9' }, 'APARTMENT', property).patch, { estimatedAgeYears: 9, ageAsOfYear: 2025 })
  assert.deepEqual(checked({ ...values, ageAsOfYear: '2026' }, 'APARTMENT', property).patch, { estimatedAgeYears: 8, ageAsOfYear: 2026 })
  assert.ok(checked({ ...values, estimatedAgeYears: '' }, 'APARTMENT', property).errors.estimatedAgeYears)
  assert.deepEqual(checked({ ...values, estimatedAgeYears: '', ageAsOfYear: '' }, 'APARTMENT', property).patch, { estimatedAgeYears: null, ageAsOfYear: null })
  assert.ok(checked({ ...empty(), estimatedAgeYears: '0' }).errors.estimatedAgeYears)
})

test('area source is explicit, exact equality ignores trailing zeroes, and a selected missing source rejects', () => {
  const values = { ...empty(), unitNetArea: '۱۵۰٫۲۵', primaryAreaSource: 'UNIT_NET' }
  assert.equal(primaryAreaValue({ area: '99' }, values), '۱۵۰٫۲۵')
  assert.deepEqual(checked(values).errors, {})
  assert.ok(specificationPayload(values, { type: 'APARTMENT', area: '150.26' }).errors.primaryAreaSource)
  assert.ok(checked({ ...values, unitNetArea: '' }).errors.primaryAreaSource)
  const property = { specifications: { primaryAreaSource: 'UNIT_NET', unitNetArea: '150.2500' } }
  assert.deepEqual(checked(values, 'APARTMENT', property).patch, {})
  assert.deepEqual(checked({ ...values, primaryAreaSource: '' }, 'APARTMENT', property).patch, { primaryAreaSource: null })
})

test('net/gross and unit totals are compared without inventing a total or constraining built area to land', () => {
  assert.ok(checked({ ...empty(), unitNetArea: '100.01', unitGrossArea: '100' }).errors.unitNetArea)
  assert.deepEqual(checked({ ...empty(), landArea: '400', totalBuiltArea: '600' }, 'VILLA').errors, {})
  assert.ok(checked({ ...empty(), totalUnits: '10', residentialUnits: '6', commercialUnits: '5' }, 'BUILDING').errors.totalUnits)
  const result = checked({ ...empty(), residentialUnits: '6' }, 'BUILDING')
  assert.deepEqual(result.errors, {})
  assert.equal(Object.hasOwn(result.patch, 'totalUnits'), false)
})

test('type/scope changes report false, zero, named areas and their main source before explicit removal', () => {
  const stored = { primaryAreaSource: 'UNIT_NET', unitNetArea: '150.25', bedrooms: 0, hasElevator: false }
  const values = specificationFormState({ specifications: stored }).values
  const original = { ...values }
  const removed = specificationRemovalFields(values, 'LAND', '', stored)
  for (const name of ['primaryAreaSource', 'unitNetArea', 'bedrooms', 'hasElevator']) assert.ok(removed.includes(name))
  assert.deepEqual(values, original) // Asking/cancelling does not mutate the draft.
  const next = specificationTransition(values, 'LAND', '')
  const result = checked(next, 'LAND', { specifications: stored })
  assert.deepEqual(result.patch, { primaryAreaSource: null, unitNetArea: null, bedrooms: null, hasElevator: null })
  assert.deepEqual(values, original)
  const unit = { ...empty(), commercialScope: 'UNIT', floorNumber: '0', unitNetArea: '150.25', primaryAreaSource: 'UNIT_NET' }
  assert.deepEqual(new Set(specificationRemovalFields(unit, 'COMMERCIAL', 'WHOLE_BUILDING')), new Set(['unitNetArea', 'floorNumber', 'primaryAreaSource']))
  assert.equal(specificationTransition(unit, 'COMMERCIAL', 'WHOLE_BUILDING').commercialScope, 'WHOLE_BUILDING')
})

test('inapplicable values reject even when false or zero, and unknown commercial scope never invents unit fields', () => {
  assert.ok(checked({ ...empty(), hasElevator: 'false', bedrooms: '0' }, 'LAND').errors.hasElevator)
  assert.ok(checked({ ...empty(), hasElevator: 'false', bedrooms: '0' }, 'LAND').errors.bedrooms)
  assert.ok(checked({ ...empty(), floorNumber: '0' }, 'COMMERCIAL').errors.floorNumber)
  assert.equal(specificationRows({ type: 'COMMERCIAL' }).some((row) => row.name === 'floorNumber'), false)
})

test('unreadable response fields cannot be silently converted to null and resaved', () => {
  const property = { type: 'APARTMENT', specifications: { unitNetArea: 9007199254740992, hasElevator: 'false', bedrooms: '3', condition: 1 } }
  const state = specificationFormState(property)
  assert.deepEqual(Object.keys(state.sourceErrors).sort(), ['bedrooms', 'condition', 'hasElevator', 'unitNetArea'])
  assert.equal(specificationPayload(state.values, { property, type: 'APARTMENT', area: '150.25', sourceErrors: state.sourceErrors }).patch, null)
})

test('display preserves decimal strings, zero, false and dated age without NEW/studio/yearBuilt inference', () => {
  const rows = specificationRows({ type: 'APARTMENT', specifications: { unitNetArea: '9999999999.99', bedrooms: 0, floorNumber: 0, hasElevator: false, hasPool: true, estimatedAgeYears: 0, ageAsOfYear: 2026 } })
  const row = (name) => rows.find((item) => item.name === name)
  assert.equal(row('unitNetArea').text, '9٬999٬999٬999٫99')
  assert.equal(row('unitNetArea').unit, 'م²')
  assert.equal(row('bedrooms').text, '0')
  assert.equal(row('floorNumber').unit, '(أرضي)')
  assert.equal(row('hasElevator').text, 'لا')
  assert.equal(row('hasPool').text, 'نعم')
  assert.equal(row('hasYard').text, 'غير محدد')
  assert.equal(row('estimatedAgeYears').year, '2026')
  assert.equal(row('condition').text, 'غير محدد')
  assert.equal(rows.some((item) => /ستوديو|سنة البناء/.test(item.text)), false)
  const malformed = specificationRows({ type: 'APARTMENT', specifications: { primaryAreaSource: '__proto__', condition: '__proto__', unitNetArea: 9007199254740992 } })
  for (const name of ['primaryAreaSource', 'condition', 'unitNetArea']) assert.equal(malformed.find((item) => item.name === name).text, 'غير محدد')
})

test('Riyadh current year boundaries match backend policy', () => {
  assert.equal(specificationYear(new Date('2026-12-31T20:59:59Z')), 2026)
  assert.equal(specificationYear(new Date('2026-12-31T21:00:00Z')), 2027)
  assert.ok(checked({ ...empty(), estimatedAgeYears: '0', ageAsOfYear: '2027' }).errors.ageAsOfYear)
})

test('combined form preserves T01/T02 precision, rental period and optional location while adding a narrow spec patch', () => {
  const property = { type: 'APARTMENT', status: 'ACTIVE', listingType: 'RENT', rentalPeriod: 'MONTHLY', area: '150.25', price: '9007199254740993.01', latitude: '-90.0000000', longitude: '180.0000000', specifications: { unitNetArea: '150.25', primaryAreaSource: 'UNIT_NET', hasElevator: true } }
  const base = propertyFormState(property)
  const specs = specificationFormState(property)
  const edited = { ...specs.values, unitNetArea: '۱۸۰٫۵۰' }
  const area = primaryAreaValue(base.values, edited)
  const { payload, errors } = propertyFormPayload({ ...base.values, area })
  assert.deepEqual(errors, {})
  const { patch } = specificationPayload(edited, { property, type: property.type, area })
  assert.deepEqual(patch, { unitNetArea: '180.50' })
  assert.equal(payload.area, '180.50')
  assert.equal(payload.price, '9007199254740993.01')
  assert.equal(payload.latitude, '-90.0000000')
  assert.equal(payload.longitude, '180.0000000')
  assert.equal(payload.rentalPeriod, 'MONTHLY')
  assert.equal(Object.hasOwn(payload, 'publisherId'), false)
})
