import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  blankPropertyNeeds,
  changePropertyNeeds,
  formatNeedsDecimal,
  validatePropertyNeeds,
} from './propertyNeeds.js'
import { propertyTypes } from './property.js'

const sale = (extra = {}) => ({
  ...blankPropertyNeeds(),
  listingType: 'SALE',
  type: 'LAND',
  maxBudget: '750000',
  ...extra,
})

test('minimal purchase needs are explicit; city is server-controlled and optional values null', () => {
  const { errors, payload } = validatePropertyNeeds(sale())
  assert.deepEqual(errors, {})
  assert.deepEqual(payload, {
    listingType: 'SALE',
    rentalPeriod: null,
    type: 'LAND',
    maxBudget: '750000',
    district: null,
    districtRequirement: null,
    minArea: null,
    maxArea: null,
    areaRequirement: null,
    searchGoal: null,
  })
})

test('all existing property types can be requested without inventing type-specific facts', () => {
  for (const type of Object.keys(propertyTypes))
    assert.equal(validatePropertyNeeds(sale({ type })).payload.type, type)
  assert.equal(validatePropertyNeeds(sale({ type: 'UNKNOWN' })).payload, null)
})

test('sale/rent and property type have no silently chosen defaults', () => {
  const { errors, payload } = validatePropertyNeeds(blankPropertyNeeds())
  assert.equal(payload, null)
  assert.ok(errors.listingType)
  assert.ok(errors.type)
  assert.ok(errors.maxBudget)
})

test('rent requires an explicit period and preserves monthly/yearly budget units', () => {
  for (const rentalPeriod of ['MONTHLY', 'YEARLY']) {
    const { payload } = validatePropertyNeeds(
      sale({ listingType: 'RENT', rentalPeriod, maxBudget: '4500.50' }),
    )
    assert.equal(payload.rentalPeriod, rentalPeriod)
    assert.equal(payload.maxBudget, '4500.50')
  }
  assert.ok(validatePropertyNeeds(sale({ listingType: 'RENT' })).errors.rentalPeriod)
  assert.ok(validatePropertyNeeds(sale({ rentalPeriod: 'MONTHLY' })).errors.rentalPeriod)
})

test('switching from rent to sale clears the period without changing the entered budget', () => {
  const previous = sale({ listingType: 'RENT', rentalPeriod: 'MONTHLY', maxBudget: '5000' })
  const next = changePropertyNeeds(previous, 'listingType', 'SALE')
  assert.equal(next.rentalPeriod, '')
  assert.equal(next.maxBudget, '5000')
  assert.equal(previous.rentalPeriod, 'MONTHLY')
})

test('budget validation rejects zero, negatives, exponent notation, extra scale and overflow', () => {
  for (const maxBudget of [
    '',
    '0',
    '0.00',
    '-1',
    '1e5',
    '1,000',
    'NaN',
    'Infinity',
    '1.001',
    '100000000000000000',
  ]) {
    assert.ok(validatePropertyNeeds(sale({ maxBudget })).errors.maxBudget, maxBudget)
  }
  assert.equal(validatePropertyNeeds(sale({ maxBudget: '0.01' })).payload.maxBudget, '0.01')
  assert.equal(
    validatePropertyNeeds(sale({ maxBudget: '99999999999999999.99' })).payload.maxBudget,
    '99999999999999999.99',
  )
})

test('Arabic and Persian digits normalize without floating-point loss', () => {
  assert.equal(
    validatePropertyNeeds(sale({ maxBudget: ' ٠٠٤٥٠٠٫٥٠ ' })).payload.maxBudget,
    '4500.50',
  )
  assert.equal(validatePropertyNeeds(sale({ maxBudget: '۴۵۰۰.۵۰' })).payload.maxBudget, '4500.50')
  assert.equal(formatNeedsDecimal('99999999999999999.99'), '99٬999٬999٬999٬999٬999٫99')
})

test('area range is positive, precision-bounded and inclusively ordered without Number rounding', () => {
  assert.equal(
    validatePropertyNeeds(sale({ minArea: '100.01', maxArea: '100.01' })).payload.minArea,
    '100.01',
  )
  assert.equal(validatePropertyNeeds(sale({ minArea: '100' })).payload.areaRequirement, 'REQUIRED')
  assert.equal(
    validatePropertyNeeds(sale({ minArea: '100', areaRequirement: 'PREFERRED' })).payload
      .areaRequirement,
    'PREFERRED',
  )
  assert.ok(validatePropertyNeeds(sale({ minArea: '100.02', maxArea: '100.01' })).errors.maxArea)
  for (const minArea of ['0', '-2', '1.001', '10000000000'])
    assert.ok(validatePropertyNeeds(sale({ minArea })).errors.minArea)
  const { payload } = validatePropertyNeeds(
    sale({ minArea: '9999999999.98', maxArea: '9999999999.99', areaRequirement: 'REQUIRED' }),
  )
  assert.equal(payload.areaRequirement, 'REQUIRED')
  assert.equal(payload.minArea, '9999999999.98')
})

test('district and priorities remain optional but are validated and explicitly scoped', () => {
  const { payload } = validatePropertyNeeds(
    sale({ district: '  النوارية  ', districtRequirement: 'REQUIRED', searchGoal: 'INVESTMENT' }),
  )
  assert.equal(payload.district, 'النوارية')
  assert.equal(payload.districtRequirement, 'REQUIRED')
  assert.equal(payload.searchGoal, 'INVESTMENT')
  assert.ok(validatePropertyNeeds(sale({ district: 'أ'.repeat(101) })).errors.district)
  assert.ok(
    validatePropertyNeeds(sale({ districtRequirement: 'INVALID' })).errors.districtRequirement,
  )
  assert.ok(validatePropertyNeeds(sale({ areaRequirement: 'INVALID' })).errors.areaRequirement)
  assert.ok(validatePropertyNeeds(sale({ searchGoal: 'MIXED' })).errors.searchGoal)
  assert.equal(
    validatePropertyNeeds(
      sale({ district: '', districtRequirement: 'REQUIRED', areaRequirement: 'REQUIRED' }),
    ).payload.districtRequirement,
    null,
  )
})

test('serialization excludes arbitrary extra values and cannot override city or identity', () => {
  const { payload } = validatePropertyNeeds(
    sale({ city: 'الرياض', userId: 1, role: 'ADMIN', matches: ['fake'], unknown: 'ignore' }),
  )
  for (const key of ['city', 'userId', 'role', 'matches', 'unknown'])
    assert.equal(Object.hasOwn(payload, key), false)
  const first = blankPropertyNeeds()
  first.maxBudget = '50'
  assert.equal(blankPropertyNeeds().maxBudget, '')
})
