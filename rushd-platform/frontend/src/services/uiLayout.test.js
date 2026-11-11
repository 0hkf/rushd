import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (file) => readFileSync(new URL(file, import.meta.url), 'utf8')
// Source-level layout guards; actual rendering/geometry is checked separately in the browser.
test('all property surfaces use the same action group without duplicating controls', () => {
  for (const name of ['Properties', 'PropertyDetails', 'PropertyNeeds', 'Favorites']) {
    const source = read(`../pages/${name}.jsx`)
    assert.match(source, /<PropertyActions\b/)
    assert.doesNotMatch(source, /<FavoriteButton\b|<ComparisonButton\b/)
  }
  const component = read('../components/PropertyActions.jsx')
  assert.match(component, /showDetails && property/)
  assert.match(component, /property && <ComparisonButton/)
  assert.match(component, /removeOnly=\{removeOnly\}/)
  assert.match(component, /state=\{detailsState\}/)
})
test('action group reserves explicit gaps, full-width touch controls and a two-column wide-container variant', () => {
  const css = read('../components/PropertyActions.css')
  assert.match(css, /\.property-actions\s*\{[^}]*gap:\s*12px/)
  assert.match(css, /\.property-actions-secondary\s*\{[^}]*gap:\s*12px/)
  assert.match(css, /\.property-actions \.button\s*\{[^}]*width:\s*100%[^}]*min-height:\s*48px/)
  assert.match(css, /@container property-actions \(min-width: 300px\)/)
  assert.match(css, /\.favorite-control, \.property-actions \.comparison-button \{ margin: 0/)
})
test('card content is flexible so variable titles do not leave action rows at arbitrary positions', () => {
  const css = read('../pages/Pages.css')
  assert.match(css, /\.property-card\s*\{[^}]*display:\s*flex/)
  assert.match(css, /\.property-body\s*\{[^}]*flex:\s*1/)
  assert.match(css, /\.property-body \.property-actions \{ margin-top: auto/)
})
test('button hover no longer moves the hit target; focus remains visible on burgundy surfaces', () => {
  const css = read('../index.css')
  assert.doesNotMatch(css, /\.button:hover:not\(:disabled\)\s*\{[^}]*transform:/)
  assert.match(css, /\.button:focus-visible\s*\{[^}]*outline:/)
  assert.match(css, /\.panel-red \.button:focus-visible \{ outline-color: var\(--color-on-red\)/)
})
test('login form leads document order with one h1 while its desktop intro placement is retained', () => {
  const component = read('../pages/Login.jsx')
  assert.ok(component.indexOf('className="login-form-panel') < component.indexOf('className="login-intro'))
  assert.equal((component.match(/<h1\b/g) ?? []).length, 1)
  assert.match(component, /<h1 id="login-title"/)
  assert.match(read('../pages/Pages.css'), /\.login-intro\s*\{\s*grid-column: 1;\s*grid-row: 1;/)
  assert.match(component, /if \(authLoading\) return[^\n]+role="status"/)
})
test('comparison keeps price/area caveats available, puts longer guidance behind an accessible disclosure', () => {
  const component = read('../pages/PropertyComparison.jsx')
  assert.match(component, /<details className="comparison-guidance"><summary>كيف أقرأ المقارنة/)
  assert.match(component, /الأسعار بفترتها الأصلية دون تحويل/)
  assert.match(component, /aria-describedby="comparison-table-help" tabIndex=\{0\}/)
  assert.match(component, /data-count=\{ids.length\}/)
})
test('comparison pins the RTL field header while retaining local horizontal scrolling and exact numeric tokens', () => {
  const css = read('../components/PropertyComparison.css')
  assert.match(css, /\.comparison-table th:first-child\s*\{[^}]*position: sticky;[^}]*inset-inline-start: 0/)
  assert.match(css, /\.comparison-table-scroll\s*\{[^}]*overflow-x: auto/)
  assert.match(css, /\.comparison-table td bdi\s*\{[^}]*white-space: nowrap/)
  assert.match(css, /--comparison-label-width: 84px/)
  assert.match(css, /padding-inline: 8px/)
})
test('mobile management gets full-width controls and details put price before long specifications', () => {
  const css = read('../pages/Management.css')
  assert.match(css, /\.details-price \{ grid-row: 1; \}/)
  assert.match(css, /\.managed-row \.row-actions, \.admin-form-panel \.form-actions\s*\{[^}]*width: 100%/)
  assert.match(css, /\.details-price strong bdi \{ white-space: nowrap/)
  assert.match(css, /font-size: clamp\(1.5rem, 10cqw, 2.5rem\)/)
})
test('catalog puts results before optional filter detail without unmounting fields or hiding applied criteria', () => {
  const component = read('../pages/Properties.jsx')
  assert.match(component, /<FormSection title="تصفية العروض"/)
  assert.match(component, /aria-label="المرشحات المطبقة"/)
  assert.match(component, /focusResults\.current = true/)
  assert.match(component, /id="catalog-results" tabIndex=\{-1\}/)
})
