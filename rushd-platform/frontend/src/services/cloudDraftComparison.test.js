import { test } from 'node:test'
import assert from 'node:assert/strict'
import { compareDraftSnapshots, propertyReviewSnapshot, reviewValue } from './cloudDraftComparison.js'

const snapshot = (values = {}, specifications = {}, extra = {}) => ({ values, specifications, ...extra })

test('review compares raw decimal strings without losing Arabic digits or large integer precision', () => {
  const rows = compareDraftSnapshots(snapshot({ price: '٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١' }),
    snapshot({ price: '9007199254740993.02' }))
  assert.equal(rows.length, 1)
  assert.deepEqual([rows[0].label, rows[0].local, rows[0].remote],
    ['السعر', '٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١', '9007199254740993.02'])
})

test('missing and empty values are distinct and zero and negative coordinates remain exact', () => {
  const rows = compareDraftSnapshots(snapshot({ title: '', latitude: '-21.1234567', longitude: '0' }),
    snapshot({ latitude: '-21.1234568', longitude: '0' }))
  assert.equal(rows.length, 2)
  const title = rows.find((row) => row.label === 'عنوان العقار')
  assert.equal(reviewValue(title.local), 'فارغ')
  assert.equal(reviewValue(title.remote), 'غير موجود في النسخة')
  assert.equal(rows.find((row) => row.label === 'خط العرض').local, '-21.1234567')
})

test('cached other-type data, scopes, and source errors are visible in draft comparisons', () => {
  const rows = compareDraftSnapshots(snapshot({}, {}, { typeDrafts: { 'LAND:': { values: { landArea: '٢٠٠' }, area: '٢٠٠' } }, scopes: { LAND: '' }, sourceErrors: { price: 'تنبيه' } }),
    snapshot({}, {}, { typeDrafts: { 'LAND:': { values: { landArea: '٣٠٠' }, area: '٣٠٠' } }, scopes: { LAND: 'UNIT' }, sourceErrors: {} }))
  assert.equal(rows.length, 4)
  assert.ok(rows.some((row) => row.label.includes('مساحة قطعة الأرض') && row.local === '٢٠٠'))
  assert.ok(rows.some((row) => row.label.includes('نطاق النوع')))
  assert.ok(rows.some((row) => row.label === 'تنبيه قراءة: السعر'))
})

test('property comparison excludes cached type data and reports unsafe source numeric values', () => {
  const remote = propertyReviewSnapshot({ title: 'أ', price: 9007199254740992, area: '100.00', specifications: { landArea: '100.00' } })
  assert.equal(remote.values.price, '')
  assert.match(remote.sourceErrors.price, /بدقة/)
  const rows = compareDraftSnapshots(snapshot({ ...remote.values }, { ...remote.specifications }, { typeDrafts: { LAND: { area: 'old' } }, sourceErrors: {} }), remote, { propertyOnly: true })
  assert.equal(rows.length, 1)
  assert.match(rows[0].label, /تنبيه قراءة/)
})

test('comparison treats html as ordinary text and preserves explicit invalid types without coercion', () => {
  const rows = compareDraftSnapshots(snapshot({ title: '<img src=x onerror=alert(1)>', price: 123 }), snapshot({ title: 'آمن', price: '' }))
  assert.equal(rows.find((row) => row.label === 'عنوان العقار').local, '<img src=x onerror=alert(1)>')
  assert.equal(reviewValue(rows.find((row) => row.label === 'السعر').local), 'تعذرت قراءته بدقة')
})
