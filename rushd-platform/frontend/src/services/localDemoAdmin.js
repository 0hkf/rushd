const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])

function isLoopbackApi(apiBaseUrl) {
  if (typeof apiBaseUrl !== 'string') return false
  try {
    const url = new URL(apiBaseUrl)
    return (
      ['http:', 'https:'].includes(url.protocol) &&
      loopbackHosts.has(url.hostname) &&
      !url.username &&
      !url.password
    )
  } catch {
    return false
  }
}

export function resolveLocalDemoAdmin({
  development,
  hostname,
  apiBaseUrl,
  enabled,
  email,
  password,
}) {
  if (development !== true || !loopbackHosts.has(hostname) || enabled !== 'true') return null
  if (!isLoopbackApi(apiBaseUrl)) return null
  if (
    typeof email !== 'string' ||
    !email.trim() ||
    typeof password !== 'string' ||
    !password.trim()
  )
    return null
  return Object.freeze({ email: email.trim(), password })
}

export function readLocalDemoAdmin(apiBaseUrl) {
  // Keep every VITE credential read inside DEV so production tree-shaking removes the values.
  if (import.meta.env.DEV) {
    return resolveLocalDemoAdmin({
      development: true,
      hostname: window.location.hostname,
      apiBaseUrl,
      enabled: import.meta.env.VITE_LOCAL_DEMO_ADMIN_ENABLED,
      email: import.meta.env.VITE_LOCAL_DEMO_ADMIN_EMAIL,
      password: import.meta.env.VITE_LOCAL_DEMO_ADMIN_PASSWORD,
    })
  }
  return null
}

export class LocalDemoAdminError extends Error {}

export async function signInLocalDemoAdmin(configuration, login) {
  if (!configuration) throw new LocalDemoAdminError('الحساب التجريبي المحلي غير مفعّل أو غير مهيأ.')
  const user = await login(configuration.email, configuration.password)
  if (user?.role !== 'ADMIN')
    throw new LocalDemoAdminError('الحساب التجريبي المهيأ ليس أدمن. تحقق من إعداد الحساب المحلي.')
  return '/dashboard'
}
