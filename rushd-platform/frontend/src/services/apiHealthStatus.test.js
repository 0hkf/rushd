import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { apiHealthStatusText } from './apiHealthStatus.js'

test('health status does not claim database readiness from a successful API response', () => {
  assert.match(apiHealthStatusText('loading'), /جارٍ التحقق من اتصال الخادم/)
  assert.match(apiHealthStatusText('ok'), /يستجيب/)
  assert.match(apiHealthStatusText('ok'), /لم تُفحص حالة قاعدة البيانات/)
  assert.match(apiHealthStatusText('error'), /تعذر الاتصال/)
  assert.equal(apiHealthStatusText('unexpected'), apiHealthStatusText('error'))
})

test('home status is bound to the existing health request and accessible live status', () => {
  const source = readFileSync(new URL('../pages/Home.jsx', import.meta.url), 'utf8')
  assert.match(source, /\.get\('\/health'\)/)
  assert.match(source, /role="status"/)
  assert.match(source, /apiHealthStatusText\(apiStatus\)/)
})
