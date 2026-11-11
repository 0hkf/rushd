import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

test('review is read-only and renders untrusted draft text without executable HTML', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-draft-review-'))
  const server = await createServer({ configFile: false, envFile: false, mode: 'test', cacheDir,
    plugins: [react()], logLevel: 'silent', server: { middlewareMode: true, hmr: false } })
  try {
    const { Differences, default: Review } = await server.ssrLoadModule('/src/components/CloudDraftReview.jsx')
    const table = renderToStaticMarkup(createElement(Differences, { title: 'اختبار', remoteHeading: 'نسخة الخادم',
      rows: [{ key: 'one', label: 'العنوان', local: '<img src=x onerror=alert(1)>', remote: 'نص' }] }))
    assert.match(table, /&lt;img/)
    assert.doesNotMatch(table, /<img/)
    const panel = renderToStaticMarkup(createElement(Review, { draft: { id: 'id' }, property: null,
      local: { values: {}, specifications: {} } }))
    assert.match(panel, /مقارنة للقراءة فقط/)
    assert.match(panel, /عرض النسخ للمراجعة/)
    assert.doesNotMatch(panel, /دمج النسخ تلقائيًا/)
  } finally { await server.close(); await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 }) }
})
