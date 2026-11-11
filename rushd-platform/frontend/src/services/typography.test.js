import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const cssUrl = new URL('../index.css', import.meta.url)
const css = readFileSync(cssUrl, 'utf8')
const html = readFileSync(new URL('../../index.html', import.meta.url), 'utf8')
const expectedFaces = new Map([
  [400, 'Regular'],
  [500, 'Medium'],
  [700, 'Bold'],
  [800, 'Bold'],
  [900, 'Black'],
])

test('all site font weights use supplied local WOFF2 files, with an explicit Bold 800 alias', () => {
  const faces = [...css.matchAll(/@font-face\s*\{([^}]+)\}/g)]
    .map((match) => match[1])
    .filter((face) => /font-family:\s*'Thmanyah Sans'/.test(face))
  assert.equal(faces.length, expectedFaces.size)
  for (const [weight, style] of expectedFaces) {
    const face = faces.find((face) => new RegExp(`font-weight:\\s*${weight}\\s*;`).test(face))
    assert.ok(face, `missing font weight ${weight}`)
    const source = face.match(/src:\s*url\('([^']+)'\)\s*format\('woff2'\)/)?.[1]
    assert.equal(source, `./assets/fonts/thmanyah/thmanyahsans-${style}.woff2`)
    const file = readFileSync(new URL(source, cssUrl))
    assert.equal(file.subarray(0, 4).toString('ascii'), 'wOF2')
    assert.match(face, /font-style:\s*normal\s*;/)
    assert.match(face, /font-display:\s*swap\s*;/)
  }
})

test('one regular font is preloaded and the global font reaches every form control', () => {
  const preloads = [...html.matchAll(/<link\b[^>]*rel="preload"[^>]*>/g)]
    .map((match) => match[0])
    .filter((link) => /as="font"/.test(link))
  assert.equal(preloads.length, 1)
  assert.match(preloads[0], /href="\/src\/assets\/fonts\/thmanyah\/thmanyahsans-Regular\.woff2"/)
  assert.match(preloads[0], /type="font\/woff2"/)
  assert.match(preloads[0], /\bcrossorigin\b/)
  assert.match(css, /:root\s*\{[^}]*font-family:\s*'Thmanyah Sans',\s*system-ui,\s*sans-serif\s*;/)
  assert.match(css, /font-synthesis:\s*none\s*;/)
  assert.match(css, /button,\s*input,\s*select,\s*textarea\s*\{\s*font:\s*inherit\s*;/)
  assert.doesNotMatch(css, /fonts\.googleapis\.com|fonts\.gstatic\.com|Tajawal/)
})

test('the existing beige and burgundy identity and RTL document stay unchanged', () => {
  assert.match(css, /--color-bg:\s*#efe2c9\s*;/)
  assert.match(css, /--color-red:\s*#8f3038\s*;/)
  assert.match(html, /<html\s+lang="ar"\s+dir="rtl">/)
})

test('exact numeric specification tokens stay together in narrow phone or tablet cards', () => {
  const management = readFileSync(new URL('../pages/Management.css', import.meta.url), 'utf8')
  assert.match(management, /\.spec-grid dd bdi\s*\{\s*white-space:\s*nowrap\s*;/)
  assert.match(management, /\.details-specs\s*\{\s*container-type:\s*inline-size\s*;/)
  assert.match(management, /@container\s*\(max-width:\s*420px\)\s*\{\s*\.spec-grid\s*\{\s*grid-template-columns:\s*1fr\s*;/)
})
