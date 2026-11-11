import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  blankProperty,
  propertyFormChange,
  propertyFormPayload,
  propertyFormState,
  propertySavedMessage,
  propertyStatusChoices,
  propertyStatusError,
} from './adminProperty.js'

const property = {
  id: 123,
  title: 'عقار للتعديل', type: 'LAND', city: 'مكة المكرمة', district: 'النوارية',
  listingType: 'SALE', rentalPeriod: null, price: '99999999999999999.99', area: '9999999999.99',
  streetWidth: '99999999.99', latitude: '-21.1234567', longitude: '0.0000000', facade: 'NORTH',
  purpose: 'RESIDENTIAL', status: 'DRAFT', description: 'وصف محفوظ', formattedAddress: 'عنوان محفوظ',
  neighborhood: 'حي محفوظ', googlePlaceId: 'place-id',
}

test('editing and resubmitting preserves all five precise fields plus optional property values', () => {
  const state = propertyFormState(property)
  assert.deepEqual(state.sourceErrors, {})
  const { payload, errors } = propertyFormPayload(state.values)
  assert.deepEqual(errors, {})
  for (const field of ['price', 'area', 'streetWidth', 'latitude', 'longitude']) {
    assert.equal(typeof state.values[field], 'string')
    assert.equal(payload[field], property[field])
  }
  for (const field of ['description', 'formattedAddress', 'neighborhood', 'googlePlaceId', 'facade', 'purpose', 'status'])
    assert.equal(payload[field], property[field])
  assert.equal(Object.hasOwn(payload, 'id'), false)
})

test('optional fields remain null when missing, but an explicit coordinate zero remains a string', () => {
  const state = propertyFormState({ ...property, streetWidth: null, latitude: null, longitude: '0' })
  assert.equal(state.values.streetWidth, '')
  assert.equal(state.values.latitude, '')
  const { payload } = propertyFormPayload(state.values)
  assert.equal(payload.streetWidth, null)
  assert.equal(payload.latitude, null)
  assert.equal(payload.longitude, '0')
})

test('create payload normalizes Arabic/Persian decimals and preserves rental period', () => {
  const values = { ...blankProperty, listingType: 'RENT', rentalPeriod: 'MONTHLY', price: '٤٥٠٠٫٥٠', area: '۱۵۰.۲۵', streetWidth: '٢٠', latitude: '-٢١٫٤٢٢٥٠٠٠', longitude: '٣٩٫٨٢٦١٠٠٠' }
  const { payload, errors } = propertyFormPayload(values)
  assert.deepEqual(errors, {})
  assert.equal(payload.price, '4500.50')
  assert.equal(payload.area, '150.25')
  assert.equal(payload.streetWidth, '20')
  assert.equal(payload.latitude, '-21.4225000')
  assert.equal(payload.longitude, '39.8261000')
  assert.equal(payload.rentalPeriod, 'MONTHLY')
})

test('invalid numeric form values produce field errors without mutating the form or truncating it', () => {
  const values = { ...blankProperty, price: '123.456', area: '0', streetWidth: '-1', latitude: '90.0000001', longitude: '0.00000001' }
  const original = { ...values }
  const checked = propertyFormPayload(values)
  assert.equal(checked.payload, null)
  assert.deepEqual(Object.keys(checked.errors).sort(), ['area', 'latitude', 'longitude', 'price', 'streetWidth'])
  assert.deepEqual(values, original)
})

test('non-string response values cannot be silently resaved or converted to null on edit', () => {
  const state = propertyFormState({ ...property, price: 9007199254740992, streetWidth: 20 })
  assert.equal(state.values.price, '')
  assert.equal(state.values.streetWidth, '')
  assert.ok(state.sourceErrors.price)
  assert.ok(state.sourceErrors.streetWidth)
  const checked = propertyFormPayload(state.values, state.sourceErrors)
  assert.equal(checked.payload, null)
  assert.ok(checked.errors.streetWidth)
})

test('payload validation rejects unsafe numeric values and never returns BigInt or extra input fields', () => {
  assert.equal(propertyFormPayload({ ...blankProperty, price: 9007199254740992, area: '1' }).payload, null)
  const { payload } = propertyFormPayload({ ...blankProperty, price: '0.01', area: '0.01', publisherId: 7, injected: 'ignore' })
  assert.doesNotThrow(() => JSON.stringify(payload))
  assert.equal(payload.price, '0.01')
  assert.equal(payload.area, '0.01')
  assert.equal(payload.rentalPeriod, null)
  assert.equal(Object.hasOwn(payload, 'publisherId'), false)
  assert.equal(Object.hasOwn(payload, 'injected'), false)
})

test('SALE permits all four existing statuses and RENT permits three for both pricing periods', () => {
  const saleStatuses = ['ACTIVE', 'DRAFT', 'INACTIVE', 'SOLD']
  const rentalStatuses = ['ACTIVE', 'DRAFT', 'INACTIVE']
  assert.deepEqual(Object.keys(propertyStatusChoices('SALE')), saleStatuses)
  assert.deepEqual(Object.keys(propertyStatusChoices('RENT')), rentalStatuses)
  for (const status of saleStatuses) {
    const { payload, errors } = propertyFormPayload({ ...property, status })
    assert.deepEqual(errors, {})
    assert.equal(payload.status, status)
    assert.equal(payload.rentalPeriod, null)
  }
  for (const rentalPeriod of ['MONTHLY', 'YEARLY']) {
    for (const status of rentalStatuses) {
      const { payload, errors } = propertyFormPayload({ ...property, listingType: 'RENT', rentalPeriod, status })
      assert.deepEqual(errors, {})
      assert.equal(payload.status, status)
      assert.equal(payload.rentalPeriod, rentalPeriod)
    }
  }
})

test('RENT plus SOLD is rejected for monthly and yearly without changing any input', () => {
  for (const rentalPeriod of ['MONTHLY', 'YEARLY']) {
    const values = { ...property, listingType: 'RENT', rentalPeriod, status: 'SOLD' }
    const original = { ...values }
    const checked = propertyFormPayload(values)
    assert.equal(checked.payload, null)
    assert.deepEqual(Object.keys(checked.errors), ['status'])
    assert.match(checked.errors.status, /مباع لعروض البيع فقط/)
    assert.deepEqual(values, original)
  }
})

test('missing and unsupported statuses are rejected instead of becoming ACTIVE', () => {
  for (const status of ['', undefined, null, 'RENTED', 'UNKNOWN', '__proto__']) {
    for (const listingType of ['SALE', 'RENT']) {
      const values = { ...property, status, listingType, rentalPeriod: 'YEARLY' }
      const checked = propertyFormPayload(values)
      assert.equal(checked.payload, null)
      assert.equal(checked.errors.status, 'اختر حالة نشر صالحة للعقار.')
      assert.equal(values.status, status)
    }
  }
})

test('legacy RENT plus SOLD remains visible in form state until an explicit valid correction', () => {
  const legacy = { ...property, listingType: 'RENT', rentalPeriod: 'MONTHLY', status: 'SOLD' }
  const state = propertyFormState(legacy)
  assert.deepEqual(state.sourceErrors, {})
  assert.equal(state.values.status, 'SOLD')
  assert.ok(propertyStatusError(state.values.listingType, state.values.status))
  assert.equal(Object.hasOwn(propertyStatusChoices('RENT'), state.values.status), false)
  assert.equal(propertyFormPayload(state.values).payload, null)
  const corrected = propertyFormChange(state.values, 'status', 'DRAFT')
  const { payload, errors } = propertyFormPayload(corrected)
  assert.deepEqual(errors, {})
  assert.equal(payload.status, 'DRAFT')
  assert.equal(state.values.status, 'SOLD')
  for (const field of Object.keys(blankProperty).filter((key) => key !== 'status'))
    assert.equal(payload[field], legacy[field])
})

test('switching a sold SALE to RENT preserves SOLD and precise values rather than republishing it', () => {
  const values = propertyFormState({ ...property, status: 'SOLD' }).values
  const original = { ...values }
  const rental = propertyFormChange(values, 'listingType', 'RENT')
  assert.equal(rental.status, 'SOLD')
  assert.equal(rental.rentalPeriod, 'YEARLY')
  assert.equal(propertyFormPayload(rental).payload, null)
  assert.deepEqual(values, original)
  for (const field of Object.keys(blankProperty).filter((key) => !['listingType', 'rentalPeriod'].includes(key)))
    assert.equal(rental[field], values[field])
  const corrected = propertyFormChange(rental, 'status', 'INACTIVE')
  assert.equal(propertyFormPayload(corrected).payload.status, 'INACTIVE')
  const saleAgain = propertyFormChange(rental, 'listingType', 'SALE')
  assert.equal(saleAgain.status, 'SOLD')
  assert.equal(saleAgain.rentalPeriod, '')
  assert.equal(propertyFormPayload(saleAgain).payload.status, 'SOLD')
})

test('correcting status preserves optional nulls, explicit coordinate zero and decimal source errors', () => {
  const state = propertyFormState({ ...property, listingType: 'RENT', rentalPeriod: 'YEARLY', status: 'SOLD',
    streetWidth: null, latitude: null, longitude: '0', formattedAddress: null, neighborhood: null,
    googlePlaceId: null, description: null })
  const values = propertyFormChange(state.values, 'status', 'ACTIVE')
  const { payload, errors } = propertyFormPayload(values)
  assert.deepEqual(errors, {})
  for (const field of ['streetWidth', 'latitude', 'formattedAddress', 'neighborhood', 'googlePlaceId', 'description'])
    assert.equal(payload[field], null)
  assert.equal(payload.longitude, '0')
  assert.equal(payload.price, property.price)
  assert.equal(payload.area, property.area)
  const sourceErrors = { price: 'تعذر قراءة السعر بدقة' }
  const checked = propertyFormPayload(values, sourceErrors)
  assert.equal(checked.payload, null)
  assert.deepEqual(checked.errors, sourceErrors)
  assert.deepEqual(sourceErrors, { price: 'تعذر قراءة السعر بدقة' })
})

test('status and decimal validation errors coexist without truncating or resetting inputs', () => {
  const values = { ...property, listingType: 'RENT', rentalPeriod: 'MONTHLY', status: 'SOLD', price: '123.456' }
  const original = { ...values }
  const { payload, errors } = propertyFormPayload(values)
  assert.equal(payload, null)
  assert.deepEqual(Object.keys(errors).sort(), ['price', 'status'])
  assert.deepEqual(values, original)
})

test('save feedback distinguishes SOLD public details from unpublished statuses', () => {
  assert.equal(propertySavedMessage('ACTIVE'), 'تم حفظ العقار ونشر مواصفاته.')
  assert.equal(propertySavedMessage('SOLD'), 'تم حفظ العقار بحالة مباع. يبقى رابط المواصفات متاحًا للزوار، ولا يظهر ضمن العروض المتاحة.')
  assert.equal(propertySavedMessage('DRAFT'), 'تم حفظ العقار كمسودة دون نشره للزوار.')
  assert.equal(propertySavedMessage('INACTIVE'), 'تم حفظ العقار بحالة غير منشور دون إتاحته للزوار.')
  assert.equal(propertySavedMessage('UNKNOWN'), 'تم حفظ العقار.')
})
