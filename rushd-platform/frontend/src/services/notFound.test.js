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

// Render the actual React route table, not a regex over its source. No new test dependency or API request.
test('unknown routes render the Arabic 404 with explicit safe navigation', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-react-test-'))
  const server = await createServer({
    configFile: false, envFile: false, mode: 'test', cacheDir,
    plugins: [react()], logLevel: 'silent',
    server: { middlewareMode: true, hmr: false },
  })
  try {
    const { AppRoutes } = await server.ssrLoadModule('/src/App.jsx')
    for (const pathname of ['/does-not-exist', '/dashboard/unknown', '/unknown?q=private']) {
      const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [pathname] }, createElement(AppRoutes)))
      assert.match(html, /<h1[^>]*id="not-found-title"[^>]*tabindex="-1"[^>]*>الصفحة غير موجودة<\/h1>/)
      assert.equal((html.match(/<h1\b/g) ?? []).length, 1)
      assert.match(html, /href="\/properties"[^>]*>استعراض العقارات<\/a>/)
      assert.match(html, /href="\/"[^>]*>العودة للرئيسية<\/a>/)
      assert.doesNotMatch(html, /q=private/)
    }
  } finally {
    await server.close()
    await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 })
  }
})
