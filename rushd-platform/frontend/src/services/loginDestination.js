const destinations = new Set(['/dashboard', '/property-needs', '/favorites', '/admin/market-references'])

// Only explicitly supported local pages can be selected after authentication.
export function loginDestination(state) {
  const target = state?.returnTo
  if (typeof target !== 'string') return '/dashboard'
  if (destinations.has(target)) return target
  const match = /^\/properties\/([1-9][0-9]{0,18})$/.exec(target)
  return match && BigInt(match[1]) <= 9223372036854775807n ? target : '/dashboard'
}
