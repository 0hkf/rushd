import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { changeComparison, comparisonId, comparisonLimit, comparisonRows, createComparisonLoader, readComparisonProperty } from './propertyComparison.js'
import { propertyTypes } from './property.js'

const property = (extra = {}) => ({ id: 1, title: 'عقار اختبار', type: 'LAND', status: 'ACTIVE', listingType: 'SALE', rentalPeriod: null,
  city: 'مكة', district: 'زهرة العمرة', price: '9007199254740993.01', area: '500.25', streetWidth: null, specifications: {}, ...extra })
const entry = (extra = {}) => { const p = property(extra); return { id: String(p.id), phase: 'ready', property: readComparisonProperty(p, String(p.id)) } }
const cells = (entries, name) => comparisonRows(entries).find((row) => row.name === name)?.cells
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

test('selection is ordered, immutable, unique, limited to three and holds IDs only', () => {
  const original = { ids: [], notice: '' }
  let state = changeComparison(original, 'add', 3)
  state = changeComparison(state, 'add', '1')
  state = changeComparison(state, 'add', 2)
  assert.equal(comparisonLimit, 3)
  assert.deepEqual(state.ids, ['3', '1', '2'])
  assert.deepEqual(original.ids, [])
  assert.equal(changeComparison(state, 'add', '1'), state)
  assert.deepEqual(changeComparison(state, 'add', 4).ids, state.ids)
  assert.match(changeComparison(state, 'add', 4).notice, /ثلاثة/)
  assert.deepEqual(Object.keys(state), ['ids', 'notice'])
})
test('removal frees a slot, absent removal is harmless, clear needs no ID', () => {
  let state = { ids: ['3', '1', '2'], notice: '' }
  state = changeComparison(state, 'remove', 1)
  assert.deepEqual(state.ids, ['3', '2'])
  state = changeComparison(state, 'add', 4)
  assert.deepEqual(state.ids, ['3', '2', '4'])
  assert.deepEqual(changeComparison(state, 'remove', 99).ids, state.ids)
  assert.deepEqual(changeComparison(state, 'clear').ids, [])
  assert.throws(() => changeComparison(state, 'toggle', 1))
})
test('IDs support exact Long strings but reject rounded JS IDs and malformed paths', () => {
  for (const raw of ['1', 1, '9223372036854775807']) assert.equal(comparisonId(raw), String(raw))
  for (const raw of ['0', 0, -1, '01', '+1', '1.0', '1e2', '1/../../admin', '9223372036854775808', Number.MAX_SAFE_INTEGER + 1, null, {}, '9'.repeat(100)]) assert.throws(() => comparisonId(raw))
})
for (const type of Object.keys(propertyTypes)) test(`public ${type} supports mixed-type comparison without a new field contract`, () => {
  const p = readComparisonProperty(property({ type }), '1')
  assert.equal(p.type, type)
  assert.equal(p.price, '9007199254740993.01')
})
for (const status of ['DRAFT', 'INACTIVE', 'PRIVATE', undefined]) test(`private/unknown status ${status} never enters comparison, even in an admin response`, () => {
  assert.equal(readComparisonProperty(property({ status, title: 'SECRET' }), '1'), null)
})
test('SOLD stays public with its explicit publication label, not a purchase availability guarantee', () => {
  assert.equal(cells([entry({ status: 'SOLD' })], 'status')[0].text, 'مباع')
})
test('only needed public fields are retained; publisher or account data is not copied', () => {
  const p = readComparisonProperty(property({ publisher: { email: 'private@example.invalid' }, publisherName: 'PRIVATE', publisherId: 98 }), '1')
  assert.equal(Object.hasOwn(p, 'publisher'), false)
  assert.equal(Object.hasOwn(p, 'publisherName'), false)
  assert.equal(Object.hasOwn(p, 'publisherId'), false)
  assert.throws(() => readComparisonProperty(property({ id: 2 }), '1'))
})
test('decimal prices/areas preserve all digits, including max precision, without Number conversion', () => {
  const entries = [entry({ price: '99999999999999999.99', area: '9999999999.99', streetWidth: '99999999.99' })]
  assert.equal(cells(entries, 'price')[0].text, '99٬999٬999٬999٬999٬999٫99')
  assert.equal(cells(entries, 'area')[0].text, '9٬999٬999٬999٫99')
  assert.equal(cells(entries, 'streetWidth')[0].text, '99٬999٬999٫99')
})
test('malformed response numbers, precision loss, exponents and overflow fail closed', () => {
  for (const name of ['price', 'area', 'streetWidth']) {
    for (const value of [12.5, '1e999999', '-1', '0', 'NaN', '1.001', '9'.repeat(129), '100000000000000000.00']) assert.throws(() => readComparisonProperty(property({ [name]: value }), '1'), `${name}: ${value}`)
  }
})
test('optional street width stays unknown, while zero counters and false equipment remain meaningful', () => {
  const entries = [entry({ specifications: { parkingSpaces: 0, electricityConnected: false } }), entry({ id: 2 })]
  assert.equal(cells(entries, 'streetWidth')[0].text, 'غير محدد')
  assert.deepEqual(cells(entries, 'spec-parkingSpaces').map((c) => c.text), ['0', 'غير محدد'])
  assert.deepEqual(cells(entries, 'spec-electricityConnected').map((c) => c.text), ['لا', 'غير محدد'])
})
test('mixed types distinguish inapplicable from unknown and never merge land, net, gross or built area', () => {
  const entries = [entry({ specifications: { primaryAreaSource: 'LAND', landArea: '500.25' } }),
    entry({ id: 2, type: 'APARTMENT', specifications: { primaryAreaSource: 'UNIT_NET', unitNetArea: '145.50', unitGrossArea: '160.00', bedrooms: 3 } }),
    entry({ id: 3, type: 'BUILDING', specifications: { totalBuiltArea: '1600.00' } })]
  assert.deepEqual(cells(entries, 'spec-landArea').map((c) => c.text), ['500٫25', 'لا ينطبق', 'غير محدد'])
  assert.deepEqual(cells(entries, 'spec-unitNetArea').map((c) => c.text), ['لا ينطبق', '145٫50', 'لا ينطبق'])
  assert.deepEqual(cells(entries, 'spec-unitGrossArea').map((c) => c.text), ['لا ينطبق', '160٫00', 'لا ينطبق'])
  assert.deepEqual(cells(entries, 'spec-totalBuiltArea').map((c) => c.text), ['لا ينطبق', 'لا ينطبق', '1٬600٫00'])
  assert.deepEqual(cells(entries, 'spec-primaryAreaSource').map((c) => c.text), ['مساحة قطعة الأرض', 'صافي مساحة الوحدة', 'معلنة غير مصنفة'])
  assert.equal(cells(entries, 'spec-bedrooms')[0].text, 'لا ينطبق')
})
test('unknown commercial scope is not falsely called inapplicable or guessed to be a unit', () => {
  const entries = [entry({ type: 'COMMERCIAL' }), entry({ id: 2, type: 'APARTMENT' })]
  assert.equal(cells(entries, 'spec-unitNetArea')[0].text, 'النطاق التجاري غير محدد')
  assert.equal(cells(entries, 'spec-bedrooms')[0].text, 'لا ينطبق')
})
test('commercial unit and whole building obey the existing field applicability rules', () => {
  const entries = [entry({ type: 'COMMERCIAL', specifications: { commercialScope: 'UNIT', floorNumber: 0 } }),
    entry({ id: 2, type: 'COMMERCIAL', specifications: { commercialScope: 'WHOLE_BUILDING', floorCount: 4 } })]
  assert.deepEqual(cells(entries, 'spec-floorNumber').map((c) => c.text), ['0', 'لا ينطبق'])
  assert.deepEqual(cells(entries, 'spec-floorCount').map((c) => c.text), ['لا ينطبق', '4'])
})
test('dated age remains the recorded estimate without advancing it or inferring NEW', () => {
  const entries = [entry({ type: 'VILLA', specifications: { estimatedAgeYears: 0, ageAsOfYear: 2025 } })]
  assert.equal(cells(entries, 'spec-estimatedAgeYears')[0].text, '0')
  assert.equal(cells(entries, 'spec-estimatedAgeYears')[0].year, '2025')
  assert.equal(cells(entries, 'spec-condition')[0].text, 'غير محدد')
})
test('sale/monthly/yearly prices retain original units and selection order; no cheapest sorting or conversion', () => {
  const entries = [entry({ id: 9, price: '100.00' }), entry({ id: 2, price: '2.00', listingType: 'RENT', rentalPeriod: 'YEARLY' }), entry({ id: 3, price: '1.00', listingType: 'RENT', rentalPeriod: 'MONTHLY' })]
  assert.deepEqual(cells(entries, 'price').map((c) => [c.text, c.unit]), [['100٫00', 'ر.س — بيع'], ['2٫00', 'ر.س / سنة'], ['1٫00', 'ر.س / شهر']])
  assert.deepEqual(entries.map((e) => e.id), ['9', '2', '3'])
  assert.match(cells([entry({ listingType: 'RENT', rentalPeriod: null })], 'price')[0].unit, /غير محددة/)
})
test('unavailable/error columns contain no stale property data and all-unavailable produces no fact rows', () => {
  const hidden = { id: '2', phase: 'unavailable', property: null }, failed = { id: '3', phase: 'error', property: null }
  for (const row of comparisonRows([entry(), hidden, failed])) assert.deepEqual(row.cells.slice(1).map((c) => c.text), ['غير متاح', 'غير متاح'])
  assert.deepEqual(comparisonRows([hidden, failed]), [])
})
test('loader fetches up to three details once per cycle, preserves requested order despite completion order', async () => {
  const pending = new Map(), output = [], calls = []
  const loader = createComparisonLoader({ get(path, options) { const task = deferred(); pending.set(path, task); calls.push({ path, ...options }); return task.promise } }, (state) => output.push(state))
  const task = loader.load(['3', '1', '2'])
  assert.equal(output.at(-1).phase, 'loading')
  assert.deepEqual(output.at(-1).entries, [])
  pending.get('/api/properties/2').resolve({ data: property({ id: 2 }) })
  pending.get('/api/properties/1').resolve({ data: property() })
  pending.get('/api/properties/3').resolve({ data: property({ id: 3 }) })
  await task
  assert.deepEqual(output.at(-1).entries.map((e) => e.id), ['3', '1', '2'])
  assert.equal(calls.length, 3)
  assert.ok(calls.every((call) => call.signal instanceof AbortSignal))
})
test('refresh removes prior data immediately and reads updated price/status; private update becomes a placeholder', async () => {
  let current = property(), output = []
  const loader = createComparisonLoader({ get: async () => ({ data: current }) }, (state) => output.push(state))
  await loader.load(['1'])
  current = property({ price: '9007199254740993.02' })
  await loader.load(['1'])
  assert.equal(output.at(-2).phase, 'loading')
  assert.deepEqual(output.at(-2).entries, [])
  assert.equal(output.at(-1).entries[0].property.price, '9007199254740993.02')
  current = property({ status: 'INACTIVE', title: 'SECRET' })
  await loader.load(['1'])
  assert.deepEqual(output.at(-1).entries[0], { id: '1', phase: 'unavailable', property: null })
})
for (const status of [403, 404, 500, 503, undefined]) test(`HTTP ${status ?? 'network'} failure clears stale data and is isolated to its column`, async () => {
  const output = []
  const loader = createComparisonLoader({ get: async (path) => { if (path.endsWith('/2')) throw { response: { status } }; return { data: property() } } }, (state) => output.push(state))
  await loader.load(['1', '2'])
  assert.equal(output.at(-1).entries[0].phase, 'ready')
  assert.equal(output.at(-1).entries[1].phase, [403, 404].includes(status) ? 'unavailable' : 'error')
  assert.equal(output.at(-1).entries[1].property, null)
})
test('a malformed response becomes an error rather than rounded or mismatched facts', async () => {
  let result
  const loader = createComparisonLoader({ get: async () => ({ data: property({ price: Number('9007199254740993') }) }) }, (state) => { result = state })
  await loader.load(['1'])
  assert.deepEqual(result.entries[0], { id: '1', phase: 'error', property: null })
})
test('late responses after removal/refresh cannot overwrite a newer selection, even if abort is ignored', async () => {
  const old = deferred(), output = [], signals = []
  const loader = createComparisonLoader({ get(path, { signal }) { signals.push(signal); return path.endsWith('/1') ? old.promise : Promise.resolve({ data: property({ id: 2 }) }) } }, (state) => output.push(state))
  const first = loader.load(['1'])
  await loader.load(['2'])
  assert.equal(signals[0].aborted, true)
  old.resolve({ data: property() }); await first
  assert.deepEqual(output.at(-1).ids, ['2'])
  assert.equal(output.at(-1).entries[0].id, '2')
})
test('unmount/account change cancellation rejects late success and late failure', async () => {
  for (const fail of [true, false]) {
    const pending = deferred(), output = []
    const loader = createComparisonLoader({ get: () => pending.promise }, (state) => output.push(state))
    const task = loader.load(['1']); loader.cancel()
    if (fail) pending.reject(new Error('offline')); else pending.resolve({ data: property() })
    await task
    assert.equal(output.length, 1)
    assert.deepEqual(output[0].entries, [])
  }
})
test('empty selection makes no request and loader refuses overflow, duplicates and invalid IDs before HTTP', async () => {
  let calls = 0, last
  const loader = createComparisonLoader({ get: () => { calls++; throw new Error() } }, (state) => { last = state })
  await loader.load([])
  assert.deepEqual(last.entries, [])
  for (const ids of [['1', '2', '3', '4'], ['1', '1'], ['../admin']]) await assert.rejects(loader.load(ids))
  assert.equal(calls, 0)
})
test('integration wiring scopes memory to auth lifecycle, keeps R3 unchanged, and table is keyboard/RTL-friendly', () => {
  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
  assert.match(read('../App.jsx'), /<FavoritesProvider><ComparisonProvider>/)
  assert.match(read('./FavoritesContext.jsx'), /key=.*loading.*user\?\.id/)
  const page = read('../pages/PropertyComparison.jsx')
  assert.match(page, /scope="row"/); assert.match(page, /scope="col"/); assert.match(page, /tabIndex=\{0\}/)
  assert.match(page, /dir="ltr"/); assert.match(page, /visibilitychange/); assert.match(page, /window.addEventListener\('focus'/)
  assert.doesNotMatch(read('./propertyComparison.js'), /localStorage|sessionStorage|\.sort\(/)
  assert.match(read('../pages/PropertyNeeds.jsx'), /<PropertyActions property=\{property\} publicListing/)
  assert.match(read('../components/PropertyActions.jsx'), /<ComparisonButton property=\{property\} publicListing=\{publicListing\}/)
})
