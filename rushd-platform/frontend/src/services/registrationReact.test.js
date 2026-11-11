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

test('registration offers named fields and no role selector, with safe login navigation', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-register-test-'))
  const server = await createServer({ configFile: false, envFile: false, mode: 'test', cacheDir,
    plugins: [react()], logLevel: 'silent', server: { middlewareMode: true, hmr: false } })
  try {
    const { default: Register } = await server.ssrLoadModule('/src/pages/Register.jsx')
    const { AuthContext } = await server.ssrLoadModule('/src/services/authState.js')
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/register'] },
      createElement(AuthContext.Provider, { value: { user: null, loading: false } }, createElement(Register))))
    assert.match(html, /إنشاء حساب عميل/)
    for (const id of ['register-name', 'register-email', 'register-password']) assert.match(html, new RegExp(`id="${id}"`))
    assert.doesNotMatch(html, /<select|name="role"/)
    assert.match(html, /href="\/login"/)
  } finally { await server.close(); await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 }) }
})
