import { test } from 'node:test'
import assert from 'node:assert/strict'
import { interfaceTitle, decimalSizeClass, propertyStatusNotice, revealAnchorTarget, focusPanel } from './interface.js'
import { errorMessage } from './property.js'

test('page titles describe the route without exposing query strings or private property text', () => {
  assert.equal(interfaceTitle('/properties'), 'عقارات مكة | روافد العقارية')
  assert.equal(interfaceTitle('/properties/123'), 'تفاصيل العقار | روافد العقارية')
  for (const path of ['/login', '/register', '/dashboard', '/property-needs', '/favorites', '/compare', '/admin/market-references']) assert.match(interfaceTitle(path), /روافد العقارية$/)
  assert.doesNotMatch(interfaceTitle('/missing?secret=private'), /secret|private/)
})
test('number size classes never convert or shorten exact decimal values', () => {
  for (const amount of ['0.00', '325.75', '85000000.50']) assert.equal(decimalSizeClass(amount), undefined)
  assert.equal(decimalSizeClass('999999999999.99'), 'medium-decimal')
  const exact = '99999999999999999.99'
  assert.equal(decimalSizeClass(exact), 'long-decimal'); assert.equal(exact, '99999999999999999.99')
  for (const value of [null, undefined, 9007199254740992]) assert.equal(decimalSizeClass(value), undefined)
})
test('details distinguish sold/private/unknown availability without claiming an inactive offer is public', () => {
  assert.equal(propertyStatusNotice('ACTIVE'), '')
  assert.match(propertyStatusNotice('SOLD'), /مباع/); assert.match(propertyStatusNotice('SOLD'), /لا يتاح/)
  for (const status of ['DRAFT', 'INACTIVE']) assert.match(propertyStatusNotice(status), /لا يظهر للزوار/)
  assert.match(propertyStatusNotice(null), /غير محددة/)
})
test('error anchors open every enclosing section before scrolling and focusing the field', () => {
  const outer = { tagName: 'DETAILS', open: false, parentElement: null }
  const fieldset = { tagName: 'FIELDSET', parentElement: outer }
  const inner = { tagName: 'DETAILS', open: false, parentElement: fieldset }
  const calls = []
  const field = { parentElement: inner, scrollIntoView: (options) => calls.push(['scroll', options]), focus: (options) => calls.push(['focus', options]) }
  assert.equal(revealAnchorTarget('field', { getElementById: () => field }), true)
  assert.equal(inner.open, true); assert.equal(outer.open, true)
  assert.deepEqual(calls, [['scroll', { block: 'start', behavior: 'auto' }], ['focus', { preventScroll: true }]])
  assert.equal(revealAnchorTarget('missing', { getElementById: () => null }), false)
  assert.equal(focusPanel(null), false)
})
test('common transport/security messages have Arabic UI copy without changing specific validation errors', () => {
  for (const message of ['An unexpected error occurred', 'Invalid email or password', 'Authentication is required', 'Insufficient permission', 'Invalid CSRF token']) assert.match(errorMessage({ response: { data: { message } } }), /[\u0600-\u06ff]/)
  assert.equal(errorMessage({ response: { data: { message: 'دليل الإثبات مطلوب' } } }), 'دليل الإثبات مطلوب')
  assert.equal(errorMessage({ response: { data: { message: { price: 'موجب', area: 'دقيق' } } } }), 'موجب، دقيق')
})
