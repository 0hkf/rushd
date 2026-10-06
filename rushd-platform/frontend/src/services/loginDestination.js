const destinations = new Set(['/dashboard', '/property-needs'])

// Only explicitly supported local pages can be selected after authentication.
export function loginDestination(state) {
  return typeof state?.returnTo === 'string' && destinations.has(state.returnTo)
    ? state.returnTo
    : '/dashboard'
}
