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
export const money = (value) =>
  new Intl.NumberFormat('ar-SA', { maximumFractionDigits: 2 }).format(value)
export const priceSuffix = (property) =>
  property.listingType === 'RENT'
    ? property.rentalPeriod === 'MONTHLY'
      ? 'ر.س / شهر'
      : 'ر.س / سنة'
    : 'ر.س'
export function errorMessage(error) {
  const message = error.response?.data?.message
  if (typeof message === 'string') return message
  if (message && typeof message === 'object') return Object.values(message).join('، ')
  return 'تعذّر الاتصال بالخدمة. حاول مرة أخرى.'
}
