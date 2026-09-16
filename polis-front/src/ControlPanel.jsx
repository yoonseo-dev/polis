import { useState } from 'react'

// M2-4 REST 제어 API를 그대로 노출하는 최소 폼. agentCount/tickCount는 재시작(=/start)에서만
// 바뀌고, mu/threshold는 실행 중에도 /params로 즉시 반영된다(SimulationRunner 주석 참조).
export function ControlPanel({ status, onStart, onStop, onApplyParams }) {
  const [agentCount, setAgentCount] = useState(100)
  const [tickCount, setTickCount] = useState(200)
  const [mu, setMu] = useState(0.01)
  const [threshold, setThreshold] = useState(1.5)

  return (
    <form
      style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'flex-end' }}
      onSubmit={(e) => e.preventDefault()}
    >
      <label>
        agentCount
        <input
          type="number"
          value={agentCount}
          min={1}
          onChange={(e) => setAgentCount(Number(e.target.value))}
        />
      </label>
      <label>
        tickCount
        <input
          type="number"
          value={tickCount}
          min={1}
          onChange={(e) => setTickCount(Number(e.target.value))}
        />
      </label>
      <label>
        μ (학습률)
        <input
          type="number"
          value={mu}
          step={0.01}
          min={0}
          max={1}
          onChange={(e) => setMu(Number(e.target.value))}
        />
      </label>
      <label>
        threshold
        <input
          type="number"
          value={threshold}
          step={0.1}
          min={0}
          onChange={(e) => setThreshold(Number(e.target.value))}
        />
      </label>

      <button type="button" onClick={() => onStart({ agentCount, tickCount, mu, threshold })}>
        start
      </button>
      <button type="button" onClick={onStop} disabled={!status?.running}>
        stop
      </button>
      <button type="button" onClick={() => onApplyParams({ mu, threshold })} disabled={!status?.running}>
        μ/threshold 즉시 반영
      </button>
    </form>
  )
}
