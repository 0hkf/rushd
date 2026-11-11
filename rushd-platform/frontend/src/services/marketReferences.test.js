import { test } from 'node:test'
import assert from 'node:assert/strict'
import { emptyMarketFacts, marketFactsPayload, marketFactsForm, marketSourcePayload, marketError, marketOutcomeUnknown, marketEnabled } from './marketReferences.js'
const values = () => ({ ...emptyMarketFacts(), transactionDate: '2026-05-01', price: '99999999999999999.99', area: '9007199254740993.01' })
test('market decimals use 17 integer digits for both observation price and area without Number', () => {
  const payload = marketFactsPayload(values())
  assert.equal(payload.price, '99999999999999999.99'); assert.equal(payload.area, '9007199254740993.01')
  assert.equal(marketFactsPayload({ ...values(), price: '0.01', area: '' }).area, null)
  assert.equal(payload.propertyCount, null); assert.equal(payload.propertyType, null); assert.equal(payload.areaMeaning, null)
  assert.equal(marketEnabled, false)
})
test('normalizes Arabic and Persian input exactly including the Arabic decimal separator', () => {
  assert.equal(marketFactsPayload({ ...values(), price: '٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١', area: '۱۲۳٫۴۵', propertyCount: '۱۲' }).price, '9007199254740993.01')
  assert.equal(marketFactsPayload({ ...values(), area: '۱۲۳٫۴۵', propertyCount: '۱۲' }).propertyCount, 12)
})
for (const raw of ['0', '-0.01', '100000000000000000', '0.001', '1.230', '1e2147483647', 'NaN', '1,000', '١٬٠٠٠', '1.2x', 9007199254740992]) {
  test(`rejects invalid price/area without rounding: ${raw}`, () => {
    assert.throws(() => marketFactsPayload({ ...values(), price: raw }))
    assert.throws(() => marketFactsPayload({ ...values(), area: raw }))
  })
}
test('counts remain bounded integers; detailed facts require explicit evidence', () => {
  for (const propertyCount of ['0', '-1', '1000001', '1.1', '1e6']) assert.throws(() => marketFactsPayload({ ...values(), propertyCount }))
  assert.equal(marketFactsPayload({ ...values(), propertyCount: '1000000' }).propertyCount, 1000000)
  assert.throws(() => marketFactsPayload({ ...values(), propertyType: 'LAND' }))
  assert.throws(() => marketFactsPayload({ ...values(), area: '', areaMeaning: 'LAND', evidenceNote: 'evidence' }))
  assert.equal(marketFactsPayload({ ...values(), propertyType: 'LAND', evidenceNote: 'evidence' }).propertyType, 'LAND')
})
test('response facts must remain strings, and optional unknowns round trip as empty inputs', () => {
  const payload = marketFactsPayload(values()), form = marketFactsForm(payload)
  assert.equal(form.price, payload.price); assert.equal(form.district, ''); assert.equal(form.propertyCount, '')
  assert.deepEqual(marketFactsPayload(form), payload)
  assert.throws(() => marketFactsForm({ ...payload, price: 9007199254740992 }))
  assert.equal(marketSourcePayload({ notes: '' }).notes, null)
})
test('session/conflict/unknown errors retain editor guidance without claiming failed saves did not happen', () => {
  assert.match(marketError({ response: { status: 401 } }), /مدخلاتك باقية/)
  assert.match(marketError({ response: { status: 409 } }), /تعارض/)
  assert.match(marketError(new Error()), /لم يؤكد/)
  for (const status of [500, 502, 504]) { assert.equal(marketOutcomeUnknown({ response: { status } }), true); assert.match(marketError({ response: { status } }), /لم يؤكد/) }
  for (const status of [400, 401, 403, 404, 409, 413, 415, 503]) assert.equal(marketOutcomeUnknown({ response: { status } }), false)
})
