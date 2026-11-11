const routeTitles = Object.freeze({ '/': 'الرئيسية', '/properties': 'عقارات مكة', '/login': 'تسجيل الدخول', '/register': 'إنشاء حساب', '/dashboard': 'حسابك وإدارة العقارات', '/property-needs': 'احتياجاتي العقارية', '/favorites': 'مفضلتي', '/compare': 'مقارنة العقارات', '/admin/market-references': 'مراجع السوق' })
export function interfaceTitle(pathname) {
  const title = routeTitles[pathname] || (/^\/properties\/[^/]+$/.test(pathname) ? 'تفاصيل العقار' : 'الصفحة غير موجودة')
  return `${title} | روافد العقارية`
}
// Presentation only. No Number conversion, truncation or rounding of monetary values.
export function decimalSizeClass(value) {
  if (typeof value !== 'string') return undefined
  return value.length > 16 ? 'long-decimal' : value.length > 11 ? 'medium-decimal' : undefined
}
export function propertyStatusNotice(status) {
  if (status === 'ACTIVE') return ''
  return ({ SOLD: 'هذا العرض مباع. تبقى مواصفاته للمرجعية والمقارنة، ولا يتاح التواصل أو تقييم الاحتياجات له.', DRAFT: 'معاينة إدارية لمسودة؛ هذا العرض لا يظهر للزوار.', INACTIVE: 'معاينة إدارية لعرض غير منشور؛ هذا العرض لا يظهر للزوار.' })[status] || 'إتاحة هذا العرض غير محددة حاليًا.'
}
export function revealAnchorTarget(id, root = document) {
  const target = root.getElementById(id)
  if (!target) return false
  for (let parent = target.parentElement; parent; parent = parent.parentElement) if (parent.tagName === 'DETAILS') parent.open = true
  return focusPanel(target)
}
export function focusPanel(target) {
  if (!target) return false
  target.scrollIntoView({ block: 'start', behavior: 'auto' })
  target.focus({ preventScroll: true })
  return true
}
