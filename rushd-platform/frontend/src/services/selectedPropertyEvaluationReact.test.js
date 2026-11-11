import { test } from 'node:test'
import assert from 'node:assert/strict'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

test('React renders an honest dated assessment with distinct unknown, incomparable, preference and precise evidence', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-evaluation-test-'))
  const server = await createServer({ configFile: false, envFile: false, mode: 'test', cacheDir, server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  try {
    const { default: Result } = await server.ssrLoadModule('/src/components/SelectedPropertyEvaluation.jsx')
    const html = renderToStaticMarkup(React.createElement(Result, { disabled: false, onRetry() {}, evaluation: {
      classification: 'INSUFFICIENT_DATA', evaluatedAt: '2026-10-27T10:00:00Z', rulesVersion: 'selected-property-evaluation-v1',
      coverage: { matched: 0, mismatched: 1, unknown: 1, notComparable: 1 }, preferredMatchedCount: 0, preferredCount: 2,
      evidence: [
        { id: 'maxBudget', requirement: 'REQUIRED', requested: '9007199254740993.01', actual: '9007199254740993.02', unit: 'SAR', outcome: 'NOT_COMPARABLE' },
        { id: 'specification.hasElevator', requirement: 'REQUIRED', requested: { value: true }, actual: null, outcome: 'UNKNOWN' },
        { id: 'specification.hasBalcony', requirement: 'PREFERRED', requested: { value: true }, actual: false, outcome: 'MISMATCHED' },
      ],
    } }))
    assert.match(html, /بيانات غير كافية للحكم/)
    assert.match(html, /غير قابل للمقارنة/)
    assert.match(html, /بيانات غير معروفة/)
    assert.match(html, /تنازل عن هذا التفضيل/)
    assert.match(html, /9٬007٬199٬254٬740٬993٫01/)
    assert.match(html, /27\/10\/2026/)
    assert.match(html, /13:00:00/)
    assert.match(html, /توقيت السعودية/)
    assert.doesNotMatch(html, /2026-10-27T10:00:00Z/)
    assert.match(html, /إعادة تقييم العقار/)
    assert.doesNotMatch(html, /مناسب جدًا/)
    const unavailable = renderToStaticMarkup(React.createElement(Result, { error: 'هذا العقار غير متاح للتقييم حاليًا.', onRetry() {} }))
    assert.match(unavailable, /role="alert"/)
    assert.match(unavailable, /غير متاح للتقييم/)
    assert.doesNotMatch(unavailable, /المعايير المطلوبة/)
  } finally { await server.close(); await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 }) }
})
