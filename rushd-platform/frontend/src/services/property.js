import { formatDecimal } from './decimal.js'

export const propertyTypes = {
  LAND: 'أرض',
  VILLA: 'فيلا',
  APARTMENT: 'شقة',
  BUILDING: 'عمارة',
  COMMERCIAL: 'تجاري',
  FARM: 'مزرعة',
  OTHER: 'أخرى',
}
export const purposes = {
  RESIDENTIAL: 'سكني',
  COMMERCIAL: 'تجاري',
  AGRICULTURAL: 'زراعي',
  INVESTMENT: 'استثماري',
  MIXED: 'متعدد الاستخدام',
  OTHER: 'أخرى',
}
export const statusLabels = { ACTIVE: 'منشور', DRAFT: 'مسودة', INACTIVE: 'غير منشور', SOLD: 'مباع' }
export const facadeLabels = {
  UNKNOWN: 'غير محددة',
  NORTH: 'شمالية',
  SOUTH: 'جنوبية',
  EAST: 'شرقية',
  WEST: 'غربية',
  NORTHEAST: 'شمالية شرقية',
  NORTHWEST: 'شمالية غربية',
  SOUTHEAST: 'جنوبية شرقية',
  SOUTHWEST: 'جنوبية غربية',
  MULTIPLE: 'متعددة',
}
export const money = (value) => formatDecimal(value) ?? 'غير محدد'
export const priceSuffix = (property) =>
  property.listingType === 'RENT'
    ? property.rentalPeriod === 'MONTHLY'
      ? 'ر.س / شهر'
      : 'ر.س / سنة'
    : 'ر.س'
export function errorMessage(error) {
  const message = error.response?.data?.message
  const known = {
    'An unexpected error occurred': 'تعذّر إكمال الطلب من الخادم. أعد المحاولة، وإن استمرت المشكلة تواصل مع الإدارة.',
    'Invalid email or password': 'البريد الإلكتروني أو كلمة المرور غير صحيحة.',
    'Authentication is required': 'سجّل الدخول للمتابعة.',
    'Insufficient permission': 'لا يملك حسابك صلاحية تنفيذ هذا الإجراء.',
    'Invalid CSRF token': 'تعذّر التحقق من الجلسة. أعد المحاولة أو سجّل الدخول مجددًا.',
  }
  if (typeof message === 'string' && Object.hasOwn(known, message)) return known[message]
  if (typeof message === 'string') return message
  if (message && typeof message === 'object') return Object.values(message).join('، ')
  return 'تعذّر الاتصال بالخدمة. حاول مرة أخرى.'
}
