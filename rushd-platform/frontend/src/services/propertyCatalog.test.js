import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  blankCatalogFilters, catalogCityChoices, catalogPageSize, catalogPagination,
  catalogPriceBasis, catalogPriceLabel, catalogPriceReviewRequired, catalogSearch,
  createCatalogLoader, editCatalogDraft, readCatalogSearch, safeCatalogReturnTo, validateCatalogFilters,
} from './propertyCatalog.js'
import { propertyTypes } from './property.js'

const sale = (extra = {}) => ({ ...blankCatalogFilters(), listingType: 'SALE', ...extra })
const parsed = (values = sale(), page = 0) => readCatalogSearch(catalogSearch(values, page))

test('catalog defaults constrain UI browsing to Makkah, twelve items and newest order without inventing other filters', () => {
  assert.deepEqual(readCatalogSearch('').params, { city: 'مكة المكرمة', page: 0, size: 12, sort: 'createdAt,desc' })
  assert.deepEqual(readCatalogSearch('').errors, {})
  assert.equal(catalogPageSize, 12)
})

for (const type of Object.keys(propertyTypes)) test(`catalog supports existing type ${type} without adding specification filters`, () => {
  assert.equal(parsed(sale({ type })).params.type, type)
  assert.deepEqual(validateCatalogFilters(sale({ type })).errors, {})
})

test('only approved Makkah aliases canonicalize; no city or letter guessing', () => {
  for (const city of [...catalogCityChoices, ' mAkKaH ', 'مكة  المكرمة', '\u2003مكة\tالمكرمة\u00a0']) {
    assert.equal(validateCatalogFilters(sale({ city })).values.city, 'مكة المكرمة')
    assert.deepEqual(validateCatalogFilters(sale({ city })).errors, {})
  }
  for (const city of ['Riyadh', 'Mecca', 'مكه', 'مكة الجديدة', '']) assert.ok(validateCatalogFilters(sale({ city })).errors.city)
})

test('URL roundtrip preserves exact prices, encoded Arabic district, type, period and current page', () => {
  const values = { ...sale(), listingType: 'RENT', rentalPeriod: 'YEARLY', type: 'APARTMENT', district: 'حي الياسمين & +', minPrice: '9007199254740993.01', maxPrice: '99999999999999999.99' }
  const search = catalogSearch(values, 2)
  assert.ok(search.includes('9007199254740993.01'))
  assert.deepEqual(readCatalogSearch(search).draft, values)
  assert.deepEqual(readCatalogSearch(search).params, { ...values, page: 2, size: 12, sort: 'createdAt,desc' })
  assert.equal(catalogSearch(values).includes('page='), false)
  assert.equal(readCatalogSearch(search).params.minPrice, values.minPrice)
})

test('zero and the maximum filter value are valid inclusive bounds; optional empty bounds are omitted', () => {
  for (const value of ['0', '0.00', '0.01', '99999999999999999.99']) {
    assert.deepEqual(validateCatalogFilters(sale({ minPrice: value, maxPrice: value })).errors, {})
    assert.equal(parsed(sale({ minPrice: value, maxPrice: value })).params.minPrice, value)
  }
  assert.equal(Object.hasOwn(parsed().params, 'minPrice'), false)
  assert.equal(Object.hasOwn(parsed().params, 'maxPrice'), false)
})

test('Arabic and Persian digits and the Arabic decimal mark normalize without a Number roundtrip', () => {
  const values = sale({ minPrice: ' ٠٠٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١ ', maxPrice: '۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹.۹۹' })
  const original = structuredClone(values)
  assert.equal(parsed(values).params.minPrice, '9007199254740993.01')
  assert.equal(parsed(values).params.maxPrice, '99999999999999999.99')
  assert.deepEqual(values, original)
})

for (const name of ['minPrice', 'maxPrice']) test(`${name} rejects invalid syntax, negatives, over-scale, overflow and huge exponents without rewriting input`, () => {
  for (const value of ['-0', '-0.01', '-1', '0.001', '1.000', '100000000000000000', '99999999999999999.991', '1e999999999', '1e2', 'NaN', 'Infinity', '+1', '.5', '5.', '1,000', '١٬٠٠٠', '1 000', '9'.repeat(129), 12.5, null, true, {}]) {
    const values = sale({ [name]: value }), original = structuredClone(values)
    assert.ok(validateCatalogFilters(values).errors[name], String(value))
    assert.deepEqual(values, original)
    assert.throws(() => catalogSearch(values))
  }
})

test('price range compares exact decimal strings beyond safe JavaScript precision', () => {
  assert.ok(validateCatalogFilters(sale({ minPrice: '9007199254740993.02', maxPrice: '9007199254740993.01' })).errors.maxPrice)
  assert.deepEqual(validateCatalogFilters(sale({ minPrice: '9007199254740993.01', maxPrice: '9007199254740993.02' })).errors, {})
  assert.deepEqual(validateCatalogFilters(sale({ minPrice: '1.00', maxPrice: '1' })).errors, {})
})

test('ambiguous sale/rent or mixed rental periods never apply a price bound', () => {
  for (const values of [blankCatalogFilters(), { ...blankCatalogFilters(), listingType: 'RENT' }]) {
    assert.deepEqual(validateCatalogFilters(values).errors, {})
    assert.ok(validateCatalogFilters({ ...values, minPrice: '0' }).errors.priceBasis)
    assert.ok(validateCatalogFilters({ ...values, maxPrice: '100' }).errors.priceBasis)
  }
  for (const rentalPeriod of ['MONTHLY', 'YEARLY']) {
    const values = sale({ listingType: 'RENT', rentalPeriod, maxPrice: '1500.50' })
    assert.deepEqual(validateCatalogFilters(values).errors, {})
    assert.equal(parsed(values).params.rentalPeriod, rentalPeriod)
    assert.equal(catalogPriceBasis(values), rentalPeriod)
  }
  assert.ok(validateCatalogFilters(sale({ rentalPeriod: 'MONTHLY' })).errors.rentalPeriod)
  assert.ok(catalogPriceLabel(sale()).includes('البيع'))
})

test('changing price units preserves amounts and requires an explicit review; returning to the original unit is safe', () => {
  const original = { draft: sale({ minPrice: '9007199254740993.01', maxPrice: '99999999999999999.99' }), reviewedPriceBasis: 'SALE' }
  let state = editCatalogDraft(original, 'listingType', 'RENT')
  assert.equal(state.draft.rentalPeriod, '')
  assert.equal(state.draft.minPrice, original.draft.minPrice)
  state = editCatalogDraft(state, 'rentalPeriod', 'MONTHLY')
  assert.equal(catalogPriceReviewRequired(state), true)
  assert.equal(catalogPriceReviewRequired({ ...state, reviewedPriceBasis: 'MONTHLY' }), false)
  state = editCatalogDraft({ ...state, reviewedPriceBasis: 'MONTHLY' }, 'rentalPeriod', 'YEARLY')
  assert.equal(catalogPriceReviewRequired(state), true)
  assert.equal(catalogPriceReviewRequired(editCatalogDraft(original, 'district', 'النوارية')), false)
  assert.deepEqual(original.draft, sale({ minPrice: '9007199254740993.01', maxPrice: '99999999999999999.99' }))
})

test('fresh amounts entered in an explicit unit need no carry-over confirmation; selecting the same listing type preserves period', () => {
  let state = { draft: blankCatalogFilters(), reviewedPriceBasis: null }
  state = editCatalogDraft(state, 'listingType', 'RENT')
  state = editCatalogDraft(state, 'rentalPeriod', 'MONTHLY')
  state = editCatalogDraft(state, 'minPrice', '١٠٠٫٥٠')
  assert.equal(catalogPriceReviewRequired(state), false)
  assert.equal(editCatalogDraft(state, 'listingType', 'RENT').draft.rentalPeriod, 'MONTHLY')
  assert.throws(() => editCatalogDraft(state, 'status', 'DRAFT'))
})

test('district remains exact without removing حي or internal spaces, bounded and unmodified on rejection', () => {
  assert.equal(parsed(sale({ district: ' حي الياسمين ' })).params.district, 'حي الياسمين')
  assert.equal(parsed(sale({ district: 'الياسمين  الجديد' })).params.district, 'الياسمين  الجديد')
  assert.equal(validateCatalogFilters(sale({ district: 'أ'.repeat(100) })).errors.district, undefined)
  for (const district of ['أ'.repeat(101), 'حي\u0000الياسمين', 'حي\tالياسمين']) assert.ok(validateCatalogFilters(sale({ district })).errors.district)
  for (const values of [sale({ type: 'UNKNOWN' }), sale({ listingType: 'SELL' }), sale({ rentalPeriod: 'DAILY' })]) assert.ok(Object.keys(validateCatalogFilters(values).errors).length)
})

test('invalid/duplicate/injected URL parameters block fetching instead of silently broadening search', () => {
  for (const search of ['type=LAND&type=VILLA', 'city=مكة&city=مكة', 'page=0&page=1', 'status=DRAFT', 'specificationCriteria=x', 'listingType=SALE&rentalPeriod=MONTHLY', 'city=Riyadh', 'minPrice=100', 'listingType=SALE&minPrice=1e99999999', 'sort=publisher.password,asc', 'size=100', 'x=' + 'x'.repeat(4096)]) {
    const result = readCatalogSearch(search)
    assert.equal(result.params, null, search.slice(0, 100))
    assert.ok(Object.keys(result.errors).length)
  }
  assert.equal(readCatalogSearch('type=UNKNOWN').draft.type, 'UNKNOWN')
  assert.equal(readCatalogSearch('listingType=SALE&minPrice=1e99999').draft.minPrice, '1e99999')
  assert.ok(readCatalogSearch('size=12&sort=createdAt,desc').params)
})

test('page bounds accept zero, Arabic digits and the Java int maximum, but reject fractions, negative or unsafe values', () => {
  for (const [raw, expected] of [['0', 0], ['١', 1], ['۲', 2], ['2147483647', 2147483647]]) assert.equal(readCatalogSearch(`page=${raw}`).page, expected)
  for (const raw of ['', '-1', '1.1', '1e2', '2147483648', '9007199254740993', '9'.repeat(129)]) assert.equal(readCatalogSearch(`page=${raw}`).params, null, raw)
  assert.throws(() => catalogSearch(sale(), -1))
  assert.throws(() => catalogSearch(sale(), 2147483648))
})

test('every filter change serializes page zero while a page link preserves the complete applied filter set', () => {
  const current = sale({ district: 'حي الياسمين', type: 'LAND', minPrice: '0.01', maxPrice: '9007199254740993.01' })
  const second = readCatalogSearch(catalogSearch(current, 1))
  assert.equal(second.page, 1)
  assert.equal(readCatalogSearch(catalogSearch({ ...second.draft, type: 'VILLA' })).page, 0)
  assert.equal(readCatalogSearch(catalogSearch(second.draft, 2)).params.minPrice, current.minPrice)
})

test('details back-link restores complete catalog filters/page but cannot become an external or unsafe navigation', () => {
  const search = catalogSearch(sale({ type: 'LAND', district: 'حي الياسمين', minPrice: '9007199254740993.01' }), 2)
  assert.equal(safeCatalogReturnTo(`/properties?${search}`), `/properties?${search}`)
  for (const value of [undefined, null, {}, '//example.com', 'https://example.com/properties', 'javascript:alert(1)', '/properties/1', '/properties?status=DRAFT', '/properties?city=Riyadh', '/properties?page=-1', '/properties?city=مكة#private']) assert.equal(safeCatalogReturnTo(value), '/properties')
})

test('pagination covers first/last, zero results and a stale out-of-range page without negative navigation', () => {
  assert.deepEqual(catalogPagination({ page: 0, totalPages: 2 }), { previous: null, next: 1, recovery: null, label: 'صفحة 1 من 2' })
  assert.equal(catalogPagination({ page: 1, totalPages: 2 }).next, null)
  assert.equal(catalogPagination({ page: 100, totalPages: 2 }).recovery, 1)
  assert.equal(catalogPagination({ page: 0, totalPages: 0 }).recovery, null)
  assert.equal(catalogPagination({ page: 2, totalPages: 0 }).recovery, 0)
})

const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const pageData = (page = 0, totalElements = 1) => ({ content: [{ id: 1, price: '9007199254740993.01' }], page, size: 12, totalElements, totalPages: Math.ceil(totalElements / 12) })

test('catalog loader aborts and ignores a late success from an older query, even if the transport ignores abort', async () => {
  const flights = [deferred(), deferred()], calls = [], states = []
  const loader = createCatalogLoader({ get: (path, config) => { calls.push({ path, config }); return flights[calls.length - 1].promise } }, (state) => states.push(state))
  const first = loader.load(parsed().params), second = loader.load(parsed(sale({ type: 'VILLA' })).params)
  assert.equal(calls[0].config.signal.aborted, true)
  flights[1].resolve({ data: pageData() }); await second
  flights[0].resolve({ data: { ...pageData(), content: [{ id: 99 }] } }); await first
  assert.equal(states.length, 3)
  assert.equal(states.at(-1).result.content[0].id, 1)
  assert.equal(calls[1].path, '/api/properties')
  assert.equal(calls[1].config.params.type, 'VILLA')
})

test('cancel/unmount suppresses a late failure and retry keeps the identical precise parameters', async () => {
  const flight = deferred(), states = [], params = parsed(sale({ minPrice: '9007199254740993.01' })).params
  const loader = createCatalogLoader({ get: () => flight.promise }, (state) => states.push(state))
  const first = loader.load(params)
  loader.cancel(); flight.reject(new Error('late failure')); await first
  assert.equal(states.length, 1)
  const calls = [], retryStates = []
  const retryLoader = createCatalogLoader({ get: async (path, config) => {
    calls.push(config.params)
    if (calls.length === 1) throw { response: { data: { message: 'اختبار تعذر مؤقت' } } }
    return { data: pageData() }
  } }, (state) => retryStates.push(state))
  await retryLoader.load(params)
  assert.equal(retryStates.at(-1).phase, 'error')
  assert.equal(retryStates.at(-1).result, null)
  await retryLoader.load(params)
  assert.deepEqual(calls, [params, params])
  assert.equal(retryStates.at(-1).result.content[0].price, '9007199254740993.01')
})

test('malformed pagination responses are not rendered as successful or stale results', async () => {
  for (const data of [null, {}, { ...pageData(), page: 2 }, { ...pageData(), totalPages: 10 }, { ...pageData(), content: {} }, { ...pageData(), totalElements: Number.MAX_SAFE_INTEGER + 1 }]) {
    const states = [], loader = createCatalogLoader({ get: async () => ({ data }) }, (state) => states.push(state))
    await loader.load(parsed().params)
    assert.equal(states.at(-1).phase, 'error')
    assert.equal(states.at(-1).result, null)
  }
})
