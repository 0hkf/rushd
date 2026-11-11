import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

test('disclosures keep inputs mounted, reveal errors, and password controls default to masked non-submit buttons', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'rushd-interface-test-'))
  const server = await createServer({ configFile: false, envFile: false, mode: 'test', cacheDir, plugins: [react()], logLevel: 'silent', server: { middlewareMode: true, hmr: false } })
  try {
    const { default: FormSection } = await server.ssrLoadModule('/src/components/FormSection.jsx')
    const { default: PasswordInput } = await server.ssrLoadModule('/src/components/PasswordInput.jsx')
    const section = (props) => renderToStaticMarkup(createElement(FormSection, { title: 'المساحات', hint: 'اختياري', ...props }, createElement('input', { id: 'exact-area', defaultValue: '9007199254.01' })))
    assert.match(section({}), /<details[^>]*>/); assert.doesNotMatch(section({}), /<details[^>]*open/)
    assert.match(section({}), /value="9007199254.01"/)
    assert.match(section({ hasError: true }), /<details[^>]*open=""/); assert.match(section({ hasError: true }), /راجع الحقول/)
    const password = renderToStaticMarkup(createElement(PasswordInput, { id: 'test-password', autoComplete: 'new-password', disabled: true, value: 'Synthetic-Only', onChange() {} }))
    assert.match(password, /type="password"/); assert.match(password, /autoComplete="new-password"/)
    assert.match(password, /<button type="button"[^>]*aria-pressed="false"[^>]*aria-controls="test-password"[^>]*disabled/)
    assert.match(password, /إظهار كلمة المرور/)
  } finally { await server.close(); await rm(cacheDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 }) }
})
