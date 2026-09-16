// polis-server(SimulationController, M2-4) REST 클라이언트. vite dev proxy가 /api를
// http://localhost:8080으로 중계하므로 여기서는 상대 경로만 쓴다(vite.config.js 참조).
const BASE = '/api/simulation'

async function postJson(path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
  if (!res.ok) {
    throw new Error(await res.text())
  }
  return res.json()
}

// agentCount/mu/threshold/tickCount 전부 선택값 — null/undefined인 필드는 서버가 이전 값을 유지한다.
export function startSimulation({ agentCount, mu, threshold, tickCount } = {}) {
  return postJson('/start', { agentCount, mu, threshold, tickCount })
}

export function stopSimulation() {
  return postJson('/stop', {})
}

export function updateParams({ mu, threshold }) {
  return postJson('/params', { mu, threshold })
}

export async function fetchStatus() {
  const res = await fetch(`${BASE}/status`)
  if (!res.ok) {
    throw new Error(await res.text())
  }
  return res.json()
}
