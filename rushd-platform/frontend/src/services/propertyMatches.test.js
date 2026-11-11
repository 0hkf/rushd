import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  createNeedsRequestGate,
  describeCriterionMatch,
  describeMatchProperty,
  matchPagination,
  snapshotNeeds,
} from './propertyMatches.js'

test('pagination is bounded and does not invent a page for an empty result', () => {
  assert.deepEqual(matchPagination({ page: 0, totalPages: 0 }), {
    previous: null,
    next: null,
    label: 'لا توجد صفحات نتائج',
  })
  assert.deepEqual(matchPagination({ page: 0, totalPages: 3 }), {
    previous: null,
    next: 1,
    label: 'صفحة 1 من 3',
  })
  assert.deepEqual(matchPagination({ page: 1, totalPages: 3 }), {
    previous: 0,
    next: 2,
    label: 'صفحة 2 من 3',
  })
  assert.equal(matchPagination({ page: 2, totalPages: 3 }).next, null)
})

test('the paging payload is an immutable snapshot independent from edited form values', () => {
  const formPayload = { type: 'LAND', maxBudget: '99999999999999999.99', district: 'حي النوارية' }
  const submitted = snapshotNeeds(formPayload)
  formPayload.maxBudget = '10'
  assert.equal(submitted.maxBudget, '99999999999999999.99')
  assert.equal(Object.isFrozen(submitted), true)
  assert.throws(() => {
    submitted.type = 'VILLA'
  }, TypeError)
})

test('shrinking inventory returns a valid previous page rather than claiming there are no matches', () => {
  assert.deepEqual(matchPagination({ page: 2, totalPages: 1, totalElements: 5 }), {
    previous: 0,
    next: null,
    label: 'الصفحة المطلوبة لم تعد ضمن النتائج الحالية',
  })
  assert.equal(matchPagination({ page: 99, totalPages: 3 }).previous, 2)
  assert.deepEqual(matchPagination({ page: 2, totalPages: 0, totalElements: 0 }), {
    previous: null,
    next: null,
    label: 'لا توجد صفحات نتائج',
  })
})

test('criterion labels separate hard conditions, preferences, unknown and not requested', () => {
  const needs = { districtRequirement: 'REQUIRED', areaRequirement: 'PREFERRED' }
  assert.equal(describeCriterionMatch('district', 'MATCHED', needs), 'الحي: يحقق الشرط الأساسي')
  assert.equal(
    describeCriterionMatch('area', 'MISMATCHED', needs),
    'المساحة: لا يحقق التفضيل المرن',
  )
  assert.equal(describeCriterionMatch('area', 'UNKNOWN', needs), 'المساحة: البيانات غير معروفة')
  assert.equal(describeCriterionMatch('district', 'NOT_REQUESTED', needs), 'الحي: لم يُحدد')
  assert.equal(
    describeCriterionMatch('area', 'NEW_STATUS', needs),
    'المساحة: حالة المطابقة غير معروفة',
  )
})

test('listing facts retain decimal precision and unknown area rather than inventing values', () => {
  const facts = describeMatchProperty(
    {
      price: '99999999999999999.99',
      area: null,
      listingType: 'SALE',
      rentalPeriod: null,
      preferredMatchedCount: 1,
      preferredCount: 2,
      districtMatch: 'MATCHED',
      areaMatch: 'UNKNOWN',
    },
    { districtRequirement: 'PREFERRED', areaRequirement: 'PREFERRED' },
  )
  assert.equal(facts.priceAmount, '99٬999٬999٬999٬999٬999٫99')
  assert.equal(facts.priceUnit, 'ر.س')
  assert.equal(facts.areaAmount, null)
  assert.equal(facts.preferredText, 'حقق 1 من 2 تفضيلات مرنة')
})

test('rent prices use advertised period; a whole area range counts as one preference', () => {
  const property = {
    price: '4500.50',
    area: '100.01',
    listingType: 'RENT',
    rentalPeriod: 'MONTHLY',
    preferredMatchedCount: 1,
    preferredCount: 1,
    districtMatch: 'NOT_REQUESTED',
    areaMatch: 'MATCHED',
  }
  const facts = describeMatchProperty(property, { areaRequirement: 'PREFERRED' })
  assert.equal(facts.priceAmount, '4٬500٫50')
  assert.equal(facts.priceUnit, 'ر.س / شهر')
  assert.equal(facts.areaAmount, '100٫01')
  assert.equal(facts.preferredText, 'حقق 1 من 1 تفضيلات مرنة')
  assert.equal(
    describeMatchProperty({ ...property, rentalPeriod: 'YEARLY' }, {}).priceUnit,
    'ر.س / سنة',
  )
  assert.equal(
    describeMatchProperty({ ...property, preferredCount: 0, preferredMatchedCount: 0 }, {})
      .preferredText,
    'لا توجد تفضيلات مرنة محددة',
  )
  assert.equal(
    describeMatchProperty({ ...property, preferredCount: 3 }, {}).preferredText,
    'عدد التفضيلات المتحققة غير متاح',
  )
})

test('a newer request aborts and invalidates older out-of-order responses', () => {
  const gate = createNeedsRequestGate()
  const first = gate.begin()
  const second = gate.begin()
  assert.equal(first.controller.signal.aborted, true)
  assert.equal(gate.isCurrent(first), false)
  assert.equal(gate.isCurrent(second), true)
})

test('editing, reset or unmount cancels the current request and rejects late completion', () => {
  const gate = createNeedsRequestGate()
  const ticket = gate.begin()
  gate.cancel()
  assert.equal(ticket.controller.signal.aborted, true)
  assert.equal(gate.isCurrent(ticket), false)
  const afterReset = gate.begin()
  assert.equal(gate.isCurrent(afterReset), true)
  assert.equal(gate.isCurrent(ticket), false)
})
