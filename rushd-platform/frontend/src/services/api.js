import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 5000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

// Only the non-authentication CSRF token is JavaScript-readable, in memory.
let csrfToken = null
let csrfFlight = null
let refreshFlight = null
let authGeneration = 0
let renewalFailed = false
const listeners = new Set()
export const onAuthenticationLost = (listener) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export const markAuthenticated = () => {
  renewalFailed = false
  authGeneration += 1
}
export const stopRenewal = () => {
  renewalFailed = true
  authGeneration += 1
}
function lostAuthentication() {
  if (renewalFailed) return
  stopRenewal()
  listeners.forEach((listener) => listener())
}
function authPath(config) {
  return new URL(config.url, api.defaults.baseURL).pathname
}
const noRenewal = new Set([
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/csrf',
])
async function csrf() {
  if (csrfToken) return csrfToken
  if (!csrfFlight)
    csrfFlight = api
      .get('/api/auth/csrf')
      .then(({ data }) => {
        csrfToken = data.token
        return csrfToken
      })
      .finally(() => {
        csrfFlight = null
      })
  return csrfFlight
}

api.interceptors.request.use(async (config) => {
  config._authGeneration ??= authGeneration
  if (['post', 'put', 'patch', 'delete'].includes(config.method?.toLowerCase())) {
    config.headers['X-XSRF-TOKEN'] = await csrf()
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    if (
      ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'].includes(
        authPath(response.config),
      )
    ) {
      csrfToken = null // Server clears CSRF cookie at lifecycle boundaries; bootstrap a fresh one next time.
    }
    return response
  },
  async (error) => {
    const config = error.config
    if (!config || error.response?.status !== 401 || noRenewal.has(authPath(config))) throw error
    if (config._retried) {
      lostAuthentication()
      throw error
    }
    if (renewalFailed) throw error
    config._retried = true
    {
      // Requests issued before the already-completed refresh only need a retry, not another rotation.
      if (config._authGeneration === authGeneration) {
        if (!refreshFlight) {
          const generation = authGeneration
          refreshFlight = api
            .post('/api/auth/refresh')
            .then((response) => {
              if (generation !== authGeneration)
                throw new Error('Authentication changed during renewal')
              authGeneration += 1
              return response
            })
            .catch((failure) => {
              lostAuthentication()
              throw failure
            })
            .finally(() => {
              refreshFlight = null
            })
        }
        await refreshFlight
      }
      if (renewalFailed) throw error
      return api(config)
    }
  },
)

export async function loginSession(email, password) {
  stopRenewal()
  if (refreshFlight) await refreshFlight.catch(() => {})
  return api.post('/api/auth/login', { email, password })
}
export async function logoutSession() {
  stopRenewal()
  // Wait for an in-flight rotation so logout revokes the last cookie, not its predecessor.
  if (refreshFlight) await refreshFlight.catch(() => {})
  await api.post('/api/auth/logout')
}
export default api
