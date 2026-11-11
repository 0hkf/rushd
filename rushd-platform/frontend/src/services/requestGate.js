// Abort work when possible, and reject late completions even if a client ignores abort.
export function createRequestGate() {
  let generation = 0
  let current = null
  return {
    begin() {
      current?.controller.abort()
      current = { generation: ++generation, controller: new AbortController() }
      return current
    },
    isCurrent(ticket) {
      return ticket === current && ticket.generation === generation && !ticket.controller.signal.aborted
    },
    cancel() {
      generation += 1
      current?.controller.abort()
      current = null
    },
  }
}
