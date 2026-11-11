import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  compareDecimals,
  formatDecimal,
  normalizeDecimalInput,
  parseDecimalInput,
  propertyDecimalRules as rules,
} from './decimal.js'
import { money } from './property.js'

test('normalization accepts Arabic/Persian digits and decimal point without changing scale', () => {
  assert.equal(normalizeDecimalInput(' ٠٠١٢٣٫٤٠ '), '00123.40')
  assert.equal(parseDecimalInput(' ٠٠١٢٣٫٤٠ ', rules.price).value, '123.40')
  assert.equal(parseDecimalInput('۱۲۳.۴۰', rules.area).value, '123.40')
  assert.equal(parseDecimalInput('-۲۱٫۱۲۳۴۵۶۷', rules.latitude).value, '-21.1234567')
})

test('financial precision boundaries remain exact; excess integer or fraction digits are rejected', () => {
  const limits = { price: '99999999999999999.99', area: '9999999999.99', streetWidth: '99999999.99' }
  const overflows = { price: '100000000000000000', area: '10000000000', streetWidth: '100000000' }
  for (const field of ['price', 'area', 'streetWidth']) {
    assert.equal(parseDecimalInput(limits[field], rules[field]).value, limits[field])
    assert.equal(parseDecimalInput(overflows[field], rules[field]).error, 'precision')
    assert.equal(parseDecimalInput('0.01', rules[field]).value, '0.01')
    assert.equal(parseDecimalInput('1.001', rules[field]).error, 'precision')
    assert.equal(parseDecimalInput('1.2300', rules[field]).error, 'precision')
  }
})

test('positive fields reject zero and negatives rather than rounding or changing the sign', () => {
  for (const field of ['price', 'area', 'streetWidth'])
    for (const value of ['0', '0.00', '-0.00', '-0.01', '-100'])
      assert.equal(parseDecimalInput(value, rules[field]).error, 'positive', `${field}: ${value}`)
})

test('required fields reject blanks; optional decimal fields preserve missing as null', () => {
  for (const value of ['', '   ', null, undefined]) {
    assert.equal(parseDecimalInput(value, rules.price).error, 'required')
    assert.equal(parseDecimalInput(value, rules.area).error, 'required')
    for (const field of ['streetWidth', 'latitude', 'longitude'])
      assert.deepEqual(parseDecimalInput(value, rules[field]), { value: null, error: null })
  }
})

test('coordinates accept signed boundaries and exact zero with at most seven decimals', () => {
  for (const value of ['-90', '90.0000000', '-90.0000000', '0', '0.0000001'])
    assert.equal(parseDecimalInput(value, rules.latitude).value, value)
  for (const value of ['-180', '180.0000000', '-180.0000000', '0', '-0.0000001'])
    assert.equal(parseDecimalInput(value, rules.longitude).value, value)
  assert.equal(parseDecimalInput('-0.0000000', rules.latitude).value, '0.0000000')
  assert.equal(parseDecimalInput('90.0000001', rules.latitude).error, 'range')
  assert.equal(parseDecimalInput('-90.0000001', rules.latitude).error, 'range')
  assert.equal(parseDecimalInput('180.0000001', rules.longitude).error, 'range')
  assert.equal(parseDecimalInput('-180.0000001', rules.longitude).error, 'range')
  for (const field of ['latitude', 'longitude']) {
    assert.equal(parseDecimalInput('0.00000001', rules[field]).error, 'precision')
    assert.equal(parseDecimalInput('1000', rules[field]).error, 'precision')
  }
})

test('malformed decimals, separators and scientific notation never get coerced or clipped', () => {
  for (const value of ['1e2', '1e999999999999999999999', '1e-999999999999999999', 'Infinity', 'NaN', '1,000', '١٬٠٠٠', '1.2.3', '+1', '.5', '1.', '1 2', '--1', '1'.repeat(1000)])
    assert.ok(parseDecimalInput(value, rules.price).error, value)
})

test('JavaScript numeric inputs are refused because their original decimal precision is unknown', () => {
  for (const value of [9007199254740992, 0.1, 0, Number.MAX_VALUE, Infinity, NaN, 1n, {}, []]) {
    assert.equal(parseDecimalInput(value, rules.price).error, 'type')
    assert.equal(parseDecimalInput(value, rules.latitude).error, 'type')
    assert.equal(formatDecimal(value), null)
  }
})

test('decimal comparison uses full precision, mixed scales and signed values', () => {
  assert.equal(compareDecimals('99999999999999999.98', '99999999999999999.99'), -1)
  assert.equal(compareDecimals('1.20', '1.2'), 0)
  assert.equal(compareDecimals('-0.0000001', '0'), -1)
  assert.equal(compareDecimals('-90', '-90.0000001'), 1)
  assert.equal(compareDecimals('-0.0000000', '0'), 0)
  assert.throws(() => compareDecimals(1, '1'), TypeError)
  assert.throws(() => compareDecimals('1e3', '1000'), TypeError)
})

test('display grouping is string-based, preserves fractions and never displays missing as zero', () => {
  assert.equal(formatDecimal('99999999999999999.99'), '99٬999٬999٬999٬999٬999٫99')
  assert.equal(money('4500.50'), '4٬500٫50')
  assert.equal(formatDecimal('-21.1234567'), '-21٫1234567')
  assert.equal(formatDecimal('0.0000000'), '0٫0000000')
  for (const value of [null, undefined, '', 'NaN', '1e3']) {
    assert.equal(formatDecimal(value), null)
    assert.equal(money(value), 'غير محدد')
  }
})
