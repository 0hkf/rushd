import { test } from 'node:test'
import assert from 'node:assert/strict'
import { registrationErrorMessage, registrationErrors } from './registration.js'
import { loginDestination } from './loginDestination.js'

test('registration validates required fields and BCrypt byte limit without changing the input', () => {
  assert.deepEqual(Object.keys(registrationErrors({ name: '', email: '', password: '' })).sort(), ['email', 'name', 'password'])
  const values = { name: 'عميل', email: 'buyer@example.com', password: 'ع'.repeat(36) }
  assert.deepEqual(registrationErrors(values), {})
  assert.match(registrationErrors({ ...values, password: `${values.password}a` }).password, /72 بايت/)
  assert.match(registrationErrors({ ...values, password: 'a'.repeat(5) }).password, /6 أحرف/)
  assert.equal(values.password, 'ع'.repeat(36))
  assert.match(registrationErrors({ ...values, email: 'a'.repeat(256) }).email, /طويل/)
})

test('registration failure messages cover invalid, conflict, throttled, CSRF and uncertain network outcomes', () => {
  const message = (status) => registrationErrorMessage({ response: { status } })
  assert.match(message(400), /تحقق/)
  assert.match(message(409), /مستخدم بالفعل/)
  assert.match(message(429), /انتظر/)
  assert.match(message(403), /أمان/)
  assert.match(registrationErrorMessage({}), /قد يكون الحساب أُنشئ/)
  assert.match(message(500), /حاليًا/)
})

test('registration forwards only the same safe login destinations', () => {
  assert.equal(loginDestination({ returnTo: '/properties/17' }), '/properties/17')
  assert.equal(loginDestination({ returnTo: '//outside.example' }), '/dashboard')
})
