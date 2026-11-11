import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
test('market page is gated and workspace has explicit uploads, safe unknowns and named inputs', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-market-test-'))
  const server = await createServer({ configFile: false, envFile: false, mode: 'test', cacheDir, plugins: [react()], logLevel: 'silent', server: { middlewareMode: true, hmr: false } })
  try {
    const { default: Page, MarketWorkspace } = await server.ssrLoadModule('/src/pages/MarketReferences.jsx')
    const { AuthContext } = await server.ssrLoadModule('/src/services/authState.js')
    const render = (user) => renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(AuthContext.Provider, { value: { user, loading: false } }, createElement(Page))))
    assert.match(render(null), /تسجيل الدخول/); assert.match(render({ role: 'BUYER' }), /للإدارة فقط/)
    assert.match(render({ role: 'ADMIN' }), /غير مفعلة/); assert.doesNotMatch(render({ role: 'BUYER' }), /<form/)
    const html = renderToStaticMarkup(createElement(MarketWorkspace, { client: {} }))
    for (const id of ['market-title', 'market-officialUrl', 'market-fact-price', 'market-fact-area', 'market-propertyType', 'market-areaMeaning', 'market-source-reference']) assert.match(html, new RegExp(`id="${id}"`))
    assert.match(html, /غير معلوم — لا نستنتج/); assert.match(html, /type="text" inputMode="decimal" dir="ltr"/)
    assert.match(html, /صفقات بيع/); assert.match(html, /ليس تحليلًا/); assert.doesNotMatch(html, /type="number"|eligible|dangerouslySetInnerHTML/)
    assert.match(html, /ميلادي YYYY-MM-DD/); assert.match(html, /pattern="\[0-9\]\{4\}-\[0-9\]\{2\}-\[0-9\]\{2\}"/)
  } finally { await server.close(); await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 }) }
})
