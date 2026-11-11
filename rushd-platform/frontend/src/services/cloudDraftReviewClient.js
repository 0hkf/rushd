import axios from 'axios'
import api from './api.js'

// Review GETs must not log out and unmount an editor containing unsaved changes.
// No new authority is granted: an expired session is still rejected by the server.
export function createDraftReviewClient() {
  return axios.create({ baseURL: api.defaults.baseURL, timeout: 5000, withCredentials: true })
}
