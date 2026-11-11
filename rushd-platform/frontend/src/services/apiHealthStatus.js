// /health checks the API process only; it does not establish database readiness.
export function apiHealthStatusText(status) {
  if (status === 'loading') return 'جارٍ التحقق من اتصال الخادم'
  if (status === 'ok') return 'خادم الواجهة البرمجية يستجيب؛ لم تُفحص حالة قاعدة البيانات.'
  return 'تعذر الاتصال بخادم الواجهة البرمجية حاليًا.'
}
